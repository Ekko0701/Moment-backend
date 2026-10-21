package com.moment.adapter.in.web.dto.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "그룹 이름을 입력해 주세요.")
        @Size(max = 10, message = "그룹 이름은 최대 10자까지 입력할 수 있어요.") String name) {
}
