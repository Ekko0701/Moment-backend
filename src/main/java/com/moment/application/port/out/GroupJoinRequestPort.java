package com.moment.application.port.out;

import com.moment.domain.space.GroupJoinRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GroupJoinRequestPort {

    GroupJoinRequest save(GroupJoinRequest request);

    Optional<GroupJoinRequest> findById(UUID id);

    Optional<GroupJoinRequest> findPending(UUID spaceId, UUID userId);

    /** 그룹에 들어온 대기 중 요청. 오래된 순. */
    List<GroupJoinRequest> findPendingBySpaceId(UUID spaceId);

    /** 스페이스별 대기 중 요청 수. 요청이 없는 스페이스는 결과에 없다. */
    Map<UUID, Long> countPendingBySpaceIds(Collection<UUID> spaceIds);

    /** 사용자가 보낸 대기 중 요청. 최신 순. */
    List<GroupJoinRequest> findPendingByUserId(UUID userId);
}
