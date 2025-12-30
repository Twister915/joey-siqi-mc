package sh.joey.mc.steve.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import sh.joey.mc.steve.SteveAnswer;
import sh.joey.mc.steve.SteveModel;
import sh.joey.mc.steve.SteveModelInfo;
import sh.joey.mc.steve.SteveModelProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Steve AI model provider using Anthropic's Claude API.
 * <p>
 * Encapsulates all Anthropic-specific logic including web search,
 * allowed domains, and response parsing.
 */
public final class AnthropicSteveProvider implements SteveModelProvider {

    private static final String ID = "anthropic";
    private static final SteveModelInfo INFO = new SteveModelInfo(
            "Anthropic",
            "claude-sonnet-4-20250514",
            "Claude Sonnet 4"
    );

    private final String apiKey;
    private final int maxSearches;
    private final Logger logger;

    public AnthropicSteveProvider(String apiKey, int maxSearches, Logger logger) {
        this.apiKey = apiKey;
        this.maxSearches = maxSearches;
        this.logger = logger;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SteveModelInfo info() {
        return INFO;
    }

    @Override
    public SteveModel create(String systemPrompt) {
        return new AnthropicSteveModel(apiKey, maxSearches, systemPrompt, logger);
    }

    /**
     * Claude-powered Steve model implementation.
     */
    private static final class AnthropicSteveModel implements SteveModel {

        private static final String API_URL = "https://api.anthropic.com/v1/messages";
        private static final String API_VERSION = "2023-06-01";
        private static final String MODEL = "claude-sonnet-4-20250514";
        private static final Duration TIMEOUT = Duration.ofSeconds(60);

        private final HttpClient httpClient;
        private final String apiKey;
        private final int maxSearches;
        private final String systemPrompt;
        private final Logger logger;

        AnthropicSteveModel(String apiKey, int maxSearches, String systemPrompt, Logger logger) {
            this.apiKey = apiKey;
            this.maxSearches = maxSearches;
            this.systemPrompt = systemPrompt;
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
            return INFO;
        }

        private SteveAnswer askBlocking(String question) throws IOException, InterruptedException {
            logger.info("Steve asking Claude: " + truncate(question, 100));

            JsonObject requestBody = buildRequestBody(question);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("Anthropic API error: HTTP " + response.statusCode() + " - " + response.body());
                throw new IOException("Anthropic API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body());
        }

        private JsonObject buildRequestBody(String question) {
            JsonObject body = new JsonObject();
            body.addProperty("model", MODEL);
            body.addProperty("max_tokens", 200);
            body.addProperty("system", systemPrompt);

            // Messages array
            JsonArray messages = new JsonArray();
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", question);
            messages.add(userMessage);
            body.add("messages", messages);

            // Tools array - web search tool
            JsonArray tools = new JsonArray();
            JsonObject webSearchTool = new JsonObject();
            webSearchTool.addProperty("type", "web_search_20250305");
            webSearchTool.addProperty("name", "web_search");
            webSearchTool.addProperty("max_uses", maxSearches);

            // Allowed domains
            JsonArray allowedDomains = new JsonArray();
            allowedDomains.add("minecraft.wiki");
            webSearchTool.add("allowed_domains", allowedDomains);

            tools.add(webSearchTool);
            body.add("tools", tools);

            return body;
        }

        private SteveAnswer parseResponse(String responseBody) {
            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            StringBuilder text = new StringBuilder();
            Set<SteveAnswer.Citation> citations = new LinkedHashSet<>();

            // Parse usage for cost calculation
            double costCents = 0.0;
            if (response.has("usage")) {
                JsonObject usage = response.getAsJsonObject("usage");
                int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                int webSearches = 0;
                if (usage.has("server_tool_use")) {
                    JsonObject serverToolUse = usage.getAsJsonObject("server_tool_use");
                    webSearches = serverToolUse.has("web_search_requests")
                            ? serverToolUse.get("web_search_requests").getAsInt() : 0;
                }

                // Sonnet 4 pricing: $3/1M input, $15/1M output, $10/1K searches
                double inputCost = (inputTokens / 1_000_000.0) * 3.0;
                double outputCost = (outputTokens / 1_000_000.0) * 15.0;
                double searchCost = (webSearches / 1_000.0) * 10.0;
                costCents = (inputCost + outputCost + searchCost) * 100.0;
            }

            JsonArray content = response.getAsJsonArray("content");
            if (content == null) {
                return new SteveAnswer("I couldn't find an answer to that question.", List.of(), costCents);
            }

            for (JsonElement element : content) {
                JsonObject block = element.getAsJsonObject();
                String type = block.get("type").getAsString();

                if ("text".equals(type)) {
                    String blockText = block.get("text").getAsString();
                    text.append(blockText);

                    // Extract citations from text block
                    if (block.has("citations")) {
                        JsonArray citationsArr = block.getAsJsonArray("citations");
                        for (JsonElement citeEl : citationsArr) {
                            JsonObject cite = citeEl.getAsJsonObject();
                            if ("web_search_result_location".equals(cite.get("type").getAsString())) {
                                String title = cite.has("title") ? cite.get("title").getAsString() : "Source";
                                String url = cite.has("url") ? cite.get("url").getAsString() : "";
                                if (!url.isEmpty()) {
                                    citations.add(new SteveAnswer.Citation(title, url));
                                }
                            }
                        }
                    }
                } else if ("web_search_tool_result".equals(type)) {
                    // Extract URLs from search results
                    if (block.has("content")) {
                        JsonArray searchContent = block.getAsJsonArray("content");
                        for (JsonElement searchEl : searchContent) {
                            JsonObject searchResult = searchEl.getAsJsonObject();
                            if ("web_search_result".equals(searchResult.get("type").getAsString())) {
                                String title = searchResult.has("title") ? searchResult.get("title").getAsString() : "Source";
                                String url = searchResult.has("url") ? searchResult.get("url").getAsString() : "";
                                if (!url.isEmpty()) {
                                    citations.add(new SteveAnswer.Citation(title, url));
                                }
                            }
                        }
                    }
                }
            }

            // Limit citations
            List<SteveAnswer.Citation> limitedCitations = citations.stream()
                    .limit(5)
                    .toList();

            String responseText = text.toString().trim();
            if (responseText.isEmpty()) {
                responseText = "I found some information but couldn't summarize it properly. Check the sources below!";
            }

            return new SteveAnswer(responseText, limitedCitations, costCents);
        }

        private static String truncate(String text, int maxLength) {
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
