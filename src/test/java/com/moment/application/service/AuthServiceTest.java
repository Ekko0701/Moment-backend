package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.AuthUseCase.LoginResult;
import com.moment.application.port.out.AppleAuthPort;
import com.moment.application.port.out.RefreshTokenPort;
import com.moment.application.port.out.TokenPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.config.MomentProperties;
import com.moment.domain.auth.RefreshToken;
import com.moment.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

    @Mock
    private AppleAuthPort appleAuthPort;
    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private RefreshTokenPort refreshTokenPort;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private HandleGenerator handleGenerator;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MomentProperties properties = new MomentProperties(
                new MomentProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(30)),
                new MomentProperties.Auth(new MomentProperties.Auth.Apple("stub")),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72)),
                new MomentProperties.Policy(true));
        authService = new AuthService(appleAuthPort, userRepository, refreshTokenPort, tokenPort,
                handleGenerator, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("미가입 사용자는 자동 가입되고 isNewUser=true로 응답한다")
    void login_newUser_signsUp() {
        when(appleAuthPort.verify("token")).thenReturn(new AppleAuthPort.AppleIdentity("sub-1"));
        when(userRepository.findByAppleSub("sub-1")).thenReturn(Optional.empty());
        when(handleGenerator.generate("동주")).thenReturn("moment_1234");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenPort.createAccessToken(any(UUID.class))).thenReturn("access-token");

        LoginResult result = authService.loginWithApple("token", "동주");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.tokens().refreshToken()).isNotBlank();
        verify(refreshTokenPort).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("기존 사용자는 가입 없이 로그인되고 isNewUser=false다")
    void login_existingUser() {
        User existing = User.signUp(UUID.randomUUID(), "dongjoo_1234", "동주", "sub-1", NOW);
        when(appleAuthPort.verify("token")).thenReturn(new AppleAuthPort.AppleIdentity("sub-1"));
        when(userRepository.findByAppleSub("sub-1")).thenReturn(Optional.of(existing));
        when(tokenPort.createAccessToken(existing.id())).thenReturn("access-token");

        LoginResult result = authService.loginWithApple("token", null);

        assertThat(result.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("리프레시 토큰은 회전된다 — 기존 토큰 삭제 후 새 쌍 발급")
    void refresh_rotatesToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken(UUID.randomUUID(), userId,
                AuthService.sha256("old-refresh"), NOW.plus(Duration.ofDays(1)), NOW);
        when(refreshTokenPort.findByTokenHash(AuthService.sha256("old-refresh")))
                .thenReturn(Optional.of(stored));
        when(tokenPort.createAccessToken(userId)).thenReturn("new-access");

        var pair = authService.refresh("old-refresh");

        verify(refreshTokenPort).deleteById(stored.id());
        verify(refreshTokenPort).save(any(RefreshToken.class));
        assertThat(pair.accessToken()).isEqualTo("new-access");
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 폐기하고 INVALID_TOKEN을 던진다")
    void refresh_expired_throws() {
        RefreshToken expired = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(),
                AuthService.sha256("old-refresh"), NOW.minus(Duration.ofSeconds(1)), NOW);
        when(refreshTokenPort.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("old-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(refreshTokenPort).deleteById(expired.id());
        verify(refreshTokenPort, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그아웃은 사용자의 모든 리프레시 토큰을 폐기한다")
    void logout_deletesAllTokens() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenPort).deleteAllByUserId(userId);
    }
}
