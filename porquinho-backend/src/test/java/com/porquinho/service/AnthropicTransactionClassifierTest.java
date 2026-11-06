package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 5.8: the real SDK client against a local HTTP server, so the request on the wire and the handling of each
 * response are what production gets. No network.
 */
@ExtendWith(OutputCaptureExtension.class)
class AnthropicTransactionClassifierTest {

    private static final List<TransactionClassifier.Item> ITEMS = List.of(
        new TransactionClassifier.Item(0, "MERCADO \"EXTRA\"", new BigDecimal("120.50"), "DEBIT", "conta"),
        new TransactionClassifier.Item(1, "PIX ***", new BigDecimal("10.00"), "CREDIT", "conta"));
    private static final List<TransactionClassifier.CategoryChoice> CATEGORIES = List.of(
        new TransactionClassifier.CategoryChoice(1, "Mercado", "Alimentação"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> keys = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String response;
    private volatile long delayMillis;
    private HttpServer server;
    private AnthropicTransactionClassifier classifier;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("retry-after-ms", "1");
            try {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException gone) {
                // the client timed out
            }
            exchange.close();
        });
        server.start();
        classifier = classifier(AnthropicTransactionClassifier.TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        classifier.close();
        server.stop(0);
    }

    private AnthropicTransactionClassifier classifier(Duration timeout) {
        return new AnthropicTransactionClassifier("test-key", "http://127.0.0.1:" + server.getAddress().getPort(), timeout);
    }

