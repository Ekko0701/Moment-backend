package com.moment.application.service;

import com.moment.application.port.in.UserUseCase;
import com.moment.application.port.out.RefreshTokenPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.domain.user.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepository;
    private final RefreshTokenPort refreshTokenPort;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepositoryPort userRepository, RefreshTokenPort refreshTokenPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenPort = refreshTokenPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public User getMe(UUID userId) {
        return findActiveUser(userId);
    }

    @Override
    public User updateMe(UUID userId, UpdateMyProfileCommand command) {
        User user = findActiveUser(userId);
        Instant now = clock.instant();
        if (command.nickname() != null) {
            user = user.changeNickname(command.nickname(), now);
        }
        if (command.profileImageUrl() != null) {
            user = user.changeProfileImage(command.profileImageUrl(), now);
        }
        if (command.handle() != null && !command.handle().equals(user.handle())) {
            if (userRepository.existsByHandle(command.handle())) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디예요.");
            }
            user = user.changeHandle(command.handle(), now);
        }
        if (command.searchable() != null) {
            user = user.changeSearchable(command.searchable(), now);
        }
        return userRepository.save(user);
    }

    @Override
    public void deleteMe(UUID userId) {
        User user = findActiveUser(userId);
        userRepository.save(user.withdraw(anonymizedHandle(), clock.instant()));
        refreshTokenPort.deleteAllByUserId(userId);
        // TODO(Task#3): 소속 스페이스 자동 이탈 + 멤버 알림 (F-10-02)
        // TODO(Task#4): 디바이스 토큰 삭제 (F-10-02)
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요."));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요.");
        }
        return user;
    }

    /** 탈퇴 계정의 핸들을 해제하기 위한 익명 핸들 ("del_" + 11 hex = 15자). */
    private String anonymizedHandle() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return "del_" + HexFormat.of().formatHex(bytes).substring(0, 11);
    }
}
