package com.shortlyai.url.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedEventService {

    private final FailedEventRepository failedEventRepository;
    private final JsonMapper jsonMapper; // Spring Boot auto-configures this bean

    // Called from Kafka whenComplete callback — that runs on Kafka I/O thread,
    // outside any existing transaction, so @Transactional opens a fresh one
    @Transactional
    public void save(String topic, String eventKey, Object event, String errorMessage) {

        try {
            String payload = jsonMapper.writeValueAsString(event); // serialize event -> JSON

            FailedEvent failed = new FailedEvent();
            failed.setTopic(topic);
            failed.setEventKey(eventKey);
            failed.setPayload(payload);
            failed.setErrorMessage(errorMessage);

            failedEventRepository.save(failed);

            log.warn("Saved failed event to DLQ: topic={} key={}", topic, eventKey);

        } catch (JacksonException e) {
            // If we can't even serialize the event, log and skip — don't throw
            // Throwing here would crash the Kafka callback thread
            log.error("Failed to serialize event for DLQ: topic={} key={} error={}",
                    topic, eventKey, e.getMessage());
        }
    }
}