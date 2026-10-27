package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.client.network.AiClientNetworking;
import io.github.foredayner.aibnf.content.SolidHitboxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mining a placed AI object: hold attack on it and it breaks after as long as a stone block
 * would take with what you hold (hand about 7.5 s, wooden pickaxe about 1.1 s, faster with
 * better tools, Efficiency and Haste). Creative breaks it at once. The server then removes it
 * and drops the item.
 */
public final class ObjectBreaking {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final float STONE_HARDNESS = 1.5F;

    private static int targetId = -1;
    private static float progress = 0;
    private static int ticks = 0;
    private static int cooldown = 0;

    private ObjectBreaking() {
    }

    public static void tick(Minecraft client) {
        Player player = client.player;
        if (cooldown > 0) {
            cooldown--;
        }
        if (player == null || client.level == null || client.screen != null || player.isSpectator()
            || !client.options.keyAttack.isDown()
            || !(client.hitResult instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof SolidHitboxEntity box)) {
            reset();
            return;
        }
        if (box.getId() != targetId) {
            targetId = box.getId();
            progress = 0;
            ticks = 0;
        }
        if (cooldown > 0) {
            return;
        }
        if (player.isCreative()) {
            AiClientNetworking.sendBreak(targetId);
            cooldown = 6;
            reset();
            return;
        }
        // Same formula the game uses for blocks: speed / hardness / (30 with the right tool, else 100).
        float speed = player.getDestroySpeed(STONE);
        progress += speed / STONE_HARDNESS / (player.hasCorrectToolForDrops(STONE) ? 30F : 100F);
        if (ticks++ % 4 == 0) {
            Vec3 at = hit.getLocation();
            client.level.playLocalSound(at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.25F, 0.5F, false);
            for (int i = 0; i < 2; i++) {
                client.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, STONE),
                    at.x, at.y, at.z, (Math.random() - 0.5) * 0.1, 0.05, (Math.random() - 0.5) * 0.1);
            }
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (progress >= 1F) {
            AiClientNetworking.sendBreak(targetId);
            cooldown = 6;
            reset();
        }
    }

    private static void reset() {
        targetId = -1;
        progress = 0;
        ticks = 0;
    }

    /** A small progress bar under the crosshair while mining an object. */
    public static void renderHud(GuiGraphics ctx) {
        if (targetId < 0 || progress <= 0) {
            return;
        }
        int w = 40;
        int x = ctx.guiWidth() / 2 - w / 2;
        int y = ctx.guiHeight() / 2 + 10;
        ctx.fill(x - 1, y - 1, x + w + 1, y + 4, 0xAA000000);
        ctx.fill(x, y, x + Math.round(w * Math.min(1F, progress)), y + 3, 0xFFE9A55A);
    }

    /** For tests: how far the current mining is (0..1). */
    public static float progress() {
        return progress;
    }
}
