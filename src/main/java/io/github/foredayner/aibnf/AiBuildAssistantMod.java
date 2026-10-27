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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AiBuildAssistantMod.MOD_ID)
public class AiBuildAssistantMod {

    public static final String MOD_ID = "aibuildassistantnf";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AiBuildAssistantMod() {
        LOGGER.info("Minecraft AI Build Assistant starting...");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        AiBuilderConfig.load();
        io.github.foredayner.aibnf.content.AibContent.register(modBus);
        io.github.foredayner.aibnf.ai.build.HitEffects.register();
        BuildTaskQueue.register();

        AiNetworking.registerPayloads();

        AiBuildCommand.register();
        AiApprovalCommands.register();
        ChatMessageHandler.registerChatFilter();

        // Blueprint papers (buildings): right-click the top of a block to build there.
        MinecraftForge.EVENT_BUS.addListener(AiBuildAssistantMod::onUseBlock);

        if (FMLEnvironment.dist == Dist.CLIENT) {
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
