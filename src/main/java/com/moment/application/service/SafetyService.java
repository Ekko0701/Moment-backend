package com.moment.application.service;

import com.moment.application.port.in.SafetyUseCase;
import com.moment.application.port.out.BlockPort;
import com.moment.application.port.out.MomentRepositoryPort;
import com.moment.application.port.out.PushPort;
import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.ReportPort;
import com.moment.application.port.out.SpaceRepositoryPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.moment.Moment;
import com.moment.domain.safety.Block;
import com.moment.domain.safety.ReportReason;
import com.moment.domain.safety.Report;
import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceType;
import com.moment.domain.space.SpaceMember;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SafetyService implements SafetyUseCase {

    private final ReportPort reportRepository;
    private final BlockPort blockRepository;
    private final MomentRepositoryPort momentRepository;
    private final SpaceRepositoryPort spaceRepository;
    private final UserRepositoryPort userRepository;
    private final PushPort pushPort;
    private final Clock clock;

    public SafetyService(
            ReportPort reportRepository,
            BlockPort blockRepository,
            MomentRepositoryPort momentRepository,
            SpaceRepositoryPort spaceRepository,
            UserRepositoryPort userRepository,
            PushPort pushPort,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.blockRepository = blockRepository;
        this.momentRepository = momentRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    public void report(UUID meId, UUID momentId, ReportReason reason, String detail) {
        // 모먼트 존재 및 미삭제 확인
        Moment moment = momentRepository.findById(momentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모먼트를 찾을 수 없어요."));
        if (moment.isDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "삭제된 모먼트입니다.");
        }

        // 신고자가 모먼트 스페이스의 멤버인지 확인
        List<SpaceMember> memberships = spaceRepository.findActiveMembershipsByUserId(meId);
        boolean isMember = memberships.stream()
                .anyMatch(m -> m.spaceId().equals(moment.spaceId()));
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 스페이스의 멤버가 아니에요.");
        }

        Instant now = clock.instant();
        Report report = Report.create(UUID.randomUUID(), meId, momentId, reason, detail, now);
        reportRepository.save(report);
    }

    @Override
    public void block(UUID meId, UUID targetUserId) {
        // 자기 자신 차단 불가
        if (meId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "자신을 차단할 수 없어요.");
        }

        // 초대 수락과 동일한 사용자 잠금 규칙 — 두 사용자 행을 id 순서로 잠근다.
        // 잠그지 않으면 "수락이 차단 없음을 확인 → 차단 저장 → 수락 커밋" 경합으로
        // 차단 관계와 활성 스페이스가 동시에 존재할 수 있다.
        userRepository.lockAllByIdsForUpdate(List.of(meId, targetUserId));

        // 중복 차단 검증 (이미 차단한 경우) — 경합 방지를 위해 잠금 이후에 검사한다
        if (blockRepository.existsBlockBetween(meId, targetUserId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 차단한 사용자입니다.");
        }

        // Block 저장
        Instant now = clock.instant();
        Block block = Block.create(UUID.randomUUID(), meId, targetUserId, now);
        blockRepository.save(block);

        // 공유 중인 활성 ONE_TO_ONE 스페이스 찾기
        List<SpaceMember> myMemberships = spaceRepository.findActiveMembershipsByUserId(meId);
        List<SpaceMember> targetMemberships = spaceRepository.findActiveMembershipsByUserId(targetUserId);

        for (SpaceMember myMem : myMemberships) {
            for (SpaceMember targetMem : targetMemberships) {
                if (myMem.spaceId().equals(targetMem.spaceId())) {
                    // 같은 스페이스에 속함 — ONE_TO_ONE 여부 확인
                    Space space = spaceRepository.findById(myMem.spaceId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                    "스페이스를 찾을 수 없어요."));
                    if (space.type() == SpaceType.ONE_TO_ONE) {
                        // 양쪽 멤버십을 모두 종료하고 스페이스를 비활성화한다.
                        // (나가기와 동일하게 멤버십/스페이스 상태를 정합적으로 유지)
                        for (SpaceMember member :
                                spaceRepository.findActiveMembersBySpaceId(space.id())) {
                            spaceRepository.saveMember(member.leave(now));
                        }
                        Space deactivated = space.deactivate();
                        spaceRepository.save(deactivated);

                        // 상대방에게 SPACE_DEACTIVATED 푸시
                        pushPort.send(new PushMessage(
                                targetUserId,
                                "SPACE_DEACTIVATED",
                                "대화가 종료되었습니다.",
                                null,
                                Map.of("spaceId", space.id().toString())));
                    }
                }
            }
        }
    }
}
