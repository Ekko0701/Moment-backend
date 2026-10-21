package com.moment.application.port.out;

import com.moment.domain.safety.Block;
import java.util.Set;
import java.util.UUID;

public interface BlockPort {

    Block save(Block block);

    boolean existsBlockBetween(UUID a, UUID b);

    /** userId가 차단했거나 userId를 차단한 사용자 id 전체. */
    Set<UUID> findBlockedUserIdsEitherDirection(UUID userId);
}
