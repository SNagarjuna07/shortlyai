package com.shortlyai.url.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class DlqRetryJob {

    private static final int MAX_RETRIES = 5; // give up after 5 failed attempts

    private final FailedEventRepository failedEventRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final JsonMapper jsonMapper;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @SchedulerLock(name = "dlq_retry_job", lockAtLeastFor = "1m", lockAtMostFor = "14m")
    public void retryFailedEvents() {

        int pageSize = 100; // process 100 rows per batch
        int pageNumber = 0;
        int totalProcessed = 0;

        Page<FailedEvent> page;

        do {
            // Each iteration loads ONE page
            page = failedEventRepository.findRetryable(
                    MAX_RETRIES,
                    PageRequest.of(pageNumber, pageSize)
            );

            if (page.isEmpty()) {

                log.debug("DLQ retry: no pending events");

                break;
            }

            // Each batch in its own transaction - one bad batch doesn't roll back all
            for (FailedEvent event : page.getContent()) {
                retryEvent(event);
            }

            totalProcessed += page.getNumberOfElements();
            pageNumber++;

        } while (page.hasNext()); // stop when no more pages

        if (totalProcessed > 0) {

            log.info("DLQ retry complete: processed {} events", totalProcessed);
        }
    }

    private void retryEvent(FailedEvent failedEvent) {

        failedEvent.setLastAttemptAt(Instant.now());

        failedEvent.setRetryCount(failedEvent.getRetryCount() + 1);

        try {

            // Deserialize stored JSON payload back to generic Object (LinkedHashMap)
            // KafkaTemplate's JacksonJsonSerializer re-serializes it on send — JSON intact
            Object payload = jsonMapper.readValue(failedEvent.getPayload(), Object.class);

            kafkaTemplate.send(failedEvent.getTopic(), failedEvent.getEventKey(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {

                            // Retry failed again - retryCount already incremented above,
                            // job will pick it up next run unless MAX_RETRIES reached
                            log.warn("DLQ retry failed: topic={} key={} attempt={} error={}",
                                    failedEvent.getTopic(), failedEvent.getEventKey(),
                                    failedEvent.getRetryCount(), ex.getMessage());

                        } else {

                            // Mark processed — job won't pick this up again
                            failedEvent.setProcessed(true);

                            failedEventRepository.save(failedEvent);

                            log.info("DLQ retry success: topic={} key={} attempt={}",
                                    failedEvent.getTopic(), failedEvent.getEventKey(),
                                    failedEvent.getRetryCount());
                        }
                    });

        } catch (JacksonException e) {

            // Payload is corrupted - can't deserialize, log and abandon
            log.error("DLQ retry: corrupt payload for id={} topic={} error={}",
                    failedEvent.getId(), failedEvent.getTopic(), e.getMessage());
        }

        // Save updated retryCount + lastAttemptAt regardless of outcome
        failedEventRepository.save(failedEvent);
    }
}