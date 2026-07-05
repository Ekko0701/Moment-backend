package com.moment.application.port.in;

import com.moment.domain.safety.ReportReason;
import java.util.UUID;

/**
 * 안전(신고/차단) 유스케이스 포트 (인바운드).
 */
public interface SafetyUseCase {

    /**
     * 모먼트 신고.
     * 신고자는 해당 모먼트의 스페이스 멤버여야 한다.
     *
     * @param meId 현재 사용자 ID (신고자)
     * @param momentId 신고 대상 모먼트 ID
     * @param reason 신고 사유
     * @param detail 상세 설명 (선택사항)
     */
    void report(UUID meId, UUID momentId, ReportReason reason, String detail);

    /**
     * 사용자 차단.
     * 공유 중인 활성 ONE_TO_ONE 스페이스가 있으면 스페이스 이탈 및 비활성화 후 상대방에게 알림.
     *
     * @param meId 현재 사용자 ID (차단자)
     * @param targetUserId 차단 대상 사용자 ID
     */
    void block(UUID meId, UUID targetUserId);
}
