package com.moment.adapter.out.auth;

import com.moment.application.port.out.AppleAuthPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 개발용 Apple 인증 스텁 — identityToken 문자열 자체를 사용자 식별자로 취급한다.
 * 운영 전환 시 moment.auth.apple.mode=apple + Apple 공개키 검증 어댑터로 교체한다.
 */
@Component
@ConditionalOnProperty(prefix = "moment.auth.apple", name = "mode", havingValue = "stub")
public class StubAppleAuthAdapter implements AppleAuthPort {

    private static final Logger log = LoggerFactory.getLogger(StubAppleAuthAdapter.class);

    public StubAppleAuthAdapter(Environment environment) {
        // fail-closed: 운영(prod) 프로필에서는 스텁 인증이 절대 기동되지 않도록 막는다.
        boolean prodActive = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prodActive) {
            throw new IllegalStateException(
                    "STUB Apple 인증은 운영 환경에서 사용할 수 없습니다. "
                            + "MOMENT_APPLE_AUTH_MODE=apple 로 설정하고 실제 Apple 검증 어댑터를 주입하세요.");
        }
        log.warn("Apple 인증이 STUB 모드로 동작 중입니다. 운영 환경에서는 사용하지 마세요.");
    }

    @Override
    public AppleIdentity authenticate(String identityToken, String authorizationCode, String nonce) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return new AppleIdentity("stub:" + identityToken, null);
    }

    @Override
    public void revoke(String encryptedRefreshToken) {
        // dev stub에는 Apple 측 세션이 없다.
    }
}
