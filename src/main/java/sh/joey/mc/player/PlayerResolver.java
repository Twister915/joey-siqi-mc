package sh.joey.mc.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.nickname.Nickname;
import sh.joey.mc.nickname.NicknameManager;
import sh.joey.mc.nickname.NicknameStorage;
import sh.joey.mc.session.PlayerSessionStorage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Central service for resolving player names (usernames or nicknames) to player UUIDs.
 * <p>
 * This service provides a unified way to resolve player identity across all commands,
 * supporting both Minecraft usernames and custom nicknames.
 * <p>
 * Resolution priority:
 * <ol>
 *   <li>Online player by exact username match</li>
 *   <li>Online player by nickname match</li>
 *   <li>Database lookup by username</li>
 *   <li>Database lookup by nickname</li>
 * </ol>
 */
public final class PlayerResolver {

    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage sessionStorage;
    private final NicknameManager nicknameManager;
    private final NicknameStorage nicknameStorage;

    public PlayerResolver(SiqiJoeyPlugin plugin, PlayerSessionStorage sessionStorage,
                          NicknameManager nicknameManager, NicknameStorage nicknameStorage) {
        this.plugin = plugin;
        this.sessionStorage = sessionStorage;
        this.nicknameManager = nicknameManager;
        this.nicknameStorage = nicknameStorage;
    }

    /**
     * Resolve a player name (username or nickname) to a UUID.
     * <p>
     * Resolution priority:
     * <ol>
     *   <li>Online player by exact username match</li>
     *   <li>Online player by nickname match</li>
     *   <li>Database lookup by username</li>
     *   <li>Database lookup by nickname</li>
     * </ol>
     *
     * @param input the player name (username or nickname)
     * @return Maybe containing the player's UUID, or empty if not found
     */
    public Maybe<UUID> resolvePlayerId(String input) {
        return Maybe.defer(() -> {
            // 1. Check online players by username (case-insensitive)
            Player onlineByUsername = Bukkit.getPlayer(input);
            if (onlineByUsername != null) {
                return Maybe.just(onlineByUsername.getUniqueId());
            }

            // 2. Check online players by nickname
            Optional<Player> onlineByNickname = nicknameManager.findOnlinePlayerByNickname(input);
            if (onlineByNickname.isPresent()) {
                return Maybe.just(onlineByNickname.get().getUniqueId());
            }

            // 3. Fall back to database lookup by username, then nickname
            return sessionStorage.findPlayerIdByName(input)
                    .switchIfEmpty(nicknameStorage.findPlayerIdByNickname(input));
        });
    }

    /**
     * Resolve a player name to a UUID, with Mojang API fallback.
     * <p>
     * This method first tries all local resolution methods (online players, database),
     * then falls back to Mojang's API if the player has never joined the server.
     * <p>
     * Use this for commands like /ban where you need to support players who have
     * never joined the server.
     *
     * @param input the player username (nicknames not supported for Mojang lookup)
     * @return Maybe containing the player's UUID, or empty if not found anywhere
     */
    public Maybe<UUID> resolvePlayerIdWithMojang(String input) {
        return resolvePlayerId(input)
                .switchIfEmpty(lookupMojangUUID(input));
    }

