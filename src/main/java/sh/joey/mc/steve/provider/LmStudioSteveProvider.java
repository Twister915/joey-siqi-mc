package sh.joey.mc.steve.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import sh.joey.mc.steve.SteveAnswer;
import sh.joey.mc.steve.SteveModel;
import sh.joey.mc.steve.SteveModelInfo;
import sh.joey.mc.steve.SteveModelProvider;
import sh.joey.mc.steve.SteveSystemPrompt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Steve AI model provider using LM Studio's OpenAI-compatible API.
 * <p>
 * LM Studio runs local LLMs and exposes an OpenAI-compatible endpoint.
 * This provider connects to the local LM Studio server.
 * <p>
 * Features:
 * <ul>
 *   <li>No cost tracking (local model)</li>
 *   <li>No web search (not supported by local models)</li>
 *   <li>Configurable endpoint URL and model name</li>
 * </ul>
 */
public final class LmStudioSteveProvider implements SteveModelProvider {

    private static final String ID = "lmstudio";

    private final String endpointUrl;
    private final String modelName;
    private final Logger logger;
    private final SteveModelInfo info;

    public LmStudioSteveProvider(String endpointUrl, String modelName, Logger logger) {
        this.endpointUrl = endpointUrl;
        this.modelName = modelName;
        this.logger = logger;
        this.info = new SteveModelInfo(
                "LM Studio",
                modelName,
                "LM Studio (" + modelName + ")"
        );
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SteveModelInfo info() {
        return info;
    }

    @Override
    public SteveModel create() {
        return new LmStudioSteveModel(endpointUrl, modelName, info, logger);
    }

    /**
     * LM Studio model implementation using OpenAI-compatible chat completions API.
     * Uses the simple instruction prompt (no cached knowledge base).
     */
    private static final class LmStudioSteveModel implements SteveModel {

        private static final Duration TIMEOUT = Duration.ofSeconds(120);
        private static final int MAX_TOKENS = 200;

        private final HttpClient httpClient;
        private final String endpointUrl;
        private final String modelName;
        private final SteveModelInfo info;
        private final Logger logger;

        LmStudioSteveModel(String endpointUrl, String modelName, SteveModelInfo info, Logger logger) {
            this.endpointUrl = endpointUrl;
            this.modelName = modelName;
            this.info = info;
            this.logger = logger;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
        }

        @Override
        public Single<SteveAnswer> ask(String question) {
            return Single.fromCallable(() -> askBlocking(question))
                    .subscribeOn(Schedulers.io());
        }

        @Override
        public SteveModelInfo info() {
            return info;
        }

        private SteveAnswer askBlocking(String question) throws IOException, InterruptedException {
            logger.info("Steve asking LM Studio (" + modelName + "): " + truncate(question, 100));

            JsonObject requestBody = buildRequestBody(question);
            String apiUrl = endpointUrl + "/v1/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("LM Studio API error: HTTP " + response.statusCode() + " - " + response.body());
                throw new IOException("LM Studio API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body());
        }

        private JsonObject buildRequestBody(String question) {
            JsonObject body = new JsonObject();
            body.addProperty("model", modelName);
            body.addProperty("max_tokens", MAX_TOKENS);
            body.addProperty("temperature", 0.7);

            // Messages array with system prompt and user message
            JsonArray messages = new JsonArray();

            // System message (uses simple instructions for local models)
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", SteveSystemPrompt.INSTRUCTIONS);
            messages.add(systemMessage);

            // User message
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", question);
            messages.add(userMessage);

            body.add("messages", messages);

            return body;
        }

        private SteveAnswer parseResponse(String responseBody) {
            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();

            // Parse response content from OpenAI format
            String text = "";
            if (response.has("choices") && response.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content")) {
                        text = message.get("content").getAsString().trim();
                    }
                }
            }

            // Strip <think>...</think> tags (reasoning models like DeepSeek)
            text = stripThinkTags(text);

            if (text.isEmpty()) {
                text = "I couldn't generate a response. Please try again.";
            }

            // No citations or cost for local models
            return new SteveAnswer(text, List.of(), 0.0);
        }

        private static String stripThinkTags(String text) {
            // Remove <think>...</think> blocks (including multiline)
            return text.replaceAll("(?s)<think>.*?</think>", "").trim();
        }

        private static String truncate(String text, int maxLength) {
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
