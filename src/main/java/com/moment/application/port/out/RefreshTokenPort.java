package com.moment.application.port.out;

import com.moment.domain.auth.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenPort {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 토큰 해시로 조건부 삭제하고 삭제된 행 수를 반환한다(회전 시 한 번만 소비 보장). */
    int deleteByTokenHash(String tokenHash);

    void deleteById(UUID id);

    void deleteAllByUserId(UUID userId);
}
