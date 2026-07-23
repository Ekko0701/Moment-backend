package com.moment.application.port.out;

/**
 * Apple identityToken/authorizationCode 검증 및 Apple 세션 폐기 아웃바운드 포트.
 * dev 프로필에서는 스텁, prod에서는 Apple 공개키 서명 검증 어댑터가 바인딩된다.
 */
public interface AppleAuthPort {

    AppleIdentity authenticate(String identityToken, String authorizationCode, String nonce);

    void revoke(String encryptedRefreshToken);

    /** encryptedRefreshToken은 어댑터만 해독할 수 있는 opaque 값이다. */
    record AppleIdentity(String appleSub, String encryptedRefreshToken) {
    }
}
