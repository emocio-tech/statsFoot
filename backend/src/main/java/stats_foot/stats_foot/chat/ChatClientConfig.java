package stats_foot.stats_foot.chat;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides the {@link ChatModel} and the {@link ChatClient.Builder} used by the chat, built from the {@code analyse.assistant.*} settings.
 * The Spring AI auto-configuration is not used: it would require an API key at start-up. Without a key the
 * application still starts and only the chat calls fail.
 */
@Configuration
public class ChatClientConfig {

	/** Placeholder so that the client can be built without a key; the LLM rejects the call. */
	private static final String NO_KEY = "no-key";

	@Bean
	ChatModel chatModel(@Value("${analyse.assistant.api-key:}") String apiKey,
			@Value("${analyse.assistant.base-url}") String baseUrl,
			@Value("${analyse.assistant.model}") String model,
			@Value("${analyse.assistant.timeout:20s}") Duration timeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		OpenAiApi api = OpenAiApi.builder()
				.baseUrl(baseUrl)
				.apiKey(apiKey.isBlank() ? NO_KEY : apiKey)
				.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
				.build();
		return OpenAiChatModel.builder()
				.openAiApi(api)
				.defaultOptions(OpenAiChatOptions.builder().model(model).build())
				.build();
	}

	@Bean
	ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
		return ChatClient.builder(chatModel);
	}

}
