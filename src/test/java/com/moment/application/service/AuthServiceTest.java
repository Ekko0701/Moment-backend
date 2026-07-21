package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moment.application.port.in.AuthUseCase.LoginResult;
import com.moment.application.port.in.AuthUseCase.TokenPair;
import com.moment.application.port.out.AppleAuthPort;
import com.moment.application.port.out.DevicePort;
import com.moment.application.port.out.PasswordHasherPort;
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
    private PasswordHasherPort passwordHasher;
    @Mock
    private RefreshTokenPort refreshTokenPort;
    @Mock
    private TokenPort tokenPort;
    @Mock
    private DevicePort devicePort;
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
        authService = new AuthService(appleAuthPort, userRepository, passwordHasher,
                refreshTokenPort, tokenPort, devicePort, handleGenerator, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Apple 미가입 사용자는 자동 가입되고 isNewUser=true로 응답한다")
    void loginWithApple_newUser_signsUp() {
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
    @DisplayName("Apple 기존 사용자는 가입 없이 로그인되고 isNewUser=false다")
    void loginWithApple_existingUser() {
        User existing = User.signUp(UUID.randomUUID(), "dongjoo_1234", "동주", "sub-1", NOW);
        when(appleAuthPort.verify("token")).thenReturn(new AppleAuthPort.AppleIdentity("sub-1"));
        when(userRepository.findByAppleSub("sub-1")).thenReturn(Optional.of(existing));
        when(tokenPort.createAccessToken(existing.id())).thenReturn("access-token");

        LoginResult result = authService.loginWithApple("token", null);

        assertThat(result.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 가입 — 신규 사용자 성공")
    void signUpWithEmail_newUser_succeeds() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(handleGenerator.generate("테스트")).thenReturn("test_user_01");
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenPort.createAccessToken(any(UUID.class))).thenReturn("access-token");

        LoginResult result = authService.signUpWithEmail("test@example.com", "password123", "테스트");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordHasher).hash("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 가입 — 중복 이메일 시 CONFLICT")
    void signUpWithEmail_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUpWithEmail("test@example.com", "password123", "테스트"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("이메일 가입 — 짧은 비밀번호는 INVALID_REQUEST")
    void signUpWithEmail_shortPassword_throwsInvalidRequest() {
        assertThatThrownBy(() -> authService.signUpWithEmail("test@example.com", "short", "테스트"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이메일 로그인 — 기존 사용자 성공")
    void loginWithEmail_existingUser_succeeds() {
        User user = User.signUpWithEmail(UUID.randomUUID(), "test_user_01", "테스트",
                "test@example.com", "hashed", NOW);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("password123", "hashed")).thenReturn(true);
        when(tokenPort.createAccessToken(user.id())).thenReturn("access-token");

        TokenPair result = authService.loginWithEmail("test@example.com", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(passwordHasher).matches("password123", "hashed");
    }

    @Test
    @DisplayName("이메일 로그인 — 존재하지 않는 이메일은 UNAUTHORIZED")
    void loginWithEmail_notFound_throwsUnauthorized() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithEmail("notfound@example.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("이메일 로그인 — 잘못된 비밀번호는 UNAUTHORIZED")
    void loginWithEmail_wrongPassword_throwsUnauthorized() {
        User user = User.signUpWithEmail(UUID.randomUUID(), "test_user_01", "테스트",
                "test@example.com", "hashed", NOW);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrongpassword", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithEmail("test@example.com", "wrongpassword"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
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
