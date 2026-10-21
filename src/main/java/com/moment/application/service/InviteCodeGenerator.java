package com.moment.application.service;

import com.moment.application.port.out.GroupInviteCodePort;
import com.moment.application.port.out.InviteCodePort;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 1:1 연결 코드와 그룹 초대 코드가 함께 쓰는 6자리 코드 생성기.
 * 두 코드 저장소 어디에도 없는 값만 돌려주므로 같은 코드가 1:1과 그룹을 동시에 가리키지 않는다.
 */
@Component
public class InviteCodeGenerator {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 6;
    private static final int RETRY_LIMIT = 5;

    private final InviteCodePort inviteCodePort;
    private final GroupInviteCodePort groupInviteCodePort;
    private final SecureRandom random = new SecureRandom();

    public InviteCodeGenerator(InviteCodePort inviteCodePort, GroupInviteCodePort groupInviteCodePort) {
        this.inviteCodePort = inviteCodePort;
        this.groupInviteCodePort = groupInviteCodePort;
    }

    public String generateUnique() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            String code = randomCode();
            if (inviteCodePort.findByCode(code).isEmpty() && groupInviteCodePort.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "초대 코드 생성에 실패했어요. 다시 시도해주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
