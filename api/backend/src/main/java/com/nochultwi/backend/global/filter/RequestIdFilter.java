package com.nochultwi.backend.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a correlation id to every request (Spec 7: error body carries a {@code requestId}).
 * The same id is put into the logging MDC and returned in the {@code X-Request-Id} response header,
 * so an error reported by the kiosk/admin screen can be matched to the exact server log lines.
 * <p>
 * Runs before the Spring Security filter chain so that 401/403 responses also carry the id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // Only accept simple client-supplied ids to prevent log injection (CR/LF, control characters, huge values).
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static String currentRequestId() {
        String requestId = MDC.get(MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
