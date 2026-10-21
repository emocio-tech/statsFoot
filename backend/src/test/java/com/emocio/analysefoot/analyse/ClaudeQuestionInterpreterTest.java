package com.emocio.analysefoot.analyse;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tool Claude rank, sans réseau : requête construite et lecture de l'entrée du tool. */
class ClaudeQuestionInterpreterTest {

    private static final String QUESTION = "quelle nation a planté le plus ?";

    private final AnthropicClient client = mock(AnthropicClient.class);
    private final MessageService messages = mock(MessageService.class);
    private final ClaudeQuestionInterpreter interpreter = new ClaudeQuestionInterpreter(client, "claude-haiku-5-5");

    ClaudeQuestionInterpreterTest() {
        when(client.messages()).thenReturn(messages);
    }

    @Test
    void requestUsesHaikuAndAStrictForcedRankToolWithTheQuestionOnly() {
        MessageCreateParams params = interpreter.buildRequest(QUESTION);

        assertThat(params.model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(params.maxTokens()).isEqualTo(1024);
        assertThat(params.temperature()).isEmpty();
        assertThat(params.topP()).isEmpty();
        assertThat(params.topK()).isEmpty();
        assertThat(params.thinking()).isEmpty();
        assertThat(params.toolChoice()).hasValueSatisfying(choice ->
                assertThat(choice.tool()).hasValueSatisfying(tool -> assertThat(tool.name()).isEqualTo("rank")));
        assertThat(params.tools()).hasValueSatisfying(tools -> assertThat(tools).hasSize(1));
        assertThat(params.messages()).singleElement().satisfies(message ->
                assertThat(message.content().string()).contains(QUESTION));
    }

    @Test
    void rankToolSchemaIsStrictAndBoundToTheCatalogue() {
        Tool tool = ClaudeQuestionInterpreter.rankTool();
        Map<String, JsonValue> properties = tool.inputSchema().properties().orElseThrow()._additionalProperties();
        List<String> ids = Arrays.stream(AnalyseCriterion.values()).map(Enum::name).toList();

        assertThat(tool.name()).isEqualTo("rank");
        assertThat(tool.strict()).contains(true);
        assertThat(tool.inputSchema().required()).contains(List.of("criterion", "scope", "alternatives"));
        assertThat(tool.inputSchema()._additionalProperties()).containsEntry("additionalProperties", JsonValue.from(false));
        assertThat(enumOf(properties.get("criterion"))).isEqualTo(List.<Object>copyOf(concat(ids, "NONE")));
        assertThat(enumOf(properties.get("scope"))).isEqualTo(List.<Object>of("PLAYER", "TEAM", "UNKNOWN"));
        Map<?, ?> alternatives = properties.get("alternatives").convert(Map.class);
        assertThat(((Map<?, ?>) alternatives.get("items")).get("enum")).isEqualTo(ids);
    }

    @Test
    void systemPromptDescribesEveryCriterion() {
        assertThat(AnalyseCriterion.values()).allSatisfy(c ->
                assertThat(ClaudeQuestionInterpreter.systemPrompt()).contains(c.name(), c.label()));
    }

    @Test
    void validChoiceIsRecognized() {
        assertThat(parse("TOP_SCORING_TEAM", "TEAM", List.of())).isEqualTo(
                Interpretation.recognized(AnalyseCriterion.TOP_SCORING_TEAM, Interpretation.Source.ASSISTANT));
    }

    @Test
    void noneIsNoInterpretation() {
        assertThat(parse("NONE", "UNKNOWN", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void alternativesAreAnAmbiguity() {
        Interpretation interpretation = parse("NONE", "UNKNOWN", List.of("LEAST_FAIR_PLAY_PLAYER", "LEAST_FAIR_PLAY_TEAM"));
        assertThat(interpretation.outcome()).isEqualTo(Interpretation.Outcome.AMBIGUOUS);
        assertThat(interpretation.alternatives()).containsExactly(
                AnalyseCriterion.LEAST_FAIR_PLAY_PLAYER, AnalyseCriterion.LEAST_FAIR_PLAY_TEAM);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThat(parse("SALARY", "PLAYER", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("TOP_SCORER", "TEAM", List.of()).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("NONE", "UNKNOWN", List.of("NONE", "TOP_SCORER")).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(parse("NONE", "UNKNOWN", List.of("TOP_SCORER", "TOP_SCORER")).outcome()).isEqualTo(Interpretation.Outcome.NONE);
        assertThat(ClaudeQuestionInterpreter.parseInput(JsonValue.from("pas un objet")).outcome())
                .isEqualTo(Interpretation.Outcome.NONE);
        assertThat(ClaudeQuestionInterpreter.parseInput(JsonValue.from(Map.of("criterion", "TOP_SCORER"))).outcome())
                .isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void toolCallIsReadFromTheResponse() {
        Message message = response(StopReason.TOOL_USE,
                Map.of("criterion", "TOP_SCORER", "scope", "PLAYER", "alternatives", List.of()));
        when(messages.create(any(MessageCreateParams.class))).thenReturn(message);

        assertThat(interpreter.interpret(QUESTION)).isEqualTo(
                Interpretation.recognized(AnalyseCriterion.TOP_SCORER, Interpretation.Source.ASSISTANT));
    }

    @Test
    void refusalIsNoInterpretation() {
        Message message = response(StopReason.REFUSAL, Map.of());
        when(messages.create(any(MessageCreateParams.class))).thenReturn(message);

        assertThat(interpreter.interpret(QUESTION).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void apiErrorOrTimeoutIsNoInterpretation() {
        when(messages.create(any(MessageCreateParams.class))).thenThrow(mock(AnthropicServiceException.class));
        assertThat(interpreter.interpret(QUESTION).outcome()).isEqualTo(Interpretation.Outcome.NONE);

        when(messages.create(any(MessageCreateParams.class))).thenThrow(new RuntimeException("délai dépassé"));
        assertThat(interpreter.interpret(QUESTION).outcome()).isEqualTo(Interpretation.Outcome.NONE);
    }

    @Test
    void configurationDisablesTheAssistantWithoutKey() {
        AssistantConfig config = new AssistantConfig();
        assertThat(config.questionInterpreter(properties("")).enabled()).isFalse();
        assertThat(config.questionInterpreter(properties(null))).isInstanceOf(DisabledQuestionInterpreter.class);
        assertThat(config.questionInterpreter(properties("sk-ant-test"))).isInstanceOf(ClaudeQuestionInterpreter.class);
    }

    private static AssistantConfig.Properties properties(String key) {
        return new AssistantConfig.Properties(key, "claude-haiku-5-5", Duration.ofSeconds(3), 30);
    }

    private static Interpretation parse(String criterion, String scope, List<String> alternatives) {
        return ClaudeQuestionInterpreter.parseInput(JsonValue.from(
                Map.of("criterion", criterion, "scope", scope, "alternatives", alternatives)));
    }

    private static Message response(StopReason stopReason, Map<String, Object> input) {
        ToolUseBlock toolUse = mock(ToolUseBlock.class);
        when(toolUse.name()).thenReturn("rank");
        when(toolUse._input()).thenReturn(JsonValue.from(input));
        ContentBlock block = mock(ContentBlock.class);
        when(block.toolUse()).thenReturn(Optional.of(toolUse));
        Message message = mock(Message.class);
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.content()).thenReturn(List.of(block));
        return message;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> enumOf(JsonValue property) {
        return (List<Object>) property.convert(Map.class).get("enum");
    }

    private static List<String> concat(List<String> ids, String last) {
        return java.util.stream.Stream.concat(ids.stream(), java.util.stream.Stream.of(last)).toList();
    }
}
