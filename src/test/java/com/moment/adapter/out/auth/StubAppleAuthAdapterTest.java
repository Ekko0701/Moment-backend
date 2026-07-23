package com.moment.adapter.out.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("StubAppleAuthAdapter (fail-closed)")
class StubAppleAuthAdapterTest {

    @Test
    @DisplayName("prod 프로필에서는 스텁 인증이 생성되지 않고 기동 실패한다")
    void prodProfile_failsFast() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new StubAppleAuthAdapter(prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영");
    }

    @Test
    @DisplayName("dev 프로필에서는 스텁이 정상 동작하고 identityToken을 식별자로 취급한다")
    void devProfile_worksAsStub() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        StubAppleAuthAdapter adapter = new StubAppleAuthAdapter(dev);
        assertThatCode(() -> adapter.authenticate("token-x", "code", "nonce"))
                .doesNotThrowAnyException();
        assertThat(adapter.authenticate("token-x", "code", "nonce").appleSub())
                .isEqualTo("stub:token-x");
    }
}
