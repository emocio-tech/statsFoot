package stats_foot.stats_foot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Logs the token consumption reported by the LLM for each chat call.
 */
public class TokenUsageLoggingAdvisor implements CallAdvisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenUsageLoggingAdvisor.class);

    private final int order;

    public TokenUsageLoggingAdvisor(int order) {
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        logUsage(response.chatResponse());
        return response;
    }

    private void logUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            LOGGER.info("LLM usage: not reported by the model");
            return;
        }
        LOGGER.info("LLM usage: model={} prompt={} completion={} total={}",
                chatResponse.getMetadata().getModel(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens());
    }

    @Override
    public String getName() {
        return "TokenUsageLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
