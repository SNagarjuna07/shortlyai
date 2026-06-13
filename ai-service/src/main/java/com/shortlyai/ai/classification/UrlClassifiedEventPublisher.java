package com.shortlyai.ai.classification;

import com.shortlyai.ai.events.dto.UrlClassifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UrlClassifiedEventPublisher {

    private static final String TOPIC = "url.classified";

    private final KafkaTemplate<String, UrlClassifiedEvent> kafkaTemplate;

    public void publish(UrlClassifiedEvent event) {

        try {
            
            kafkaTemplate.send(TOPIC, String.valueOf(event.urlId()), event)

                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.error("Failed to publish url.classified urlId={}", event.urlId(), ex);

                        } else {

                            log.info("Published url.classified urlId={}, category={}",
                                    event.urlId(), event.category());
                        }
                    });

        } catch (Exception ex) {

            // catches SYNCHRONOUS failures (serialization, broker unreachable at send-call time)
            log.error("Synchronous failure publishing url.classified urlId={}", event.urlId(), ex);
        }
    }
}