package com.moment.application.port.out;

import java.util.UUID;

/**
 * 액세스 토큰 발급/검증 아웃바운드 포트.
 * 구현 기술(JWT)은 어댑터 계층에 격리한다.
 */
public interface TokenPort {

    String createAccessToken(UUID userId);

    /**
     * @throws com.moment.common.exception.BusinessException 토큰이 유효하지 않은 경우 (INVALID_TOKEN)
     */
    UUID parseUserId(String accessToken);
}
