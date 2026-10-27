package com.example.event;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public class ChatMessageHandler {

    public static void registerChatFilter() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String content = message.signedContent();
            if (content.startsWith("/aibuild")) {
                return false;
            }
            return true;
        });
    }
}
