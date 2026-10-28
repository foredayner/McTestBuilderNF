package io.github.foredayner.aibnf.content;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A worn AI piece (helmet, hat, chest piece, back piece): right-click puts it on, like armour,
 * and it gives the armour of its vanilla item. Drawn on the wearer by WearRenderer.
 */
public class GearWornItem extends GearToolItem implements Equipable {

    public GearWornItem(GearKind kind, Properties settings) {
        super(kind, settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, world, player, hand);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return kind().slot;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return kind().base instanceof ArmorItem armor ? armor.getMaterial().value().equipSound() : SoundEvents.ARMOR_EQUIP_GENERIC;
    }
}
