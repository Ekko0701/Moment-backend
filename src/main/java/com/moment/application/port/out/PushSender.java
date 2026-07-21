package com.moment.application.port.out;

/**
 * 실제 푸시 전송 포트 (아웃바운드).
 *
 * <p>{@link PushPort}는 서비스가 호출하는 "발행" 계약이고, 이 포트는 트랜잭션 커밋 이후
 * 실제 전송을 담당한다. 둘을 분리해 DB 커밋 전에 푸시가 나가거나 푸시 실패로 DB가
 * 롤백되는 문제를 막는다.
 */
public interface PushSender {

    void send(PushPort.PushMessage message);
}
