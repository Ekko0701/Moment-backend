package com.moment.application.port.out;

import java.util.UUID;

public interface BlockPort {

    boolean existsBlockBetween(UUID a, UUID b);
}
