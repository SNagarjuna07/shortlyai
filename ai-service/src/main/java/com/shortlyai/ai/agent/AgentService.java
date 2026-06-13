    package com.shortlyai.ai.agent;

    import com.shortlyai.ai.agent.dto.AgentResponse;
    import com.shortlyai.ai.agent.tools.AnalyticsServiceTools;
    import com.shortlyai.ai.agent.tools.UrlServiceTools;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.ai.chat.client.ChatClient;
    import org.springframework.stereotype.Service;

    import java.util.Map;

    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class AgentService {

        private final ChatClient chatClient;

        private final UrlServiceTools urlServiceTools;

        private final AnalyticsServiceTools analyticsServiceTools;

        public AgentResponse chat(String userId, String message) {

            log.info("Agent chat request userId: {}, message: {}", userId, message);

            String reply = chatClient.prompt()
                    .system("""
                            You are ShortlyAI's assistant. You can shorten URLs,
                            look up URL details, check analytics, and delete URLs
                            for the logged-in user. Use tools when needed.
                            Confirm before destructive actions like delete.
                            """)
                    .user(message)
                    .tools(urlServiceTools, analyticsServiceTools)
                    .toolContext(Map.of("userId", userId))   // passed to @Tool methods, not seen by LLM
                    .call()
                    .content();

            log.debug("Agent reply userId: {}: {}", userId, reply);

            return new AgentResponse(reply);
        }
    }





