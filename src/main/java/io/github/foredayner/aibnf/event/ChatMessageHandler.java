package io.github.foredayner.aibnf.event;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

public class ChatMessageHandler {

    public static void registerChatFilter() {
        NeoForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            if (event.getRawText().startsWith("/aibuild")) {
                event.setCanceled(true);
            }
        });
    }
}
