package com.moment.domain.moment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reaction 도메인")
class ReactionTest {

    @Test
    @DisplayName("허용되지 않은 이모지면 예외")
    void 허용되지_않은_이모지면_예외() {
        assertThatThrownBy(() -> Reaction.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "🚀", Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
