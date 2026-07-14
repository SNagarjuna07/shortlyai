package com.shortlyai.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Single entry point, no self-invocation possible.
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType, UUID userId, HttpServletRequest request) {

        String ip = extractIp(request);

        String userAgent = request.getHeader("User-Agent");

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .userId(userId)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit logged: event= {}, userId= {}, ip= {}", eventType, userId, ip);
    }

    private String extractIp(HttpServletRequest request) {

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}