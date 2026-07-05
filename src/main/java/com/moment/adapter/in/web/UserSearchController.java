package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.user.SearchUserResponse;
import com.moment.application.port.in.UserSearchUseCase;
import com.moment.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserSearchController {

    private final UserSearchUseCase userSearchUseCase;

    public UserSearchController(UserSearchUseCase userSearchUseCase) {
        this.userSearchUseCase = userSearchUseCase;
    }

    @GetMapping("/search")
    ApiResponse<SearchUserResponse> search(@AuthenticationPrincipal UUID userId, @RequestParam String handle) {
        var user = userSearchUseCase.search(userId, handle);
        return ApiResponse.ok(new SearchUserResponse(user.id(), user.handle(), user.nickname(), user.profileImageUrl()));
    }
}
