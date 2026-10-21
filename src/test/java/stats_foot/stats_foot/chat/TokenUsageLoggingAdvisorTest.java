package stats_foot.stats_foot.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class TokenUsageLoggingAdvisorTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(TokenUsageLoggingAdvisor.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsTokenCountsAndReturnsResponseUnchanged() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("Hello"))))
                .metadata(ChatResponseMetadata.builder()
                        .model("nova-lite")
                        .usage(new DefaultUsage(123, 45, 168))
                        .build())
                .build();
        ChatClientResponse expected = ChatClientResponse.builder().chatResponse(chatResponse).build();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(expected);
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("hi")).build();

        ChatClientResponse actual = new TokenUsageLoggingAdvisor(0).adviseCall(request, chain);

        assertSame(expected, actual);
        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("model=nova-lite"), message);
        assertTrue(message.contains("prompt=123"), message);
        assertTrue(message.contains("completion=45"), message);
        assertTrue(message.contains("total=168"), message);
    }

    @Test
    void doesNotFailWhenThereIsNoChatResponse() {
        ChatClientResponse expected = ChatClientResponse.builder().build();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(expected);
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("hi")).build();

        assertSame(expected, new TokenUsageLoggingAdvisor(0).adviseCall(request, chain));
        assertTrue(appender.list.isEmpty());
    }
}
