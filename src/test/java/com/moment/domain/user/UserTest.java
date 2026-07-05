package com.moment.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    private User signUpUser() {
        return User.signUp(UUID.randomUUID(), "dongjoo_1234", "동주", "apple-sub", NOW);
    }

    @Test
    @DisplayName("가입 시 검색 허용이 기본 활성화되고 상태는 ACTIVE다")
    void signUp_defaults() {
        User user = signUpUser();

        assertThat(user.searchable()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.handleUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("형식에 맞지 않는 핸들로 가입할 수 없다")
    void signUp_invalidHandle_throws() {
        assertThatThrownBy(() -> User.signUp(UUID.randomUUID(), "AB!", "동주", "sub", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("핸들 변경은 불변 객체를 유지하며 변경 시각을 기록한다")
    void changeHandle_returnsNewInstance() {
        User user = signUpUser();

        User changed = user.changeHandle("new_handle", NOW);

        assertThat(user.handle()).isEqualTo("dongjoo_1234");
        assertThat(changed.handle()).isEqualTo("new_handle");
        assertThat(changed.handleUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("핸들은 14일 이내 재변경할 수 없다")
    void changeHandle_within14Days_throws() {
        User changed = signUpUser().changeHandle("first_change", NOW);
        Instant thirteenDaysLater = NOW.plus(Duration.ofDays(13));

        assertThatThrownBy(() -> changed.changeHandle("second_try", thirteenDaysLater))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("14일");
    }

    @Test
    @DisplayName("14일 경과 후에는 핸들을 변경할 수 있다")
    void changeHandle_after14Days_succeeds() {
        User changed = signUpUser().changeHandle("first_change", NOW);
        Instant fifteenDaysLater = NOW.plus(Duration.ofDays(15));

        User result = changed.changeHandle("second_try", fifteenDaysLater);

        assertThat(result.handle()).isEqualTo("second_try");
    }

    @Test
    @DisplayName("탈퇴 시 개인 정보가 익명화되고 애플 식별자가 해제된다")
    void withdraw_anonymizes() {
        User user = signUpUser();

        User withdrawn = user.withdraw("del_abc12345678", NOW);

        assertThat(withdrawn.status()).isEqualTo(UserStatus.DELETED);
        assertThat(withdrawn.nickname()).isEqualTo(User.WITHDRAWN_NICKNAME);
        assertThat(withdrawn.appleSub()).isNull();
        assertThat(withdrawn.profileImageUrl()).isNull();
        assertThat(withdrawn.searchable()).isFalse();
        assertThat(withdrawn.isActive()).isFalse();
    }
}
