package com.moment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.moment.application.port.out.UserRepositoryPort;
import com.moment.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleGeneratorTest {

    @Mock
    private UserRepositoryPort userRepository;

    private HandleGenerator handleGenerator;

    @BeforeEach
    void setUp() {
        handleGenerator = new HandleGenerator(userRepository);
    }

    @Test
    @DisplayName("영문 닉네임은 소문자 베이스 + 4자리 난수 핸들이 된다")
    void generate_englishNickname() {
        when(userRepository.existsByHandle(anyString())).thenReturn(false);

        String handle = handleGenerator.generate("DongJoo");

        assertThat(handle).matches("^dongjoo_\\d{4}$");
        assertThat(User.HANDLE_PATTERN.matcher(handle).matches()).isTrue();
    }

    @Test
    @DisplayName("한글 닉네임은 기본 베이스(moment)로 대체된다")
    void generate_koreanNickname_usesFallbackBase() {
        when(userRepository.existsByHandle(anyString())).thenReturn(false);

        String handle = handleGenerator.generate("동주");

        assertThat(handle).matches("^moment_\\d{4}$");
    }

    @Test
    @DisplayName("모든 후보가 충돌하면 난수 기반 핸들로 폴백한다")
    void generate_allCollide_fallsBack() {
        when(userRepository.existsByHandle(anyString())).thenReturn(true);

        String handle = handleGenerator.generate("dongjoo");

        assertThat(handle).matches("^user_[0-9a-f]{10}$");
        assertThat(User.HANDLE_PATTERN.matcher(handle).matches()).isTrue();
    }

    @Test
    @DisplayName("긴 닉네임은 10자로 잘라 15자 핸들 제한을 지킨다")
    void generate_longNickname_truncated() {
        when(userRepository.existsByHandle(anyString())).thenReturn(false);

        String handle = handleGenerator.generate("verylongnicknameover");

        assertThat(handle.length()).isLessThanOrEqualTo(15);
        assertThat(User.HANDLE_PATTERN.matcher(handle).matches()).isTrue();
    }
}
