package io.github.foredayner.aibnf.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An AI-modelled weapon or tool. Its stats (damage, speed, mining, durability, enchantability)
 * are copied from an iron vanilla item when it is registered, and right-click and hit
 * behaviour is passed on to that item (an axe strips logs, a shovel makes paths, a hoe tills).
 * The look comes from the model stored on each stack (drawn by the client).
 */
public class GearToolItem extends Item implements GearItem {

    private final GearKind kind;

    public GearToolItem(GearKind kind, Properties settings) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public GearKind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return kind.base.useOn(context);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player)) {
            // Players get their effect when they swing (sent by their client); mobs when they hit.
            io.github.foredayner.aibnf.ai.build.GearEffects.onHit(stack, target, attacker);
        }
        return kind.base.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        kind.base.postHurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
        return kind.base.canAttackBlock(state, world, pos, miner);
    }
}
