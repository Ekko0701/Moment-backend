package com.moment.application.port.out;

/**
 * 비밀번호 해싱 포트 (F-01-05).
 * BCrypt 등의 구현으로 원문 비밀번호를 안전하게 암호화하고 검증한다.
 */
public interface PasswordHasherPort {

    /**
     * 원문 비밀번호를 해싱한다.
     *
     * @param rawPassword 원문 비밀번호
     * @return 해싱된 비밀번호
     */
    String hash(String rawPassword);

    /**
     * 원문 비밀번호가 해시와 일치하는지 검증한다.
     *
     * @param rawPassword 원문 비밀번호
     * @param passwordHash 해싱된 비밀번호
     * @return 일치 여부
     */
    boolean matches(String rawPassword, String passwordHash);
}
