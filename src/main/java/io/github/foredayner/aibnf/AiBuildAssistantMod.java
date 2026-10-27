package io.github.foredayner.aibnf;

import io.github.foredayner.aibnf.ai.build.BuildTaskQueue;
import io.github.foredayner.aibnf.command.AiApprovalCommands;
import io.github.foredayner.aibnf.command.AiBuildCommand;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.event.ChatMessageHandler;
import io.github.foredayner.aibnf.network.AiNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiBuildAssistantMod implements ModInitializer {

    public static final String MOD_ID = "aibuildassistantnf";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Minecraft AI Build Assistant starting...");

        AiBuilderConfig.load();
        io.github.foredayner.aibnf.content.AibContent.register();
        io.github.foredayner.aibnf.ai.build.HitEffects.register();
        BuildTaskQueue.register();

        AiNetworking.registerPayloads();
        AiNetworking.registerServerReceivers();

        AiBuildCommand.register();
        AiApprovalCommands.register();
        ChatMessageHandler.registerChatFilter();

        // Blueprint papers (buildings): right-click the top of a block to build there.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            if (io.github.foredayner.aibnf.ai.build.BlueprintStore.idOf(stack) == null) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            if (world.isClientSide()) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            io.github.foredayner.aibnf.ai.build.BlueprintStore.use((net.minecraft.server.level.ServerPlayer) player, stack,
                hit.getBlockPos(), hit.getDirection());
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
    }
}
