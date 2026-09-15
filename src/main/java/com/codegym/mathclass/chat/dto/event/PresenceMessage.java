package com.codegym.mathclass.chat.dto.event;

import java.io.Serializable;

public record PresenceMessage(
        String originInstanceId,
        Long userId,
        boolean isOnline,
        String lastActiveAt
) implements Serializable {
}
