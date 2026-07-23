package com.moment.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Apple refresh token의 암호문만 저장하는 포트. 평문은 AppleAuthPort 어댑터 밖으로 나오지 않는다. */
public interface AppleCredentialPort {

    void save(UUID userId, String encryptedRefreshToken, Instant updatedAt);

    Optional<String> findEncryptedRefreshToken(UUID userId);

    void deleteByUserId(UUID userId);
}
