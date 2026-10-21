package com.emocio.analysefoot.analyse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interprète Spring AI contre un serveur HTTP local qui parle le format OpenAI : la vraie pile Spring AI et la vraie
 * configuration, sans réseau externe.
 */
class SpringAiQuestionInterpreterTest {

    private static final String QUESTION = "quelle nation a planté le plus ?";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String response = "";
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private QuestionInterpreter interpreter(String provider, String toolChoice, Duration timeout) {
        AssistantConfig.Properties properties = new AssistantConfig.Properties(provider, "proxy-key", baseUrl(),
                "claude-sonnet-4-6", timeout, 1, 200, toolChoice,
                new AssistantConfig.Properties.Bedrock("bedrock-token", baseUrl() + "/openai", "openai.gpt-oss-120b-1:0",
                        toolChoice, 1024, 30));
        return new AssistantConfig().questionInterpreter(properties);
    }

    private QuestionInterpreter proxy() {
        return interpreter("proxy", "forced", Duration.ofSeconds(3));
    }

    private static String toolCall(String arguments) throws IOException {
        return MAPPER.writeValueAsString(Map.of(
                "id", "chatcmpl-1", "object", "chat.completion", "created", 1, "model", "m",
                "usage", Map.of("prompt_tokens", 900, "completion_tokens", 60, "total_tokens", 960),
                "choices", List.of(Map.of("index", 0, "finish_reason", "tool_calls", "message", Map.of(
                        "role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", "call_1", "type", "function",
                                "function", Map.of("name", "rank", "arguments", arguments))))))));
    }

    @Test
    void validToolCallIsRecognizedAndNotExecuted() throws IOException {
        response = toolCall("{\"criterion\":\"TOP_SCORING_TEAM\",\"scope\":\"TEAM\",\"alternatives\":[]}");

        assertThat(proxy().interpret(QUESTION)).isEqualTo(
                Interpretation.recognized(AnalyseCriterion.TOP_SCORING_TEAM, Interpretation.Source.ASSISTANT));
        // un seul aller : Spring AI ne renvoie pas le résultat du tool au modèle
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void proxyRequestHasTheModelAForcedRankToolAndTheQuestionOnly() throws IOException {
        response = toolCall("{\"criterion\":\"NONE\",\"scope\":\"UNKNOWN\",\"alternatives\":[]}");
        proxy().interpret(QUESTION);

        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer proxy-key");
        JsonNode request = MAPPER.readTree(body.get());
        assertThat(request.path("model").asText()).isEqualTo("claude-sonnet-4-6");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(200);
        assertThat(request.has("max_completion_tokens")).isFalse();
        assertThat(request.has("temperature")).isFalse();
        JsonNode messages = request.path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo(RankTool.systemPrompt());
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo(QUESTION);
        assertThat(request.path("tools")).hasSize(1);
        JsonNode function = request.path("tools").get(0).path("function");
        assertThat(request.path("tools").get(0).path("type").asText()).isEqualTo("function");
        assertThat(function.path("name").asText()).isEqualTo("rank");
        JsonNode schema = function.path("parameters");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        List<String> criteria = new ArrayList<>();
        schema.path("properties").path("criterion").path("enum").forEach(n -> criteria.add(n.asText()));
        List<String> expected = new ArrayList<>(RankTool.criterionIds());
        expected.add("NONE");
        assertThat(criteria).isEqualTo(expected);
        assertThat(request.path("tool_choice").path("function").path("name").asText()).isEqualTo("rank");
    }

    @Test
    void bedrockUsesItsTokenUrlAndMaxCompletionTokens() throws IOException {
        response = toolCall("{\"criterion\":\"TOP_SCORER\",\"scope\":\"PLAYER\",\"alternatives\":[]}");

        Interpretation result = interpreter("bedrock", "forced", Duration.ofSeconds(3)).interpret("meilleur buteure du mondiale");

        assertThat(result.criterion()).isEqualTo(AnalyseCriterion.TOP_SCORER);
        assertThat(path.get()).isEqualTo("/openai/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer bedrock-token");
        JsonNode request = MAPPER.readTree(body.get());
        assertThat(request.path("model").asText()).isEqualTo("openai.gpt-oss-120b-1:0");
        assertThat(request.path("max_completion_tokens").asInt()).isEqualTo(1024);
        assertThat(request.has("max_tokens")).isFalse();
    }

    @Test
    void autoModeLeavesTheChoiceToTheModel() throws IOException {
        response = toolCall("{\"criterion\":\"TOP_SCORER\",\"scope\":\"PLAYER\",\"alternatives\":[]}");

        assertThat(interpreter("proxy", "auto", Duration.ofSeconds(3)).interpret(QUESTION).criterion())
                .isEqualTo(AnalyseCriterion.TOP_SCORER);
        assertThat(MAPPER.readTree(body.get()).path("tool_choice").asText()).isEqualTo("auto");
    }

    @Test
    void braceTextInTheQuestionIsSentAsIs() throws IOException {
        response = toolCall("{\"criterion\":\"NONE\",\"scope\":\"UNKNOWN\",\"alternatives\":[]}");
        String question = "qui a marqué {le plus} ? {{ignore}} <st> {0}";

        proxy().interpret(question);

        assertThat(MAPPER.readTree(body.get()).path("messages").get(1).path("content").asText()).isEqualTo(question);
    }

    @Test
    void alternativesAreAnAmbiguity() throws IOException {
        response = toolCall("{\"criterion\":\"NONE\",\"scope\":\"UNKNOWN\",\"alternatives\":"
                + "[\"LEAST_FAIR_PLAY_PLAYER\",\"LEAST_FAIR_PLAY_TEAM\"]}");

        Interpretation interpretation = proxy().interpret("le moins fair-play");
        assertThat(interpretation.outcome()).isEqualTo(Interpretation.Outcome.AMBIGUOUS);
        assertThat(interpretation.alternatives()).containsExactly(
                AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER, AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
    }

    @Test
    void invalidChoicesAreRejected() throws IOException {
        for (String arguments : List.of(
                "{\"criterion\":\"SALARY\",\"scope\":\"PLAYER\",\"alternatives\":[]}",
                "{\"criterion\":\"TOP_SCORER\",\"scope\":\"TEAM\",\"alternatives\":[]}",
                "{\"criterion\":\"TOP_SCORER\"}",
                "pas du json")) {
            response = toolCall(arguments);
            assertThat(proxy().interpret(QUESTION).outcome()).as(arguments).isEqualTo(Interpretation.Outcome.NONE);
        }
    }

    @Test
    void replyWithoutToolCallIsNoInterpretation() {
        response = "{\"id\":\"c\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"m\",\"choices\":[{\"index\":0,"
                + "\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"France\"}}]}";
        assertThat(proxy().interpret(QUESTION).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void httpErrorsAreNoInterpretationWithoutRetry() {
        for (int code : List.of(400, 401, 403, 429, 500, 503)) {
            requests.set(0);
            status = code;
            response = "{\"error\":{\"message\":\"refusé\"}}";
            assertThat(proxy().interpret(QUESTION).outcome()).as("HTTP " + code).isEqualTo(Interpretation.Outcome.NONE);
            assertThat(requests.get()).as("aucune nouvelle tentative après HTTP " + code).isEqualTo(1);
        }
    }

    @Test
    void slowServerIsNoInterpretation() throws IOException {
        response = toolCall("{\"criterion\":\"TOP_SCORER\",\"scope\":\"PLAYER\",\"alternatives\":[]}");
        delayMillis = 2500;

        long start = System.nanoTime();
        assertThat(interpreter("proxy", "forced", Duration.ofMillis(400)).interpret(QUESTION).outcome())
                .isEqualTo(Interpretation.Outcome.NONE);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void unreachableServerIsNoInterpretation() {
        QuestionInterpreter interpreter = interpreter("proxy", "forced", Duration.ofSeconds(1));
        server.stop(0);
        assertThat(interpreter.interpret(QUESTION).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void providerIsChosenByConfiguration() {
        assertThat(interpreter("proxy", "forced", Duration.ofSeconds(3))).isInstanceOf(SpringAiQuestionInterpreter.class);
        assertThat(interpreter("bedrock", "forced", Duration.ofSeconds(3))).isInstanceOf(SpringAiQuestionInterpreter.class);

        AssistantConfig config = new AssistantConfig();
        assertThat(config.questionInterpreter(properties("proxy", "", "b"))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("proxy", null, "b"))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("bedrock", "p", ""))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("bedrock", "", "token"))).isInstanceOf(SpringAiQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("proxy", "p", ""))).isInstanceOf(SpringAiQuestionInterpreter.class);
    }

    @Test
    void rateLimitFollowsTheProvider() {
        AssistantConfig config = new AssistantConfig();
        assertThat(accepted(config.assistantRateLimiter(properties("proxy", "p", "b")))).isEqualTo(1);
        assertThat(accepted(config.assistantRateLimiter(properties("bedrock", "p", "b")))).isEqualTo(30);
    }

    private static int accepted(AssistantRateLimiter limiter) {
        int accepted = 0;
        while (accepted < 100 && limiter.tryAcquire()) {
            accepted++;
        }
        return accepted;
    }

    private static AssistantConfig.Properties properties(String provider, String proxyKey, String bedrockKey) {
        return new AssistantConfig.Properties(provider, proxyKey, "https://proxy.example", "claude-sonnet-4-6",
                Duration.ofSeconds(3), 1, 200, "forced", new AssistantConfig.Properties.Bedrock(bedrockKey,
                "https://bedrock-runtime.us-west-2.amazonaws.com/openai", "openai.gpt-oss-120b-1:0", "forced", 1024, 30));
    }
}
