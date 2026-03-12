package com.aamir.util;

// WarehouseScrollCodec.java
// Problem solved: the raw ScrollPosition object cannot be sent over HTTP.
// We encode it as a Base64 JSON string so clients can pass it as a query param.
// This version handles multiple sort keys (unlike simple key=value encoding).


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class WarehouseScrollCodec {

    // Reusable ObjectMapper — Jackson handles LocalDateTime, Long, Double correctly
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Encodes a ScrollPosition into a URL-safe Base64 JSON token.
     * Returns null if position is null or initial (first page has no cursor).
     */
    public static String encode(ScrollPosition position) {
        if (position == null || position.isInitial()) {
            return null;
        }

        if (position instanceof KeysetScrollPosition keyset) {
            try {
                // Preserve insertion order — important for multi-key sorts
                Map<String, Object> keys = new LinkedHashMap<>(keyset.getKeys());
                String json = MAPPER.writeValueAsString(keys);
                return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(json.getBytes());
            } catch (Exception ex) {
                log.warn("Failed to encode scroll position: {}", ex.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * Decodes a Base64 scroll token back into a ScrollPosition.
     * Returns initial position (first page) on null, blank, or corrupt token.
     *
     * IMPORTANT: Never throw from here — corrupt tokens should restart from page 1,
     * not crash the request. This prevents cursor-poisoning attacks.
     */
    public static ScrollPosition decode(String token) {
        if (token == null || token.isBlank()) {
            return ScrollPosition.keyset(); // Initial / first page
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            Map<String, Object> keys = MAPPER.readValue(
                    decoded,
                    new TypeReference<LinkedHashMap<String, Object>>() {}
            );

            // Jackson deserializes numbers as Integer by default for small values.
            // Warehouse IDs can be Long — normalize all integers to Long.
            Map<String, Object> normalized = new LinkedHashMap<>();
            keys.forEach((k, v) -> {
                if (v instanceof Integer i) {
                    normalized.put(k, i.longValue());
                } else {
                    normalized.put(k, v);
                }
            });

            return ScrollPosition.forward(normalized);

        } catch (Exception ex) {
            // Log + graceful fallback — never let bad token crash the API
            log.warn("Invalid scroll token received, resetting to first page. Reason: {}", ex.getMessage());
            return ScrollPosition.keyset();
        }
    }
}