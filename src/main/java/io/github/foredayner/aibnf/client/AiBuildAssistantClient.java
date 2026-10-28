package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.client.network.AiClientNetworking;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.content.AibContent;
import io.github.foredayner.aibnf.content.GearKind;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** The player's game: keys, screens, renderers and the messages the server sends. */
public class AiBuildAssistantClient {

    private static KeyMapping OPEN_AI_UI_KEY;

    /** Called from the mod's constructor, on the player's game only. */
    public static void init(IEventBus modBus) {
        AiClientNetworking.registerReceivers();
        SmokeTest.registerIfEnabled();

        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            // Solid object hitboxes are invisible; object items draw their own picture.
            event.registerEntityRenderer(AibContent.HITBOX, net.minecraft.client.renderer.entity.NoopRenderer::new);
            event.registerEntityRenderer(AibContent.ARROW, AibArrowRenderer::new);
        });
        modBus.addListener((EntityRenderersEvent.AddLayers event) -> WearRenderer.addLayers(event));
        // AI gear and object items are drawn by the mod's item renderers.
        modBus.addListener((RegisterClientExtensionsEvent event) -> {
            event.registerItem(ObjectIconRenderer.EXTENSIONS, AibContent.OBJECT_ITEM);
            for (GearKind kind : GearKind.values()) {
                event.registerItem(GearClientExtensions.INSTANCE, AibContent.GEAR.get(kind));
            }
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(io.github.foredayner.aibnf.AiBuildAssistantMod.MOD_ID, "object_breaking"),
            (graphics, deltaTracker) -> ObjectBreaking.renderHud(graphics)));

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

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
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
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> SparkRenderer.render(event));
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
