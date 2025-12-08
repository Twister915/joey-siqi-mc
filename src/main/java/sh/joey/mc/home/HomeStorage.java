package sh.joey.mc.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence of player homes to JSON.
 */
public final class HomeStorage {

    private final Path dataFile;
    private final Gson gson;
    private final JavaPlugin plugin;

    // playerId -> (homeName -> Home)
    private final Map<UUID, Map<String, Home>> playerHomes = new ConcurrentHashMap<>();

    public HomeStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("homes.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public void load() {
        if (!Files.exists(dataFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Map<UUID, Map<String, Home>>>() {}.getType();
            Map<UUID, Map<String, Home>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                playerHomes.clear();
                loaded.forEach((uuid, homes) ->
                    playerHomes.put(uuid, new ConcurrentHashMap<>(homes)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load homes: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                gson.toJson(playerHomes, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save homes: " + e.getMessage());
        }
    }

    public void setHome(UUID playerId, Home home) {
        playerHomes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(home.name().toLowerCase(), home);
        save();
    }

    public boolean deleteHome(UUID playerId, String name) {
        Map<String, Home> homes = playerHomes.get(playerId);
        if (homes == null) {
            return false;
        }
        boolean removed = homes.remove(name.toLowerCase()) != null;
        if (removed) {
            save();
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
        save();
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
        save();
        return true;
    }

    public record SharedHomeEntry(UUID ownerId, Home home) {}
}
