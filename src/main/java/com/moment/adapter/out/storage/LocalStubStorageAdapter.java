package com.moment.adapter.out.storage;

import com.moment.application.port.out.StoragePort;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 개발용 스토리지 스텁 — 실제 업로드 없이 가짜 pre-signed URL을 발급한다.
 * 키 구조(moments/{userId}/{uuid}.{ext})는 S3 어댑터와 동일하게 유지해
 * 관리 URL 검증 로직이 두 모드에서 같은 계약으로 동작하게 한다.
 * 운영 전환 시 moment.storage.mode=s3 + S3 presign 어댑터를 사용한다.
 */
@Component
@ConditionalOnProperty(prefix = "moment.storage", name = "mode", havingValue = "stub")
class LocalStubStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalStubStorageAdapter.class);
    private static final String IMAGE_URL_PREFIX = "https://storage.moment.local/";

    LocalStubStorageAdapter(Environment environment) {
        // fail-closed: 운영(prod) 프로필에서는 스텁 스토리지가 절대 기동되지 않도록 막는다.
        boolean prodActive = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prodActive) {
            throw new IllegalStateException(
                    "STUB 스토리지는 운영 환경에서 사용할 수 없습니다. "
                            + "MOMENT_STORAGE_MODE=s3 로 설정하고 S3 버킷/리전/공개 URL을 주입하세요.");
        }
        log.warn("스토리지가 STUB 모드로 동작 중입니다. 업로드 URL은 실제로 동작하지 않습니다.");
    }

    @Override
    public PresignedUpload issueUploadUrl(UUID userId, String contentType) {
        String key = StorageKeys.newImageKey(userId, contentType);
        String uploadUrl = IMAGE_URL_PREFIX + "upload/" + key;
        String imageUrl = IMAGE_URL_PREFIX + key;
        return new PresignedUpload(uploadUrl, imageUrl);
    }

    @Override
    public boolean isManagedImageUrl(UUID userId, String imageUrl) {
        return StorageKeys.isManagedImageUrl(IMAGE_URL_PREFIX, userId, imageUrl);
    }
}
