package stats_foot.stats_foot.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

    private MockMvc mockMvc;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(builder)).build();
    }

    @Test
    void chat_returnsLlmAnswer() throws Exception {
        when(chatClient.prompt()
                .user(anyString())
                .advisors(any(Consumer.class))
                .call()
                .content()).thenReturn("Hello from LLM");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from LLM"));
    }

    @Test
    void chat_withoutBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest());
    }
}
