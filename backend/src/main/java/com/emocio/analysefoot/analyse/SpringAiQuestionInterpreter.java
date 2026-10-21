package com.emocio.analysefoot.analyse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * Interprète une question libre avec le {@link ChatClient} de Spring AI et le tool {@code rank(criterion, scope)} :
 * un seul appel au modèle du projet (proxy de l'équipe ou Amazon Bedrock, au format OpenAI). Le tool n'est jamais
 * exécuté par Spring AI : le modèle ne fait que choisir un identifiant du catalogue, et le serveur revalide ce choix
 * ({@link RankTool#parse}). Toute erreur, refus, délai dépassé ou réponse invalide donne « aucune interprétation ».
 * La question n'est jamais journalisée.
 */
public class SpringAiQuestionInterpreter implements QuestionInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQuestionInterpreter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ChatOptions options;

    /** @param options modèle, plafond de la réponse, tool {@link #rankToolCallback()} et {@code tool_choice} */
    public SpringAiQuestionInterpreter(ChatClient chatClient, ChatOptions options) {
        this.chatClient = chatClient;
        this.options = options;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Interpretation interpret(String question) {
        try {
            // messages déjà construits : ni la question ni les consignes ne passent par le moteur de modèles de Spring AI
            ChatResponse response = chatClient.prompt()
                    .messages(new SystemMessage(RankTool.systemPrompt()), new UserMessage(question))
                    .options(options)
                    .call()
                    .chatResponse();
            return interpret(response);
        } catch (RuntimeException e) {
            log.warn("Assistant indisponible : {}", e.getClass().getSimpleName());
            return RankTool.none();
        }
    }

    /** Lit l'appel du tool dans la réponse ; tout autre cas (texte seul, arguments illisibles) : aucune interprétation. */
    Interpretation interpret(ChatResponse response) {
        if (response == null) {
            return RankTool.none();
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && usage.getPromptTokens() != null) {
            // consommation, pour suivre le quota du fournisseur (jamais la question)
            log.info("Assistant : {} jetons en entrée, {} en sortie", usage.getPromptTokens(), usage.getCompletionTokens());
        }
        Generation generation = response.getResult();
        AssistantMessage message = generation == null ? null : generation.getOutput();
        if (message == null || !message.hasToolCalls()) {
            log.warn("Assistant : réponse sans appel du tool");
            return RankTool.none();
        }
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            if (RankTool.NAME.equals(call.name())) {
                try {
                    return RankTool.parse(MAPPER.readValue(call.arguments(), new TypeReference<Map<String, Object>>() { }));
                } catch (Exception e) {
                    return RankTool.none();
                }
            }
        }
        return RankTool.none();
    }

    /** Le tool rank tel que Spring AI l'envoie au modèle ; il n'est jamais exécuté (voir la configuration). */
    static ToolCallback rankToolCallback() {
        ToolDefinition definition;
        try {
            definition = ToolDefinition.builder()
                    .name(RankTool.NAME)
                    .description(RankTool.DESCRIPTION)
                    .inputSchema(MAPPER.writeValueAsString(RankTool.inputSchema()))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return "";
            }
        };
    }

    static List<ToolCallback> toolCallbacks() {
        return List.of(rankToolCallback());
    }
}