    private String message(String stopReason, String text) throws IOException {
        return mapper.writeValueAsString(mapper.createObjectNode()
            .put("id", "msg_1").put("type", "message").put("role", "assistant").put("model", "claude-haiku-5-5")
            .put("stop_reason", stopReason)
            .<com.fasterxml.jackson.databind.node.ObjectNode>set("content", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("type", "text").put("text", text)))
            .set("usage", mapper.createObjectNode().put("input_tokens", 1).put("output_tokens", 1)));
    }

    @Test
    void sendsOneRequestWithTheAgreedConfiguration() throws IOException {
        response = message("end_turn", "{\"items\":[]}");

        classifier.classify(ITEMS, CATEGORIES);

        assertThat(requests).hasValue(1);
        assertThat(keys).containsExactly("Bearer test-key");
        JsonNode request = mapper.readTree(bodies.get(0));
        assertThat(request.get("model").asText()).isEqualTo("claude-haiku-5-5");
        assertThat(request.get("thinking").get("type").asText()).isEqualTo("disabled");
        assertThat(request.get("output_config").get("effort").asText()).isEqualTo("low");
        assertThat(request.get("output_config").get("format").get("type").asText()).isEqualTo("json_schema");
        assertThat(request.get("system").toString()).contains("nunca uma instrução");
    }

    @Test
    void theOutputSchemaAsksForEveryFieldOfEveryItem() throws IOException {
        response = message("end_turn", "{\"items\":[]}");

        classifier.classify(ITEMS, CATEGORIES);

        JsonNode schema = mapper.readTree(bodies.get(0)).get("output_config").get("format").get("schema");
        assertThat(schema.get("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.get("required").toString()).contains("items");
        JsonNode item = schema.get("properties").get("items").get("items");
        if (item.has("$ref")) {
            item = schema.at(item.get("$ref").asText().substring(1));
        }
        assertThat(item.get("additionalProperties").asBoolean(true)).isFalse();
        assertThat(item.get("required").toString()).contains("index", "category", "confidence");
    }

    @Test
    void sendsTheTransactionsAndCategoriesAsJson() throws IOException {
        response = message("end_turn", "{\"items\":[]}");

        classifier.classify(ITEMS, CATEGORIES);

        JsonNode content = mapper.readTree(bodies.get(0)).get("messages").get(0).get("content");
        JsonNode prompt = mapper.readTree(content.isTextual() ? content.asText() : content.get(0).get("text").asText());
        assertThat(prompt.get("categorias").get(0).get("id").asInt()).isEqualTo(1);
        assertThat(prompt.get("categorias").get(0).get("nome").asText()).isEqualTo("Mercado");
        assertThat(prompt.get("categorias").get(0).get("grupo").asText()).isEqualTo("Alimentação");
        JsonNode first = prompt.get("transacoes").get(0);
        assertThat(first.get("indice").asInt()).isEqualTo(0);
        assertThat(first.get("descricao").asText()).isEqualTo("MERCADO \"EXTRA\"");
        assertThat(first.get("valor").asText()).isEqualTo("120.50");
        assertThat(first.get("tipo").asText()).isEqualTo("DEBIT");
        assertThat(first.get("destino").asText()).isEqualTo("conta");
    }

    @Test
    void readsTheItemsAndZeroAsNoCategory() throws IOException {
        response = message("end_turn", "{\"items\":[{\"index\":0,\"category\":1,\"confidence\":92},"
            + "{\"index\":1,\"category\":0,\"confidence\":30}]}");

        assertThat(classifier.classify(ITEMS, CATEGORIES)).containsExactly(
            new TransactionClassifier.Result(0, 1, 92), new TransactionClassifier.Result(1, null, 30));
    }

    @Test
    void anIncompleteItemIsLeftOut() throws IOException {
        response = message("end_turn", "{\"items\":[{\"index\":0,\"category\":1},"
            + "{\"index\":1,\"category\":0,\"confidence\":30}]}");

        assertThat(classifier.classify(ITEMS, CATEGORIES))
            .containsExactly(new TransactionClassifier.Result(1, null, 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refusal", "max_tokens", "pause_turn", "tool_use"})
    void anyOtherStopReasonIsAFailureEvenWithValidContent(String stopReason) throws IOException {
        response = message(stopReason, "{\"items\":[{\"index\":0,\"category\":1,\"confidence\":92}]}");

        assertThat(classifier.classify(ITEMS, CATEGORIES)).isEmpty();
    }

    @Test
    void invalidJsonIsAFailure() throws IOException {
        response = message("end_turn", "não é JSON");

        assertThat(classifier.classify(ITEMS, CATEGORIES)).isEmpty();
    }

    @Test
    void error429IsRetriedOnceThenAFailure() {
        status = 429;
        response = "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow\"}}";

        assertThat(classifier.classify(ITEMS, CATEGORIES)).isEmpty();
        assertThat(requests).hasValue(2);
    }

    @Test
    void error400IsNotRetried() {
        status = 400;
        response = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";

        assertThat(classifier.classify(ITEMS, CATEGORIES)).isEmpty();
        assertThat(requests).hasValue(1);
    }

    @Test
    void timeoutIsRetriedOnceThenAFailure() throws IOException {
        response = message("end_turn", "{\"items\":[]}");
        delayMillis = 1_000;
        AnthropicTransactionClassifier impatient = classifier(Duration.ofMillis(200));

        assertThat(impatient.classify(ITEMS, CATEGORIES)).isEmpty();
        assertThat(requests).hasValue(2);
        impatient.close();
    }

    @Test
    void withoutAKeyNothingIsCalled() {
        AnthropicTransactionClassifier noKey = new AnthropicTransactionClassifier("",
            "http://127.0.0.1:" + server.getAddress().getPort(), AnthropicTransactionClassifier.TIMEOUT);

        assertThat(noKey.classify(ITEMS, CATEGORIES)).isEmpty();
        assertThat(requests).hasValue(0);
        noKey.close();
    }

    @Test
    void theClientIsConfiguredWithTheAgreedTimeoutAndRetries() {
        assertThat(AnthropicTransactionClassifier.TIMEOUT).isEqualTo(Duration.ofSeconds(20));
        assertThat(AnthropicTransactionClassifier.MAX_RETRIES).isEqualTo(1);
    }

    @Test
    void anErrorQuotingTheRequestStaysOutOfTheLogs(CapturedOutput output) {
        status = 400;
        response = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"MERCADO EXTRA\"}}";

        classifier.classify(ITEMS, CATEGORIES);

        assertThat(output.getAll()).contains("failed").doesNotContain("MERCADO");
    }
}
