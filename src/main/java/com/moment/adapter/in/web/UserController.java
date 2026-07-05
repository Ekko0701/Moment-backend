package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.UpdateUserRequest;
import com.moment.adapter.in.web.dto.UserResponse;
import com.moment.application.port.in.UserUseCase;
import com.moment.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserUseCase userUseCase;

    public UserController(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @GetMapping("/me")
    ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(UserResponse.from(userUseCase.getMe(userId)));
    }

    @PatchMapping("/me")
    ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        var command = new UserUseCase.UpdateMyProfileCommand(request.nickname(),
                request.profileImageUrl(), request.handle(), request.isSearchable());
        return ApiResponse.ok(UserResponse.from(userUseCase.updateMe(userId, command)));
    }

    @DeleteMapping("/me")
    ApiResponse<Void> deleteMe(@AuthenticationPrincipal UUID userId) {
        userUseCase.deleteMe(userId);
        return ApiResponse.ok();
    }
}
