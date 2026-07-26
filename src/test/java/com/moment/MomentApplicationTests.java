package com.moment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moment.config.MomentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("dev")
class MomentApplicationTests {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private MomentProperties momentProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(restClientBuilder).isNotNull();
        assertThat(momentProperties.auth()).isNotNull();
        assertThat(momentProperties.auth().apple()).isNotNull();
        // dev 프로필은 스텁 인증 모드를 사용한다 — yaml 프로필 오버라이드 회귀 가드
        assertThat(momentProperties.auth().apple().mode()).isEqualTo("stub");
        assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

}
