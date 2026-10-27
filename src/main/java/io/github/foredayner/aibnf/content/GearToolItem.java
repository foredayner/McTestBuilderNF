package io.github.foredayner.aibnf.content;

import com.google.common.collect.Multimap;
import io.github.foredayner.aibnf.client.GearClientExtensions;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;

/**
 * An AI-modelled weapon, tool or worn piece. Its stats (damage, speed, mining, durability,
 * enchantability, armour) come from an iron vanilla item of its kind, and right-click and hit
 * behaviour is passed on to that item (an axe strips logs, a shovel makes paths, a hoe tills).
 * The look comes from the model stored on each stack (drawn by the client).
 *
 * <p>1.21 copies the vanilla item's data components onto this item; 1.20.1 has no components,
 * so each behaviour is handed on to the vanilla item here instead.
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

    private ItemStack baseStack() {
        return new ItemStack(kind.base);
    }

    // --- use and hit -------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (kind.worn()) {
            return InteractionResult.PASS;
        }
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
    public boolean mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity miner) {
        return kind.base.mineBlock(stack, world, state, pos, miner);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
        return kind.base.canAttackBlock(state, world, pos, miner);
    }

    // --- stats -------------------------------------------------------------------------------

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return kind.base.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return kind.base.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return kind.base.canPerformAction(baseStack(), toolAction);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return kind.base.getDefaultAttributeModifiers(slot);
    }

    @Override
    public int getEnchantmentValue() {
        return kind.base.getEnchantmentValue();
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1 && kind.base.isEnchantable(baseStack());
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category.canEnchant(kind.base);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return kind.base.isValidRepairItem(baseStack(), repair);
    }

    // --- worn kinds (see GearWornItem) ------------------------------------------------------

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return kind.worn() ? kind.slot : null;
    }

    // --- look (client) -----------------------------------------------------------------------

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(GearClientExtensions.INSTANCE);
    }
}
