package com.moment.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface PushPort {

    void send(PushMessage message);

    record PushMessage(
            UUID targetUserId,
            String type,
            String title,
            String body,
            Map<String, String> data) {
    }
}
