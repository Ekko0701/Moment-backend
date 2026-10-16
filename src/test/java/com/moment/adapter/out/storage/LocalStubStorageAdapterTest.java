package com.moment.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moment.application.port.out.StoragePort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("LocalStubStorageAdapter")
class LocalStubStorageAdapterTest {

    private final UUID userId = UUID.randomUUID();
    private LocalStubStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        adapter = new LocalStubStorageAdapter(dev);
    }

    @Test
    @DisplayName("prod 프로필에서는 스텁 스토리지가 생성되지 않고 기동 실패한다")
    void prodProfile_failsFast() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new LocalStubStorageAdapter(prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영");
    }

    @Test
    @DisplayName("발급 키는 사용자 prefix(moments/{userId}/)를 포함한다")
    void issuedKey_containsUserPrefix() {
        StoragePort.PresignedUpload presigned = adapter.issueUploadUrl(userId, "image/jpeg");

        assertThat(presigned.imageUrl())
                .startsWith("https://storage.moment.local/moments/" + userId + "/")
                .endsWith(".jpg");
        assertThat(presigned.uploadUrl())
                .startsWith("https://storage.moment.local/upload/moments/" + userId + "/");
    }

    @Test
    @DisplayName("본인에게 발급된 이미지 URL만 관리 경로로 인정한다")
    void isManagedImageUrl_ownUrlOnly() {
        StoragePort.PresignedUpload presigned = adapter.issueUploadUrl(userId, "image/png");

        assertThat(adapter.isManagedImageUrl(userId, presigned.imageUrl())).isTrue();
        // 타인 검증으로는 같은 URL도 거부된다
        assertThat(adapter.isManagedImageUrl(UUID.randomUUID(), presigned.imageUrl())).isFalse();
        // 업로드 경로·외부 URL·null 모두 거부
        assertThat(adapter.isManagedImageUrl(userId, presigned.uploadUrl())).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, "https://evil.example.com/x.jpg")).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, null)).isFalse();
    }

    @Test
    @DisplayName("사용자 prefix 뒤에 발급 형식(uuid.확장자)이 아닌 경로는 거부한다")
    void isManagedImageUrl_rejectsForgedTail() {
        String base = "https://storage.moment.local/moments/" + userId + "/";
        String otherUsersFile = "../" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg";

        assertThat(adapter.isManagedImageUrl(userId, base + otherUsersFile)).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, base + "not-a-uuid.jpg")).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, base + UUID.randomUUID() + ".exe")).isFalse();
        assertThat(adapter.isManagedImageUrl(userId, base + UUID.randomUUID() + ".heic")).isTrue();
    }
}
