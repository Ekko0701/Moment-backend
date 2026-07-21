package com.moment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL(Testcontainers)에서 잠금·조건부 삭제의 동시성 보장을 검증한다.
 *
 * <p>기본 {@code test} 태스크에서는 제외되고 {@code integrationTest} 태스크(CI 전용)에서만 실행된다.
 * Docker가 없으면 컨테이너 기동이 실패하며 테스트도 실패한다(조용히 스킵하지 않음).
 *
 * <p>클래스 전체에 @Transactional을 붙이지 않는다 — 실제 HTTP 요청으로 서로 독립된
 * 트랜잭션에서 동시성을 재현한다.
 */
@Tag("integration")
@Testcontainers
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    // ---- 1. 동일 refresh token 동시 사용 ----

    @Test
    @DisplayName("동일 refresh token 동시 사용: 정확히 하나만 성공하고 DB에 새 토큰이 하나만 남는다")
    void concurrentRefresh_onlyOneSucceeds() throws Exception {
        RestClient client = client();
        String runId = UUID.randomUUID().toString();
        JsonNode login = login(client, "refresh-" + runId, "리프레시");
        String refreshToken = login.get("data").get("refreshToken").asText();
        UUID userId = UUID.fromString(me(client, login.get("data").get("accessToken").asText())
                .get("data").get("id").asText());

        List<JsonNode> results = runConcurrently(2, () ->
                post(client, "/v1/auth/refresh", null,
                        "{\"refreshToken\":\"" + refreshToken + "\"}"));

        long ok = results.stream().filter(r -> r.get("success").asBoolean()).count();
        assertThat(ok).isEqualTo(1);
        assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                .map(r -> r.get("error").get("code").asText()))
                .containsExactly("INVALID_TOKEN");

        Integer tokenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId);
        assertThat(tokenCount).isEqualTo(1);
    }

    // ---- 2. 동일 초대 동시 수락 ----

    @Test
    @DisplayName("동일 초대 동시 수락: 하나만 성공, 활성 멤버십 1개, 초대는 ACCEPTED")
    void concurrentAcceptSameInvitation_onlyOneSucceeds() throws Exception {
        RestClient client = client();
        String runId = UUID.randomUUID().toString();
        String tokenA = login(client, "a-" + runId, "에이").get("data").get("accessToken").asText();
        String tokenB = login(client, "b-" + runId, "비비").get("data").get("accessToken").asText();
        UUID aId = UUID.fromString(me(client, tokenA).get("data").get("id").asText());

        // A가 코드를 발급하고 B가 제출 → 수신자는 A. A가 그 초대를 수락한다.
        String code = post(client, "/v1/invite-codes", tokenA, null).get("data").get("code").asText();
        post(client, "/v1/invitations", tokenB, "{\"toUserId\":null,\"code\":\"" + code + "\"}");
        String invId = firstReceivedInvitationId(client, tokenA);

        List<JsonNode> results = runConcurrently(2, () ->
                patch(client, "/v1/invitations/" + invId, tokenA, "{\"action\":\"accept\"}"));

        long ok = results.stream().filter(r -> r.get("success").asBoolean()).count();
        assertThat(ok).isEqualTo(1);
        assertThat(activeMembershipCount(aId)).isEqualTo(1);
    }

    // ---- 3. 서로 다른 초대가 동일 사용자(B)를 공유 ----

    @Test
    @DisplayName("서로 다른 초대 동시 수락(A→B, C→B): 하나만 성공, B의 활성 1:1 스페이스는 정확히 하나")
    void concurrentAcceptDifferentInvitationsSharingUser_singleSpace() throws Exception {
        RestClient client = client();
        String runId = UUID.randomUUID().toString();
        String tokenA = login(client, "a-" + runId, "에이").get("data").get("accessToken").asText();
        String tokenB = login(client, "b-" + runId, "비비").get("data").get("accessToken").asText();
        String tokenC = login(client, "c-" + runId, "씨씨").get("data").get("accessToken").asText();
        UUID bId = UUID.fromString(me(client, tokenB).get("data").get("id").asText());

        // B가 두 개의 코드를 발급하고 A·C가 각각 제출 → 두 초대 모두 수신자는 B.
        String codeForA = post(client, "/v1/invite-codes", tokenB, null).get("data").get("code").asText();
        post(client, "/v1/invitations", tokenA, "{\"toUserId\":null,\"code\":\"" + codeForA + "\"}");
        String codeForC = post(client, "/v1/invite-codes", tokenB, null).get("data").get("code").asText();
        post(client, "/v1/invitations", tokenC, "{\"toUserId\":null,\"code\":\"" + codeForC + "\"}");

        // B가 받은 두 초대(발신자 A, C)를 동시에 수락
        List<String> invIds = receivedInvitationIds(client, tokenB);
        assertThat(invIds).hasSizeGreaterThanOrEqualTo(2);

        AtomicInteger idx = new AtomicInteger();
        List<JsonNode> results = runConcurrently(2, () -> {
            String invId = invIds.get(idx.getAndIncrement());
            return patch(client, "/v1/invitations/" + invId, tokenB, "{\"action\":\"accept\"}");
        });

        long ok = results.stream().filter(r -> r.get("success").asBoolean()).count();
        assertThat(ok).isEqualTo(1);
        assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                .map(r -> r.get("error").get("code").asText()))
                .containsExactly("CONFLICT");
        // B의 활성 1:1 스페이스는 정확히 하나 (고아 스페이스/부분 멤버십 없음)
        assertThat(activeMembershipCount(bId)).isEqualTo(1);
    }

    // ---- Helpers ----

    private interface Call {
        JsonNode run();
    }

    private List<JsonNode> runConcurrently(int n, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<JsonNode> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(call.run());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown(); // 모든 요청을 동시에 출발
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        return results;
    }

    private JsonNode login(RestClient client, String token, String nickname) {
        return post(client, "/v1/auth/apple", null,
                "{\"identityToken\":\"" + token + "\",\"nickname\":\"" + nickname + "\"}");
    }

    private JsonNode me(RestClient client, String accessToken) {
        return get(client, "/v1/users/me", accessToken);
    }

    private String firstReceivedInvitationId(RestClient client, String token) {
        return receivedInvitationIds(client, token).get(0);
    }

    private List<String> receivedInvitationIds(RestClient client, String token) {
        JsonNode resp = get(client, "/v1/invitations?received=true", token);
        List<String> ids = new java.util.ArrayList<>();
        resp.get("data").forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private long activeMembershipCount(UUID userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM space_members WHERE user_id = ? AND left_at IS NULL",
                Long.class, userId);
    }

    private JsonNode get(RestClient client, String uri, String token) {
        return parse(client.get().uri(uri)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve().body(String.class));
    }

    private JsonNode post(RestClient client, String uri, String token, String body) {
        var spec = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); });
        JsonNode result = (body != null)
                ? parse(spec.body(body).retrieve().body(String.class))
                : parse(spec.retrieve().body(String.class));
        return result;
    }

    private JsonNode patch(RestClient client, String uri, String token, String body) {
        return parse(client.patch().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(body).retrieve().body(String.class));
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Bad JSON: " + json, e);
        }
    }
}
