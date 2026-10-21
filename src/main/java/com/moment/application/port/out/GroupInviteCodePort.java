package com.moment.application.port.out;

import com.moment.domain.space.GroupInviteCode;
import java.util.Optional;
import java.util.UUID;

/**
 * 그룹 초대 코드 저장소 포트. 스페이스당 코드는 최대 1개다.
 */
public interface GroupInviteCodePort {

    GroupInviteCode save(GroupInviteCode code);

    Optional<GroupInviteCode> findByCode(String code);

    Optional<GroupInviteCode> findBySpaceId(UUID spaceId);

    void deleteBySpaceId(UUID spaceId);
}
