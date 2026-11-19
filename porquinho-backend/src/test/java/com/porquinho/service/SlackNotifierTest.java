package com.porquinho.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Story 10.5: the Slack webhook. */
class SlackNotifierTest {

    private static final String URL = "https://hooks.slack.test/services/T/B/X";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void postsTheTextAsJson() {
        server.expect(requestTo(URL)).andExpect(method(POST))
            .andExpect(jsonPath("$.text").value("Redis fora do ar"))
            .andRespond(withSuccess());
        assertThat(new SlackNotifier(URL, builder, false).send("Redis fora do ar")).isTrue();
        server.verify();
    }

    @Test
    void serverErrorIsFalseAndDoesNotThrow() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(new SlackNotifier(URL, builder, false).send("x")).isFalse();
    }

    @Test
    void timeoutIsFalseAndDoesNotThrow() {
        server.expect(requestTo(URL)).andRespond(withException(new java.net.SocketTimeoutException("read timed out")));
        assertThat(new SlackNotifier(URL, builder, false).send("x")).isFalse();
    }

    @Test
    void withoutUrlIsANoOp() {
        SlackNotifier slack = new SlackNotifier("", builder, false);
        assertThat(slack.isConfigured()).isFalse();
        assertThat(slack.send("x")).isFalse();
        server.verify();
    }
}
