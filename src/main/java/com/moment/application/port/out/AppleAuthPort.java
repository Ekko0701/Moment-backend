package com.moment.application.port.out;

/**
 * Apple identityToken 검증 아웃바운드 포트.
 * dev 프로필에서는 스텁, prod에서는 Apple 공개키 서명 검증 어댑터가 바인딩된다.
 */
public interface AppleAuthPort {

    AppleIdentity verify(String identityToken);

    record AppleIdentity(String appleSub) {
    }
}
