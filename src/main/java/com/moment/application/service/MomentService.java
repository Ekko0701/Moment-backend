package com.moment.application.service;

import com.moment.application.port.in.MomentUseCase;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.MomentRepositoryPort.TimelinePage;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.ReactionPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.StoragePort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.moment.Moment;
import com.moment.domain.moment.Reaction;
import com.moment.domain.space.SpaceMember;
import com.moment.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MomentService implements MomentUseCase {

    private static final int TIMELINE_DEFAULT_LIMIT = 20;
    private static final int TIMELINE_MAX_LIMIT = 50;

    private final MomentRepositoryPort momentRepository;
    private final ReactionPort reactionRepository;
    private final StoragePort storagePort;
    private final SpaceRepositoryPort spaceRepository;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final Clock clock;

    public MomentService(
            MomentRepositoryPort momentRepository,
            ReactionPort reactionRepository,
            StoragePort storagePort,
            SpaceRepositoryPort spaceRepository,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            Clock clock) {
        this.momentRepository = momentRepository;
        this.reactionRepository = reactionRepository;
        this.storagePort = storagePort;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    public PresignedUrlResponse presign(UUID meId, String contentType) {
        validateImageContentType(contentType);
        StoragePort.PresignedUpload presigned = storagePort.issueUploadUrl(meId, contentType);
        return new PresignedUrlResponse(presigned.uploadUrl(), presigned.imageUrl());
    }

    @Override
    public MomentView create(UUID meId, CreateMomentCommand command) {
        // 멤버십 검증
        List<SpaceMember> memberships = spaceRepository.findActiveMembershipsByUserId(meId);
        boolean isMember = memberships.stream()
                .anyMatch(m -> m.spaceId().equals(command.spaceId()));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 스페이스의 멤버가 아니에요.");
        }

        // imageUrl이 있으면 이 스토리지가 발급한 관리 경로인지 검증 (임의 외부 URL 주입 차단)
        if (command.imageUrl() != null && !storagePort.isManagedImageUrl(command.imageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효하지 않은 이미지 경로예요.");
        }

        Instant now = clock.instant();
        Moment moment = Moment.create(UUID.randomUUID(), command.spaceId(), meId,
                command.imageUrl(), command.text(), now);
        Moment saved = momentRepository.save(moment);

        // 팬아웃: 작성자 제외 활성 멤버 전원에게 알림
        List<SpaceMember> activeMembers = spaceRepository.findActiveMembersBySpaceId(command.spaceId());
        User author = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요."));

        for (SpaceMember member : activeMembers) {
            if (!member.userId().equals(meId)) {
                // 타입 1: MOMENT_CREATED — UI 업데이트
                pushPort.send(new PushMessage(
                        member.userId(),
                        "MOMENT_CREATED",
                        author.nickname() + "님이 모먼트를 공유했어요.",
                        "모먼트를 확인해 보세요.",
                        Map.of("spaceId", command.spaceId().toString(), "momentId", saved.id().toString())));

                // 타입 2: WIDGET_REFRESH — 사일런트 갱신 (data 포함)
                pushPort.send(new PushMessage(
                        member.userId(),
                        "WIDGET_REFRESH",
                        null, null,
                        Map.of("spaceId", command.spaceId().toString(), "momentId", saved.id().toString())));
            }
        }

        return buildMomentView(saved, meId);
    }

    @Override
    @Transactional(readOnly = true)
    public TimelinePage timeline(UUID meId, UUID spaceId, String cursor, int limit) {
        // 멤버십 검증
        List<SpaceMember> memberships = spaceRepository.findActiveMembershipsByUserId(meId);
        boolean isMember = memberships.stream()
                .anyMatch(m -> m.spaceId().equals(spaceId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 스페이스의 멤버가 아니에요.");
        }

        Cursor decoded = decodeCursor(cursor);
        int actualLimit = Math.min(limit <= 0 ? TIMELINE_DEFAULT_LIMIT : limit, TIMELINE_MAX_LIMIT);
        MomentRepositoryPort.TimelinePage page =
                momentRepository.findTimeline(spaceId, decoded.createdAt(), decoded.id(), actualLimit + 1);

        // nextCursor 및 hasNext 결정
        List<Moment> momentItems;
        String nextCursor = null;
        boolean hasNext = false;
        if (page.items().size() > actualLimit) {
            momentItems = page.items().stream().limit(actualLimit).toList();
            Moment lastMoment = momentItems.get(momentItems.size() - 1);
            nextCursor = encodeCursor(lastMoment.createdAt(), lastMoment.id());
            hasNext = true;
        } else {
            momentItems = page.items();
        }

        return new TimelinePage(buildMomentViews(momentItems, meId), nextCursor, hasNext);
    }

    // (createdAt, id) 복합 커서 인코딩/디코딩 — 불투명 문자열 "{ISO-8601}|{uuid}".
    private record Cursor(Instant createdAt, UUID id) {
    }

    private static String encodeCursor(Instant createdAt, UUID id) {
        return createdAt.toString() + "|" + id;
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }
        int sep = cursor.lastIndexOf('|');
        if (sep < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "잘못된 커서예요.");
        }
        try {
            Instant createdAt = Instant.parse(cursor.substring(0, sep));
            UUID id = UUID.fromString(cursor.substring(sep + 1));
            return new Cursor(createdAt, id);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "잘못된 커서예요.");
        }
    }

    // 타임라인 배치 뷰 — 작성자와 리액션을 한 번에 조회해 N+1을 제거한다.
    private List<MomentView> buildMomentViews(List<Moment> moments, UUID meId) {
        if (moments.isEmpty()) {
            return List.of();
        }

        List<UUID> authorIds = moments.stream().map(Moment::authorId).distinct().toList();
        Map<UUID, User> authorsById = userRepository.findAllByIds(authorIds).stream()
                .collect(Collectors.toMap(User::id, u -> u));

        List<UUID> momentIds = moments.stream().map(Moment::id).toList();
        Map<UUID, List<Reaction>> reactionsByMoment =
                reactionRepository.findAllByMomentIdIn(momentIds).stream()
                        .collect(Collectors.groupingBy(Reaction::momentId));

        return moments.stream()
                .map(moment -> {
                    User author = authorsById.get(moment.authorId());
                    if (author == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요.");
                    }
                    List<Reaction> reactions = reactionsByMoment.getOrDefault(moment.id(), List.of());
                    return toView(moment, author, reactions, meId);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MomentView> latest(UUID meId, UUID spaceId, boolean excludeMine) {
        // 멤버십 검증
        List<SpaceMember> memberships = spaceRepository.findActiveMembershipsByUserId(meId);
        boolean isMember = memberships.stream()
                .anyMatch(m -> m.spaceId().equals(spaceId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 스페이스의 멤버가 아니에요.");
        }

        UUID excludeAuthorId = excludeMine ? meId : null;
        return momentRepository.findLatestBySpace(spaceId, excludeAuthorId)
                .map(moment -> buildMomentView(moment, meId));
    }

    @Override
    public void delete(UUID meId, UUID momentId) {
        Moment moment = momentRepository.findById(momentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모먼트를 찾을 수 없어요."));

        // 작성자 검증
        if (!moment.authorId().equals(meId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 모먼트만 삭제할 수 있어요.");
        }

        Instant now = clock.instant();
        Moment deleted = moment.delete(now);
        momentRepository.save(deleted);
    }

    private MomentView buildMomentView(Moment moment, UUID meId) {
        User author = userRepository.findById(moment.authorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요."));
        List<Reaction> reactions = reactionRepository.findAllByMomentIdIn(List.of(moment.id()));
        return toView(moment, author, reactions, meId);
    }

    // 이미 로딩된 작성자·리액션으로 뷰를 조립한다(배치·단건 공용).
    private MomentView toView(Moment moment, User author, List<Reaction> reactions, UUID meId) {
        MomentView.AuthorView authorView = new MomentView.AuthorView(
                author.id(), author.handle(), author.nickname(), author.profileImageUrl());

        Map<String, Long> emojiCounts = reactions.stream()
                .collect(Collectors.groupingBy(Reaction::emoji, Collectors.counting()));
        List<MomentView.ReactionCount> reactionCounts = emojiCounts.entrySet().stream()
                .map(e -> new MomentView.ReactionCount(e.getKey(), e.getValue().intValue()))
                .toList();

        String myReaction = reactions.stream()
                .filter(r -> r.userId().equals(meId))
                .map(Reaction::emoji)
                .findFirst()
                .orElse(null);

        return new MomentView(
                moment.id(),
                moment.spaceId(),
                authorView,
                moment.imageUrl(),
                moment.text(),
                moment.createdAt().toEpochMilli(),
                reactionCounts,
                myReaction);
    }

    private void validateImageContentType(String contentType) {
        Set<String> allowed = Set.of("image/jpeg", "image/png", "image/heic");
        if (contentType == null || !allowed.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "지원하는 이미지 형식: JPEG, PNG, HEIC");
        }
    }
}
