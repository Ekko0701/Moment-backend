package com.moment.adapter.out.persistence.user;

import com.moment.domain.user.AuthProvider;
import com.moment.domain.user.User;
import com.moment.domain.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 15)
    private String handle;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "apple_sub", unique = true)
    private String appleSub;

    @Column(name = "is_searchable", nullable = false)
    private boolean searchable;

    @Column(name = "handle_updated_at")
    private Instant handleUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.applyDomain(user);
        entity.createdAt = user.createdAt();
        return entity;
    }

    void applyDomain(User user) {
        this.handle = user.handle();
        this.nickname = user.nickname();
        this.profileImageUrl = user.profileImageUrl();
        this.authProvider = user.authProvider();
        this.appleSub = user.appleSub();
        this.searchable = user.searchable();
        this.handleUpdatedAt = user.handleUpdatedAt();
        this.status = user.status();
        this.updatedAt = user.updatedAt();
    }

    User toDomain() {
        return new User(id, handle, nickname, profileImageUrl, authProvider, appleSub,
                searchable, handleUpdatedAt, status, createdAt, updatedAt);
    }
}
