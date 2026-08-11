package com.fh5.telemetry.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads parameters from the query string and, for POST bodies, from
 * application/x-www-form-urlencoded content. The UI submits plain form
 * data rather than JSON so this app doesn't need a JSON parser too.
 */
public final class RequestParams {

    private final Map<String, String> values = new LinkedHashMap<>();

    private RequestParams() {
    }

    public static RequestParams from(HttpExchange exchange) throws IOException {
        RequestParams params = new RequestParams();
        String query = exchange.getRequestURI().getRawQuery();
        params.parseInto(query);

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (InputStream body = exchange.getRequestBody()) {
                String bodyText = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                params.parseInto(bodyText);
            }
        }
        return params;
    }

    private void parseInto(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    public long getLong(String key, long defaultValue) {
        String value = values.get(key);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    public float requireFloat(String key) {
        return Float.parseFloat(require(key));
    }

    public int requireInt(String key) {
        return Integer.parseInt(require(key));
    }
}
