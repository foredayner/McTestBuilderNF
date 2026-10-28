package io.github.foredayner.aibnf;

import io.github.foredayner.aibnf.ai.build.BlueprintStore;
import io.github.foredayner.aibnf.ai.build.BuildTaskQueue;
import io.github.foredayner.aibnf.command.AiApprovalCommands;
import io.github.foredayner.aibnf.command.AiBuildCommand;
import io.github.foredayner.aibnf.config.AiBuilderConfig;
import io.github.foredayner.aibnf.event.ChatMessageHandler;
import io.github.foredayner.aibnf.network.AiNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AiBuildAssistantMod.MOD_ID)
public class AiBuildAssistantMod {

    public static final String MOD_ID = "aibuildassistantnf";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AiBuildAssistantMod(IEventBus modBus, Dist dist) {
        LOGGER.info("Minecraft AI Build Assistant starting...");

        AiBuilderConfig.load();
        io.github.foredayner.aibnf.content.AibContent.register(modBus);
        io.github.foredayner.aibnf.ai.build.HitEffects.register();
        BuildTaskQueue.register();

        AiNetworking.register(modBus);

        AiBuildCommand.register();
        AiApprovalCommands.register();
        ChatMessageHandler.registerChatFilter();

        // Blueprint papers (buildings): right-click the top of a block to build there.
        NeoForge.EVENT_BUS.addListener(AiBuildAssistantMod::onUseBlock);

        if (dist == Dist.CLIENT) {
            io.github.foredayner.aibnf.client.AiBuildAssistantClient.init(modBus);
        }
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (BlueprintStore.idOf(stack) == null) {
            return;
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            BlueprintStore.use(player, stack, event.getPos(), event.getHitVec().getDirection());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
