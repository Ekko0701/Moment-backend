package com.moment.adapter.out.storage;

import com.moment.application.port.out.StoragePort;
import com.moment.config.MomentProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 presigned URL 스토리지 어댑터 (운영).
 *
 * <p>업로드는 클라이언트가 presigned PUT으로 직접 수행하고(서버는 서명만 발급),
 * 조회는 공개 베이스 URL(CloudFront 등) 뒤의 키로 서빙한다.
 * 자격 증명은 기본 체인(EC2 인스턴스 롤 / 환경변수)을 사용한다 — 저장된 키 없음.
 *
 * <p>서명에 Content-Type을 포함하므로 클라이언트는 presign 요청과 동일한
 * Content-Type 헤더로 PUT해야 한다(불일치 시 S3가 403으로 거부).
 */
@Component
@ConditionalOnProperty(prefix = "moment.storage", name = "mode", havingValue = "s3",
        matchIfMissing = true)
class S3StorageAdapter implements StoragePort {

    private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(10);

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final Duration presignTtl;

    // 생성자가 둘이라 Spring이 고를 수 없다(기본 생성자 탐색 → 기동 실패). 운영 주입 경로를 명시한다.
    @Autowired
    S3StorageAdapter(MomentProperties properties) {
        this(properties, buildPresigner(properties));
    }

    /** 테스트 전용 — presigner를 주입해 네트워크 없이 서명 결과를 검증한다. */
    S3StorageAdapter(MomentProperties properties, S3Presigner presigner) {
        MomentProperties.Storage.S3 s3 = requireS3Config(properties);
        this.presigner = presigner;
        this.bucket = s3.bucket();
        this.publicBaseUrl = normalizeBaseUrl(s3.publicBaseUrl());
        this.presignTtl = s3.presignTtl() != null ? s3.presignTtl() : DEFAULT_PRESIGN_TTL;
    }

    @Override
    public PresignedUpload issueUploadUrl(UUID userId, String contentType) {
        String key = StorageKeys.newImageKey(userId, contentType);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(presignTtl)
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = presigner.presignPutObject(presignRequest).url().toString();
        String imageUrl = publicBaseUrl + key;
        return new PresignedUpload(uploadUrl, imageUrl);
    }

    @Override
    public boolean isManagedImageUrl(UUID userId, String imageUrl) {
        return StorageKeys.isManagedImageUrl(publicBaseUrl, userId, imageUrl);
    }

    private static S3Presigner buildPresigner(MomentProperties properties) {
        MomentProperties.Storage.S3 s3 = requireS3Config(properties);
        return S3Presigner.builder()
                .region(Region.of(s3.region()))
                .build();
    }

    // fail-closed: s3 모드인데 필수 설정이 비어 있으면 기동 자체를 막는다.
    private static MomentProperties.Storage.S3 requireS3Config(MomentProperties properties) {
        MomentProperties.Storage.S3 s3 =
                properties.storage() != null ? properties.storage().s3() : null;
        if (s3 == null
                || isBlank(s3.bucket()) || isBlank(s3.region()) || isBlank(s3.publicBaseUrl())) {
            throw new IllegalStateException(
                    "S3 스토리지 설정이 없습니다. MOMENT_STORAGE_S3_BUCKET / MOMENT_STORAGE_S3_REGION / "
                            + "MOMENT_STORAGE_S3_PUBLIC_BASE_URL 환경변수를 설정하거나, "
                            + "개발 환경이라면 MOMENT_STORAGE_MODE=stub 을 사용하세요.");
        }
        return s3;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
