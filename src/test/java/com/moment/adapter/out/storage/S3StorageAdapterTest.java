package com.moment.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.application.port.out.StoragePort;
import com.moment.config.MomentProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@DisplayName("S3StorageAdapter")
class S3StorageAdapterTest {

    private final UUID userId = UUID.randomUUID();

    // 서명은 로컬 연산이므로 네트워크 없이 고정 자격 증명으로 검증한다.
    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    private static MomentProperties properties(String bucket, String region, String baseUrl) {
        return new MomentProperties(
                new MomentProperties.Jwt("secret", Duration.ofMinutes(30), Duration.ofDays(30)),
                new MomentProperties.Auth(MomentProperties.Auth.Apple.stub()),
                new MomentProperties.Invite(Duration.ofHours(24), Duration.ofHours(72), Duration.ofDays(7)),
                new MomentProperties.Policy(true, 6),
                new MomentProperties.Storage("s3",
                        new MomentProperties.Storage.S3(bucket, region, baseUrl, Duration.ofMinutes(10))));
    }

    private S3StorageAdapter adapter() {
        return new S3StorageAdapter(
                properties("moment-images", "ap-northeast-2", "https://img.momentapplication.com"),
                presigner);
    }

    @Test
    @DisplayName("presigned PUT URL은 버킷·사용자 prefix 키·서명·Content-Type 서명 헤더를 포함한다")
    void issueUploadUrl_signsPutWithContentType() {
        StoragePort.PresignedUpload presigned = adapter().issueUploadUrl(userId, "image/jpeg");

        assertThat(presigned.uploadUrl())
                .contains("moment-images")
                .contains("/moments/" + userId + "/")
                .contains("X-Amz-Signature=")
                // Content-Type이 서명에 포함되어야 클라이언트가 다른 타입으로 PUT하는 것을 S3가 거부한다
                .contains("content-type");
        assertThat(presigned.imageUrl())
                .startsWith("https://img.momentapplication.com/moments/" + userId + "/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("확장자는 contentType을 따른다 (png/heic)")
    void issueUploadUrl_extensionFollowsContentType() {
        assertThat(adapter().issueUploadUrl(userId, "image/png").imageUrl()).endsWith(".png");
        assertThat(adapter().issueUploadUrl(userId, "image/heic").imageUrl()).endsWith(".heic");
    }

    @Test
    @DisplayName("공개 베이스 URL의 끝 슬래시 유무와 무관하게 같은 imageUrl을 만든다")
    void publicBaseUrl_trailingSlashNormalized() {
        S3StorageAdapter withSlash = new S3StorageAdapter(
                properties("b", "ap-northeast-2", "https://img.example.com/"), presigner);

        String imageUrl = withSlash.issueUploadUrl(userId, "image/jpeg").imageUrl();
        assertThat(imageUrl).startsWith("https://img.example.com/moments/" + userId + "/");
        assertThat(withSlash.isManagedImageUrl(userId, imageUrl)).isTrue();
    }

    @Test
    @DisplayName("본인에게 발급된 공개 URL만 관리 경로로 인정한다")
    void isManagedImageUrl_ownUrlOnly() {
        S3StorageAdapter adapter = adapter();
        StoragePort.PresignedUpload presigned = adapter.issueUploadUrl(userId, "image/jpeg");

        assertThat(adapter.isManagedImageUrl(userId, presigned.imageUrl())).isTrue();
        assertThat(adapter.isManagedImageUrl(UUID.randomUUID(), presigned.imageUrl())).isFalse();
        // presigned 업로드 URL(S3 도메인)은 이미지 URL로 인정하지 않는다
        assertThat(adapter.isManagedImageUrl(userId, presigned.uploadUrl())).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, "https://evil.example.com/moments/" + userId
                + "/" + UUID.randomUUID() + ".jpg")).isFalse();
    }

    @Test
    @DisplayName("s3 모드에서 필수 설정(버킷/리전/공개 URL)이 비면 기동 실패한다 (fail-closed)")
    void missingConfig_failsFast() {
        assertThatThrownBy(() -> new S3StorageAdapter(
                properties("", "ap-northeast-2", "https://img.example.com"), presigner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MOMENT_STORAGE_S3_BUCKET");
        assertThatThrownBy(() -> new S3StorageAdapter(
                properties("bucket", "", "https://img.example.com"), presigner))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new S3StorageAdapter(
                properties("bucket", "ap-northeast-2", null), presigner))
                .isInstanceOf(IllegalStateException.class);
    }
}
