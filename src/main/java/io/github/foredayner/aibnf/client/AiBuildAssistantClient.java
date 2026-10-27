package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.client.network.AiClientNetworking;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** The player's game: keys, screens, renderers and the messages the server sends. */
public class AiBuildAssistantClient {

    private static KeyMapping OPEN_AI_UI_KEY;

    /** Called from the mod's constructor, on the player's game only. */
    public static void init(IEventBus modBus) {
        AiClientNetworking.registerReceivers();

        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            // Solid object hitboxes are invisible; object items draw their own picture.
            event.registerEntityRenderer(io.github.foredayner.aibnf.content.AibContent.HITBOX,
                net.minecraft.client.renderer.entity.NoopRenderer<io.github.foredayner.aibnf.content.SolidHitboxEntity>::new);
            event.registerEntityRenderer(io.github.foredayner.aibnf.content.AibContent.ARROW, AibArrowRenderer::new);
        });
        modBus.addListener((EntityRenderersEvent.AddLayers event) -> WearRenderer.addLayers(event));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("object_breaking",
            (gui, graphics, partialTick, width, height) -> ObjectBreaking.renderHud(graphics)));

        OPEN_AI_UI_KEY = new KeyMapping(
            "key.aibuildassistantnf.open_ai_ui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.misc"
        );
        REMOVE_OBJECT_KEY = new KeyMapping(
            "key.aibuildassistantnf.remove_object",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DELETE,
            "key.categories.misc"
        );
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(OPEN_AI_UI_KEY);
            event.register(REMOVE_OBJECT_KEY);
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            while (OPEN_AI_UI_KEY.consumeClick()) {
                if (!isOperator(client)) {
                    continue;
                }
                AiClientState.clearConversation();
                AiClientState.setEffects("", ""); // a new order starts with empty effect fields
                client.setScreen(new AiPromptScreen());
            }
            while (REMOVE_OBJECT_KEY.consumeClick()) {
                if (isOperator(client)) {
                    removeLookedAtObject(client);
                }
            }
            ObjectBreaking.tick(client);
            SparkRenderer.tick(client);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent event) -> SparkRenderer.render(event));
    }

    private static KeyMapping REMOVE_OBJECT_KEY;

    /** The AI 공방 is for operators only (the server checks again); others get a short notice. */
    static boolean isOperator(net.minecraft.client.Minecraft client) {
        if (client.player == null) {
            return false;
        }
        if (client.player.hasPermissions(io.github.foredayner.aibnf.network.AiNetworking.OP_LEVEL)) {
            return true;
        }
        client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(io.github.foredayner.aibnf.network.AiNetworking.OP_ONLY), true);
        return false;
    }
    private static int pendingRemoveId = -1;
    private static long pendingRemoveAt = 0;

    /** Look at an AI object and press the key twice within 3 seconds to remove it. */
    private static void removeLookedAtObject(net.minecraft.client.Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
            && hit.getEntity() instanceof net.minecraft.world.entity.Interaction) {
            int id = hit.getEntity().getId();
            long now = System.currentTimeMillis();
            if (id == pendingRemoveId && now - pendingRemoveAt < 3000) {
                pendingRemoveId = -1;
                AiClientNetworking.sendRemoveObject(id, "");
            } else {
                pendingRemoveId = id;
                pendingRemoveAt = now;
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("한 번 더 누르면 이 소품을 지워요")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            }
            return;
        }
        pendingRemoveId = -1;
        client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("지울 소품을 조준하고 누르세요")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }
}
