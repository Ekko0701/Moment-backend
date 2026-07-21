package com.moment.adapter.in.web.security;

import com.moment.application.port.out.TokenPort;
import com.moment.application.port.out.UserRepositoryPort;
import com.moment.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 파싱해 인증 컨텍스트를 구성한다.
 * principal은 사용자 ID(UUID)다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenPort tokenPort;
    private final UserRepositoryPort userRepository;

    public JwtAuthenticationFilter(TokenPort tokenPort, UserRepositoryPort userRepository) {
        this.tokenPort = tokenPort;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                UUID userId = tokenPort.parseUserId(token);
                // 서명·만료가 유효해도 사용자가 여전히 활성 상태인지 확인한다.
                // 탈퇴/정지 계정의 남은 Access Token(최대 30분 유효)으로 요청하는 것을 차단.
                boolean active = userRepository.findById(userId)
                        .map(user -> user.isActive())
                        .orElse(false);
                if (active) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (BusinessException ignored) {
                // 유효하지 않은 토큰은 미인증으로 통과시키고, 보호 자원 접근 시 401로 응답된다.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
