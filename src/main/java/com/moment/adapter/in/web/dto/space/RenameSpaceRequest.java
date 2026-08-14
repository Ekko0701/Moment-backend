package com.moment.adapter.in.web.dto.space;

import jakarta.validation.constraints.Size;

public record RenameSpaceRequest(
    @Size(max = 10, message = "스페이스 이름은 최대 10자까지 입력할 수 있어요.") String name) {
}
