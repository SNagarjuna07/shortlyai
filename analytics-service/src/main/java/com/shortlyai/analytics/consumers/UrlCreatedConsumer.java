package com.shortlyai.analytics.consumers;

import com.shortlyai.analytics.clicks.ClickService;
import com.shortlyai.analytics.events.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedConsumer {

    private final ClickService clickService;

    @KafkaListener(
            topics = "url.created",
            groupId = "analytics-service-group",
            containerFactory = "createdKafkaListenerContainerFactory"
    )
    public void onUrlCreated(
            @Payload UrlCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received url.created: slug={} urlId={} partition={} offset={}",
                event.slug(), event.urlId(), partition, offset);

        clickService.initializeCounter(event);
    }
}