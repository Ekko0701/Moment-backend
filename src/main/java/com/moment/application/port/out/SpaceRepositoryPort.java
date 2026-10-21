package com.moment.application.port.out;

import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepositoryPort {

    Space save(Space space);

    Optional<Space> findById(UUID id);

    /** 스페이스 행을 비관적 쓰기 잠금으로 조회한다. 입장·나가기처럼 멤버 수를 바꾸는 작업을 직렬화한다. */
    Optional<Space> findByIdForUpdate(UUID id);

    List<Space> findAllByIds(Collection<UUID> ids);

    /** 활성 여부와 무관하게 (스페이스, 사용자)의 멤버 행을 조회한다. 재입장 판단에 쓴다. */
    Optional<SpaceMember> findMember(UUID spaceId, UUID userId);

    SpaceMember saveMember(SpaceMember member);

    List<SpaceMember> findActiveMembersBySpaceId(UUID spaceId);

    /** 여러 스페이스의 활성 멤버를 한 번에 조회한다. */
    List<SpaceMember> findActiveMembersBySpaceIds(Collection<UUID> spaceIds);

    List<SpaceMember> findActiveMembershipsByUserId(UUID userId);

    boolean existsActiveOneToOneMembership(UUID userId);
}
