package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Story 10.5: posts a text to a Slack Incoming Webhook. No-op without {@code app.alerts.slack-webhook-url}. Never
 * throws: a failure (HTTP error, timeout) is a warning and {@code false}.
 */
@Service
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String webhookUrl;
    private final RestClient restClient;

    @Autowired
    public SlackNotifier(@Value("${app.alerts.slack-webhook-url:}") String webhookUrl, RestClient.Builder builder) {
        this(webhookUrl, builder, true);
    }

    /** Tests pass {@code applyTimeouts=false} so the MockRestServiceServer bound to the builder is kept. */
    SlackNotifier(String webhookUrl, RestClient.Builder builder, boolean applyTimeouts) {
        this.webhookUrl = webhookUrl;
        if (applyTimeouts) {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
            factory.setReadTimeout(READ_TIMEOUT);
            builder = builder.requestFactory(factory);
        }
        this.restClient = builder.build();
    }

    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /** True when Slack accepted the message; false when not configured or on failure. */
    public boolean send(String text) {
        if (!isConfigured()) return false;
        try {
            restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text)).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            // the webhook URL is a secret: never logged
            log.warn("Slack alert failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
