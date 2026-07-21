package com.moment.adapter.out.push;

import com.moment.application.port.out.PushPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 서비스가 호출하는 {@link PushPort} 구현.
 *
 * <p>실제 전송을 즉시 하지 않고 애플리케이션 이벤트로 발행만 한다. 실제 전송은
 * {@link PushEventListener}가 트랜잭션 커밋 이후에 수행한다. 이렇게 하면 DB 커밋 전에
 * 푸시가 나가거나, 푸시 실패가 DB 트랜잭션을 롤백시키거나, 외부 호출 지연이 트랜잭션을
 * 길게 잡는 문제를 피할 수 있다.
 */
@Component
class DeferredPushPort implements PushPort {

    private final ApplicationEventPublisher publisher;

    DeferredPushPort(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void send(PushMessage message) {
        publisher.publishEvent(message);
    }
}
