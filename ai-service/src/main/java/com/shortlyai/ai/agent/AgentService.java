    package com.shortlyai.ai.agent;

    import com.shortlyai.ai.agent.dto.AgentResponse;
    import com.shortlyai.ai.agent.tools.AnalyticsServiceTools;
    import com.shortlyai.ai.agent.tools.UrlServiceTools;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.ai.chat.client.ChatClient;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.core.io.Resource;
    import org.springframework.stereotype.Service;

    import java.util.Map;

    @Service
    @Slf4j
    public class AgentService {

        private final ChatClient chatClient;

        private final UrlServiceTools urlServiceTools;

        private final AnalyticsServiceTools analyticsServiceTools;

        private final Resource agentPrompt;

        public AgentService(
                ChatClient chatClient,
                UrlServiceTools urlServiceTools,
                AnalyticsServiceTools analyticsServiceTools,
                @Value("classpath:prompts/agent-system.st")
                Resource agentPrompt
        ) {
            this.chatClient = chatClient;
            this.urlServiceTools = urlServiceTools;
            this.analyticsServiceTools = analyticsServiceTools;
            this.agentPrompt = agentPrompt;
        }

        public AgentResponse chat(String userId, String message) {

            log.info("Agent chat request userId: {}, message: {}", userId, message);

            String reply = chatClient.prompt()
                    .system(agentPrompt)
                    .user(message)
                    .tools(urlServiceTools, analyticsServiceTools)
                    .toolContext(Map.of("userId", userId))   // passed to @Tool methods, not seen by LLM
                    .call()
                    .content();

            log.debug("Agent reply userId: {}: {}", userId, reply);

            return new AgentResponse(reply);
        }
    }