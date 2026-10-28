package io.github.foredayner.aibnf.command;

import io.github.foredayner.aibnf.ai.approval.BuildApproval;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class AiApprovalCommands {

    private AiApprovalCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            var dispatcher = event.getDispatcher();
            dispatcher.register(
                literal("ai-approve")
                    .executes(context -> {
                        BuildApproval.approveFor(context.getSource().getPlayer());
                        context.getSource().sendSuccess(() -> Component.literal("Build approved."), false);
                        return 1;
                    })
            );

            dispatcher.register(
                literal("ai-deny")
                    .executes(context -> {
                        BuildApproval.denyFor(context.getSource().getPlayer());
                        context.getSource().sendSuccess(() -> Component.literal("Build cancelled."), false);
                        return 1;
                    })
            );
        });
    }
}
