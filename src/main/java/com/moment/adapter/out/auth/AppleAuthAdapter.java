package com.moment.adapter.out.auth;

import com.moment.application.port.out.AppleAuthPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/** 실제 Sign in with Apple 검증 어댑터. Apple 비밀키와 refresh token 평문은 이 클래스 밖으로 노출하지 않는다. */
@Component
@ConditionalOnProperty(prefix = "moment.auth.apple", name = "mode", havingValue = "apple",
        matchIfMissing = true)
public class AppleAuthAdapter implements AppleAuthPort {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);
    private static final Duration JWK_CACHE_TTL = Duration.ofHours(6);
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey signingKey;
    private final SecretKey tokenEncryptionKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile CachedKeys cachedKeys = new CachedKeys(Map.of(), Instant.EPOCH);

    @Autowired
    public AppleAuthAdapter(MomentProperties properties, ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this(properties, objectMapper, productionRestClient(restClientBuilder));
    }

    AppleAuthAdapter(MomentProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        MomentProperties.Auth.Apple apple = properties.auth().apple();
        this.clientId = required(apple.clientId(), "MOMENT_APPLE_CLIENT_ID");
        this.teamId = required(apple.teamId(), "MOMENT_APPLE_TEAM_ID");
        this.keyId = required(apple.keyId(), "MOMENT_APPLE_KEY_ID");
        this.signingKey = parseSigningKey(required(
                apple.privateKeyBase64(), "MOMENT_APPLE_PRIVATE_KEY_BASE64"));
        this.tokenEncryptionKey = parseEncryptionKey(required(
                apple.tokenEncryptionKeyBase64(), "MOMENT_APPLE_TOKEN_ENCRYPTION_KEY_BASE64"));
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public AppleIdentity authenticate(String identityToken, String authorizationCode, String nonce) {
        Claims suppliedClaims = verifyIdentityToken(identityToken, nonce);
        AppleTokenResponse exchanged = exchangeAuthorizationCode(authorizationCode);
        Claims exchangedClaims = verifyIdentityToken(exchanged.idToken(), nonce);

        if (!MessageDigest.isEqual(
                suppliedClaims.getSubject().getBytes(StandardCharsets.UTF_8),
                exchangedClaims.getSubject().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (exchanged.refreshToken() == null || exchanged.refreshToken().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return new AppleIdentity(suppliedClaims.getSubject(), encrypt(exchanged.refreshToken()));
    }

    @Override
    public void revoke(String encryptedRefreshToken) {
        String refreshToken = decrypt(encryptedRefreshToken);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", createClientSecret());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post()
                    .uri("/auth/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Apple 계정 연결을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private AppleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", createClientSecret());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        try {
            AppleTokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            if (response == null || response.idToken() == null) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Claims verifyIdentityToken(String token, String rawNonce) {
        try {
            TokenHeader header = readHeader(token);
            if (!"RS256".equals(header.alg()) || header.kid() == null) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            PublicKey publicKey = findPublicKey(header.kid());
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String tokenNonce = claims.get("nonce", String.class);
            String expectedNonce = sha256Hex(rawNonce);
            if (subject == null || subject.isBlank() || tokenNonce == null
                    || !MessageDigest.isEqual(tokenNonce.getBytes(StandardCharsets.UTF_8),
                    expectedNonce.getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return claims;
        } catch (BusinessException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private TokenHeader readHeader(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(decoded, Map.class);
            return new TokenHeader((String) header.get("kid"), (String) header.get("alg"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private PublicKey findPublicKey(String kid) {
        CachedKeys snapshot = cachedKeys;
        if (Instant.now().isAfter(snapshot.expiresAt()) || !snapshot.keys().containsKey(kid)) {
            refreshPublicKeys();
            snapshot = cachedKeys;
        }
        PublicKey key = snapshot.keys().get(kid);
        if (key == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return key;
    }

    private synchronized void refreshPublicKeys() {
        try {
            AppleJwkSet response = restClient.get().uri("/auth/keys").retrieve().body(AppleJwkSet.class);
            if (response == null || response.keys() == null || response.keys().isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            Map<String, PublicKey> keys = new HashMap<>();
            for (AppleJwk jwk : response.keys()) {
                if ("RSA".equals(jwk.kty()) && "RS256".equals(jwk.alg()) && jwk.kid() != null) {
                    keys.put(jwk.kid(), rsaPublicKey(jwk.n(), jwk.e()));
                }
            }
            if (keys.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            cachedKeys = new CachedKeys(Map.copyOf(keys), Instant.now().plus(JWK_CACHE_TTL));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException | GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String createClientSecret() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .subject(clientId)
                .audience().add(APPLE_ISSUER).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
                .signWith(signingKey, Jwts.SIG.ES256)
                .compact();
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, tokenEncryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith("v1.")) {
                throw new GeneralSecurityException("Unsupported ciphertext version");
            }
            byte[] packed = Base64.getUrlDecoder().decode(ciphertext.substring(3));
            if (packed.length <= GCM_IV_BYTES) {
                throw new GeneralSecurityException("Invalid ciphertext");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] encrypted = new byte[packed.length - GCM_IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, iv.length);
            System.arraycopy(packed, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, tokenEncryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static PublicKey rsaPublicKey(String modulus, String exponent)
            throws GeneralSecurityException {
        var n = new java.math.BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        var e = new java.math.BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private static PrivateKey parseSigningKey(String encoded) {
        try {
            byte[] outer = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
            String decoded = new String(outer, StandardCharsets.US_ASCII);
            byte[] der = decoded.contains("BEGIN PRIVATE KEY")
                    ? decodePem(decoded)
                    : outer;
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("MOMENT_APPLE_PRIVATE_KEY_BASE64 값이 유효한 .p8 키가 아닙니다.", ex);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static SecretKey parseEncryptionKey(String encoded) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
            if (key.length != 32) {
                throw new IllegalArgumentException("AES-256 key must be 32 bytes");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "MOMENT_APPLE_TOKEN_ENCRYPTION_KEY_BASE64 값은 32바이트 base64여야 합니다.", ex);
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static String required(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " 환경변수가 필요합니다.");
        }
        return value.strip();
    }

    private static RestClient productionRestClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.clone()
                .baseUrl(APPLE_ISSUER)
                .requestFactory(requestFactory)
                .build();
    }

    private record TokenHeader(String kid, String alg) {
    }

    private record CachedKeys(Map<String, PublicKey> keys, Instant expiresAt) {
    }

    private record AppleJwkSet(List<AppleJwk> keys) {
    }

    private record AppleJwk(String kty, String kid, String use, String alg, String n, String e) {
    }

    private record AppleTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("id_token") String idToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn) {
    }
}
