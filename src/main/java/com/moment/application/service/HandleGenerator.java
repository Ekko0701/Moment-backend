package com.moment.application.service;

import com.moment.application.port.out.UserRepositoryPort;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 가입 시 검색용 핸들 자동 생성 (F-01-07).
 * 닉네임에서 사용 가능한 문자를 추출하고 난수를 붙여 유일한 핸들을 만든다.
 * 예: "동주" → "moment_8241", "DongJoo Kim" → "dongjookim_8241"
 */
@Component
public class HandleGenerator {

    private static final String FALLBACK_BASE = "moment";
    private static final int BASE_MAX_LENGTH = 10;
    private static final int BASE_MIN_LENGTH = 2;
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepositoryPort userRepository;
    private final SecureRandom random = new SecureRandom();

    public HandleGenerator(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    public String generate(String nicknameSeed) {
        String base = normalize(nicknameSeed);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = base + "_" + (1000 + random.nextInt(9000));
            if (!userRepository.existsByHandle(candidate)) {
                return candidate;
            }
        }
        byte[] bytes = new byte[5];
        random.nextBytes(bytes);
        return "user_" + HexFormat.of().formatHex(bytes);
    }

    private String normalize(String seed) {
        if (seed == null) {
            return FALLBACK_BASE;
        }
        String cleaned = seed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleaned.length() < BASE_MIN_LENGTH) {
            return FALLBACK_BASE;
        }
        return cleaned.length() > BASE_MAX_LENGTH ? cleaned.substring(0, BASE_MAX_LENGTH) : cleaned;
    }
}
