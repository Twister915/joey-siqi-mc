package sh.joey.mc.player;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.nickname.Nickname;
import sh.joey.mc.nickname.NicknameManager;
import sh.joey.mc.nickname.NicknameStorage;
import sh.joey.mc.session.PlayerSessionStorage;

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
