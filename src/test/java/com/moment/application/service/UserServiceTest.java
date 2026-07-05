package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.UserUseCase.UpdateMyProfileCommand;
import com.moment.application.port.out.RefreshTokenPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.user.User;
import com.moment.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private RefreshTokenPort refreshTokenPort;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, refreshTokenPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.signUp(UUID.randomUUID(), "dongjoo_1234", "동주", "sub-1", NOW);
    }

    @Test
    @DisplayName("이미 사용 중인 핸들로 변경하면 CONFLICT를 던진다")
    void updateMe_duplicateHandle_throws() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.existsByHandle("taken_handle")).thenReturn(true);

        var command = new UpdateMyProfileCommand(null, null, "taken_handle", null);

        assertThatThrownBy(() -> userService.updateMe(user.id(), command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("부분 업데이트 — 전달된 필드만 변경된다")
    void updateMe_partialUpdate() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateMe(user.id(),
                new UpdateMyProfileCommand("새닉네임", null, null, null));

        assertThat(result.nickname()).isEqualTo("새닉네임");
        assertThat(result.handle()).isEqualTo("dongjoo_1234");
        assertThat(result.searchable()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 시 익명화 저장과 리프레시 토큰 폐기가 수행된다")
    void deleteMe_withdrawsAndClearsTokens() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteMe(user.id());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(UserStatus.DELETED);
        assertThat(captor.getValue().handle()).startsWith("del_");
        verify(refreshTokenPort).deleteAllByUserId(user.id());
    }

    @Test
    @DisplayName("탈퇴한 사용자 조회는 NOT_FOUND를 던진다")
    void getMe_deletedUser_throws() {
        User withdrawn = user.withdraw("del_abc12345678", NOW);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.getMe(user.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
