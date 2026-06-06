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

    // @Async — audit write runs on separate thread, never slows login response
    // @Transactional(REQUIRES_NEW) — audit gets its OWN transaction
    // Why REQUIRES_NEW? If login throws and rolls back, audit record still saves
    // LOGIN_FAILED must persist even when the main transaction fails
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType, UUID userId, HttpServletRequest request) {

        // Extract IP — X-Forwarded-For present when behind proxy/load balancer
        // Falls back to getRemoteAddr() for direct connections (local dev)
        String ip = extractIp(request);

        // User-Agent — identifies client (browser, mobile app, Postman, etc.)
        String userAgent = request.getHeader("User-Agent");

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .userId(userId)       // null for LOGIN_FAILED — user not identified yet
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(entry);

        log.debug("Audit logged: event= {}, userId= {}, ip= {}", eventType, userId, ip);
    }

    // Overload — for events with no userId (login failures before user found)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType, HttpServletRequest request) {

        log(eventType, null, request);
    }

    // X-Forwarded-For can contain chain of IPs: "client, proxy1, proxy2"
    // First IP in chain = real client — take that one
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}