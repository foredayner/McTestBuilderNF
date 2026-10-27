package com.example.content;

import com.example.ai.build.ObjectItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * An AI-made object as an item. Right-click the top of a block to set it there (facing you);
 * mine it like stone to get the item back. Which object it is, and its icon picture, are in
 * the stack's custom data (see ObjectItems).
 */
public class ObjectItem extends Item {

    public ObjectItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            if (!context.getLevel().isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal("블록 윗면을 클릭하면 그 위에 놓여요").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        return ObjectItems.use(player, context.getItemInHand(), context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
