package com.emocio.analysefoot.analyse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assistant Spring AI contre un serveur HTTP local qui parle le format OpenAI : la vraie pile Spring AI et la vraie
 * configuration, sans réseau externe. Le serveur rejoue une suite de réponses (appels de tools puis texte).
 */
class SpringAiQuestionInterpreterTest {

    private static final String QUESTION = "combien de buts a marqué Mbappé ?";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    /** Réponses rejouées dans l'ordre ; la dernière se répète. */
    private volatile List<String> script = List.of();
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            int index = requests.getAndIncrement();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            paths.add(exchange.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<String> replies = script;
            byte[] bytes = (replies.isEmpty() ? "" : replies.get(Math.min(index, replies.size() - 1)))
                    .getBytes(StandardCharsets.UTF_8);
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

    /** Un tool de lecture factice : renvoie {@code result} (ou lève {@code failure}) et note ses arguments. */
    private static ToolCallback tool(String name, Function<String, String> behaviour) {
        ToolDefinition definition = ToolDefinition.builder().name(name).description("tool " + name)
                .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return behaviour.apply(toolInput);
            }
        };
    }

    private SpringAiQuestionInterpreter assistant(Duration timeout, ToolCallback... tools) {
        return new SpringAiQuestionInterpreter(
                AssistantConfig.chatClient(baseUrl(), "proxy-key", "claude-sonnet-4-6", timeout),
                callTools -> AssistantConfig.options("claude-sonnet-4-6", 400, false, callTools),
                () -> List.of(tools));
    }

    private SpringAiQuestionInterpreter assistant(ToolCallback... tools) {
        return assistant(Duration.ofSeconds(5), tools);
    }

    private static String toolCall(String name, String arguments) throws IOException {
        return MAPPER.writeValueAsString(Map.of(
                "id", "chatcmpl-1", "object", "chat.completion", "created", 1, "model", "m",
                "usage", Map.of("prompt_tokens", 900, "completion_tokens", 60, "total_tokens", 960),
                "choices", List.of(Map.of("index", 0, "finish_reason", "tool_calls", "message", Map.of(
                        "role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", "call_" + name, "type",
                                "function", "function", Map.of("name", name, "arguments", arguments))))))));
    }

    private static String text(String content) throws IOException {
        return MAPPER.writeValueAsString(Map.of(
                "id", "chatcmpl-2", "object", "chat.completion", "created", 1, "model", "m",
                "usage", Map.of("prompt_tokens", 950, "completion_tokens", 30, "total_tokens", 980),
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content)))));
    }

    @Test
    void theModelCallsAToolThenAnswersWithItsResult() throws IOException {
        List<String> inputs = new ArrayList<>();
        script = List.of(toolCall("stat", "{\"scope\":\"PLAYER\",\"metric\":\"goals\"}"), text("Mbappé a marqué 10 buts."));

        String answer = assistant(tool("stat", in -> {
            inputs.add(in);
            return "{\"value\":10}";
        })).answerFreely(QUESTION);

        assertThat(answer).isEqualTo("Mbappé a marqué 10 buts.");
        assertThat(inputs).hasSize(1);
        assertThat(MAPPER.readTree(inputs.getFirst()).path("metric").asText()).isEqualTo("goals");
        // un aller et un retour : l'appli exécute le tool et renvoie son résultat au modèle
        assertThat(requests.get()).isEqualTo(2);
        JsonNode secondMessages = MAPPER.readTree(bodies.get(1)).path("messages");
        assertThat(secondMessages).hasSize(4);
        assertThat(secondMessages.get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(secondMessages.get(3).path("role").asText()).isEqualTo("tool");
        assertThat(secondMessages.get(3).path("content").asText()).isEqualTo("{\"value\":10}");
    }

    @Test
    void firstRequestOffersTheToolsWithAFreeChoiceAndTheQuestionOnly() throws IOException {
        script = List.of(text("ok"));
        assistant(tool("stat", in -> "{}"), tool("calculate", in -> "0")).answerFreely(QUESTION);

        assertThat(paths.getFirst()).isEqualTo("/v1/chat/completions");
        assertThat(authorizations.getFirst()).isEqualTo("Bearer proxy-key");
        JsonNode request = MAPPER.readTree(bodies.getFirst());
        assertThat(request.path("model").asText()).isEqualTo("claude-sonnet-4-6");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(400);
        assertThat(request.has("max_completion_tokens")).isFalse();
        assertThat(request.path("tool_choice").asText()).isEqualTo("auto");
        JsonNode messages = request.path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo(FreePrompt.system());
        assertThat(messages.get(1).path("content").asText()).isEqualTo(QUESTION);
        List<String> names = new ArrayList<>();
        request.path("tools").forEach(t -> names.add(t.path("function").path("name").asText()));
        assertThat(names).containsExactly("stat", "calculate");
    }

    @Test
    void theModelCanChainSeveralToolsBeforeAnswering() throws IOException {
        script = List.of(toolCall("stat", "{}"), toolCall("stat", "{}"), toolCall("calculate", "{\"expression\":\"10 - 1\"}"),
                text("Neuf buts d'écart."));
        List<String> called = new ArrayList<>();

        String answer = assistant(tool("stat", in -> {
            called.add("stat");
            return "{\"value\":1}";
        }), tool("calculate", in -> {
            called.add("calculate");
            return "9";
        })).answerFreely(QUESTION);

        assertThat(answer).isEqualTo("Neuf buts d'écart.");
        assertThat(called).containsExactly("stat", "stat", "calculate");
        assertThat(requests.get()).isEqualTo(4);
    }

    @Test
    void anUnknownToolOrAFailingToolIsReportedToTheModelNotThrown() throws IOException {
        script = List.of(toolCall("delete_everything", "{}"), toolCall("stat", "{}"), text("Je ne sais pas."));

        String answer = assistant(tool("stat", in -> {
            throw new IllegalArgumentException("Statistique inconnue : salary");
        })).answerFreely(QUESTION);

        assertThat(answer).isEqualTo("Je ne sais pas.");
        assertThat(MAPPER.readTree(bodies.get(1)).path("messages").get(3).path("content").asText())
                .startsWith("Erreur : tool inconnu delete_everything");
        assertThat(MAPPER.readTree(bodies.get(2)).path("messages").get(5).path("content").asText())
                .contains("Statistique inconnue : salary");
    }

    @Test
    void aModelThatNeverStopsCallingToolsIsInterruptedAtTheStepLimit() throws IOException {
        script = List.of(toolCall("stat", "{}"));

        assertThat(assistant(tool("stat", in -> "{}")).answerFreely(QUESTION)).isNull();
        assertThat(requests.get()).isEqualTo(SpringAiQuestionInterpreter.MAX_STEPS);
    }

    @Test
    void theSecondQuestionSeesTheFirstExchange() throws IOException {
        script = List.of(text("Mbappé a marqué 10 buts."), text("Et 3 passes décisives."));
        SpringAiQuestionInterpreter assistant = assistant();

        assistant.answerFreely(QUESTION);
        assistant.answerFreely("et ses passes décisives ?");

        JsonNode messages = MAPPER.readTree(bodies.get(1)).path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(1).path("content").asText()).isEqualTo(QUESTION);
        assertThat(messages.get(2).path("content").asText()).isEqualTo("Mbappé a marqué 10 buts.");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("et ses passes décisives ?");
    }

    @Test
    void onlyTheLastTenMessagesAreKept() throws IOException {
        script = List.of(text("ok"));
        SpringAiQuestionInterpreter assistant = assistant();

        for (int i = 1; i <= 7; i++) {
            assistant.answerFreely("question " + i);
        }

        // système + 10 messages mémorisés (5 échanges) + la question en cours
        JsonNode messages = MAPPER.readTree(bodies.get(6)).path("messages");
        assertThat(messages).hasSize(12);
        assertThat(messages.get(1).path("content").asText()).isEqualTo("question 2");
        assertThat(messages.get(11).path("content").asText()).isEqualTo("question 7");
    }

    @Test
    void braceTextInTheQuestionIsSentAsIs() throws IOException {
        script = List.of(text("ok"));
        String question = "qui a marqué {le plus} ? {{ignore}} <st> {0}";

        assistant().answerFreely(question);

        assertThat(MAPPER.readTree(bodies.getFirst()).path("messages").get(1).path("content").asText()).isEqualTo(question);
    }

    @Test
    void failuresGiveNoAnswerWithoutRetry() {
        for (int code : List.of(400, 401, 403, 429, 500, 503)) {
            requests.set(0);
            status = code;
            script = List.of("{\"error\":{\"message\":\"refusé\"}}");
            assertThat(assistant().answerFreely(QUESTION)).as("HTTP " + code).isNull();
            assertThat(requests.get()).as("aucune nouvelle tentative après HTTP " + code).isEqualTo(1);
        }
    }

    @Test
    void emptyReplyGivesNoAnswer() {
        script = List.of("{\"id\":\"c\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"m\",\"choices\":[]}");
        assertThat(assistant().answerFreely(QUESTION)).isNull();
    }

    @Test
    void slowServerGivesNoAnswerWithinTheTimeout() throws IOException {
        script = List.of(text("trop tard"));
        delayMillis = 2500;

        long start = System.nanoTime();
        assertThat(assistant(Duration.ofMillis(400)).answerFreely(QUESTION)).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void unreachableServerGivesNoAnswer() {
        SpringAiQuestionInterpreter assistant = assistant(Duration.ofSeconds(1));
        server.stop(0);
        assertThat(assistant.answerFreely(QUESTION)).isNull();
    }

    @Test
    void bedrockUsesItsTokenUrlAndMaxCompletionTokens() throws IOException {
        script = List.of(text("ok"));

        QuestionInterpreter interpreter = new AssistantConfig().questionInterpreter(new AssistantConfig.Properties(
                "bedrock", "proxy-key", "https://proxy.example", "claude-sonnet-4-6", Duration.ofSeconds(5), 400,
                new AssistantConfig.Properties.Bedrock("bedrock-token", baseUrl() + "/openai", "openai.gpt-oss-120b-1:0", 1024)));

        assertThat(interpreter.answerFreely(QUESTION)).isEqualTo("ok");
        assertThat(paths.getFirst()).isEqualTo("/openai/v1/chat/completions");
        assertThat(authorizations.getFirst()).isEqualTo("Bearer bedrock-token");
        JsonNode request = MAPPER.readTree(bodies.getFirst());
        assertThat(request.path("model").asText()).isEqualTo("openai.gpt-oss-120b-1:0");
        assertThat(request.path("max_completion_tokens").asInt()).isEqualTo(1024);
        assertThat(request.has("max_tokens")).isFalse();
    }

    @Test
    void providerIsChosenByConfigurationAndNeedsAKey() {
        AssistantConfig config = new AssistantConfig();
        assertThat(config.questionInterpreter(properties("proxy", "p", "b"))).isInstanceOf(SpringAiQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("bedrock", "", "token"))).isInstanceOf(SpringAiQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("proxy", "", "b"))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("proxy", null, "b"))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("bedrock", "p", ""))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(new DisabledQuestionInterpreter().enabled()).isFalse();
        assertThat(new DisabledQuestionInterpreter().answerFreely(QUESTION)).isNull();
    }

    private static AssistantConfig.Properties properties(String provider, String proxyKey, String bedrockKey) {
        return new AssistantConfig.Properties(provider, proxyKey, "https://proxy.example", "claude-sonnet-4-6",
                Duration.ofSeconds(5), 400, new AssistantConfig.Properties.Bedrock(bedrockKey,
                "https://bedrock-runtime.us-west-2.amazonaws.com/openai", "openai.gpt-oss-120b-1:0", 1024));
    }
}
