package com.moment.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 로그인 요청 (F-01-05).
 */
public record EmailLoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아니에요.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password) {
}
