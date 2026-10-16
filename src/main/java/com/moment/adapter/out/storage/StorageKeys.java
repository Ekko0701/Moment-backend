package com.moment.adapter.out.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 이미지 객체 키 규약 — stub·S3 어댑터가 같은 키 구조와 검증 규칙을 공유한다.
 *
 * <p>키 구조: {@code moments/{userId}/{uuid}.{ext}}
 * 사용자 prefix를 키에 새겨 두면 모먼트 생성 시 "본인에게 발급된 경로인지"를
 * URL 문자열만으로 검증할 수 있다(타인 객체 참조 차단).
 */
final class StorageKeys {

    /** 발급 UUID 파일명만 통과 — 경로 세그먼트 추가·확장자 위조·상위 경로 탈출을 모두 걸러낸다. */
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f\\-]{36}\\.(jpg|png|heic)$");

    private StorageKeys() {
    }

    static String newImageKey(UUID userId, String contentType) {
        return "moments/" + userId + "/" + UUID.randomUUID() + extensionOf(contentType);
    }

    static boolean isManagedImageUrl(String publicBaseUrl, UUID userId, String imageUrl) {
        if (imageUrl == null) {
            return false;
        }
        String prefix = publicBaseUrl + "moments/" + userId + "/";
        if (!imageUrl.startsWith(prefix)) {
            return false;
        }
        return FILE_NAME.matcher(imageUrl.substring(prefix.length())).matches();
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }
}
