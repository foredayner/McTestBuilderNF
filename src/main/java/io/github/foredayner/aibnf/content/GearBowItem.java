package io.github.foredayner.aibnf.content;

import io.github.foredayner.aibnf.client.GearClientExtensions;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * An AI-modelled bow: works like a vanilla bow (drawing, power, enchantments). Plain arrows it
 * shoots take the arrow shape and the trail / landing effects ordered with the bow.
 */
public class GearBowItem extends BowItem implements GearItem {

    public GearBowItem(Properties settings) {
        super(settings);
    }

    @Override
    public GearKind kind() {
        return GearKind.BOW;
    }

    /**
     * Forge's hook for the arrow a bow is about to shoot. Plain arrows (not tipped or spectral)
     * become AI arrows; vanilla then sets power, crits and enchantments on what we return.
     */
    @Override
    public AbstractArrow customArrow(AbstractArrow arrow) {
        if (arrow.getClass() != Arrow.class || !(arrow.getOwner() instanceof LivingEntity shooter)
            || ((Arrow) arrow).getColor() != -1) {
            return arrow; // tipped, spectral or modded arrows stay as they are
        }
        ItemStack bow = shooter.getUseItem();
        if (!(bow.getItem() instanceof GearBowItem)) {
            bow = shooter.getMainHandItem();
        }
        AibArrowEntity aib = new AibArrowEntity(arrow.level(), shooter, bow);
        aib.pickup = arrow.pickup;
        return aib;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(GearClientExtensions.INSTANCE);
    }
}
