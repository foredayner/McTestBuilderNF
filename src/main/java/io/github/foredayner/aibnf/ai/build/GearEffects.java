package io.github.foredayner.aibnf.ai.build;

import io.github.foredayner.aibnf.content.GearItem;
import io.github.foredayner.aibnf.content.GearKind;
import io.github.foredayner.aibnf.network.AiNetworkPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colour-dot effects of AI equipment. The effects are stored on the item; the server only tells
 * nearby players "these effects play here now" (on a swing, a mob's hit, an arrow landing), and
 * every player's game draws the dots. Effects while held or worn and arrow trails need no
 * messages at all: each game sees the equipment and draws them itself.
 */
public final class GearEffects {

    /** Item data key: the equipment's effects as JSON (Spark.Effects). */
    public static final String KEY = "aib_fx";

    private GearEffects() {
    }

    static void write(CompoundTag nbt, Spark.Effects fx) {
        if (fx != null && !fx.isEmpty()) {
            nbt.putString(KEY, fx.toJson());
        }
    }

    /** The effects stored on a stack (empty if none). */
    public static Spark.Effects of(ItemStack stack) {
        CustomData data = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return new Spark.Effects();
        }
        return Spark.Effects.fromJson(data.copyTag().getString(KEY));
    }

    /** Last swing effect per player (server ticks), so a modified client cannot flood effects. */
    private static final Map<UUID, Integer> LAST_SWING = new HashMap<>();

    /**
     * A player swung an equipment weapon (told by their client when the attack key is pressed and
     * the attack is charged). Returns how many effects were sent.
     */
    public static int onSwing(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GearItem gear) || gear.kind().worn()
            || gear.kind() == GearKind.BOW || !(player.level() instanceof ServerLevel world)) {
            return 0;
        }
        Spark.Effects fx = of(stack);
        if (fx.attack.isEmpty()) {
            return 0;
        }
        int now = player.getServer() == null ? player.tickCount : player.getServer().getTickCount();
        int interval = Math.max(4, Math.round(player.getCurrentItemAttackStrengthDelay() * 0.8F));
        Integer last = LAST_SWING.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < interval) {
            return 0;
        }
        LAST_SWING.put(player.getUUID(), now);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = player.entityInteractionRange();
        net.minecraft.world.phys.EntityHitResult aimed = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye,
            eye.add(look.scale(reach)), player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
            e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable(), reach * reach);
        return play(world, player, fx.attack, aimed == null ? null : aimed.getEntity(), look, sizeFor(stack));
    }

    /** A mob hit {@code target} with an AI weapon (players get theirs when they swing). */
    public static int onHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(target.level() instanceof ServerLevel world)) {
            return 0;
        }
        Spark.Effects fx = of(stack);
        return fx.attack.isEmpty() ? 0 : play(world, attacker, fx.attack, target, attacker.getViewVector(1.0F), sizeFor(stack));
    }

    /** An arrow from an AI bow landed at {@code at}: the bow's attack effects play there. */
    public static int arrowImpact(ServerLevel world, io.github.foredayner.aibnf.content.AibArrowEntity arrow, Vec3 at) {
        Spark.Effects fx = of(arrow.bow());
        if (fx.attack.isEmpty()) {
            return 0;
        }
        Vec3 dir = arrow.getDeltaMovement().lengthSqr() < 1e-6 ? arrow.getViewVector(1.0F) : arrow.getDeltaMovement().normalize();
        for (Spark s : fx.attack) {
            Vec3 o = switch (s.start) {
                case "above" -> at.add(0, 1.5, 0);
                default -> at;
            };
            send(world, o, dir, s, 1, PlayerLookup.around(world, at, 64));
        }
        return fx.attack.size();
    }

    /** Where each effect starts: at the weapon, on the target, on the ground under it, above it, or on the wielder. */
    private static int play(ServerLevel world, LivingEntity wielder, List<Spark> sparks, Entity target, Vec3 look, double scale) {
        Vec3 eye = wielder.getEyePosition();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 targetCenter = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : eye.add(look.scale(2.5));
        Vec3 targetFeet = target != null ? target.position() : wielder.position().add(flat.scale(2.5));
        List<ServerPlayer> viewers = new ArrayList<>(PlayerLookup.tracking(wielder));
        if (wielder instanceof ServerPlayer self && !viewers.contains(self)) {
            viewers.add(self);
        }
        for (Spark s : sparks) {
            Vec3 o = switch (s.start) {
                case "target" -> targetCenter;
                case "ground" -> targetFeet.add(0, 0.05, 0);
                case "above" -> targetCenter.add(0, 1.5, 0);
                case "self" -> wielder.position().add(0, wielder.getBbHeight() * 0.5, 0);
                default -> eye.add(0, -0.35, 0).add(look.scale(1.2));
            };
            send(world, o, look, s, scale, viewers);
        }
        return sparks.size();
    }

    private static void send(ServerLevel world, Vec3 at, Vec3 dir, Spark spark, double scale, Collection<ServerPlayer> to) {
        AiNetworkPayloads.SparkS2CPayload payload = new AiNetworkPayloads.SparkS2CPayload("[" + spark.toJson() + "]",
            at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, (float) scale);
        for (ServerPlayer p : to) {
            if (p.serverLevel() == world && ServerPlayNetworking.canSend(p, AiNetworkPayloads.SparkS2CPayload.ID)) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Big weapons get bigger effects: about the weapon's length over a normal sword's (1-2.5x). */
    private static double sizeFor(ItemStack stack) {
        BuildPlan model = GearItems.modelOf(stack);
        if (model == null || model.parts.isEmpty()) {
            return 1;
        }
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (BuildPlan.Part p : model.parts) {
            if (p.group != BuildPlan.ARROW_GROUP) {
                low = Math.min(low, p.y - p.sy / 2);
                high = Math.max(high, p.y + p.sy / 2);
            }
        }
        return high > low ? Math.max(1, Math.min(2.5, (high - low) / 1.5)) : 1;
    }
}
