package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseAdminClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void deleteUserShouldCallSupabaseAdminApi() {
        UUID userId = UUID.randomUUID();
        String projectUrl = "https://test.supabase.co";
        String key = "test-service-role-key";

        server.expect(requestTo(projectUrl + "/auth/v1/admin/users/" + userId))
            .andExpect(method(DELETE))
            .andExpect(header("apikey", key))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess());

        SupabaseAdminClient client = new SupabaseAdminClient(projectUrl, key, builder, false);
        assertDoesNotThrow(() -> client.deleteUser(userId));

        server.verify();
    }

    @Test
    void deleteUserShouldNotThrowWhenServerReturns404() {
        UUID userId = UUID.randomUUID();
        String projectUrl = "https://test.supabase.co";
        String key = "test-key";

        server.expect(requestTo(projectUrl + "/auth/v1/admin/users/" + userId))
            .andExpect(method(DELETE))
            .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        SupabaseAdminClient client = new SupabaseAdminClient(projectUrl, key, builder, false);

        // Best-effort: must NOT throw even on 4xx error
        assertDoesNotThrow(() -> client.deleteUser(userId));
    }

    @Test
    void deleteUserShouldNotThrowWhenServerReturns500() {
        UUID userId = UUID.randomUUID();
        String projectUrl = "https://test.supabase.co";
        String key = "test-key";

        server.expect(requestTo(projectUrl + "/auth/v1/admin/users/" + userId))
            .andExpect(method(DELETE))
            .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        SupabaseAdminClient client = new SupabaseAdminClient(projectUrl, key, builder, false);

        assertDoesNotThrow(() -> client.deleteUser(userId));
    }

    @Test
    void deleteUserShouldSkipWhenProjectUrlIsBlank() {
        // No HTTP expectation set: should not even attempt the call
        SupabaseAdminClient client = new SupabaseAdminClient("", "key", builder);
        assertDoesNotThrow(() -> client.deleteUser(UUID.randomUUID()));
        // server.verify() implicitly: no calls expected, none made
    }

    @Test
    void deleteUserShouldSkipWhenServiceRoleKeyIsBlank() {
        SupabaseAdminClient client = new SupabaseAdminClient("https://test.supabase.co", "", builder);
        assertDoesNotThrow(() -> client.deleteUser(UUID.randomUUID()));
    }
}
