package com.emocio.analysefoot.analyse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Assistant de l'onglet Analyse par le {@link ChatClient} de Spring AI (proxy de l'équipe ou Amazon Bedrock, au format
 * OpenAI). Le modèle interprète librement la saisie : il appelle les tools de lecture, que l'application exécute
 * elle-même (lecture seule, SQL) en lui renvoyant chaque résultat, jusqu'à sa réponse finale en texte. Spring AI
 * n'exécute jamais les tools : chaque aller et retour est visible et tracé ({@link Trace}). Toute erreur, délai dépassé ou
 * réponse vide donne « aucune réponse ». La question n'est journalisée que par la trace (désactivable).
 */
public class SpringAiQuestionInterpreter implements QuestionInterpreter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQuestionInterpreter.class);

    /** Allers et retours LLM, tool au plus par saisie : garde-fou contre une boucle, pas un plafond d'usage. */
    static final int MAX_STEPS = 6;
    private static final int MAX_RESULT_SHOWN = 400;
    private static final String CONVERSATION_ID = "default";

    private final ChatClient chatClient;
    private final Function<List<ToolCallback>, ChatOptions> options;
    private final Supplier<List<ToolCallback>> dataTools;
    /** Derniers échanges (question et réponse, sans les tools), partagés par tous les visiteurs comme le chat de main. */
    private final ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(10).build();

    /**
     * @param options   options de l'appel pour les tools donnés (choix libre du modèle, jamais exécutés par Spring AI)
     * @param dataTools tools de lecture réellement exécutés ici, adossés au SQL (résolus à l'appel)
     */
    public SpringAiQuestionInterpreter(ChatClient chatClient, Function<List<ToolCallback>, ChatOptions> options,
                                       Supplier<List<ToolCallback>> dataTools) {
        this.chatClient = chatClient;
        this.options = options;
        this.dataTools = dataTools;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String answerFreely(String question) {
        try {
            Map<String, ToolCallback> tools = new LinkedHashMap<>();
            dataTools.get().forEach(t -> tools.put(t.getToolDefinition().name(), t));
            ChatOptions callOptions = options.apply(List.copyOf(tools.values()));
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(FreePrompt.system()));
            messages.addAll(memory.get(CONVERSATION_ID));
            messages.add(new UserMessage(question));
            for (int step = 1; step <= MAX_STEPS; step++) {
                Trace.out("LLM", "Appel du modèle (étape " + step + ")", step == 1
                        ? "question : « " + question + " » ; tools de lecture : " + String.join(", ", tools.keySet())
                        : "avec les résultats des tools");
                ChatResponse response = chatClient.prompt().messages(messages).options(callOptions).call().chatResponse();
                Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
                if (usage != null && usage.getPromptTokens() != null) {
                    log.info("Assistant : {} jetons en entrée, {} en sortie", usage.getPromptTokens(),
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
                    memory.add(CONVERSATION_ID, List.of(new UserMessage(question), new AssistantMessage(message.getText())));
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
            Trace.out("APP", "Assistant interrompu", "pas de réponse finale après " + MAX_STEPS + " étapes");
            return null;
        } catch (RuntimeException e) {
            log.warn("Assistant indisponible : {}", e.getClass().getSimpleName());
            Trace.in("LLM", "Appel du modèle en erreur", e.getClass().getSimpleName());
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
}
