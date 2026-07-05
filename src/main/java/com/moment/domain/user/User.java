package com.moment.domain.user;

import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 사용자 도메인 모델 (기능정의서 F-01/F-08).
 * 불변 객체 — 상태 변경은 새 인스턴스를 반환한다.
 */
public record User(
        UUID id,
        String handle,
        String nickname,
        String profileImageUrl,
        AuthProvider authProvider,
        String appleSub,
        boolean searchable,
        Instant handleUpdatedAt,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static final Pattern HANDLE_PATTERN = Pattern.compile("^[a-z0-9_]{4,15}$");
    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 12;
    public static final Duration HANDLE_CHANGE_INTERVAL = Duration.ofDays(14);
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    public static User signUp(UUID id, String handle, String nickname, String appleSub, Instant now) {
        validateHandle(handle);
        validateNickname(nickname);
        return new User(id, handle, nickname, null, AuthProvider.APPLE, appleSub,
                true, null, UserStatus.ACTIVE, now, now);
    }

    public User changeNickname(String newNickname, Instant now) {
        validateNickname(newNickname);
        return new User(id, handle, newNickname, profileImageUrl, authProvider, appleSub,
                searchable, handleUpdatedAt, status, createdAt, now);
    }

    public User changeProfileImage(String newProfileImageUrl, Instant now) {
        return new User(id, handle, nickname, newProfileImageUrl, authProvider, appleSub,
                searchable, handleUpdatedAt, status, createdAt, now);
    }

    /** 핸들 변경 — 14일에 1회 제한 (F-08-02). */
    public User changeHandle(String newHandle, Instant now) {
        validateHandle(newHandle);
        if (handleUpdatedAt != null && now.isBefore(handleUpdatedAt.plus(HANDLE_CHANGE_INTERVAL))) {
            throw new BusinessException(ErrorCode.CONFLICT, "핸들은 14일에 한 번만 변경할 수 있어요.");
        }
        return new User(id, newHandle, nickname, profileImageUrl, authProvider, appleSub,
                searchable, now, status, createdAt, now);
    }

    /** 검색 허용 on/off (F-02-10). */
    public User changeSearchable(boolean newSearchable, Instant now) {
        return new User(id, handle, nickname, profileImageUrl, authProvider, appleSub,
                newSearchable, handleUpdatedAt, status, createdAt, now);
    }

    /** 탈퇴 — 개인 식별 정보를 익명화하고 핸들/애플 식별자를 해제한다 (F-10). */
    public User withdraw(String anonymizedHandle, Instant now) {
        return new User(id, anonymizedHandle, WITHDRAWN_NICKNAME, null, authProvider, null,
                false, handleUpdatedAt, UserStatus.DELETED, createdAt, now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public static void validateHandle(String handle) {
        if (handle == null || !HANDLE_PATTERN.matcher(handle).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "핸들은 4~15자의 영소문자, 숫자, 밑줄(_)만 사용할 수 있어요.");
        }
    }

    public static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()
                || nickname.length() < NICKNAME_MIN_LENGTH || nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "닉네임은 2~12자로 입력해 주세요.");
        }
    }
}
