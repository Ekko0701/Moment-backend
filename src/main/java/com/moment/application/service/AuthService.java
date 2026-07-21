package com.moment.application.service;

import com.moment.application.port.in.AuthUseCase;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService implements AuthUseCase {

    private static final String DEFAULT_NICKNAME = "모먼트";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppleAuthPort appleAuthPort;
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenPort tokenPort;
    private final DevicePort devicePort;
    private final HandleGenerator handleGenerator;
    private final MomentProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppleAuthPort appleAuthPort, UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher, RefreshTokenPort refreshTokenPort, TokenPort tokenPort,
            DevicePort devicePort, HandleGenerator handleGenerator, MomentProperties properties,
            Clock clock) {
        this.appleAuthPort = appleAuthPort;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenPort = refreshTokenPort;
        this.tokenPort = tokenPort;
        this.devicePort = devicePort;
        this.handleGenerator = handleGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public LoginResult loginWithApple(String identityToken, String nickname) {
        AppleAuthPort.AppleIdentity identity = appleAuthPort.verify(identityToken);
        Optional<User> existing = userRepository.findByAppleSub(identity.appleSub());
        boolean isNewUser = existing.isEmpty();
        User user = existing.orElseGet(() -> signUpWithApple(identity.appleSub(), nickname));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "사용할 수 없는 계정이에요.");
        }
        return new LoginResult(issueTokens(user.id()), isNewUser);
    }

    @Override
    public LoginResult signUpWithEmail(String email, String rawPassword, String nickname) {
        User.validateEmail(email);
        validatePassword(rawPassword);
        User.validateNickname(nickname);

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 가입한 이메일이에요.");
        }

        String handle = handleGenerator.generate(nickname);
        String passwordHash = passwordHasher.hash(rawPassword);
        User user = userRepository.save(User.signUpWithEmail(UUID.randomUUID(), handle, nickname,
                email, passwordHash, clock.instant()));

        return new LoginResult(issueTokens(user.id()), true);
    }

    @Override
    public TokenPair loginWithEmail(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "이메일 또는 비밀번호가 올바르지 않아요."));

        if (user.passwordHash() == null || !passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "이메일 또는 비밀번호가 올바르지 않아요.");
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "사용할 수 없는 계정이에요.");
        }

        return issueTokens(user.id());
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokenPort.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        refreshTokenPort.deleteById(stored.id());
        if (stored.isExpired(clock.instant())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return issueTokens(stored.userId());
    }

    @Override
    public void logout(UUID userId) {
        refreshTokenPort.deleteAllByUserId(userId);
        // 로그아웃 시 디바이스(APNs) 토큰도 폐기해 이전 계정으로 푸시가 가지 않도록 한다.
        devicePort.deleteAllByUserId(userId);
    }

    private User signUpWithApple(String appleSub, String nickname) {
        String resolvedNickname =
                (nickname == null || nickname.isBlank()) ? DEFAULT_NICKNAME : nickname;
        String handle = handleGenerator.generate(resolvedNickname);
        return userRepository.save(
                User.signUp(UUID.randomUUID(), handle, resolvedNickname, appleSub, clock.instant()));
    }

    private TokenPair issueTokens(UUID userId) {
        String accessToken = tokenPort.createAccessToken(userId);
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokenPort.save(new RefreshToken(UUID.randomUUID(), userId, sha256(refreshToken),
                now.plus(properties.jwt().refreshTokenTtl()), now));
        return new TokenPair(accessToken, refreshToken);
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "비밀번호는 8자 이상이어야 해요.");
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