    /**
     * Look up a player's UUID from Mojang's API.
     * <p>
     * This is a blocking HTTP call that runs on the IO scheduler.
     *
     * @param username the exact Minecraft username
     * @return Maybe containing the UUID, or empty if the username doesn't exist
     */
    public Maybe<UUID> lookupMojangUUID(String username) {
        return Maybe.<UUID>create(emitter -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MOJANG_API_URL + username))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String id = json.get("id").getAsString();
                    // Mojang returns UUID without dashes, need to insert them
                    UUID uuid = parseUUIDWithoutDashes(id);
                    emitter.onSuccess(uuid);
                } else if (response.statusCode() == 404 || response.statusCode() == 204) {
                    // Player doesn't exist
                    emitter.onComplete();
                } else {
                    plugin.getLogger().warning("Mojang API returned status " + response.statusCode() + " for username: " + username);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to lookup UUID from Mojang for " + username + ": " + e.getMessage());
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io());
    }

    private static UUID parseUUIDWithoutDashes(String id) {
        // Insert dashes: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        String withDashes = id.substring(0, 8) + "-" +
                id.substring(8, 12) + "-" +
                id.substring(12, 16) + "-" +
                id.substring(16, 20) + "-" +
                id.substring(20);
        return UUID.fromString(withDashes);
    }

    /**
     * Resolve a player name to an online Player object.
     * Checks both usernames and nicknames.
     *
     * @param input the player name (username or nickname)
     * @return Optional containing the online Player, or empty if not online
     */
    public Optional<Player> resolveOnlinePlayer(String input) {
        // 1. Check by username (case-insensitive)
        Player byUsername = Bukkit.getPlayer(input);
        if (byUsername != null) {
            return Optional.of(byUsername);
        }

        // 2. Check by nickname
        return nicknameManager.findOnlinePlayerByNickname(input);
    }

    /**
     * Get tab completions for player names.
     * Includes both usernames and nicknames of online players.
     * Falls back to database for partial prefix matching.
     *
     * @param prefix the prefix to match
     * @param limit maximum number of results
     * @return Single containing the list of matching names
     */
    public Single<List<String>> getCompletions(String prefix, int limit) {
        String normalizedPrefix = prefix.toLowerCase();

        return Single.defer(() -> {
            Set<String> completions = new LinkedHashSet<>();

            // Add matching online player usernames
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(normalizedPrefix)) {
                    completions.add(player.getName());
                }
            }

            // Add matching online player nicknames
            for (Player player : Bukkit.getOnlinePlayers()) {
                String nickname = nicknameManager.getNickname(player.getUniqueId());
                if (nickname != null && Nickname.normalize(nickname).startsWith(normalizedPrefix)) {
                    completions.add(nickname);
                }
            }

            // If we have enough completions, return them
            if (completions.size() >= limit) {
                return Single.just(new ArrayList<>(completions).subList(0, Math.min(limit, completions.size())));
            }

            // Fall back to database for more completions
            int remaining = limit - completions.size();

            return sessionStorage.findUsernamesByPrefix(prefix, remaining)
                    .toList()
                    .flatMap(usernames -> {
                        completions.addAll(usernames);
                        int stillRemaining = limit - completions.size();
                        if (stillRemaining <= 0) {
                            return Single.just(new ArrayList<>(completions).subList(0, Math.min(limit, completions.size())));
                        }

                        return nicknameStorage.findNicknamesByPrefix(prefix, stillRemaining)
                                .toList()
                                .map(nicknames -> {
                                    completions.addAll(nicknames);
                                    List<String> result = new ArrayList<>(completions);
                                    return result.subList(0, Math.min(limit, result.size()));
                                });
                    });
        });
    }

    /**
     * Get username for a player ID.
     * Checks online players first, then falls back to database.
     *
     * @param playerId the player's UUID
     * @return Maybe containing the username, or empty if not found
     */
    public Maybe<String> getUsername(UUID playerId) {
        return Maybe.defer(() -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                return Maybe.just(online.getName());
            }
            return sessionStorage.findUsernameById(playerId);
        });
    }

    /**
     * Get display name for a player (nickname if set, otherwise username).
     * Checks online players first, then falls back to database.
     *
     * @param player the online player
     * @return the display name
     */
    public String getDisplayName(Player player) {
        return nicknameManager.getDisplayName(player);
    }

    /**
     * Get display name for a player ID (nickname if set, otherwise username).
     * Checks nickname cache, online players, then database.
     *
     * @param playerId the player's UUID
     * @return Maybe containing the display name, or empty if not found
     */
    public Maybe<String> getDisplayName(UUID playerId) {
        return Maybe.defer(() -> {
            // Check nickname cache first
            String nickname = nicknameManager.getNickname(playerId);
            if (nickname != null) {
                return Maybe.just(nickname);
            }

            // Fall back to username
            return getUsername(playerId);
        });
    }
}
