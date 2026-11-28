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

    @Test
    @DisplayName("사진과 글이 함께 있으면 고른 위치와 색을 저장하고, 비워 두면 TOP·WHITE")
    void 사진_위_글씨_위치와_색() {
        Moment chosen = Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "url", "글", TextPosition.BOTTOM, TextColor.BUTTER, Instant.now());
        assertThat(chosen.textPosition()).isEqualTo(TextPosition.BOTTOM);
        assertThat(chosen.textColor()).isEqualTo(TextColor.BUTTER);

        Moment defaults = Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "url", "글", Instant.now());
        assertThat(defaults.textPosition()).isEqualTo(TextPosition.TOP);
        assertThat(defaults.textColor()).isEqualTo(TextColor.WHITE);
    }

    @Test
    @DisplayName("글만 있거나 사진만 있으면 위치와 색을 저장하지 않는다")
    void 글씨를_올릴_사진이_없으면_위치와_색은_없다() {
        Moment textOnly = Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, "글", TextPosition.BOTTOM, TextColor.LILAC, Instant.now());
        assertThat(textOnly.textPosition()).isNull();
        assertThat(textOnly.textColor()).isNull();

        Moment photoOnly = Moment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "url", " ", TextPosition.BOTTOM, TextColor.LILAC, Instant.now());
        assertThat(photoOnly.textPosition()).isNull();
        assertThat(photoOnly.textColor()).isNull();
    }

    @Test
    @DisplayName("요청 값 해석: 대소문자 무시, 빈 값은 null, 모르는 값은 예외")
    void 위치와_색_요청_값_해석() {
        assertThat(TextPosition.parse("bottom")).isEqualTo(TextPosition.BOTTOM);
        assertThat(TextPosition.parse(null)).isNull();
        assertThat(TextColor.parse(" Peach ")).isEqualTo(TextColor.PEACH);
        assertThat(TextColor.parse("")).isNull();
        assertThatThrownBy(() -> TextPosition.parse("CENTER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> TextColor.parse("RED"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
