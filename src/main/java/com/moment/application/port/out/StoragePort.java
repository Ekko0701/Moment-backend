package com.moment.application.port.out;

import java.util.UUID;

/**
 * 스토리지(이미지 업로드) 포트 (아웃바운드).
 */
public interface StoragePort {

    /**
     * 파일 업로드용 pre-signed URL 발급.
     *
     * @param userId 업로드 사용자 ID
     * @param contentType 파일 MIME 타입 (e.g., "image/jpeg", "image/png", "image/heic")
     * @return pre-signed URL과 업로드 후 이미지 URL
     */
    PresignedUpload issueUploadUrl(UUID userId, String contentType);

    /**
     * 클라이언트가 모먼트 생성 시 제출한 imageUrl이 이 스토리지가 발급한 관리 경로인지 검증한다.
     * 임의의 외부 URL을 모먼트 이미지로 주입하는 것을 막는다.
     * (S3 어댑터로 교체 시 사용자별 prefix까지 확인하도록 강화)
     */
    boolean isManagedImageUrl(String imageUrl);

    /**
     * Pre-signed 업로드 정보.
     *
     * @param uploadUrl 클라이언트가 PUT 요청을 보낼 URL
     * @param imageUrl 업로드 완료 후 이미지를 참조할 URL
     */
    record PresignedUpload(String uploadUrl, String imageUrl) {
    }
}
