package com.moment.application.port.out;

import com.moment.domain.space.Space;
import com.moment.domain.space.SpaceMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepositoryPort {

    Space save(Space space);

    Optional<Space> findById(UUID id);

    SpaceMember saveMember(SpaceMember member);

    List<SpaceMember> findActiveMembersBySpaceId(UUID spaceId);

    List<SpaceMember> findActiveMembershipsByUserId(UUID userId);

    boolean existsActiveOneToOneMembership(UUID userId);
}
