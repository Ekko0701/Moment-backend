package com.moment.adapter.out.storage;

import com.moment.application.port.out.StoragePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Local stub storage adapter for development/testing.
 * Generates fake pre-signed URLs without actual S3/cloud storage interaction.
 * Production: Replace with S3/Cloud Storage implementation (e.g., AWS S3 presignedRequest).
 */
@Component
class LocalStubStorageAdapter implements StoragePort {

    private static final String IMAGE_URL_PREFIX = "https://storage.moment.local/";

    @Override
    public PresignedUpload issueUploadUrl(UUID userId, String contentType) {
        // Generate fake URLs for development
        String fileName = UUID.randomUUID().toString() + getExtension(contentType);
        String uploadUrl = "https://storage.moment.local/upload/" + fileName;
        String imageUrl = IMAGE_URL_PREFIX + fileName;

        return new PresignedUpload(uploadUrl, imageUrl);
    }

    @Override
    public boolean isManagedImageUrl(String imageUrl) {
        // 업로드 경로(/upload/)가 아닌 관리 도메인 하위의 이미지 경로만 허용한다.
        return imageUrl != null
                && imageUrl.startsWith(IMAGE_URL_PREFIX)
                && !imageUrl.startsWith(IMAGE_URL_PREFIX + "upload/");
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }
}
