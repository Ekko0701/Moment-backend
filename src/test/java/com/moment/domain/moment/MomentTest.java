package com.moment.domain.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Moment 도메인")
class MomentTest {

    @Test
    @DisplayName("사진과 글 둘 다 없으면 예외")
    void 사진과_글_둘_다_없으면_예외() {
        assertThatThrownBy(() -> Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("글이 500자 초과하면 예외")
    void 글이_500자_초과하면_예외() {
        String longText = "a".repeat(501);
        assertThatThrownBy(() -> Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, longText, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
