package io.github.foredayner.aibnf.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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

    @Override
    public Projectile createProjectile(Level world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack,
                                              boolean critical) {
        if (!projectileStack.is(Items.ARROW)) {
            return super.createProjectile(world, shooter, weaponStack, projectileStack, critical); // tipped, spectral
        }
        AibArrowEntity arrow = new AibArrowEntity(world, shooter, projectileStack.copyWithCount(1), weaponStack);
        if (critical) {
            arrow.setCritArrow(true);
        }
        return arrow;
    }
}
