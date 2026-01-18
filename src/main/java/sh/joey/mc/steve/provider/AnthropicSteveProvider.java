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
import sh.joey.mc.steve.SteveSystemPrompt;

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
 * allowed domains, prompt caching, and response parsing.
 * <p>
 * Uses Claude Haiku 3.5 with prompt caching for cost efficiency.
 * The Minecraft knowledge base is cached to reduce input token costs by ~90%.
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
    public SteveModel create() {
        return new AnthropicSteveModel(apiKey, maxSearches, logger);
    }

    /**
     * Claude-powered Steve model implementation with prompt caching.
     */
    private static final class AnthropicSteveModel implements SteveModel {

        private static final String API_URL = "https://api.anthropic.com/v1/messages";
        private static final String API_VERSION = "2023-06-01";
        private static final String MODEL = "claude-sonnet-4-20250514";
        private static final Duration TIMEOUT = Duration.ofSeconds(60);

        // Sonnet 4 pricing (per million tokens)
        private static final double INPUT_PRICE_PER_MTOK = 3.00;
        private static final double OUTPUT_PRICE_PER_MTOK = 15.00;
        private static final double CACHE_WRITE_PRICE_PER_MTOK = 3.75;  // 1.25x input
        private static final double CACHE_READ_PRICE_PER_MTOK = 0.30;   // 0.1x input
        private static final double SEARCH_PRICE_PER_KTOK = 10.0;

        private final HttpClient httpClient;
        private final String apiKey;
        private final int maxSearches;
        private final Logger logger;

        AnthropicSteveModel(String apiKey, int maxSearches, Logger logger) {
            this.apiKey = apiKey;
            this.maxSearches = maxSearches;
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
                    .header("anthropic-beta", "prompt-caching-2024-07-31,web-search-2025-03-05")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("Anthropic API error: HTTP " + response.statusCode() + " - " + response.body());
                if (response.statusCode() == 429) {
                    throw new RateLimitException("Too many requests. Please try again in a minute!");
                }
                throw new IOException("Anthropic API error: HTTP " + response.statusCode());
            }

            return parseResponse(response.body());
        }

        private JsonObject buildRequestBody(String question) {
            JsonObject body = new JsonObject();
            body.addProperty("model", MODEL);
            body.addProperty("max_tokens", 200);

            // System prompt as array with caching
            // The Minecraft knowledge base is cached to reduce costs by ~90%
            JsonArray system = new JsonArray();

            // First block: Minecraft knowledge (cached - must come first for cache prefix matching)
            JsonObject knowledgeBlock = new JsonObject();
            knowledgeBlock.addProperty("type", "text");
            knowledgeBlock.addProperty("text", SteveSystemPrompt.MINECRAFT_KNOWLEDGE);
            JsonObject cacheControl = new JsonObject();
            cacheControl.addProperty("type", "ephemeral");
            knowledgeBlock.add("cache_control", cacheControl);
            system.add(knowledgeBlock);

            // Second block: behavioral instructions (not cached, small)
            JsonObject instructionsBlock = new JsonObject();
            instructionsBlock.addProperty("type", "text");
            instructionsBlock.addProperty("text", SteveSystemPrompt.INSTRUCTIONS);
            system.add(instructionsBlock);

            body.add("system", system);

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

            // Parse usage for cost calculation with Haiku 3.5 pricing and cache tracking
            double costCents = 0.0;
            if (response.has("usage")) {
                JsonObject usage = response.getAsJsonObject("usage");
                int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                int cacheCreationTokens = usage.has("cache_creation_input_tokens")
                        ? usage.get("cache_creation_input_tokens").getAsInt() : 0;
                int cacheReadTokens = usage.has("cache_read_input_tokens")
                        ? usage.get("cache_read_input_tokens").getAsInt() : 0;
                int webSearches = 0;
                if (usage.has("server_tool_use")) {
                    JsonObject serverToolUse = usage.getAsJsonObject("server_tool_use");
                    webSearches = serverToolUse.has("web_search_requests")
                            ? serverToolUse.get("web_search_requests").getAsInt() : 0;
                }

                // Haiku 3.5 pricing with cache tracking
                // Anthropic reports each category separately (not overlapping):
                // - input_tokens: non-cached input tokens
                // - cache_creation_input_tokens: tokens written to cache
                // - cache_read_input_tokens: tokens read from cache
                double inputCost = (inputTokens / 1_000_000.0) * INPUT_PRICE_PER_MTOK;
                double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_PRICE_PER_MTOK;
                double cacheWriteCost = (cacheCreationTokens / 1_000_000.0) * CACHE_WRITE_PRICE_PER_MTOK;
                double cacheReadCost = (cacheReadTokens / 1_000_000.0) * CACHE_READ_PRICE_PER_MTOK;
                double searchCost = (webSearches / 1_000.0) * SEARCH_PRICE_PER_KTOK;
                costCents = (inputCost + outputCost + cacheWriteCost + cacheReadCost + searchCost) * 100.0;

                // Log token usage for monitoring
                logger.info(String.format("Steve tokens: %d in, %d out, %d cache-read, %d cache-write, %d searches, %.2f¢",
                        inputTokens, outputTokens, cacheReadTokens, cacheCreationTokens, webSearches, costCents));
            }

            JsonArray content = response.getAsJsonArray("content");
            if (content == null) {
                return new SteveAnswer("I couldn't find an answer to that question.", List.of(), costCents);
            }

            for (JsonElement element : content) {
                JsonObject block = element.getAsJsonObject();
                String type = block.get("type").getAsString();

                if ("text".equals(type)) {
                    String blockText = block.has("text") ? block.get("text").getAsString() : "";
                    text.append(blockText);

                    // Extract citations from text block
                    if (block.has("citations") && block.get("citations").isJsonArray()) {
                        JsonArray citationsArr = block.getAsJsonArray("citations");
                        for (JsonElement citeEl : citationsArr) {
                            if (!citeEl.isJsonObject()) continue;
                            JsonObject cite = citeEl.getAsJsonObject();
                            if (cite.has("type") && "web_search_result_location".equals(cite.get("type").getAsString())) {
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
                    if (block.has("content") && block.get("content").isJsonArray()) {
                        JsonArray searchContent = block.getAsJsonArray("content");
                        for (JsonElement searchEl : searchContent) {
                            if (!searchEl.isJsonObject()) continue;
                            JsonObject searchResult = searchEl.getAsJsonObject();
                            if (searchResult.has("type") && "web_search_result".equals(searchResult.get("type").getAsString())) {
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
            // Strip <web_search>...</web_search> tags that may appear in output
            responseText = stripWebSearchTags(responseText);

            if (responseText.isEmpty()) {
                responseText = "I found some information but couldn't summarize it properly. Check the sources below!";
            }

            return new SteveAnswer(responseText, limitedCitations, costCents);
        }

        /**
         * Strips XML-like tags that can leak into model output.
         * Haiku sometimes outputs internal tool-use markup like:
         * - &lt;web_search&gt;...&lt;/web_search&gt;
         * - &lt;web_search_calls&gt;...&lt;/web_search_calls&gt;
         * - &lt;invoke&gt;...&lt;/invoke&gt;
         * - &lt;parameter&gt;...&lt;/parameter&gt;
         */
        private static String stripWebSearchTags(String text) {
            // Strip common XML tags that leak from tool use
            String result = text;
            result = result.replaceAll("(?s)<web_search>.*?</web_search>", "");
            result = result.replaceAll("(?s)<web_search_calls>.*?</web_search_calls>", "");
            result = result.replaceAll("(?s)<invoke[^>]*>.*?</invoke>", "");
            result = result.replaceAll("(?s)<parameter[^>]*>.*?</parameter>", "");
            // Catch any remaining self-closing or orphaned tags
            result = result.replaceAll("<[^>]*web_search[^>]*>", "");
            result = result.replaceAll("</?invoke[^>]*>", "");
            result = result.replaceAll("</?parameter[^>]*>", "");
            return result.trim();
        }

        private static String truncate(String text, int maxLength) {
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
