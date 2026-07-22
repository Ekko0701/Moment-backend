package com.moment.adapter.out.push;

import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 발행된 푸시 메시지를 트랜잭션 커밋 이후에 실제 전송한다.
 *
 * <p>{@code fallbackExecution = true}로 트랜잭션이 없는 경우(예: 테스트, 비트랜잭션 호출)에도
 * 즉시 전송하도록 한다.
 */
@Component
class PushEventListener {

    private static final Logger log = LoggerFactory.getLogger(PushEventListener.class);

    private final PushSender pushSender;

    PushEventListener(PushSender pushSender) {
        this.pushSender = pushSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onPushMessage(PushMessage message) {
        try {
            pushSender.send(message);
        } catch (RuntimeException ex) {
            // 커밋 이후의 푸시 실패가 호출자에게 전파되면 DB는 성공했는데 API가 실패로 보인다.
            // 푸시는 best-effort — 로그만 남기고 삼킨다. (실전 APNs 어댑터에서 재시도 정책 추가 예정)
            log.warn("push_send_failed userId={} type={}", message.targetUserId(), message.type(), ex);
        }
    }
}
