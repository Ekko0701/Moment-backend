package com.moment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 핸들러가 없는 경로에 대한 응답을 실제 HTTP 호출로 검증한다.
 *
 * springdoc을 끈 상태(prod 프로필과 같은 설정)로 띄워, 인증 없이 통과하는(permitAll)
 * 경로에 핸들러가 없을 때 표준 에러 엔벨로프의 404가 나가는지 확인한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@ActiveProfiles("dev")
class UnmappedPathE2eTest {

    @LocalServerPort
    private int port;

    private RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    @Test
    @DisplayName("springdoc이 꺼져 있으면 /v3/api-docs는 404 NOT_FOUND")
    void disabledApiDocs_returnsNotFound() {
        assertNotFoundEnvelope(get("/v3/api-docs"));
    }

    @Test
    @DisplayName("springdoc이 꺼져 있으면 /swagger-ui/index.html은 404 NOT_FOUND")
    void disabledSwaggerUi_returnsNotFound() {
        assertNotFoundEnvelope(get("/swagger-ui/index.html"));
    }

    @Test
    @DisplayName("permitAll 경로 아래 매핑되지 않은 주소는 404 NOT_FOUND")
    void unmappedPermitAllPath_returnsNotFound() {
        assertNotFoundEnvelope(get("/v1/auth/no-such-endpoint"));
    }

    @Test
    @DisplayName("인증이 필요한 영역의 매핑되지 않은 주소는 인증 없이 401 UNAUTHORIZED")
    void unmappedProtectedPath_returnsUnauthorized() {
        ResponseEntity<String> response = get("/no-such-endpoint");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHORIZED");
    }

    private ResponseEntity<String> get(String path) {
        return client.get().uri(path).retrieve().toEntity(String.class);
    }

    private void assertNotFoundEnvelope(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode root = mapper.readTree(response.getBody());
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.get("error").get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(root.get("error").get("message").asString()).isEqualTo("대상을 찾을 수 없어요.");
    }

    private String errorCode(ResponseEntity<String> response) {
        return mapper.readTree(response.getBody()).get("error").get("code").asString();
    }
}
