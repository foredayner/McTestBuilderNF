package io.github.foredayner.aibnf.mixin.client;

import io.github.foredayner.aibnf.client.GearModelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game skips drawing an entity whose outline is off screen. Big AI equipment (a balloon
 * floating above the head, a giant hammer) reaches far past that outline, so it would vanish
 * while still in view: the outline used for that check is grown by the equipment's reach.
 * (In 1.20.1 that outline comes from the entity, not its renderer.)
 */
@Mixin(Entity.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    private void aib$growForBigEquipment(CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof LivingEntity living && living.level().isClientSide()) {
            float reach = GearModelRenderer.reachOf(living);
            if (reach > 1.0F) {
                cir.setReturnValue(cir.getReturnValue().inflate(reach));
            }
        }
    }
}
