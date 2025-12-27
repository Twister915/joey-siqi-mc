package sh.joey.mc.statue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Fetches player skins from Mojang's API.
 */
public final class MojangSkinApi {

    private static final String UUID_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_API = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public MojangSkinApi() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Fetches a player's skin texture by username.
     *
     * @param username the Minecraft username
     * @return Single emitting the SkinTexture, or error if not found
     */
    public Single<SkinTexture> fetchSkin(String username) {
        return Single.fromCallable(() -> fetchSkinBlocking(username))
                .subscribeOn(Schedulers.io());
    }

    private SkinTexture fetchSkinBlocking(String username) throws IOException, InterruptedException {
        // Step 1: Get UUID from username
        String uuid = fetchUuid(username);

        // Step 2: Get profile with textures
        String skinUrl = fetchSkinUrl(uuid);

        // Step 3: Download skin PNG
        byte[] skinBytes = downloadSkin(skinUrl);

        // Step 4: Parse PNG
        return SkinTexture.fromPng(skinBytes);
    }

    private String fetchUuid(String username) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UUID_API + username))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404 || response.statusCode() == 204) {
            throw new PlayerNotFoundException("Player '" + username + "' not found");
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch UUID: HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("id").getAsString();
    }

    private String fetchSkinUrl(String uuid) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_API + uuid))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch profile: HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray properties = json.getAsJsonArray("properties");

        for (int i = 0; i < properties.size(); i++) {
            JsonObject prop = properties.get(i).getAsJsonObject();
            if ("textures".equals(prop.get("name").getAsString())) {
                String base64Value = prop.get("value").getAsString();
                return extractSkinUrl(base64Value);
            }
        }

        throw new IOException("No textures property found in profile");
    }

    private String extractSkinUrl(String base64Value) throws IOException {
        byte[] decoded = Base64.getDecoder().decode(base64Value);
        String json = new String(decoded);
        JsonObject texturesPayload = JsonParser.parseString(json).getAsJsonObject();

        JsonObject textures = texturesPayload.getAsJsonObject("textures");
        if (textures == null || !textures.has("SKIN")) {
            throw new NoSkinException("Player has no custom skin");
        }

        JsonObject skin = textures.getAsJsonObject("SKIN");
        return skin.get("url").getAsString();
    }

    private byte[] downloadSkin(String skinUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(skinUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download skin: HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Exception thrown when a player is not found.
     */
    public static class PlayerNotFoundException extends IOException {
        public PlayerNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when a player has no custom skin.
     */
    public static class NoSkinException extends IOException {
        public NoSkinException(String message) {
            super(message);
        }
    }
}
