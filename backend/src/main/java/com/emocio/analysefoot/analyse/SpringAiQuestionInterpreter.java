package com.emocio.analysefoot.analyse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Interprète une question libre avec le {@link ChatClient} de Spring AI et le tool {@code rank(criterion, scope)} :
 * un seul appel au modèle du projet (proxy de l'équipe ou Amazon Bedrock, au format OpenAI). Le tool n'est jamais
 * exécuté par Spring AI : le modèle ne fait que choisir un identifiant du catalogue, et le serveur revalide ce choix
 * ({@link RankTool#parse}). Toute erreur, refus, délai dépassé ou réponse invalide donne « aucune interprétation ».
 * La question n'est journalisée que par la trace ({@link Trace}, désactivable).
 */
public class SpringAiQuestionInterpreter implements QuestionInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQuestionInterpreter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Allers et retours LLM, tool au plus par question libre (au-delà, le mode libre n'aboutit pas). */
    static final int MAX_FREE_STEPS = 5;
    private static final int MAX_RESULT_SHOWN = 400;

    private final ChatClient chatClient;
    private final ChatOptions options;
    private final ChatClient freeClient;
    private final Function<List<ToolCallback>, ChatOptions> freeOptions;
    private final Supplier<List<ToolCallback>> dataTools;

    /** @param options modèle, plafond de la réponse, tool {@link #rankToolCallback()} et {@code tool_choice} */
    public SpringAiQuestionInterpreter(ChatClient chatClient, ChatOptions options) {
        this(chatClient, options, null, null, null);
    }

    /**
     * @param freeClient  client du mode libre (délai plus long : plusieurs allers et retours)
     * @param freeOptions options du mode libre pour les tools de lecture donnés (choix libre, jamais exécutés par Spring AI)
     * @param dataTools   tools de lecture réellement exécutés ici, adossés au SQL (résolus à l'appel)
     */
    public SpringAiQuestionInterpreter(ChatClient chatClient, ChatOptions options, ChatClient freeClient,
                                       Function<List<ToolCallback>, ChatOptions> freeOptions,
                                       Supplier<List<ToolCallback>> dataTools) {
        this.chatClient = chatClient;
        this.options = options;
        this.freeClient = freeClient;
        this.freeOptions = freeOptions;
        this.dataTools = dataTools;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Interpretation interpret(String question) {
        try {
            Trace.out("LLM", "Appel du modèle " + options.getModel(), "question : « " + question + " » ; tool proposé : "
                    + RankTool.NAME + " (" + RankTool.criterionIds().size() + " critères + NONE)");
            // messages déjà construits : ni la question ni les consignes ne passent par le moteur de modèles de Spring AI
            ChatResponse response = chatClient.prompt()
                    .messages(new SystemMessage(RankTool.systemPrompt()), new UserMessage(question))
                    .options(options)
                    .call()
                    .chatResponse();
            return interpret(response);
        } catch (RuntimeException e) {
            log.warn("Assistant indisponible : {}", e.getClass().getSimpleName());
            Trace.in("LLM", "Appel du modèle en erreur", e.getClass().getSimpleName());
            return RankTool.none();
        }
    }

    /**
     * Mode libre : boucle LLM, tool, LLM... L'application exécute chaque appel de tool demandé par le modèle (lecture
     * seule, SQL) et lui renvoie le résultat, jusqu'à sa réponse finale en texte. Chaque aller et retour est tracé.
     */
    @Override
    public String answerFreely(String question) {
        if (freeClient == null || dataTools == null) {
            return null;
        }
        try {
            Map<String, ToolCallback> tools = new LinkedHashMap<>();
            dataTools.get().forEach(t -> tools.put(t.getToolDefinition().name(), t));
            ChatOptions callOptions = freeOptions.apply(List.copyOf(tools.values()));
            List<Message> messages = new ArrayList<>(List.of(new SystemMessage(RankTool.freeSystemPrompt()),
                    new UserMessage(question)));
            for (int step = 1; step <= MAX_FREE_STEPS; step++) {
                Trace.out("LLM", "Appel du modèle (étape " + step + ")", step == 1
                        ? "question : « " + question + " » ; tools de lecture : " + String.join(", ", tools.keySet())
                        : "avec les résultats des tools");
                ChatResponse response = freeClient.prompt().messages(messages).options(callOptions).call().chatResponse();
                Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
                if (usage != null && usage.getPromptTokens() != null) {
                    log.info("Assistant (mode libre) : {} jetons en entrée, {} en sortie", usage.getPromptTokens(),
                            usage.getCompletionTokens());
                }
                AssistantMessage message = response == null || response.getResult() == null ? null
                        : response.getResult().getOutput();
                if (message == null) {
                    Trace.in("LLM", "Réponse vide", "aucune réponse");
                    return null;
                }
                if (!message.hasToolCalls()) {
                    Trace.in("LLM", "Réponse finale", message.getText());
                    return message.getText();
                }
                Trace.in("LLM", "Le modèle demande " + message.getToolCalls().size() + " appel(s) de tool",
                        usage == null ? "" : usage.getPromptTokens() + " jetons en entrée, " + usage.getCompletionTokens()
                                + " en sortie");
                messages.add(message);
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall call : message.getToolCalls()) {
                    responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), execute(tools, call)));
                }
                messages.add(ToolResponseMessage.builder().responses(responses).build());
            }
            Trace.out("APP", "Mode libre interrompu", "pas de réponse finale après " + MAX_FREE_STEPS + " étapes");
            return null;
        } catch (RuntimeException e) {
            log.warn("Assistant (mode libre) indisponible : {}", e.getClass().getSimpleName());
            Trace.in("LLM", "Mode libre en erreur", e.getClass().getSimpleName());
            return null;
        }
    }

    /** Exécute un tool de lecture demandé par le modèle ; une erreur lui est renvoyée comme résultat. */
    private static String execute(Map<String, ToolCallback> tools, AssistantMessage.ToolCall call) {
        Trace.in("TOOL", "Le modèle appelle " + call.name(), call.arguments());
        ToolCallback callback = tools.get(call.name());
        String result;
        if (callback == null) {
            result = "Erreur : tool inconnu " + call.name();
        } else {
            try {
                result = callback.call(call.arguments());
            } catch (RuntimeException e) {
                result = "Erreur : " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            }
        }
        Trace.out("TOOL", "Résultat de " + call.name() + " renvoyé au modèle",
                result.length() > MAX_RESULT_SHOWN ? result.substring(0, MAX_RESULT_SHOWN) + "…" : result);
        return result;
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
            Trace.in("LLM", "Retour du modèle", usage.getPromptTokens() + " jetons en entrée, " + usage.getCompletionTokens()
                    + " en sortie");
        }
        Generation generation = response.getResult();
        AssistantMessage message = generation == null ? null : generation.getOutput();
        if (message == null || !message.hasToolCalls()) {
            log.warn("Assistant : réponse sans appel du tool");
            Trace.in("LLM", "Réponse sans appel du tool", "aucune interprétation");
            return RankTool.none();
        }
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            if (RankTool.NAME.equals(call.name())) {
                Trace.in("TOOL", "Le modèle appelle " + RankTool.NAME, call.arguments());
                try {
                    Interpretation parsed = RankTool.parse(MAPPER.readValue(call.arguments(),
                            new TypeReference<Map<String, Object>>() { }));
                    Trace.out("TOOL", "Choix validé par le serveur", parsed.outcome() + (parsed.criterion() == null ? ""
                            : " : " + parsed.criterion().name()) + (parsed.alternatives().isEmpty() ? ""
                            : " : " + parsed.alternatives()));
                    return parsed;
                } catch (Exception e) {
                    Trace.out("TOOL", "Arguments illisibles", e.getClass().getSimpleName());
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
