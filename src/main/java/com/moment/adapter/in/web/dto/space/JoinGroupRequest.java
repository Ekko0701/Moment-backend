package com.moment.adapter.in.web.dto.space;

import jakarta.validation.constraints.NotBlank;

public record JoinGroupRequest(@NotBlank(message = "초대 코드를 입력해 주세요.") String code) {
}
