package io.github.foredayner.aibnf.event;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;

public class ChatMessageHandler {

    public static void registerChatFilter() {
        MinecraftForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            if (event.getRawText().startsWith("/aibuild")) {
                event.setCanceled(true);
            }
        });
    }
}
