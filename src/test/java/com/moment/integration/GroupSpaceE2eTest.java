package com.moment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * 그룹 스페이스 HTTP 흐름 E2E.
 *
 * <p>방장 A가 그룹을 만들고, B·C가 코드로 참여를 요청하면 A가 수락한다. 모먼트 공유 → 홈 요약의 나 제외 최신 1건 →
 * 멤버별 최신 1건 → 알림 끄기 → 나가기 → 방장 위임 → 코드 재발급으로 옛 코드 무효화까지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class GroupSpaceE2eTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;
    private String tokenA;
    private String tokenB;
    private String tokenC;
    private String idA;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
        String runId = UUID.randomUUID().toString();
        tokenA = login("group-a-" + runId, "방장");
        tokenB = login("group-b-" + runId, "비비");
        tokenC = login("group-c-" + runId, "씨씨");
        idA = ok(get("/v1/users/me", tokenA)).get("id").asText();
    }

    @Test
    @DisplayName("그룹: 생성 → 코드 참여 요청·수락 → 모먼트 → 홈 요약·멤버별 최신 → 알림 끄기 → 나가기·위임 → 코드 재발급")
    void groupLifecycle() {
        // 1. A가 그룹 생성 → 방장, 6자리 코드
        JsonNode created = ok(post("/v1/groups", tokenA, "{\"name\":\"주말 산책\"}"));
        String spaceId = created.get("space").get("id").asText();
        String code = created.get("inviteCode").asText();
        assertThat(code).hasSize(6);
        assertThat(created.get("space").get("type").asText()).isEqualTo("GROUP");
        assertThat(created.get("space").get("myRole").asText()).isEqualTo("OWNER");
        assertThat(created.get("space").get("maxMembers").asInt()).isEqualTo(6);

        // 2. B는 소문자로 입력해도 요청이 생기고, 수락 전에는 멤버가 아니다. 같은 요청을 다시 보내면 같은 요청이 돌아온다
        JsonNode requestB = ok(post("/v1/groups/join", tokenB, "{\"code\":\"" + code.toLowerCase() + "\"}"));
        assertThat(requestB.get("spaceId").asText()).isEqualTo(spaceId);
        assertThat(requestB.get("status").asText()).isEqualTo("PENDING");
        assertThat(findSpaceOrNull(ok(get("/v1/spaces/me", tokenB)), spaceId)).isNull();
        assertThat(ok(post("/v1/groups/join", tokenB, "{\"code\":\"" + code + "\"}")).get("id").asText())
                .isEqualTo(requestB.get("id").asText());
        assertThat(ok(get("/v1/groups/join-requests/me", tokenB)).get(0).get("spaceName").asText()).isEqualTo("주말 산책");

        // 방장 홈에는 대기 요청 수가 보이고, 요청 목록은 방장만 볼 수 있다
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenA)), spaceId).get("pendingJoinRequestCount").asInt())
                .isEqualTo(1);
        assertThat(errorCode(get("/v1/groups/" + spaceId + "/join-requests", tokenB))).isEqualTo("FORBIDDEN");

        // A가 B를 수락하면 B는 MEMBER. C도 요청·수락. 이미 멤버인 B가 다시 요청하면 CONFLICT
        approve(tokenA, spaceId, requestB.get("id").asText());
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenB)), spaceId).get("myRole").asText()).isEqualTo("MEMBER");
        joinAndApprove(tokenC, tokenA, code, spaceId);
        assertThat(errorCode(post("/v1/groups/join", tokenB, "{\"code\":\"" + code + "\"}"))).isEqualTo("CONFLICT");

        // 3. 멤버는 현재 코드를 조회할 수 있지만 재발급은 방장만
        assertThat(ok(get("/v1/groups/" + spaceId + "/invite-code", tokenB)).get("code").asText()).isEqualTo(code);
        assertThat(errorCode(post("/v1/groups/" + spaceId + "/invite-code", tokenB, null))).isEqualTo("FORBIDDEN");

        // 4. A가 모먼트 공유
        ok(post("/v1/moments", tokenA,
                "{\"spaceId\":\"" + spaceId + "\",\"imageUrl\":null,\"text\":\"산책 시작\"}"));

        // 5. B의 홈 요약: 그룹 카드에 A의 모먼트, 멤버 수 3
        JsonNode bGroup = findSpace(ok(get("/v1/spaces/me", tokenB)), spaceId);
        assertThat(bGroup.get("memberCount").asInt()).isEqualTo(3);
        assertThat(bGroup.get("latestMoment").get("author").get("userId").asText()).isEqualTo(idA);
        assertThat(bGroup.get("notificationsEnabled").asBoolean()).isTrue();

        // A 자신의 홈 요약에는 자기 모먼트가 최신으로 오지 않는다
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenA)), spaceId).get("latestMoment").isNull()).isTrue();

        // 6. 멤버 목록: 방장이 먼저, A에게만 최신 모먼트
        JsonNode members = ok(get("/v1/spaces/" + spaceId + "/members", tokenB));
        assertThat(members.size()).isEqualTo(3);
        assertThat(members.get(0).get("userId").asText()).isEqualTo(idA);
        assertThat(members.get(0).get("role").asText()).isEqualTo("OWNER");
        assertThat(members.get(0).get("latestMoment").get("text").asText()).isEqualTo("산책 시작");
        assertThat(members.get(1).get("latestMoment").isNull()).isTrue();

        // 7. B가 알림을 끈다
        ok(patch("/v1/spaces/" + spaceId + "/members/me", tokenB, "{\"notificationsEnabled\":false}"));
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenB)), spaceId).get("notificationsEnabled").asBoolean())
                .isFalse();

        // 8. 방장 A가 나가면 다음으로 먼저 들어온 B가 방장
        ok(delete("/v1/spaces/" + spaceId + "/members/me", tokenA));
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenB)), spaceId).get("myRole").asText()).isEqualTo("OWNER");

        // 9. 새 방장 B가 코드를 재발급하면 옛 코드로는 A가 요청할 수 없고, 새 코드로 요청해 B가 수락하면 들어온다
        String newCode = ok(post("/v1/groups/" + spaceId + "/invite-code", tokenB, null)).get("code").asText();
        assertThat(newCode).isNotEqualTo(code);
        assertThat(errorCode(post("/v1/groups/join", tokenA, "{\"code\":\"" + code + "\"}"))).isEqualTo("INVALID_REQUEST");
        joinAndApprove(tokenA, tokenB, newCode, spaceId);
        assertThat(findSpace(ok(get("/v1/spaces/me", tokenA)), spaceId).get("myRole").asText()).isEqualTo("MEMBER");

        // 10. 방장 B가 C를 내보낸다 → C의 홈에서 그룹이 사라진다.
        //     코드는 그대로지만, C가 다시 요청해도 방장이 거절하면 들어올 수 없다
        String idC = ok(get("/v1/users/me", tokenC)).get("id").asText();
        ok(delete("/v1/spaces/" + spaceId + "/members/" + idC, tokenB));
        assertThat(findSpaceOrNull(ok(get("/v1/spaces/me", tokenC)), spaceId)).isNull();
        String requestC = ok(post("/v1/groups/join", tokenC, "{\"code\":\"" + newCode + "\"}")).get("id").asText();
        ok(patch("/v1/groups/" + spaceId + "/join-requests/" + requestC, tokenB, "{\"action\":\"reject\"}"));
        assertThat(findSpaceOrNull(ok(get("/v1/spaces/me", tokenC)), spaceId)).isNull();
        assertThat(ok(get("/v1/groups/join-requests/me", tokenC)).size()).isZero();

        // 11. 비멤버는 그룹 정보에 접근할 수 없다
        assertThat(errorCode(get("/v1/spaces/" + spaceId + "/members", tokenC))).isEqualTo("FORBIDDEN");
        assertThat(errorCode(get("/v1/spaces/" + spaceId + "/moments", tokenC))).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("그룹: 1:1 연결이 있어도 그룹을 만들고 참여할 수 있고, 홈 목록에서 1:1이 먼저 온다")
    void groupCoexistsWithOneToOne() {
        // A와 B를 1:1로 연결
        String code = ok(post("/v1/invite-codes", tokenA, null)).get("code").asText();
        ok(post("/v1/invitations", tokenB, "{\"toUserId\":null,\"code\":\"" + code + "\"}"));
        String invId = ok(get("/v1/invitations?received=true", tokenA)).get(0).get("id").asText();
        ok(patch("/v1/invitations/" + invId, tokenA, "{\"action\":\"accept\"}"));

        // A가 그룹을 만들고 B가 참여
        JsonNode created = ok(post("/v1/groups", tokenA, "{\"name\":\"가족\"}"));
        joinAndApprove(tokenB, tokenA, created.get("inviteCode").asText(), created.get("space").get("id").asText());

        JsonNode spaces = ok(get("/v1/spaces/me", tokenB));
        assertThat(spaces.size()).isEqualTo(2);
        assertThat(spaces.get(0).get("type").asText()).isEqualTo("ONE_TO_ONE");
        assertThat(spaces.get(1).get("type").asText()).isEqualTo("GROUP");

        // 그룹 코드를 1:1 연결 요청에 넣으면 1:1 코드가 아니므로 거부된다
        String groupCode = created.get("inviteCode").asText();
        assertThat(errorCode(post("/v1/invitations", tokenC, "{\"toUserId\":null,\"code\":\"" + groupCode + "\"}")))
                .isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("그룹: 방장이 탈퇴하면 먼저 들어온 멤버가 방장이 되고, 마지막 멤버가 탈퇴하면 그룹과 코드가 사라진다")
    void withdrawalHandsOverAndClosesGroup() {
        JsonNode created = ok(post("/v1/groups", tokenA, "{\"name\":\"탈퇴 확인\"}"));
        String spaceId = created.get("space").get("id").asText();
        String code = created.get("inviteCode").asText();
        joinAndApprove(tokenB, tokenA, code, spaceId);

        // 방장 A 탈퇴 → B가 방장이 되어 코드를 재발급할 수 있다
        ok(delete("/v1/users/me", tokenA));
        JsonNode bGroup = findSpace(ok(get("/v1/spaces/me", tokenB)), spaceId);
        assertThat(bGroup.get("myRole").asText()).isEqualTo("OWNER");
        assertThat(bGroup.get("memberCount").asInt()).isEqualTo(1);
        String reissued = ok(post("/v1/groups/" + spaceId + "/invite-code", tokenB, null)).get("code").asText();

        // 마지막 멤버 B 탈퇴 → 그룹 종료, 코드로 들어올 수 없다
        ok(delete("/v1/users/me", tokenB));
        assertThat(errorCode(post("/v1/groups/join", tokenC, "{\"code\":\"" + reissued + "\"}")))
                .isEqualTo("INVALID_REQUEST");
    }

    // ---------- helpers

    private void joinAndApprove(String requesterToken, String ownerToken, String code, String spaceId) {
        String requestId = ok(post("/v1/groups/join", requesterToken, "{\"code\":\"" + code + "\"}")).get("id").asText();
        approve(ownerToken, spaceId, requestId);
    }

    private void approve(String ownerToken, String spaceId, String requestId) {
        JsonNode pending = ok(get("/v1/groups/" + spaceId + "/join-requests", ownerToken));
        assertThat(StreamSupport.stream(pending.spliterator(), false).map(r -> r.get("id").asText()))
                .contains(requestId);
        ok(patch("/v1/groups/" + spaceId + "/join-requests/" + requestId, ownerToken, "{\"action\":\"approve\"}"));
    }

    private String login(String identityToken, String nickname) {
        JsonNode res = post("/v1/auth/apple", null,
                "{\"identityToken\":\"" + identityToken + "\",\"authorizationCode\":\"stub-code\","
                        + "\"nonce\":\"stub-nonce\",\"nickname\":\"" + nickname + "\"}");
        return ok(res).get("accessToken").asText();
    }

    private JsonNode findSpace(JsonNode spaces, String spaceId) {
        JsonNode found = findSpaceOrNull(spaces, spaceId);
        assertThat(found).as("space %s should be in /spaces/me", spaceId).isNotNull();
        return found;
    }

    private JsonNode findSpaceOrNull(JsonNode spaces, String spaceId) {
        return StreamSupport.stream(spaces.spliterator(), false)
                .filter(s -> s.get("id").asText().equals(spaceId))
                .findFirst()
                .orElse(null);
    }

    private JsonNode ok(JsonNode res) {
        assertThat(res.get("success").asBoolean()).as("API should succeed: %s", res).isTrue();
        return res.get("data");
    }

    private String errorCode(JsonNode res) {
        assertThat(res.get("success").asBoolean()).as("API should fail: %s", res).isFalse();
        return res.get("error").get("code").asText();
    }

    private JsonNode get(String uri, String token) {
        return parse(client.get().uri(uri).headers(h -> h.setBearerAuth(token)).retrieve().body(String.class));
    }

    private JsonNode post(String uri, String token, String body) {
        var spec = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (token != null) h.setBearerAuth(token); });
        return parse(body != null ? spec.body(body).retrieve().body(String.class) : spec.retrieve().body(String.class));
    }

    private JsonNode patch(String uri, String token, String body) {
        return parse(client.patch().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token)).body(body).retrieve().body(String.class));
    }

    private JsonNode delete(String uri, String token) {
        return parse(client.delete().uri(uri).headers(h -> h.setBearerAuth(token)).retrieve().body(String.class));
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("invalid json: " + json, e);
        }
    }
}
