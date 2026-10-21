package com.moment.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AppleAuthAdapterTest {

    private static final String CLIENT_ID = "com.ekko.moment";
    private static final String KEY_ID = "apple-rsa-key";
    private static final String RAW_NONCE = "test-nonce";

    private KeyPair appleSigningKeys;
    private AppleAuthAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        appleSigningKeys = rsa.generateKeyPair();

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        KeyPair developerKey = ec.generateKeyPair();

        byte[] encryptionKey = new byte[32];
        java.util.Arrays.fill(encryptionKey, (byte) 7);
        var apple = new MomentProperties.Auth.Apple(
                "apple", CLIENT_ID, "TEAM123", "DEVKEY123",
                Base64.getEncoder().encodeToString(developerKey.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(encryptionKey));
        var properties = new MomentProperties(
                new MomentProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(30)),
                new MomentProperties.Auth(apple),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72), Duration.ofDays(7)),
                new MomentProperties.Policy(true, 6, 10),
                MomentProperties.Storage.stub());

        RestClient.Builder builder = RestClient.builder().baseUrl("https://appleid.apple.com");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AppleAuthAdapter(properties, new ObjectMapper(), builder.build());
    }

    @Test
    @DisplayName("Apple 공개키로 두 identity token과 nonce를 검증하고 refresh token을 암호화한다")
    void authenticate_verifiesAndEncryptsRefreshToken() throws Exception {
        String identityToken = identityToken(RAW_NONCE, CLIENT_ID, "apple-sub-1");

        server.expect(requestTo("https://appleid.apple.com/auth/keys"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwkResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"refresh_token\":\"apple-refresh-secret\",\"id_token\":\""
                        + identityToken + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        var result = adapter.authenticate(identityToken, "one-time-code", RAW_NONCE);

        assertThat(result.appleSub()).isEqualTo("apple-sub-1");
        assertThat(result.encryptedRefreshToken()).startsWith("v1.");
        assertThat(result.encryptedRefreshToken()).doesNotContain("apple-refresh-secret");

        adapter.revoke(result.encryptedRefreshToken());
        server.verify();
    }

    @Test
    @DisplayName("identity token의 nonce가 요청 nonce와 다르면 거부한다")
    void authenticate_rejectsWrongNonce() throws Exception {
        String identityToken = identityToken("another-nonce", CLIENT_ID, "apple-sub-1");
        server.expect(requestTo("https://appleid.apple.com/auth/keys"))
                .andRespond(withSuccess(jwkResponse(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.authenticate(identityToken, "code", RAW_NONCE))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        server.verify();
    }

    private String identityToken(String rawNonce, String audience, String subject) throws Exception {
        Instant now = Instant.now();
        String nonceHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawNonce.getBytes(StandardCharsets.UTF_8)));
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("https://appleid.apple.com")
                .subject(subject)
                .audience().add(audience).and()
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("nonce", nonceHash)
                .signWith(appleSigningKeys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String jwkResponse() {
        RSAPublicKey key = (RSAPublicKey) appleSigningKeys.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getPublicExponent()));
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KEY_ID
                + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"" + n
                + "\",\"e\":\"" + e + "\"}]}";
    }

    private static byte[] unsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
