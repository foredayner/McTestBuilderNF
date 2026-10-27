package com.example.mixin.client;

import com.example.client.network.AiClientNetworking;
import com.example.content.GearItem;
import com.example.content.GearKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attack key pressed with an AI weapon (not at a block, charged): tell the server, which plays
 * the weapon's attack effect for everyone, whether or not the swing hits something.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public LocalPlayer player;

    @Shadow
    public HitResult hitResult;

    @Shadow
    protected int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void aib$attackEffect(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer p = this.player;
        if (p == null || p.isSpectator() || this.missTime > 0
            || !(p.getMainHandItem().getItem() instanceof GearItem gear) || gear.kind().worn() || gear.kind() == GearKind.BOW) {
            return;
        }
        if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
            return; // starting to mine
        }
        if (p.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        AiClientNetworking.sendAttackSwing();
    }
}
