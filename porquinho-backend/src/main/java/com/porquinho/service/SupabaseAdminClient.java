package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Client for Supabase Admin API operations (Story 1.7).
 *
 * Always best-effort: failures (missing config, HTTP errors, timeouts) log a warning
 * and return normally so callers don't need try/catch. Compliance with LGPD on the
 * application database prevails over consistency with Supabase Auth.
 */
@Service
public class SupabaseAdminClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAdminClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String projectUrl;
    private final String serviceRoleKey;
    private final RestClient restClient;

    /**
     * Production constructor: applies bounded timeouts to prevent the purge job from
     * blocking indefinitely on a stuck Supabase request.
     * {@code @Autowired} is required: with two constructors Spring would otherwise look for a no-arg one.
     */
    @Autowired
    public SupabaseAdminClient(
            @Value("${supabase.project-url:}") String projectUrl,
            @Value("${supabase.service-role-key:}") String serviceRoleKey,
            RestClient.Builder restClientBuilder) {
        this(projectUrl, serviceRoleKey, restClientBuilder, true);
    }

    /**
     * Package-private constructor for tests: when MockRestServiceServer.bindTo(builder)
     * has already installed a mock request factory, applying our own timeouts would
     * overwrite the mock. Tests pass {@code applyTimeouts=false} to keep the bound mock.
     */
    SupabaseAdminClient(String projectUrl, String serviceRoleKey,
                        RestClient.Builder restClientBuilder, boolean applyTimeouts) {
        this.projectUrl = projectUrl;
        this.serviceRoleKey = serviceRoleKey;
        if (applyTimeouts) {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(READ_TIMEOUT);
            this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        } else {
            this.restClient = restClientBuilder.build();
        }
    }

    /**
     * Permanently deletes a user from Supabase Auth via the Admin API.
     *
     * Best-effort: never throws. Logs warning on any failure.
     *
     * @param userId User UUID (matches Supabase auth user id)
     */
    public void deleteUser(UUID userId) {
        if (projectUrl == null || projectUrl.isBlank()
                || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            log.warn("Supabase admin not configured - skipping user deletion in Supabase Auth (userId={})", userId);
            return;
        }

        String endpoint = projectUrl + "/auth/v1/admin/users/" + userId;

        try {
            // retrieve() throws on 4xx/5xx, so any return from this chain is a 2xx success.
            restClient.delete()
                .uri(endpoint)
                // secret keys (sb_secret_...) are not JWTs: only on apikey, never as Bearer (Supabase docs)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .toBodilessEntity();
            log.info("Supabase user deleted successfully (userId={})", userId);
        } catch (RestClientException e) {
            // NEVER log the service-role-key. Only log message + class name.
            log.warn("Supabase user deletion failed (userId={}): {} - {}",
                userId, e.getClass().getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error deleting Supabase user (userId={}): {} - {}",
                userId, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
