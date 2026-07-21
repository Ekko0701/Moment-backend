package com.moment.adapter.out.push;

import com.moment.application.port.out.PushPort.PushMessage;
import com.moment.application.port.out.PushSender;
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

    private final PushSender pushSender;

    PushEventListener(PushSender pushSender) {
        this.pushSender = pushSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onPushMessage(PushMessage message) {
        pushSender.send(message);
    }
}
