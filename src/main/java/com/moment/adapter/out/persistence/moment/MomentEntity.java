package com.moment.adapter.out.persistence.moment;

import com.moment.domain.moment.Moment;
import com.moment.domain.moment.TextColor;
import com.moment.domain.moment.TextPosition;
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
@Table(name = "moments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MomentEntity {

    @Id
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "text", length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_position", length = 8)
    private TextPosition textPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_color", length = 8)
    private TextColor textColor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    static MomentEntity fromDomain(Moment moment) {
        MomentEntity entity = new MomentEntity();
        entity.id = moment.id();
        entity.applyDomain(moment);
        entity.createdAt = moment.createdAt();
        return entity;
    }

    void applyDomain(Moment moment) {
        this.spaceId = moment.spaceId();
        this.authorId = moment.authorId();
        this.imageUrl = moment.imageUrl();
        this.text = moment.text();
        this.textPosition = moment.textPosition();
        this.textColor = moment.textColor();
        this.deletedAt = moment.deletedAt();
    }

    Moment toDomain() {
        return new Moment(id, spaceId, authorId, imageUrl, text, textPosition, textColor, createdAt, deletedAt);
    }
}
