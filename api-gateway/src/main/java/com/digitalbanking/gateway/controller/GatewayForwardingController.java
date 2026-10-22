package com.digitalbanking.gateway.controller;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.gateway.ratelimit.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;

@Slf4j
@RestController
public class GatewayForwardingController {

    private final RateLimiterService rateLimiterService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.transaction.url:http://localhost:8080}")
    private String transactionServiceUrl;

    @Value("${services.account.url:http://localhost:8081}")
    private String accountServiceUrl;

    @Value("${services.fraud.url:http://localhost:8082}")
    private String fraudServiceUrl;

    @Value("${services.notification.url:http://localhost:8083}")
    private String notificationServiceUrl;

    @Value("${services.payment.url:http://localhost:8084}")
    private String paymentServiceUrl;

    public GatewayForwardingController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @RequestMapping(value = "/api/v1/**")
    public ResponseEntity<?> routeRequest(
            @RequestBody(required = false) byte[] body,
            HttpMethod method,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);

        // 1. Rate Limiting Check
        if (!rateLimiterService.isAllowed(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Rate limit exceeded: Max 60 requests per minute allowed"));
        }

        // 2. Route Resolution
        String path = request.getRequestURI();
        String targetBaseUrl = resolveTargetUrl(path);

        if (targetBaseUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No route found for path: " + path));
        }

        String targetUrl = targetBaseUrl + path;
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        // 3. Forward Headers
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                headers.put(headerName, Collections.list(request.getHeaders(headerName)));
            }
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            log.error("Gateway forward error to {}: {}", targetUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Gateway error contacting upstream service: " + e.getMessage()));
        }
    }

    private String resolveTargetUrl(String path) {
        if (path.startsWith("/api/v1/transfers")) {
            return transactionServiceUrl;
        } else if (path.startsWith("/api/v1/accounts")) {
            return accountServiceUrl;
        } else if (path.startsWith("/api/v1/fraud")) {
            return fraudServiceUrl;
        } else if (path.startsWith("/api/v1/notifications")) {
            return notificationServiceUrl;
        } else if (path.startsWith("/api/v1/payments")) {
            return paymentServiceUrl;
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
