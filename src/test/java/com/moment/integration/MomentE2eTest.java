package com.moment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * E2E Integration Test: Moment Core User Flow via actual HTTP calls
 *
 * Scenario: User A → User B (1:1 space creation via invitation)
 * 1. A & B sign up/login
 * 2. A issues invite code
 * 3. B sends invitation via code
 * 4. A receives and accepts → space created
 * 5. A creates moment
 * 6. B retrieves timeline (sees A's moment)
 * 7. B adds reaction (emoji)
 * 8. Timeline shows aggregated reaction count + myReaction
 * 9. Widget endpoint (latest excluding own)
 * 10. Security: 401 unauthenticated, 403 non-member, IDOR prevention
 * 11. User C searches for A by handle
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MomentE2eTest {

    @LocalServerPort
    private int port;

    private RestClient client;
    private ObjectMapper mapper = new ObjectMapper();

    private String tokenA;
    private String tokenB;
    private String tokenC;
    private UUID spaceId;
    private UUID momentId;

    @BeforeEach
    void setUp() {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();

        // 테스트 메서드마다 고유 사용자를 쓴다 — H2가 클래스 전체에서 공유되므로
        // 고정 토큰을 쓰면 앞선 테스트가 만든 스페이스/연결이 다음 테스트를 오염시킨다.
        String runId = UUID.randomUUID().toString();

        // 1. User A login
        String respA = client.post()
                .uri("/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"identityToken\":\"user-a-" + runId + "\",\"nickname\":\"에이\"}")
                .retrieve()
                .body(String.class);
        tokenA = extractToken(respA);

        // 2. User B login
        String respB = client.post()
                .uri("/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"identityToken\":\"user-b-" + runId + "\",\"nickname\":\"비비\"}")
                .retrieve()
                .body(String.class);
        tokenB = extractToken(respB);

        // 3. User C login
        String respC = client.post()
                .uri("/v1/auth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"identityToken\":\"user-c-" + runId + "\",\"nickname\":\"씨씨\"}")
                .retrieve()
                .body(String.class);
        tokenC = extractToken(respC);
    }

    @Test
    @DisplayName("E2E: Invitation flow → moment creation → reactions")
    void e2e_completeUserFlow() {
        // 1. A issues code
        String codeResp = client.post()
                .uri("/v1/invite-codes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        String code = extractFromData(codeResp, "code");

        // 2. B sends invitation via code
        String sendResp = client.post()
                .uri("/v1/invitations")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"toUserId\":null,\"code\":\"" + code + "\"}")
                .retrieve()
                .body(String.class);
        assertSuccess(sendResp);

        // 3. A receives invitation
        String invListResp = client.get()
                .uri("/v1/invitations?received=true")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var invList = (List<?>) extractRoot(invListResp).get("data");
        assertThat(invList).isNotEmpty();

        var inv = mapper.convertValue(invList.get(0), Map.class);
        String invId = (String) inv.get("id");

        // 4. A accepts invitation
        String acceptResp = client.patch()
                .uri("/v1/invitations/{id}", invId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"action\":\"accept\"}")
                .retrieve()
                .body(String.class);
        assertSuccess(acceptResp);

        // 5. Verify space created
        String spacesResp = client.get()
                .uri("/v1/spaces/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var spaces = (List<?>) extractRoot(spacesResp).get("data");
        assertThat(spaces).hasSize(1);
        var space = mapper.convertValue(spaces.get(0), Map.class);
        spaceId = UUID.fromString((String) space.get("id"));

        // 6. A creates moment
        String createResp = client.post()
                .uri("/v1/moments")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"spaceId\":\"" + spaceId + "\",\"text\":\"첫 순간\"}")
                .retrieve()
                .body(String.class);
        assertSuccess(createResp);
        momentId = UUID.fromString(extractFromData(createResp, "id"));

        // 7. B sees timeline
        String timelineResp = client.get()
                .uri("/v1/spaces/{id}/moments", spaceId)
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(String.class);
        var moments = (List<?>) extractRoot(timelineResp).get("data");
        assertThat(moments).hasSize(1);

        // 8. B adds reaction
        String reactionResp = client.put()
                .uri("/v1/moments/{id}/reaction", momentId)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"emoji\":\"❤️\"}")
                .retrieve()
                .body(String.class);
        assertSuccess(reactionResp);

        // 9. Verify reaction in timeline
        String timelineResp2 = client.get()
                .uri("/v1/spaces/{id}/moments", spaceId)
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(String.class);
        var moment = mapper.convertValue(((List<?>) extractRoot(timelineResp2).get("data")).get(0), Map.class);
        var reactions = (List<?>) moment.get("reactions");
        assertThat(reactions).hasSize(1);
        assertThat(moment.get("myReaction")).isEqualTo("❤️");
    }

    @Test
    @DisplayName("회원 탈퇴 후: 본인 토큰 무효화 + 상대방의 스페이스·타임라인 접근 차단")
    void withdrawal_revokesSelfAndPartnerAccess() {
        UUID space = connectAAndB();

        // A가 회원 탈퇴
        String deleteResp = client.delete()
                .uri("/v1/users/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        assertSuccess(deleteResp);

        // 1) 탈퇴한 A의 Access Token은 더 이상 통하지 않는다 (계정 비활성)
        String meResp = client.get()
                .uri("/v1/users/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        assertThat(extractRoot(meResp).get("success")).isEqualTo(false);

        // 2) 상대방 B의 스페이스 목록에서 해당 스페이스가 사라진다
        String spacesResp = client.get()
                .uri("/v1/spaces/me")
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(String.class);
        var spaces = (List<?>) extractRoot(spacesResp).get("data");
        assertThat(spaces).isEmpty();

        // 3) 상대방 B는 더 이상 해당 스페이스 타임라인에 접근할 수 없다 (비멤버)
        String timelineResp = client.get()
                .uri("/v1/spaces/{id}/moments", space)
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(String.class);
        assertThat(extractRoot(timelineResp).get("success")).isEqualTo(false);
    }

    // A와 B를 연결하고 생성된 스페이스 ID를 반환한다.
    private UUID connectAAndB() {
        String codeResp = client.post()
                .uri("/v1/invite-codes")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        String code = extractFromData(codeResp, "code");

        assertSuccess(client.post()
                .uri("/v1/invitations")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"toUserId\":null,\"code\":\"" + code + "\"}")
                .retrieve()
                .body(String.class));

        String invListResp = client.get()
                .uri("/v1/invitations?received=true")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var invList = (List<?>) extractRoot(invListResp).get("data");
        String invId = (String) mapper.convertValue(invList.get(0), Map.class).get("id");

        assertSuccess(client.patch()
                .uri("/v1/invitations/{id}", invId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"action\":\"accept\"}")
                .retrieve()
                .body(String.class));

        String spacesResp = client.get()
                .uri("/v1/spaces/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var spaces = (List<?>) extractRoot(spacesResp).get("data");
        return UUID.fromString((String) mapper.convertValue(spaces.get(0), Map.class).get("id"));
    }

    @Test
    @DisplayName("Security: 401 unauthenticated access")
    void security_unauthenticatedForbidden() {
        String resp = client.get()
                .uri("/v1/spaces/me")
                .retrieve()
                .body(String.class);
        var root = extractRoot(resp);
        assertThat(root.get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("Security: 403 non-member timeline access")
    void security_nonMemberForbidden() {
        // Complete invitation to create space
        String codeResp = client.post()
                .uri("/v1/invite-codes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        String code = extractFromData(codeResp, "code");

        client.post()
                .uri("/v1/invitations")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"toUserId\":null,\"code\":\"" + code + "\"}")
                .retrieve()
                .body(String.class);

        String invListResp = client.get()
                .uri("/v1/invitations?received=true")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var invList = (List<?>) extractRoot(invListResp).get("data");
        var inv = mapper.convertValue(invList.get(0), Map.class);

        client.patch()
                .uri("/v1/invitations/{id}", inv.get("id"))
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"action\":\"accept\"}")
                .retrieve()
                .body(String.class);

        String spacesResp = client.get()
                .uri("/v1/spaces/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        var spaces = (List<?>) extractRoot(spacesResp).get("data");
        spaceId = UUID.fromString((String) mapper.convertValue(spaces.get(0), Map.class).get("id"));

        // C tries to access A-B space
        String forbiddenResp = client.get()
                .uri("/v1/spaces/{id}/moments", spaceId)
                .header("Authorization", "Bearer " + tokenC)
                .retrieve()
                .body(String.class);
        assertThat(extractRoot(forbiddenResp).get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("Search: User C finds A by auto-generated handle")
    void search_findUserByHandle() {
        // 핸들은 가입 시 자동 생성되므로(F-01-07) A의 실제 핸들을 먼저 조회한다.
        String meResp = client.get()
                .uri("/v1/users/me")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(String.class);
        String handleA = extractFromData(meResp, "handle");
        assertThat(handleA).matches("^[a-z0-9_]{4,15}$");

        String searchResp = client.get()
                .uri("/v1/users/search?handle={handle}", handleA)
                .header("Authorization", "Bearer " + tokenC)
                .retrieve()
                .body(String.class);
        var userData = extractData(searchResp);
        assertThat(userData.get("handle")).isEqualTo(handleA);
        assertThat(userData.get("nickname")).isEqualTo("에이");
    }

    // Helpers

    private String extractToken(String json) {
        return extractFromData(json, "accessToken");
    }

    private String extractFromData(String json, String key) {
        return (String) extractData(json).get(key);
    }

    // 변경 요청 응답이 실제로 성공(success=true)했는지 검증한다.
    // (모든 오류를 삼키는 status handler 때문에 중간 실패가 조용히 통과하는 것을 방지)
    private void assertSuccess(String json) {
        assertThat(extractRoot(json).get("success"))
                .as("API response should be successful: %s", json)
                .isEqualTo(true);
    }

    private Map<String, Object> extractData(String json) {
        try {
            var root = extractRoot(json);
            return mapper.convertValue(root.get("data"), new TypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> extractRoot(String json) {
        try {
            return mapper.readValue(json, new TypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
