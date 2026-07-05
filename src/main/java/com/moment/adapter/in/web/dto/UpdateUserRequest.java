package com.moment.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/** 변경할 필드만 담아 보낸다 (PATCH 시맨틱). */
public record UpdateUserRequest(
        @Size(min = 2, max = 12) String nickname,
        @Size(max = 500) String profileImageUrl,
        @Size(min = 4, max = 15) String handle,
        Boolean isSearchable) {
}
