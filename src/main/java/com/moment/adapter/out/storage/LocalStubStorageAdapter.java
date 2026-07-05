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

    @Override
    public PresignedUpload issueUploadUrl(UUID userId, String contentType) {
        // Generate fake URLs for development
        String fileName = UUID.randomUUID().toString() + getExtension(contentType);
        String uploadUrl = "https://storage.moment.local/upload/" + fileName;
        String imageUrl = "https://storage.moment.local/" + fileName;

        return new PresignedUpload(uploadUrl, imageUrl);
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
