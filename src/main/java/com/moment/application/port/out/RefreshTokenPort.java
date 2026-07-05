package com.moment.application.port.out;

import com.moment.domain.auth.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenPort {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteById(UUID id);

    void deleteAllByUserId(UUID userId);
}
