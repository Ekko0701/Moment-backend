package com.moment.application.port.out;

import com.moment.domain.safety.Block;
import java.util.UUID;

public interface BlockPort {

    Block save(Block block);

    boolean existsBlockBetween(UUID a, UUID b);
}
