package com.moment.adapter.out.persistence.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "apple_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleCredentialEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "encrypted_refresh_token", nullable = false, length = 2048)
    private String encryptedRefreshToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    AppleCredentialEntity(UUID userId, String encryptedRefreshToken, Instant updatedAt) {
        this.userId = userId;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.updatedAt = updatedAt;
    }

    void update(String value, Instant now) {
        this.encryptedRefreshToken = value;
        this.updatedAt = now;
    }

    String encryptedRefreshToken() {
        return encryptedRefreshToken;
    }
}
