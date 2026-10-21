package com.moment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 트랜잭션에서 동시성을 재현한다. 스케줄링 특성상 단일 실행으로 겹침이 보장되지는 않으므로
 * 각 시나리오를 여러 번 반복해 겹칠 확률을 높인다.
 */
@Tag("integration")
@Testcontainers
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyIntegrationTest {

    /** 시나리오 반복 횟수 — 순차 실행으로도 통과할 수 있는 거짓 음성을 줄이기 위한 반복. */
    private static final int REPEAT = 5;

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
        for (int round = 0; round < REPEAT; round++) {
            RestClient client = client();
            String runId = UUID.randomUUID().toString();
            JsonNode login = login(client, "refresh-" + runId, "리프레시");
            String refreshToken = login.get("data").get("refreshToken").asText();
            UUID userId = UUID.fromString(me(client, login.get("data").get("accessToken").asText())
                    .get("data").get("id").asText());

            List<JsonNode> results = runConcurrently(2, () ->
                    post(client, "/v1/auth/refresh", null,
                            "{\"refreshToken\":\"" + refreshToken + "\"}"));

            assertThat(results).hasSize(2);
            assertThat(results.stream().filter(r -> r.get("success").asBoolean())).hasSize(1);
            assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                    .map(r -> r.get("error").get("code").asText()))
                    .containsExactly("INVALID_TOKEN");

            Integer tokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId);
            assertThat(tokenCount).as("round %d: 새 refresh token은 정확히 1개", round).isEqualTo(1);
        }
    }

    // ---- 2. 동일 초대 동시 수락 ----

    @Test
    @DisplayName("동일 초대 동시 수락: 하나만 성공, 스페이스 1개·멤버십 2개, 초대는 ACCEPTED")
    void concurrentAcceptSameInvitation_onlyOneSucceeds() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            RestClient client = client();
            String runId = UUID.randomUUID().toString();
            String tokenA = login(client, "a-" + runId, "에이").get("data").get("accessToken").asText();
            String tokenB = login(client, "b-" + runId, "비비").get("data").get("accessToken").asText();
            UUID aId = UUID.fromString(me(client, tokenA).get("data").get("id").asText());
            UUID bId = UUID.fromString(me(client, tokenB).get("data").get("id").asText());

            // A가 코드를 발급하고 B가 제출 → 수신자는 A. A가 그 초대를 수락한다.
            String code = post(client, "/v1/invite-codes", tokenA, null).get("data").get("code").asText();
            post(client, "/v1/invitations", tokenB, "{\"toUserId\":null,\"code\":\"" + code + "\"}");
            String invId = firstReceivedInvitationId(client, tokenA);

            List<JsonNode> results = runConcurrently(2, () ->
                    patch(client, "/v1/invitations/" + invId, tokenA, "{\"action\":\"accept\"}"));

            assertThat(results).hasSize(2);
            assertThat(results.stream().filter(r -> r.get("success").asBoolean())).hasSize(1);
            // 패자는 CONFLICT("이미 처리된 요청이에요" 또는 "이미 연결된 상대가 있어요")
            assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                    .map(r -> r.get("error").get("code").asText()))
                    .containsExactly("CONFLICT");

            // 초대는 ACCEPTED로 커밋되어야 한다
            String status = jdbc.queryForObject(
                    "SELECT status FROM space_invitations WHERE id = ?",
                    String.class, UUID.fromString(invId));
            assertThat(status).isEqualTo("ACCEPTED");

            // 스페이스 1개, 멤버십 2개 — 이중 스페이스·부분 멤버십 없음
            Long distinctSpaces = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT space_id) FROM space_members "
                            + "WHERE user_id IN (?, ?) AND left_at IS NULL",
                    Long.class, aId, bId);
            Long memberships = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space_members WHERE user_id IN (?, ?) AND left_at IS NULL",
                    Long.class, aId, bId);
            assertThat(distinctSpaces).as("round %d", round).isEqualTo(1);
            assertThat(memberships).as("round %d", round).isEqualTo(2);
        }
    }

    // ---- 3. 서로 다른 초대가 동일 사용자(B)를 공유 ----

    @Test
    @DisplayName("서로 다른 초대 동시 수락(A→B, C→B): 하나만 성공, B의 활성 1:1 스페이스는 정확히 하나")
    void concurrentAcceptDifferentInvitationsSharingUser_singleSpace() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            RestClient client = client();
            String runId = UUID.randomUUID().toString();
            String tokenA = login(client, "a-" + runId, "에이").get("data").get("accessToken").asText();
            String tokenB = login(client, "b-" + runId, "비비").get("data").get("accessToken").asText();
            String tokenC = login(client, "c-" + runId, "씨씨").get("data").get("accessToken").asText();
            UUID aId = UUID.fromString(me(client, tokenA).get("data").get("id").asText());
            UUID bId = UUID.fromString(me(client, tokenB).get("data").get("id").asText());
            UUID cId = UUID.fromString(me(client, tokenC).get("data").get("id").asText());

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

            assertThat(results).hasSize(2);
            assertThat(results.stream().filter(r -> r.get("success").asBoolean())).hasSize(1);
            assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                    .map(r -> r.get("error").get("code").asText()))
                    .containsExactly("CONFLICT");

            // B의 활성 1:1 스페이스는 정확히 하나, 전체 활성 멤버십은 승자 쌍의 2개뿐
            // (고아 스페이스·부분 멤버십 없음)
            assertThat(activeMembershipCount(bId)).as("round %d", round).isEqualTo(1);
            Long totalMemberships = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space_members WHERE user_id IN (?, ?, ?) AND left_at IS NULL",
                    Long.class, aId, bId, cId);
            assertThat(totalMemberships).as("round %d", round).isEqualTo(2);
        }
    }

    // ---- 4. 초대 수락과 차단의 경합 ----

    @Test
    @DisplayName("수락과 차단 동시 실행: 차단 관계와 ACTIVE 공유 스페이스가 동시에 존재해서는 안 된다")
    void concurrentAcceptAndBlock_neverBothBlockAndActiveSpace() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            RestClient client = client();
            String runId = UUID.randomUUID().toString();
            String tokenA = login(client, "a-" + runId, "에이").get("data").get("accessToken").asText();
            String tokenB = login(client, "b-" + runId, "비비").get("data").get("accessToken").asText();
            UUID aId = UUID.fromString(me(client, tokenA).get("data").get("id").asText());
            UUID bId = UUID.fromString(me(client, tokenB).get("data").get("id").asText());

            // B가 코드 발급, A가 제출 → 수신자 B가 수락할 초대가 준비됨
            String code = post(client, "/v1/invite-codes", tokenB, null).get("data").get("code").asText();
            post(client, "/v1/invitations", tokenA, "{\"toUserId\":null,\"code\":\"" + code + "\"}");
            String invId = firstReceivedInvitationId(client, tokenB);

            // 동시 실행: B는 수락, A는 B를 차단
            AtomicInteger idx = new AtomicInteger();
            List<JsonNode> results = runConcurrently(2, () -> {
                if (idx.getAndIncrement() == 0) {
                    return patch(client, "/v1/invitations/" + invId, tokenB, "{\"action\":\"accept\"}");
                }
                return post(client, "/v1/blocks", tokenA, "{\"targetUserId\":\"" + bId + "\"}");
            });
            assertThat(results).hasSize(2);

            // 차단은 반드시 성공한다 (수락이 먼저면 차단이 스페이스를 정리, 차단이 먼저면 수락이 거부됨)
            Long blockCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
                    Long.class, aId, bId);
            assertThat(blockCount).as("round %d: 차단 저장", round).isEqualTo(1);

            // 불변식: 차단 관계가 있는 두 사용자가 ACTIVE 스페이스를 공유하면 안 된다
            Long activeSharedSpaces = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM spaces s WHERE s.status = 'ACTIVE' "
                            + "AND EXISTS (SELECT 1 FROM space_members m WHERE m.space_id = s.id "
                            + "            AND m.user_id = ? AND m.left_at IS NULL) "
                            + "AND EXISTS (SELECT 1 FROM space_members m WHERE m.space_id = s.id "
                            + "            AND m.user_id = ? AND m.left_at IS NULL)",
                    Long.class, aId, bId);
            assertThat(activeSharedSpaces)
                    .as("round %d: 차단된 사용자끼리 활성 스페이스 공유 금지", round)
                    .isEqualTo(0);
        }
    }

    // ---- Helpers ----

    /**
     * n개의 호출을 최대한 동시에 실행한다.
     * ready barrier로 모든 worker가 출발선에 도달한 것을 확인한 뒤 start를 연다.
     * Future.get()으로 worker 예외를 테스트 스레드로 전파하고, 결과 개수(n)를 보장한다.
     */
    // ---- 그룹 정원 동시 입장 ----

    @Test
    @DisplayName("정원 6 그룹에 7명이 동시에 같은 코드로 입장: 5명만 성공(방장 포함 6), 나머지는 CONFLICT")
    void concurrentGroupJoin_neverExceedsCapacity() throws Exception {
        for (int round = 0; round < REPEAT; round++) {
            RestClient client = client();
            String runId = UUID.randomUUID().toString();
            String ownerToken = login(client, "owner-" + runId, "방장").get("data").get("accessToken").asText();
            JsonNode created = post(client, "/v1/groups", ownerToken, "{\"name\":\"동시입장\"}").get("data");
            String spaceId = created.get("space").get("id").asText();
            String code = created.get("inviteCode").asText();

            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                tokens.add(login(client, "joiner-" + i + "-" + runId, "참여" + i).get("data").get("accessToken").asText());
            }
            AtomicInteger next = new AtomicInteger();
            List<JsonNode> results = runConcurrently(tokens.size(), () ->
                    post(client, "/v1/groups/join", tokens.get(next.getAndIncrement()),
                            "{\"code\":\"" + code + "\"}"));

            assertThat(results.stream().filter(r -> r.get("success").asBoolean()))
                    .as("round %d: 성공한 입장 수", round).hasSize(5);
            assertThat(results.stream().filter(r -> !r.get("success").asBoolean())
                    .map(r -> r.get("error").get("code").asText()))
                    .as("round %d: 실패는 모두 CONFLICT", round)
                    .containsOnly("CONFLICT");

            Integer active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM space_members WHERE space_id = ?::uuid AND left_at IS NULL",
                    Integer.class, spaceId);
            assertThat(active).as("round %d: 활성 멤버는 정원 6", round).isEqualTo(6);
        }
    }

    private List<JsonNode> runConcurrently(int n, Callable<JsonNode> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start latch timeout");
                    }
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("모든 worker가 출발선에 도달해야 한다")
                    .isTrue();
            start.countDown();

            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                // worker에서 발생한 예외(전송 오류·타임아웃 포함)를 여기서 그대로 전파한다
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).hasSize(n);
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode login(RestClient client, String token, String nickname) {
        return post(client, "/v1/auth/apple", null,
                "{\"identityToken\":\"" + token
                        + "\",\"authorizationCode\":\"stub-code\",\"nonce\":\"stub-nonce\",\"nickname\":\""
                        + nickname + "\"}");
    }

    private JsonNode me(RestClient client, String accessToken) {
        return get(client, "/v1/users/me", accessToken);
    }

    private String firstReceivedInvitationId(RestClient client, String token) {
        return receivedInvitationIds(client, token).get(0);
    }

    private List<String> receivedInvitationIds(RestClient client, String token) {
        JsonNode resp = get(client, "/v1/invitations?received=true", token);
        List<String> ids = new ArrayList<>();
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
