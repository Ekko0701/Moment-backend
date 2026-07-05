package com.moment.adapter.in.web.dto;

import com.moment.domain.user.User;
import java.util.UUID;

public record UserResponse(UUID id, String handle, String nickname, String profileImageUrl,
                           boolean isSearchable) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.handle(), user.nickname(),
                user.profileImageUrl(), user.searchable());
    }
}
