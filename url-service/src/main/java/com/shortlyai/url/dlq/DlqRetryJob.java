package com.shortlyai.url.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
    @SchedulerLock(name = "dlq_retry_job", lockAtLeastFor = "5s", lockAtMostFor = "1m")
    public void retryFailedEvents() {

        log.info("DLQ retry fired");

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

            log.info("DLQ retry complete: attempted {} events", totalProcessed);
        }
    }

    private void retryEvent(FailedEvent failedEvent) {

        // Record that we are attempting a retry now
        failedEvent.setLastAttemptAt(Instant.now());

        // Increment retry counter before sending
        failedEvent.setRetryCount(failedEvent.getRetryCount() + 1);

        // Persist retry attempt immediately.
        // This guarantees retryCount and lastAttemptAt are saved
        // even if Kafka throws synchronously.
        failedEventRepository.save(failedEvent);

        try {

            // Deserialize JSON payload stored in failed_events
            Object payload = jsonMapper.readValue(
                    failedEvent.getPayload(),
                    Object.class
            );

            kafkaTemplate.send(
                            failedEvent.getTopic(),
                            failedEvent.getEventKey(),
                            payload
                    )
                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            // Async Kafka failure
                            log.warn(
                                    "DLQ retry failed: topic={} key={} attempt={} error={}",
                                    failedEvent.getTopic(),
                                    failedEvent.getEventKey(),
                                    failedEvent.getRetryCount(),
                                    ex.getMessage()
                            );

                        } else {

                            // Successfully republished to Kafka
                            // Mark processed so scheduler never picks it again
                            failedEvent.setProcessed(true);

                            failedEventRepository.save(failedEvent);

                            log.info(
                                    "DLQ retry success: topic={} key={} attempt={}",
                                    failedEvent.getTopic(),
                                    failedEvent.getEventKey(),
                                    failedEvent.getRetryCount()
                            );
                        }
                    });

        } catch (Exception e) {

            // Handles synchronous failures:
            // - KafkaException
            // - Serialization exceptions
            // - Any unexpected runtime exception
            log.warn(
                    "DLQ retry immediate failure: topic={} key={} attempt={} error={}",
                    failedEvent.getTopic(),
                    failedEvent.getEventKey(),
                    failedEvent.getRetryCount(),
                    e.getMessage()
            );
        }
    }
}