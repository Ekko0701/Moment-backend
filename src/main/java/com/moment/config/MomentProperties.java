package com.moment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code moment.*} 설정 바인딩.
 * 정책 값(1인 1스페이스 등)은 여기서만 읽어 정책 계층과 구조 계층을 분리한다 (기능정의서 2.3).
 */
@ConfigurationProperties(prefix = "moment")
public record MomentProperties(Jwt jwt, Auth auth, Invite invite, Policy policy, Storage storage) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Auth(Apple apple) {

        public record Apple(String mode, String clientId, String teamId, String keyId,
                String privateKeyBase64, String tokenEncryptionKeyBase64) {

            /** 테스트 전용 — dev 프로필의 stub 모드 프로퍼티. 운영 코드에서 호출하지 않는다. */
            public static Apple stub() {
                return new Apple("stub", null, null, null, null, null);
            }
        }
    }

    public record Invite(Duration codeTtl, Duration invitationTtl) {
    }

    public record Policy(boolean singleSpaceEnabled) {
    }

    public record Storage(String mode, S3 s3) {

        public record S3(String bucket, String region, String publicBaseUrl, Duration presignTtl) {
        }

        /** 테스트 전용 — dev 프로필의 stub 모드 프로퍼티. 운영 코드에서 호출하지 않는다. */
        public static Storage stub() {
            return new Storage("stub", null);
        }
    }
}
