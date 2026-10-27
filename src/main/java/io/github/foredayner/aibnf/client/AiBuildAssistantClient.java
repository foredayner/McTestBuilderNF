package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.client.network.AiClientNetworking;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.network.AiNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AiBuildAssistantClient implements ClientModInitializer {

    private static KeyMapping OPEN_AI_UI_KEY;

    @Override
    public void onInitializeClient() {
        AiBuilderConfig.load();
        AiNetworking.registerPayloads();
        AiClientNetworking.registerReceivers();

        // Solid object hitboxes are invisible; object items draw their own picture.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            io.github.foredayner.aibnf.content.AibContent.HITBOX, net.minecraft.client.renderer.entity.NoopRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
            io.github.foredayner.aibnf.content.AibContent.ARROW, AibArrowRenderer::new);
        net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(
            ObjectIconRenderer.TYPE_ID, ObjectIconRenderer.Unbaked.CODEC);
        net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(
            GearModelRenderer.TYPE_ID, GearModelRenderer.Unbaked.CODEC);
        WearRenderer.register();
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            (context, tickCounter) -> ObjectBreaking.renderHud(context));

        OPEN_AI_UI_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.aibuildassistantnf.open_ai_ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.misc"
            )
        );

        REMOVE_OBJECT_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.aibuildassistantnf.remove_object",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_DELETE,
                "key.categories.misc"
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(SparkRenderer::render);
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
