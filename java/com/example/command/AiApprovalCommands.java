package com.example.command;

import com.example.ai.approval.BuildApproval;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class AiApprovalCommands {

    private AiApprovalCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
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
