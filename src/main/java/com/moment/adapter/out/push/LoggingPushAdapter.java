package com.moment.adapter.out.push;

import com.moment.application.port.out.PushPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingPushAdapter implements PushPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPushAdapter.class);

    @Override
    public void send(PushMessage message) {
        logger.info(
                "Push notification: userId={}, type={}, title={}, body={}",
                message.targetUserId(),
                message.type(),
                message.title(),
                message.body());
        // TODO Task#4: Replace with APNs adapter for production
    }
}
