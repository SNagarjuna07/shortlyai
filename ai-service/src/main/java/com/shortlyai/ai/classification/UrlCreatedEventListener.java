package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import com.shortlyai.ai.events.dto.UrlClassifiedEvent;
import com.shortlyai.ai.events.dto.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UrlCreatedEventListener {

    private final ClassificationService classificationService;

    private final UrlClassifiedEventPublisher publisher;

    // no containerFactory specified -> uses default "kafkaListenerContainerFactory"
    @KafkaListener(topics = "url.created")
    public void onUrlCreated(UrlCreatedEvent event) {

        log.info("Received url.created event urlId: {}, originalUrl: {}",
                event.urlId(), event.originalUrl()
        );

        try {

            ClassificationResponse classification = classificationService.classify(
                    new ClassificationRequest(event.originalUrl())
            );

            UrlClassifiedEvent classifiedEvent = new UrlClassifiedEvent(
                    event.urlId(),
                    classification.title(),
                    classification.category(),
                    classification.confidence(),
                    classification.tags()
            );

            publisher.publish(classifiedEvent);

        } catch (Exception ex) {

            log.error("Failed to classify urlId={}", event.urlId(), ex);
            // no failed_events table in ai-service (no DB) - error handler in
            // KafkaConfig retries 3x, then logs + moves on
        }
    }
}