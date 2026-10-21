package com.moment.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.moment.application.port.out.StoragePort;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 운영 모드(s3)로 Spring 컨텍스트가 뜨는지 검증한다.
 *
 * <p>dev·it 프로필은 stub 모드라 S3StorageAdapter 빈을 생성하지 않으므로, 이 테스트가 s3 모드의
 * 빈 생성과 설정 바인딩을 컨텍스트 수준에서 보장한다. 서명기(S3Presigner)는 자격 증명을 지연 해석하므로
 * AWS 자격 증명·네트워크 없이 빈 생성까지 검증한다. 실제 서명은 S3StorageAdapterTest가 고정 키로 검증한다.
 */
@SpringBootTest(properties = {
        "moment.storage.mode=s3",
        "moment.storage.s3.bucket=context-test-bucket",
        "moment.storage.s3.region=ap-northeast-2",
        "moment.storage.s3.public-base-url=https://cdn.example.test"
})
@ActiveProfiles("dev")
@DisplayName("storage mode=s3 컨텍스트")
class S3StorageModeContextTest {

    @Autowired
    private StoragePort storagePort;

    @Test
    @DisplayName("s3 모드에서 StoragePort 구현체는 S3StorageAdapter로 기동된다")
    void s3ModeBootsWithS3Adapter() {
        assertThat(storagePort).isInstanceOf(S3StorageAdapter.class);
    }

    @Test
    @DisplayName("관리 URL 검증은 설정된 공개 베이스 URL과 사용자 prefix를 기준으로 동작한다")
    void managedUrlCheckUsesConfiguredBase() {
        UUID userId = UUID.randomUUID();
        String mine = "https://cdn.example.test/moments/" + userId + "/" + UUID.randomUUID() + ".jpg";
        String other = "https://cdn.example.test/moments/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg";

        assertThat(storagePort.isManagedImageUrl(userId, mine)).isTrue();
        assertThat(storagePort.isManagedImageUrl(userId, other)).isFalse();
    }
}
