package com.moment.adapter.out.persistence.space;

import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceStatus;
import com.moment.domain.space.SpaceType;
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
@Table(name = "spaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceType type;

    @Column(length = 50)
    private String name;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static SpaceEntity fromDomain(Space space) {
        SpaceEntity entity = new SpaceEntity();
        entity.id = space.id();
        entity.applyDomain(space);
        entity.createdAt = space.createdAt();
        return entity;
    }

    void applyDomain(Space space) {
        this.type = space.type();
        this.name = space.name();
        this.maxMembers = space.maxMembers();
        this.status = space.status();
    }

    Space toDomain() {
        return new Space(id, type, name, maxMembers, status, createdAt);
    }

    SpaceType getType() {
        return type;
    }
}
