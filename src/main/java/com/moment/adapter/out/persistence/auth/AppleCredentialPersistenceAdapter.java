package com.moment.adapter.out.persistence.auth;

import com.moment.application.port.out.AppleCredentialPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AppleCredentialPersistenceAdapter implements AppleCredentialPort {

    private final AppleCredentialJpaRepository repository;

    public AppleCredentialPersistenceAdapter(AppleCredentialJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(UUID userId, String encryptedRefreshToken, Instant updatedAt) {
        AppleCredentialEntity entity = repository.findById(userId)
                .orElseGet(() -> new AppleCredentialEntity(userId, encryptedRefreshToken, updatedAt));
        entity.update(encryptedRefreshToken, updatedAt);
        repository.save(entity);
    }

    @Override
    public Optional<String> findEncryptedRefreshToken(UUID userId) {
        return repository.findById(userId).map(AppleCredentialEntity::encryptedRefreshToken);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repository.deleteById(userId);
    }
}
