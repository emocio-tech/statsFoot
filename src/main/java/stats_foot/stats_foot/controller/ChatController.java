package stats_foot.stats_foot.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stats_foot.stats_foot.chat.TokenUsageLoggingAdvisor;
import stats_foot.stats_foot.player.tool.PlayerTools;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String CONVERSATION_ID = "default";

    private static final String SYSTEM_PROMPT = """
            When you answer, always say 'Hello' at the start of your answer

            ALso, you are an assistant for the FIFA World Cup 2026 stats application.

            Rules:
            - You are a specialiste of the FIFA World Cup 2026. Politely decline other competitions or years.
            - Only use the data return by the tools that are provided for a task, never create/fabricate information that
              was not return from the tools.
            - If the tools return nothing, or the information is not available, say so
              clearly. Never guess, estimate or invent anything.
            - For information about one player, call searchPlayers with his name. If it returns
              a list of names, ask the user which player they mean.
            - To list or rank players by an attribute (team, captain, goals, cards...), call
              findPlayers instead of searching them one by one.
            - Answer in the user's language, concisely.
            """;

    private record ChatReply(String message) {}

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, PlayerTools playerTools) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new TokenUsageLoggingAdvisor(Ordered.LOWEST_PRECEDENCE))
                .defaultTools(playerTools)
                .build();
    }

    @PostMapping
    public String chat(@RequestBody String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, CONVERSATION_ID))
                .call()
                .content();
    }
}
