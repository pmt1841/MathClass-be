package com.codegym.mathclass.chat.dto.event;

import com.codegym.mathclass.chat.dto.response.ChatMessageResponse;

import java.io.Serializable;

public record ChatBroadcastEvent(
        String originInstanceId,
        String destinationTopic,
        ChatMessageResponse message
) implements Serializable {
}
