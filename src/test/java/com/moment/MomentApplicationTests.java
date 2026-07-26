package com.moment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("dev")
class MomentApplicationTests {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Test
    void contextLoads() {
        assertThat(restClientBuilder).isNotNull();
    }

}
