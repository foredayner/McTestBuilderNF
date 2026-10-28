package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import io.github.foredayner.aibnf.ai.AiController;
import io.github.foredayner.aibnf.ai.build.BuildPlan;
import io.github.foredayner.aibnf.ai.build.BuildRecord;
import io.github.foredayner.aibnf.ai.build.GearItems;
import io.github.foredayner.aibnf.ai.build.ObjectItems;
import io.github.foredayner.aibnf.ai.build.RebuildService;
import io.github.foredayner.aibnf.gametest.Demo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * CI only (run with -Daib.smoketest=true): opens a flat creative world, hands the player AI
 * equipment and objects, takes screenshots (first person, third person, inventory, the AI 공방
 * screen) into screenshots/, and quits. Fabric had client game tests for this; Forge 1.20.1
 * does not, so the mod drives the game itself.
 */
public final class SmokeTest {

    private static int tick;
    private static int inWorld = -1;
    private static boolean started;
    private static final List<String> TAKEN = new ArrayList<>();

    private SmokeTest() {
    }

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("aib.smoketest")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        }
    }

    private static void log(String message) {
        AiBuildAssistantMod.LOGGER.info("[smoketest] {}", message);
    }

    private static void tick(Minecraft mc) {
        tick++;
        if (!started) {
            if (mc.getOverlay() == null && tick > 40) {
                started = true;
                log("creating world");
                createWorld(mc);
            }
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (inWorld < 0) {
            inWorld = tick;
            log("in world");
        }
        int t = tick - inWorld;
        switch (t) {
            case 40 -> mc.getSingleplayerServer().execute(() -> setUp(mc));
            case 140 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }
            case 160 -> shot(mc, "1_first_person_greatsword");
            case 170 -> mc.getSingleplayerServer().execute(() -> swing(mc));
            case 172 -> shot(mc, "2_first_person_swing");
            case 190 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 210 -> shot(mc, "3_third_person_worn");
            case 220 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(180);
                mc.player.setXRot(25);
            }
            case 240 -> shot(mc, "4_objects_and_armor_stand");
            case 250 -> mc.setScreen(new InventoryScreen(mc.player));
            case 270 -> shot(mc, "5_inventory");
            case 280 -> {
                mc.setScreen(null);
                mc.setScreen(new AiPromptScreen());
            }
            case 300 -> shot(mc, "6_ai_prompt_screen");
            case 310 -> mc.setScreen(new AiSettingsScreen(null));
            case 330 -> shot(mc, "7_ai_settings_screen");
            case 340 -> {
                log("done: " + TAKEN);
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("aib-smoketest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        settings.gameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        settings.gameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel("aib-smoketest", settings, new WorldOptions(1234L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                .createWorldDimensions(), null);
    }

    private static BuildRecord gear(ServerLevel world, BlockPos at, ServerPlayer player, String script, String kind) {
        BuildPlan plan = Demo.plan(world, at, player, script, true);
        BuildRecord record = BuildRecord.create("smoketest " + kind, "TEST", "script", plan);
        record.kind = "gear";
        record.gear = kind;
        return record;
    }

    /** On the server thread: equipment on the player, objects and a dressed armour stand in front. */
    private static void setUp(Minecraft mc) {
        try {
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            ServerLevel world = player.serverLevel();
            world.setDayTime(6000);
            BlockPos at = player.blockPosition();
            player.setItemSlot(EquipmentSlot.MAINHAND, GearItems.stackFor(gear(world, at, player, Demo.GREATSWORD, "sword")));
            player.setItemSlot(EquipmentSlot.OFFHAND, GearItems.stackFor(gear(world, at, player, Demo.BOW, "bow")));
            player.setItemSlot(EquipmentSlot.HEAD, GearItems.stackFor(gear(world, at, player, AiController.EXAMPLE_HELMET, "helmet")));
            player.setItemSlot(EquipmentSlot.CHEST, GearItems.stackFor(gear(world, at, player, AiController.EXAMPLE_WINGS, "back")));

            // Objects behind the player (they look north later): an apple as a placed object and as an item.
            BuildRecord apple = BuildRecord.create("apple", "TEST", "script", Demo.plan(world, at, player, Demo.APPLE));
            apple.icon = new int[16 * 16];
            for (int i = 0; i < apple.icon.length; i++) {
                int x = i % 16, y = i / 16;
                if ((x - 8) * (x - 8) + (y - 9) * (y - 9) < 30) {
                    apple.icon[i] = 0xFFCC2222;
                }
            }
            RebuildService.placeObjectOn(player, apple, at.offset(-1, -1, -3));
            player.getInventory().setItem(1, ObjectItems.stackFor(apple));
            player.getInventory().setItem(2, GearItems.stackFor(gear(world, at, player, Demo.SCYTHE, "sword")));

            ArmorStand stand = EntityType.ARMOR_STAND.create(world);
            if (stand != null) {
                stand.moveTo(at.getX() + 1.5, at.getY(), at.getZ() - 2.5, 0, 0);
                stand.setItemSlot(EquipmentSlot.HEAD, GearItems.stackFor(gear(world, at, player, AiController.EXAMPLE_HAT, "hat")));
                stand.setItemSlot(EquipmentSlot.CHEST, GearItems.stackFor(gear(world, at, player, Demo.BALLOON, "back")));
                world.addFreshEntity(stand);
            }
            log("set up: " + player.getMainHandItem() + " / " + player.getItemBySlot(EquipmentSlot.HEAD));
        } catch (RuntimeException exception) {
            AiBuildAssistantMod.LOGGER.error("[smoketest] set up failed", exception);
        }
    }

    private static void swing(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> log(message.getString()));
        TAKEN.add(name);
    }
}
