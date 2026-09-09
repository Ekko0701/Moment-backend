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
     * 클라이언트가 모먼트 생성 시 제출한 imageUrl이 이 스토리지가 해당 사용자에게 발급한
     * 관리 경로인지 검증한다. 임의의 외부 URL 주입과 타인이 업로드한 객체 참조를 함께 막는다.
     *
     * @param userId 모먼트 작성자 ID — 발급 시 키에 새긴 사용자 prefix와 대조한다
     */
    boolean isManagedImageUrl(UUID userId, String imageUrl);

    /**
     * Pre-signed 업로드 정보.
     *
     * @param uploadUrl 클라이언트가 PUT 요청을 보낼 URL
     * @param imageUrl 업로드 완료 후 이미지를 참조할 URL
     */
    record PresignedUpload(String uploadUrl, String imageUrl) {
    }
}
