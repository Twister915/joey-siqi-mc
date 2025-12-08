package sh.joey.mc.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence of player homes to JSON files.
 * Each player's homes are stored in a separate file at:
 * data/player-{uuid}/homes.json
 */
public final class HomeStorage {

    private static final String DATA_DIR = "data";
    private static final String PLAYER_PREFIX = "player-";
    private static final String HOMES_FILE = "homes.json";

    private final Path dataDirectory;
    private final Gson gson;
    private final JavaPlugin plugin;

    // playerId -> (homeName -> Home)
    private final Map<UUID, Map<String, Home>> playerHomes = new ConcurrentHashMap<>();

    public HomeStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath().resolve(DATA_DIR);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public void load() {
        playerHomes.clear();

        if (!Files.exists(dataDirectory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, PLAYER_PREFIX + "*")) {
            for (Path playerDir : stream) {
                if (!Files.isDirectory(playerDir)) {
                    continue;
                }

                String dirName = playerDir.getFileName().toString();
                if (!dirName.startsWith(PLAYER_PREFIX)) {
                    continue;
                }

                String uuidString = dirName.substring(PLAYER_PREFIX.length());
                UUID playerId;
                try {
                    playerId = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid player directory name: " + dirName);
                    continue;
                }

                loadPlayerHomes(playerId, playerDir);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to scan player data directories: " + e.getMessage());
        }
    }

    private void loadPlayerHomes(UUID playerId, Path playerDir) {
        Path homesFile = playerDir.resolve(HOMES_FILE);
        if (!Files.exists(homesFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(homesFile)) {
            Type type = new TypeToken<Map<String, Home>>() {}.getType();
            Map<String, Home> homes = gson.fromJson(reader, type);
            if (homes != null && !homes.isEmpty()) {
                playerHomes.put(playerId, new ConcurrentHashMap<>(homes));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load homes for player " + playerId + ": " + e.getMessage());
        }
    }

    private void savePlayerHomes(UUID playerId) {
        Map<String, Home> homes = playerHomes.get(playerId);

        Path playerDir = getPlayerDirectory(playerId);
        Path homesFile = playerDir.resolve(HOMES_FILE);

        try {
            if (homes == null || homes.isEmpty()) {
                // Delete the file if no homes exist
                if (Files.exists(homesFile)) {
                    Files.delete(homesFile);
                    // Try to delete the directory if empty
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerDir)) {
                        if (!stream.iterator().hasNext()) {
                            Files.delete(playerDir);
                        }
                    }
                }
                return;
            }

            Files.createDirectories(playerDir);
            try (Writer writer = Files.newBufferedWriter(homesFile)) {
                gson.toJson(homes, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save homes for player " + playerId + ": " + e.getMessage());
        }
    }

    private Path getPlayerDirectory(UUID playerId) {
        return dataDirectory.resolve(PLAYER_PREFIX + playerId.toString());
    }

    public void setHome(UUID playerId, Home home) {
        playerHomes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(home.name().toLowerCase(), home);
        savePlayerHomes(playerId);
    }

    public boolean deleteHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) {
            return false;
        }
        boolean removed = homes.remove(name.toLowerCase()) != null;
        if (removed) {
            savePlayerHomes(playerId);
        }
        return removed;
    }

    public Optional<Home> getHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(homes.get(name.toLowerCase()));
    }

    public List<Home> getOwnedHomes(UUID playerId) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) {
            return List.of();
        }
        return new ArrayList<>(homes.values());
    }

    public boolean hasAnyHomes(UUID playerId) {
        Map<String, Home> homes = playerHomes.get(playerId);
        return homes != null && !homes.isEmpty();
    }

    public List<SharedHomeEntry> getSharedWithPlayer(UUID playerId) {
        List<SharedHomeEntry> result = new ArrayList<>();
        playerHomes.forEach((ownerId, homes) -> {
            if (ownerId.equals(playerId)) return;
            homes.values().stream()
                    .filter(home -> home.isSharedWith(playerId))
                    .forEach(home -> result.add(new SharedHomeEntry(ownerId, home)));
        });
        return result;
    }

    public boolean shareHome(UUID ownerId, String homeName, UUID targetId) {
        Map<String, Home> homes = playerHomes.get(ownerId);
        if (homes == null) {
            return false;
        }
        Home home = homes.get(homeName.toLowerCase());
        if (home == null) {
            return false;
        }
        homes.put(homeName.toLowerCase(), home.withSharedPlayer(targetId));
        savePlayerHomes(ownerId);
        return true;
    }

    public boolean unshareHome(UUID ownerId, String homeName, UUID targetId) {
        Map<String, Home> homes = playerHomes.get(ownerId);
        if (homes == null) {
            return false;
        }
        Home home = homes.get(homeName.toLowerCase());
        if (home == null) {
            return false;
        }
        homes.put(homeName.toLowerCase(), home.withoutSharedPlayer(targetId));
        savePlayerHomes(ownerId);
        return true;
    }

    public record SharedHomeEntry(UUID ownerId, Home home) {}
}
