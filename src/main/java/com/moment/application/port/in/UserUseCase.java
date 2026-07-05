package com.moment.application.port.in;

import com.moment.domain.user.User;
import java.util.UUID;

public interface UserUseCase {

    User getMe(UUID userId);

    User updateMe(UUID userId, UpdateMyProfileCommand command);

    /** 계정 삭제 — 익명화 + 토큰 폐기 (F-10). */
    void deleteMe(UUID userId);

    /** 변경할 필드만 non-null로 전달한다. */
    record UpdateMyProfileCommand(String nickname, String profileImageUrl, String handle,
                                  Boolean searchable) {
    }
}
