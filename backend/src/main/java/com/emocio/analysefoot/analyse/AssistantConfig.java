package com.emocio.analysefoot.analyse;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Assistant LLM de l'onglet Analyse par le {@link ChatClient} de Spring AI, au choix du fournisseur
 * {@code analyse.assistant.provider}. Les deux parlent le format OpenAI Chat Completions :
 * <ul>
 *   <li>{@code proxy} (défaut) : le LLM du projet, proxy de l'équipe, clé {@code LLM_KEY} ;</li>
 *   <li>{@code bedrock} : Amazon Bedrock (modèle gpt-oss), jeton {@code AWS_BEARER_TOKEN_BEDROCK}.</li>
 * </ul>
 * Le client est construit ici et non par l'autoconfiguration de Spring AI, qui exigerait une clé au démarrage :
 * sans clé, l'assistant est désactivé. Aucune nouvelle tentative ; aucun plafond d'appels par minute.
 */
@Configuration
@EnableConfigurationProperties(AssistantConfig.Properties.class)
public class AssistantConfig {

    static final String BEDROCK = "bedrock";

    /** Plancher du plafond de la réponse : plusieurs allers et retours avec tools, puis un texte. */
    static final int MIN_TOKENS = 400;

    /** Absent hors contexte Spring (tests unitaires) : l'assistant n'a alors aucun tool de lecture. */
    @Autowired(required = false)
    private BeanFactory beanFactory;

    @ConfigurationProperties("analyse.assistant")
    public record Properties(String provider, String apiKey, String baseUrl, String model, Duration timeout,
                             int maxTokens, Bedrock bedrock) {

        /** Amazon Bedrock, interface compatible OpenAI. Un modèle à raisonnement compte ses jetons de réflexion dans {@code maxTokens}. */
        public record Bedrock(String apiKey, String baseUrl, String model, int maxTokens) {
        }

        boolean useBedrock() {
            return BEDROCK.equalsIgnoreCase(provider) && bedrock != null;
        }
    }

    @Bean
    QuestionInterpreter questionInterpreter(Properties properties) {
        boolean bedrock = properties.useBedrock();
        String apiKey = bedrock ? properties.bedrock().apiKey() : properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new DisabledQuestionInterpreter();
        }
        String baseUrl = bedrock ? properties.bedrock().baseUrl() : properties.baseUrl();
        String model = bedrock ? properties.bedrock().model() : properties.model();
        int maxTokens = Math.max(bedrock ? properties.bedrock().maxTokens() : properties.maxTokens(), MIN_TOKENS);
        // les tools de lecture sont ceux du serveur MCP et du mode libre (mêmes fonctions, même SQL), résolus à l'appel
        return new SpringAiQuestionInterpreter(chatClient(baseUrl, apiKey, model, properties.timeout()),
                tools -> options(model, maxTokens, bedrock, tools),
                beanFactory == null ? List::of : () -> dataTools(beanFactory));
    }

    /** Tools de lecture de l'assistant : ceux du serveur MCP (metrics, rank, find, rapports) et stat, matches, calculate. */
    static List<ToolCallback> dataTools(BeanFactory beanFactory) {
        List<ToolCallback> tools = new ArrayList<>();
        for (String provider : List.of("statsFootTools", "statToolsProvider")) {
            tools.addAll(List.of(beanFactory.getBean(provider, ToolCallbackProvider.class).getToolCallbacks()));
        }
        return tools;
    }

    /** Client Spring AI sans nouvelle tentative et avec les délais de connexion et de lecture demandés. */
    static ChatClient chatClient(String baseUrl, String apiKey, String model, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.create(chatModel);
    }

    /**
     * Options de l'appel : tools de lecture au choix du modèle, exécutés par l'application et non par Spring AI. Le
     * plafond de la réponse est {@code max_tokens} pour le proxy et {@code max_completion_tokens} pour Bedrock.
     */
    static OpenAiChatOptions options(String model, int maxTokens, boolean maxCompletionTokens, List<ToolCallback> tools) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.AUTO);
        return (maxCompletionTokens ? builder.maxCompletionTokens(maxTokens) : builder.maxTokens(maxTokens)).build();
    }
}
