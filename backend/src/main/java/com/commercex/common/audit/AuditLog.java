package com.commercex.common.audit;

import com.commercex.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Append-only audit trail. Runs in its own transaction so that failures (e.g. rejected logins)
 * are recorded even when the business transaction rolls back. Never put secrets in {@code details}.
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final int MAX_USER_AGENT = 512;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    AuditLog(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorUserId, String action, String resourceType, Object resourceId,
            Map<String, ?> details) {
        HttpServletRequest request = currentRequest();
        String ip = request != null ? request.getRemoteAddr() : null;
        String userAgent = request != null ? truncate(request.getHeader("User-Agent")) : null;
        jdbc.update("""
                INSERT INTO audit_log (actor_user_id, action, resource_type, resource_id, ip, user_agent,
                                       correlation_id, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), now())
                """,
                actorUserId, action, resourceType, resourceId != null ? resourceId.toString() : null,
                ip, userAgent, CorrelationIdFilter.current(), toJson(details));
    }

    public void record(Long actorUserId, String action) {
        record(actorUserId, action, null, null, null);
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize audit details", e);
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
    }
}
