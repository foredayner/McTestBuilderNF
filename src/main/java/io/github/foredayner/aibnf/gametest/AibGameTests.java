package io.github.foredayner.aibnf.gametest;

import io.github.foredayner.aibnf.ai.build.BuildPlan;
import io.github.foredayner.aibnf.ai.build.BuildRecord;
import io.github.foredayner.aibnf.ai.build.UndoService;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Real-server checks of the build pipeline: review + dry run place nothing, placing matches the
 * script, a saved build turns with the player, undo restores the area, and objects are display
 * entities that undo removes.
 */
@GameTestHolder(io.github.foredayner.aibnf.AiBuildAssistantMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AibGameTests {

    /** An empty 16 x 8 x 16 area (data/aibuildassistantnf/structures/empty.nbt). */
    private static final String EMPTY_STRUCTURE = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerPlayer opPlayer(GameTestHelper context, BlockPos at, float yaw) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        // The GameTest server gives new operators level 0; set level 4 directly.
        player.getServer().getPlayerList().getOps().add(
            new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 4, false));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        return player;
    }

    private static final String SMALL =
        "ai.placeBox(0, 0, -4, 3, 2, 3, 'stone_bricks')\n"
            + "ai.placeBlock(1, 2, -3, 'oak_stairs[facing=north]')\n";

    @GameTest(template = EMPTY_STRUCTURE)
    public static void dryRunPlacesNothingThenBuildThenUndo(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(3, 1, 7));
        ServerPlayer player = opPlayer(context, origin, 180f); // facing north

        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        check(plan.ops.size() == 2, "expected 2 recorded commands, got " + plan.ops.size());
        check(world.getBlockState(origin.offset(0, 0, -4)).isAir(), "dry run must not place blocks");

        Demo.place(player, origin, plan, 0, "small");
        check(world.getBlockState(origin.offset(0, 0, -4)).is(Blocks.STONE_BRICKS), "box corner placed");
        BlockState stairs = world.getBlockState(origin.offset(1, 2, -3));
        check(stairs.is(Blocks.OAK_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.NORTH, "stairs face north: " + stairs);

        UndoService.undo(player);
        check(world.getBlockState(origin.offset(0, 0, -4)).isAir(), "undo removed the box");
        check(world.getBlockState(origin.offset(1, 2, -3)).isAir(), "undo removed the stairs");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void savedBuildTurnsWithThePlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(3, 1, 3));
        ServerPlayer player = opPlayer(context, origin, 180f);
        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        check("north".equals(plan.facing), "recorded facing north, got " + plan.facing);

        // Rebuilt by someone facing east: one quarter turn clockwise.
        int turns = BuildPlan.turnsBetween(plan.facing, "east");
        Demo.place(player, origin, plan, turns, "turned");
        check(world.getBlockState(origin.offset(4, 0, 0)).is(Blocks.STONE_BRICKS), "box now east of the player");
        BlockState stairs = world.getBlockState(origin.offset(3, 2, 1));
        check(stairs.is(Blocks.OAK_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.EAST, "stairs turned east: " + stairs);
        UndoService.undo(player);
        check(world.getBlockState(origin.offset(4, 0, 0)).isAir(), "undo after a turned rebuild");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void objectsAreDisplayEntitiesAndUndoRemovesThem(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = opPlayer(context, origin, 0f);
        BuildPlan plan = Demo.plan(world, origin, player, Demo.APPLE);
        int n = plan.parts.size();
        check(n >= 6 && n <= 120, "a smooth sphere + stem + leaf is a few dozen merged parts, got " + n);
        check(plan.parts.stream().anyMatch(p -> p.block.equals("minecraft:lime_concrete") && p.yaw == 30), "the exact leaf piece stays as it is");
        check(plan.ops.isEmpty(), "objects place no blocks");

        BuildRecord record = Demo.place(player, origin, plan, 0, "apple");
        String tag = "aib_" + record.id;
        AABB area = new AABB(origin).inflate(6);
        int count = world.getEntities(EntityType.BLOCK_DISPLAY, area, e -> e.getTags().contains(tag)).size();
        check(count == n, n + " display entities summoned, got " + count);

        var solid = world.getEntitiesOfClass(io.github.foredayner.aibnf.content.SolidHitboxEntity.class, area, e -> e.getTags().contains(tag));
        check(!solid.isEmpty() && solid.size() <= 60, "solid hitboxes follow the shape, got " + solid.size());
        var hitboxes = solid.stream().filter(e -> e.getTags().contains("aib_obj")).toList();
        check(hitboxes.size() == 1, "one hitbox carries the object's name, got " + hitboxes.size());
        check(hitboxes.get(0).getCustomName() != null && "apple".equals(hitboxes.get(0).getCustomName().getString()),
            "hitbox carries the title");
        var listed = io.github.foredayner.aibnf.ai.build.ObjectService.nearby(player);
        // Other tests place objects close by at the same time: look for this one.
        check(listed.stream().anyMatch(o -> o.tag.equals(tag) && o.parts == n && "apple".equals(o.name)), "nearby list finds it");

        // Delete key path: remove by the hitbox entity id.
        io.github.foredayner.aibnf.ai.build.ObjectService.removeByEntity(player, hitboxes.get(0).getId());
        context.runAfterDelay(2, () -> {
            int left = world.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area, e -> e.getTags().contains(tag)).size();
            check(left == 0, "removing the object took every part and the hitbox, " + left + " left");
            context.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void objectsCanBePlacedBigger(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(2, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 0f); // facing south
        BuildPlan plan = Demo.plan(world, origin, player, Demo.APPLE);
        BuildRecord record = BuildRecord.create("apple", "TEST", "script", plan);

        BuildRecord big = io.github.foredayner.aibnf.ai.build.RebuildService.withScale(player, record, 300);
        check(big != null && big != record, "x3 is allowed for a small apple");
        check(big.id.endsWith("_x300") && big.scale == 3, "scaled copy has its own id and scale: " + big.id);
        check(record.plan.parts.get(0).sx * 3 == big.plan.parts.get(0).sx, "parts are three times bigger");
        double[] a = record.plan.partBounds();
        double[] b = big.plan.partBounds();
        check(Math.abs(b[1] - a[1]) < 1e-6, "still on the ground: " + a[1] + " -> " + b[1]);
        check(Math.abs(b[2] - a[2]) < 1e-6, "near (north) edge keeps its gap: " + a[2] + " -> " + b[2]);
        check(Math.abs((b[4] - b[1]) - 3 * (a[4] - a[1])) < 1e-4, "three times taller");
        check(io.github.foredayner.aibnf.ai.build.RebuildService.withScale(player, record, 5000) == null, "x50 is refused");

        Demo.place(player, origin, big.plan, 0, "big apple");
        AABB area = new AABB(origin).inflate(8);
        var hitbox = world.getEntitiesOfClass(io.github.foredayner.aibnf.content.SolidHitboxEntity.class, area, e -> true);
        check(!hitbox.isEmpty(), "solid hitboxes placed");
        AABB solid = hitbox.stream().map(net.minecraft.world.entity.Entity::getBoundingBox).reduce(AABB::minmax).get();
        check(solid.getYsize() > 1.5, "hitboxes grew with the object: " + solid);
        var parts = world.getEntities(EntityType.BLOCK_DISPLAY, area, e -> e.getTags().contains("aib"));
        check(parts.size() == big.plan.parts.size(), big.plan.parts.size() + " parts, got " + parts.size());
        for (var part : parts) {
            // The culling box is display data, not the entity's own size: read it back as NBT.
            var nbt = part.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(nbt.getFloat("width") > 0.2f && nbt.getFloat("height") > 0.2f, "display culling box is set: " + nbt.getFloat("width"));
        }
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void promptExamplesPassTheReviewAndDryRun(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 90f); // facing west: objects still use their own frame
        for (String example : new String[] {
            io.github.foredayner.aibnf.ai.AiController.EXAMPLE_DUCK, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_LAMP, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_CHAIR }) {
            BuildPlan plan = Demo.plan(world, origin, player, example);
            check(plan.ops.isEmpty() && !plan.parts.isEmpty(), "an object");
            check(plan.parts.size() <= BuildPlan.MAX_PARTS, "within the part limit: " + plan.parts.size());
            check("south".equals(plan.facing), "objects are designed facing south (+Z), got " + plan.facing);
            double[] b = plan.partBounds();
            check(Math.abs((b[0] + b[3]) / 2) < 0.2, "centred on x = 0: " + b[0] + ".." + b[3]);
            check(b[1] > -0.05 && b[1] < 0.1, "stands on y = 0: " + b[1]);
        }
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void gearIsAnItemWithItsModelAndIronStats(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 0f);
        for (String example : new String[] { io.github.foredayner.aibnf.ai.AiController.EXAMPLE_SWORD, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_AXE }) {
            BuildPlan plan = Demo.plan(world, origin, player, example, true);
            String problem = io.github.foredayner.aibnf.ai.AiController.gearProblem(plan);
            check(problem == null, "example fits the hand: " + problem);
        }
        BuildPlan sword = Demo.plan(world, origin, player, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_SWORD);
        BuildRecord record = BuildRecord.create("long sword", "TEST", "script", sword);
        record.kind = "gear";
        record.gear = "sword";
        io.github.foredayner.aibnf.ai.build.BlueprintStore.give(player, record);
        net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof io.github.foredayner.aibnf.content.GearItem) {
                stack = player.getInventory().getItem(i);
            }
        }
        check(!stack.isEmpty(), "the player got the equipment item");
        check(((io.github.foredayner.aibnf.content.GearItem) stack.getItem()).kind() == io.github.foredayner.aibnf.content.GearKind.SWORD, "a sword");
        BuildPlan model = io.github.foredayner.aibnf.ai.build.GearItems.modelOf(stack);
        check(model != null && model.parts.size() == sword.parts.size(), "the item carries the whole model");
        check(stack.getMaxDamage() == new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD).getMaxDamage(), "iron sword durability");
        check(stack.has(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS), "it has attack damage");
        check(stack.is(net.minecraft.tags.ItemTags.SWORDS), "it counts as a sword (enchantments)");
        check(stack.getMaxStackSize() == 1, "does not stack");
        // Worn equipment: the examples stay on the head / torso; a helmet is a head item with iron armour.
        BuildPlan helmet = Demo.plan(world, origin, player, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_HELMET, true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(helmet, io.github.foredayner.aibnf.content.GearKind.HELMET) == null,
            "helmet example fits: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(helmet, io.github.foredayner.aibnf.content.GearKind.HELMET));
        check(helmet.parts.size() <= BuildPlan.MAX_PARTS, "mask within the part limit: " + helmet.parts.size());
        check(helmet.parts.stream().anyMatch(q -> Math.abs((q.x - q.sx / 2) * 16 - Math.round((q.x - q.sx / 2) * 16)) > 1e-6
            || Math.abs(q.sx * 16 - Math.round(q.sx * 16)) > 1e-6), "the mask uses the finer equipment grid");
        BuildPlan hat = Demo.plan(world, origin, player, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_HAT, true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(hat, io.github.foredayner.aibnf.content.GearKind.HAT) == null,
            "hat example fits: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(hat, io.github.foredayner.aibnf.content.GearKind.HAT));
        // Big only when asked: the balloon passes with ai.oversize(), fails without it, and nothing passes 5 blocks.
        BuildPlan balloon = Demo.plan(world, origin, player, Demo.BALLOON, true);
        check(balloon.oversize, "oversize recorded");
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(balloon, io.github.foredayner.aibnf.content.GearKind.BACK) == null,
            "a big balloon fits when asked: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(balloon, io.github.foredayner.aibnf.content.GearKind.BACK));
        BuildPlan small = Demo.plan(world, origin, player, Demo.BALLOON.replace("ai.oversize()\n", ""), true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(small, io.github.foredayner.aibnf.content.GearKind.BACK) != null, "not big unless asked");
        BuildPlan huge = Demo.plan(world, origin, player, "ai.oversize()\nai.addEllipsoid('red_concrete', 0, 4, 0, 3.5, 3, 3)\n", true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(huge, io.github.foredayner.aibnf.content.GearKind.BACK) != null, "at most 5 blocks");
        BuildPlan wings = Demo.plan(world, origin, player, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_WINGS, true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(wings, io.github.foredayner.aibnf.content.GearKind.BACK) == null,
            "wings example fits: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(wings, io.github.foredayner.aibnf.content.GearKind.BACK));
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(wings, io.github.foredayner.aibnf.content.GearKind.HELMET) != null, "wings are too wide for a head");
        BuildRecord helm = BuildRecord.create("helm", "TEST", "script", helmet);
        helm.kind = "gear";
        helm.gear = "helmet";
        net.minecraft.world.item.ItemStack helmStack = io.github.foredayner.aibnf.ai.build.GearItems.stackFor(helm);
        check(helmStack.getEquipmentSlot() == net.minecraft.world.entity.EquipmentSlot.HEAD, "a helmet goes on the head");
        check(helmStack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE), "takes head armour enchantments");
        check(!helmStack.getAttributeModifiers().modifiers().isEmpty(), "it gives armour");

        // Effects and movement: recorded, carried by the item, and dropped when the order did not ask.
        BuildPlan fx = Demo.plan(world, origin, player, """
            ai.addCapsule('oak_planks', 0, -0.1, 0, 0, 1.4, 0, 0.04)
            ai.mirrorOn()
            ai.groupStart('gem')
            ai.addSphere('amethyst_block', 0.12, 1.45, 0, 0.06)
            ai.groupEnd()
            ai.mirrorOff()
            ai.animate('gem', 'spin', 0.12, 1.45, 0, 'y', 0.5, 0)
            ai.glow('gem')
            ai.effect('attack', [shape: 'heart', fill: true, color: '#ff66cc', size: 0.45, moves: ['forward', 'fade'], speed: 7])
            ai.effect('idle', [shape: 'burst', color: '#33ccff', size: 0.3, count: 6, moves: ['rise', 'fade'], every: 6])
            ai.aura('#ffdd66', 0.6)
            ai.effect('attack', [shape: 'points', points: [[0, 1], [1, 0], [0, -1], [-1, 0], [0, 1]], color: '#ffffff', plane: 'flat', start: 'ground'])
            """);
        check(fx.aura != null && fx.aura.color == 0xffdd66, "aura recorded");
        check(fx.fx.attack.size() == 2 && "points".equals(fx.fx.attack.get(1).shape) && fx.fx.attack.get(1).points.size() == 5,
            "a second attack effect with its own points plays together");
        check("flat".equals(fx.fx.attack.get(1).plane) && "ground".equals(fx.fx.attack.get(1).start), "its settings are kept");
        check(fx.fx.attack.get(0).dots().size() == fx.fx.attack.get(0).count, "a filled heart has its dots");
        check(fx.parts.stream().anyMatch(q -> q.group > 0), "group parts are marked");
        check(fx.anims.size() == 4, "spin + glow for the gem and its mirrored twin, got " + fx.anims.size());
        check("heart".equals(fx.fx.attack.get(0).shape) && fx.fx.attack.get(0).color == 0xff66cc
            && fx.fx.idle.size() == 1 && fx.fx.idle.get(0).color == 0x33ccff, "effects recorded");
        BuildRecord fxRecord = BuildRecord.create("staff", "TEST", "script", fx);
        fxRecord.kind = "gear";
        fxRecord.gear = "staff";
        net.minecraft.world.item.ItemStack fxStack = io.github.foredayner.aibnf.ai.build.GearItems.stackFor(fxRecord);
        check(io.github.foredayner.aibnf.ai.build.GearEffects.of(fxStack).attack.size() == 2, "attack effects on the item");
        check(io.github.foredayner.aibnf.ai.build.GearItems.modelOf(fxStack).anims.size() == 4, "movements ride in the model");
        check(io.github.foredayner.aibnf.ai.build.GearItems.modelOf(fxStack).aura != null, "the aura rides in the model");
        check(io.github.foredayner.aibnf.ai.build.GearEffects.of(fxStack).idle.size() == 1, "idle effect on the item");
        io.github.foredayner.aibnf.ai.AiController.applyEffectRequest(fx, io.github.foredayner.aibnf.content.GearKind.STAFF, "", "");
        check(fx.fx.isEmpty() && fx.anims.isEmpty() && fx.aura == null,
            "empty effect fields = no effects at all");
        try {
            Demo.plan(world, origin, player, "ai.addSphere('stone', 0, 0.5, 0, 0.2)\nai.effect('attack', [shape: 'nuke'])");
            throw new GameTestAssertException("an unknown shape should be refused");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("unknown shape"), expected.getMessage());
        }

        // A greatsword taller than the player is fine without asking.
        BuildPlan great = Demo.plan(world, origin, player, Demo.GREATSWORD, true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(great, io.github.foredayner.aibnf.content.GearKind.SWORD) == null,
            "a greatsword fits: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(great, io.github.foredayner.aibnf.content.GearKind.SWORD));
        check(great.partBounds()[4] - great.partBounds()[1] > 2.5, "it really is long");

        // Too long for the hand: refused with a reason the AI can fix.
        BuildPlan pole = Demo.plan(world, origin, player, "ai.addCapsule('oak_planks', 0, 0, 0, 0, 4, 0, 0.05)");
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(pole) != null, "a 4-block pole is too long");
        context.succeed();
    }

    /** A sword's attack effect plays when it is swung (in front of the player), even at nothing. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public static void swingingASwordPlaysItsAttackEffect(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(4, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 0f); // facing south (+Z)
        BuildPlan plan = Demo.plan(world, origin, player, io.github.foredayner.aibnf.ai.AiController.EXAMPLE_SWORD
            + "\nai.effect('attack', [shape: 'magic_circle', color: '#9933ff', size: 1.5, plane: 'flat', start: 'ground', moves: ['spin', 'fade']])\n", true);
        plan.motion = "thrust";
        BuildRecord record = BuildRecord.create("swing test", "TEST", "script", plan);
        record.kind = "gear";
        record.gear = "sword";
        net.minecraft.world.item.ItemStack sword = io.github.foredayner.aibnf.ai.build.GearItems.stackFor(record);
        check("thrust".equals(io.github.foredayner.aibnf.content.GearMotions.of(sword)), "the sword keeps its attack motion");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        check(io.github.foredayner.aibnf.ai.build.GearEffects.onSwing(player) == 1, "the swing plays the attack effect");
        check(io.github.foredayner.aibnf.ai.build.GearEffects.onSwing(player) == 0, "a second swing too soon is ignored");
        context.succeed();
    }

    /** An AI bow shoots arrows that carry the bow (for their shape) and land like normal arrows. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public static void bowShootsItsOwnArrows(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = opPlayer(context, origin, 0f);
        BuildPlan plan = Demo.plan(world, origin, player, Demo.BOW, true);
        check(io.github.foredayner.aibnf.ai.AiController.gearProblem(plan, io.github.foredayner.aibnf.content.GearKind.BOW) == null,
            "the bow fits: " + io.github.foredayner.aibnf.ai.AiController.gearProblem(plan, io.github.foredayner.aibnf.content.GearKind.BOW));
        check(plan.fx.trail.size() == 1 && plan.fx.attack.size() == 1, "trail and landing effect recorded");
        check(plan.parts.stream().anyMatch(p -> p.group == BuildPlan.ARROW_GROUP), "arrow pieces recorded");
        io.github.foredayner.aibnf.ai.AiController.applyEffectRequest(plan, io.github.foredayner.aibnf.content.GearKind.BOW, "불꽃 화살", "");
        check(plan.fx.trail.size() == 1, "the arrow effect field keeps the trail");
        BuildRecord record = BuildRecord.create("bow test", "TEST", "script", plan);
        record.kind = "gear";
        record.gear = "bow";
        net.minecraft.world.item.ItemStack bow = io.github.foredayner.aibnf.ai.build.GearItems.stackFor(record);
        check(bow.getItem() instanceof net.minecraft.world.item.BowItem, "it is a bow");
        check(bow.is(net.minecraft.tags.ItemTags.BOW_ENCHANTABLE), "takes bow enchantments");
        var gearBow = (io.github.foredayner.aibnf.content.GearBowItem) bow.getItem();
        var arrow = gearBow.createProjectile(world, player, bow,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), false);
        check(arrow instanceof io.github.foredayner.aibnf.content.AibArrowEntity, "plain arrows become AI arrows");
        io.github.foredayner.aibnf.content.AibArrowEntity aib = (io.github.foredayner.aibnf.content.AibArrowEntity) arrow;
        check(io.github.foredayner.aibnf.ai.build.GearItems.modelOf(aib.bow()) != null, "the arrow carries the bow's model");
        var tipped = gearBow.createProjectile(world, player, bow,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPECTRAL_ARROW), false);
        check(!(tipped instanceof io.github.foredayner.aibnf.content.AibArrowEntity), "other arrows stay vanilla");
        aib.moveTo(origin.getX() + 0.5, origin.getY() + 2, origin.getZ() + 0.5, 0, 0);
        aib.setDeltaMovement(0, -1, 0.3);
        world.addFreshEntity(aib);
        context.runAfterDelay(40, () -> {
            check(aib.hasLanded(), "the arrow landed (and played its landing effect)");
            context.succeed();
        });
    }

    /**
     * The API other mods use: a building designed for a site (front on z = -1) is checked against
     * the site and its marked spots, then placed turned so its front faces east, with the marked
     * spots reported in world positions.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public static void apiPlacesASiteBuildingTurnedWithItsMarks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 180f); // the design frame looks north
        BuildPlan plan = Demo.plan(world, origin, player,
            "ai.placeBox(0, 0, -6, 5, 3, 5, 'oak_planks')\n"
                + "ai.placeBlock(2, 0, -1, 'oak_door[facing=south,half=lower]')\n"
                + "ai.placeBlock(2, 1, -1, 'oak_door[facing=south,half=upper]')\n"
                + "ai.placeBlock(2, 1, -4, 'bookshelf')\n"
                + "ai.mark('clue:diary', 2, 1, -4)\n");
        check(plan.marks.size() == 1 && "clue:diary".equals(plan.marks.get(0).name), "the mark is recorded");
        io.github.foredayner.aibnf.api.AiBuildApi.Site site = new io.github.foredayner.aibnf.api.AiBuildApi.Site(6, 7, 8);
        check(io.github.foredayner.aibnf.api.AiBuildApi.siteProblemForTests(plan, site) == null, "it fits the site");
        check(io.github.foredayner.aibnf.api.AiBuildApi.siteProblemForTests(plan, new io.github.foredayner.aibnf.api.AiBuildApi.Site(3, 7, 8)) != null,
            "a narrower site is refused");
        check(io.github.foredayner.aibnf.api.AiBuildApi.missingMarksForTests(plan, java.util.List.of("clue:diary")) == null, "the wanted mark is there");
        check(io.github.foredayner.aibnf.api.AiBuildApi.missingMarksForTests(plan, java.util.List.of("npc:mayor")) != null, "a missing mark is reported");

        io.github.foredayner.aibnf.config.AiBuilderConfig.get().ticksPerBlock = 1;
        BlockPos siteMin = context.absolutePos(new BlockPos(1, 1, 1));
        var placed = io.github.foredayner.aibnf.api.AiBuildApi.placeBuilding(world, plan, siteMin, site, "east", "aib_api_test",
            java.util.UUID.randomUUID(), null);
        check(placed.min().getX() == siteMin.getX() && placed.min().getZ() == siteMin.getZ(), "the footprint starts at the site corner");
        int[] size = io.github.foredayner.aibnf.api.AiBuildApi.footprint(site, "east");
        check(placed.max().getX() - placed.min().getX() + 1 == size[0] && placed.max().getZ() - placed.min().getZ() + 1 == size[1],
            "the footprint is turned: " + size[0] + "x" + size[1]);
        BlockPos diary = placed.marks().get("clue:diary").get(0);
        check(world.getBlockState(diary).is(Blocks.BOOKSHELF), "the marked spot is where the script put the bookshelf");
        int[] d = BuildPlan.rotate(2, -1, placed.turns());
        BlockPos door = placed.origin().offset(d[0], 0, d[1]);
        check(world.getBlockState(door).is(Blocks.OAK_DOOR), "the door was placed");
        check(door.getX() == placed.max().getX(), "the front (door) is on the east side");
        check(world.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.FACING) == Direction.EAST, "the door turned east");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void reviewRejectsBadScriptsBeforeRunning(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = opPlayer(context, origin, 0f);
        try {
            Demo.plan(world, origin, player, "ai.placeBox(0,0,0,'stone')");
            throw new GameTestAssertException("a wrong argument count should be rejected");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("placeBox got 4"), expected.getMessage());
        }
        context.succeed();
    }

    /**
     * Two operators build at the same time with slow placement. Each queued command must still run
     * as its own player at its own spot, and undo must stop only the undoing player's build.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public static void twoPlayersBuildAtOnceWithoutMixingUp(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos a = context.absolutePos(new BlockPos(2, 1, 7));
        BlockPos b = context.absolutePos(new BlockPos(7, 1, 7));
        ServerPlayer alice = opPlayer(context, a, 180f);
        ServerPlayer bob = opPlayer(context, b, 180f);
        check(!alice.getUUID().equals(bob.getUUID()), "two different players");
        BuildPlan planA = Demo.plan(world, a, alice, SMALL);
        BuildPlan planB = Demo.plan(world, b, bob, SMALL);
        BuildPlan planC = Demo.plan(world, a, alice, SMALL);

        io.github.foredayner.aibnf.config.AiBuilderConfig.get().ticksPerBlock = 2;
        // Alice starts, then Bob starts before Alice's commands have run (this used to make
        // Alice's remaining commands run as Bob).
        UndoService.capture(alice, planA, a, 0, "aib_twoA", "A");
        io.github.foredayner.aibnf.ai.api.MinecraftAI.init(world, a, alice.getYRot(), alice.getXRot(), alice);
        io.github.foredayner.aibnf.ai.api.MinecraftAI.executePlan(planA, a, 0, "aib_twoA", "A");
        UndoService.capture(bob, planB, b, 0, "aib_twoB", "B");
        io.github.foredayner.aibnf.ai.api.MinecraftAI.init(world, b, bob.getYRot(), bob.getXRot(), bob);
        io.github.foredayner.aibnf.ai.api.MinecraftAI.executePlan(planB, b, 0, "aib_twoB", "B");
        check(world.getBlockState(a.offset(0, 0, -4)).isAir() && world.getBlockState(b.offset(0, 0, -4)).isAir(),
            "slow placement has not run yet");
        // Alice undoes right away: her build stops, Bob's keeps going.
        UndoService.undo(alice);
        check(io.github.foredayner.aibnf.ai.build.BuildTaskQueue.pendingCount() > 0, "Bob's build is still queued");

        context.runAtTickTime(40, () -> {
            check(world.getBlockState(b.offset(0, 0, -4)).is(Blocks.STONE_BRICKS), "Bob's box was built at Bob's spot");
            check(world.getBlockState(b.offset(1, 2, -3)).is(Blocks.OAK_STAIRS), "Bob's stairs too");
            check(world.getBlockState(a.offset(0, 0, -4)).isAir(), "Alice's cancelled build did not appear");
            // Now both build at once again; both finish, each at their own place.
            UndoService.capture(alice, planC, a, 0, "aib_twoC", "C");
            io.github.foredayner.aibnf.ai.api.MinecraftAI.init(world, a, alice.getYRot(), alice.getXRot(), alice);
            io.github.foredayner.aibnf.ai.api.MinecraftAI.executePlan(planC, a, 0, "aib_twoC", "C");
            io.github.foredayner.aibnf.ai.api.MinecraftAI.init(world, b, bob.getYRot(), bob.getXRot(), bob);
        });
        context.runAtTickTime(80, () -> {
            check(world.getBlockState(a.offset(0, 0, -4)).is(Blocks.STONE_BRICKS), "Alice's second build finished");
            check(world.getBlockState(a.offset(1, 2, -3)).is(Blocks.OAK_STAIRS), "with its stairs");
            io.github.foredayner.aibnf.config.AiBuilderConfig.get().ticksPerBlock = 1;
            context.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void onlyOperatorsCanUseTheWorkshop(GameTestHelper context) {
        ServerPlayer guest = context.makeMockServerPlayerInLevel();
        check(!io.github.foredayner.aibnf.network.AiNetworking.allowed(guest), "a non-operator is turned away");
        ServerPlayer op = opPlayer(context, context.absolutePos(new BlockPos(1, 1, 1)), 0f);
        check(io.github.foredayner.aibnf.network.AiNetworking.allowed(op), "an operator may use it");
        var root = context.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("aibuild");
        check(root != null, "/aibuild exists");
        check(!root.canUse(guest.createCommandSourceStack()), "/aibuild is hidden from non-operators");
        check(root.canUse(op.createCommandSourceStack()), "/aibuild works for operators");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void blueprintPaperBuildsWhereClicked(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(4, 1, 6));
        ServerPlayer player = opPlayer(context, origin, 180f); // facing north
        BuildPlan plan = Demo.plan(world, origin, player, SMALL);
        BuildRecord record = BuildRecord.create("blueprint", "TEST", "script", plan);
        io.github.foredayner.aibnf.config.AiBuilderConfig.get().ticksPerBlock = 1;

        io.github.foredayner.aibnf.ai.build.BlueprintStore.give(player, record);
        net.minecraft.world.item.ItemStack paper = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (io.github.foredayner.aibnf.ai.build.BlueprintStore.idOf(player.getInventory().getItem(i)) != null) {
                paper = player.getInventory().getItem(i);
            }
        }
        check(!paper.isEmpty(), "the player got a blueprint paper");
        check(record.id.equals(io.github.foredayner.aibnf.ai.build.BlueprintStore.idOf(paper)), "paper points at the record");

        BlockPos target = origin.offset(0, 0, -2);
        check(io.github.foredayner.aibnf.ai.build.BlueprintStore.use(player, paper, target.below(), Direction.NORTH), "side click handled");
        check(world.getBlockState(target).isAir(), "clicking the side of a block builds nothing");
        check(io.github.foredayner.aibnf.ai.build.BlueprintStore.use(player, paper, target.below(), Direction.UP), "blueprint use handled");
        // The middle of the box's near (south) edge lands on the target; the box runs north from it.
        check(world.getBlockState(target).is(Blocks.STONE_BRICKS), "near edge sits on the clicked spot");
        check(world.getBlockState(target.offset(-1, 0, -2)).is(Blocks.STONE_BRICKS), "far corner north of it");
        check(world.getBlockState(target.offset(0, 0, 1)).isAir(), "nothing behind the clicked spot");
        check(!paper.isEmpty(), "creative players keep the paper");
        UndoService.undo(player);
        check(world.getBlockState(target).isAir(), "undo removes a blueprint build too");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void objectItemSitsOnTheClickedBlockAndMinesBack(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = opPlayer(context, origin, 0f); // facing south
        BuildRecord record = BuildRecord.create("apple", "TEST", "script", Demo.plan(world, origin, player, Demo.APPLE));
        record.icon = new int[16 * 16];
        record.icon[200] = 0xFFCC2222;

        io.github.foredayner.aibnf.ai.build.BlueprintStore.give(player, record);
        net.minecraft.world.item.ItemStack item = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (io.github.foredayner.aibnf.ai.build.ObjectItems.idOf(player.getInventory().getItem(i)) != null) {
                item = player.getInventory().getItem(i);
            }
        }
        check(!item.isEmpty() && item.is(io.github.foredayner.aibnf.content.AibContent.OBJECT_ITEM), "an object comes as an object item");
        check(io.github.foredayner.aibnf.ai.build.BlueprintStore.idOf(item) == null, "not a blueprint paper");
        var data = io.github.foredayner.aibnf.content.ItemData.get(item);
        check(data.getIntArray("aib_icon").length == 256 && data.getIntArray("aib_icon")[200] == 0xFFCC2222, "the icon picture rides on the item");

        // The mock player is always in creative: the item is kept (survival takes one, like blocks).
        BlockPos clicked = origin.offset(0, -1, 3);
        int before = item.getCount();
        check(io.github.foredayner.aibnf.ai.build.ObjectItems.use(player, item, clicked), "placed from the item");
        check(item.getCount() == before, "creative keeps the item");
        net.minecraft.world.item.ItemStack placedFrom = item;

        context.runAfterDelay(60, () -> {
            AABB area = new AABB(clicked).inflate(4);
            var boxes = world.getEntitiesOfClass(io.github.foredayner.aibnf.content.SolidHitboxEntity.class, area, e -> true);
            check(!boxes.isEmpty(), "solid hitboxes placed");
            AABB all = boxes.stream().map(net.minecraft.world.entity.Entity::getBoundingBox).reduce(AABB::minmax).get();
            check(Math.abs(all.minY - (clicked.getY() + 1)) < 0.05, "sits on top of the clicked block: " + all.minY);
            check(Math.abs(all.getCenter().x - (clicked.getX() + 0.5)) < 0.15
                && Math.abs(all.getCenter().z - (clicked.getZ() + 0.5)) < 0.15, "centred over the clicked block: " + all.getCenter());
            check(!world.getEntityCollisions(player, all.deflate(0.02)).isEmpty(), "the object is solid");
            check(boxes.stream().allMatch(b -> b.getTags().contains("aibid_" + record.id)), "hitboxes know which item to drop");

            io.github.foredayner.aibnf.ai.build.ObjectItems.breakObject(player, boxes.get(0).getId(), true); // as in survival
            context.runAfterDelay(2, () -> {
                int left = world.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                    e -> e.getTags().contains("aib")).size();
                check(left == 0, "mining removed every part and hitbox, " + left + " left");
                var drops = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area, e -> true);
                check(drops.size() == 1 && record.id.equals(io.github.foredayner.aibnf.ai.build.ObjectItems.idOf(drops.get(0).getItem())),
                    "mining drops the object item, got " + drops.size());
                check(placedFrom.getCount() == before, "count unchanged");
                context.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void placedBuildsShowTheirFrontToThePlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos origin = context.absolutePos(new BlockPos(1, 1, 4));
        ServerPlayer player = opPlayer(context, origin, 0f); // designed facing south: door on the near side
        BuildPlan plan = Demo.plan(world, origin, player, Demo.HOUSE);
        check("south".equals(plan.facing), "designed facing south, got " + plan.facing);
        BuildRecord record = BuildRecord.create("house", "TEST", "script", plan);
        io.github.foredayner.aibnf.config.AiBuilderConfig.get().ticksPerBlock = 1;

        player.setYRot(-90f); // now facing east
        io.github.foredayner.aibnf.ai.build.RebuildService.placeInFront(player, record);
        BlockState door = world.getBlockState(origin.offset(4, 0, 0));
        check(door.is(Blocks.OAK_DOOR), "door on the side nearest the player, got " + door);
        check(door.getValue(net.minecraft.world.level.block.DoorBlock.FACING) == Direction.WEST, "door faces back toward the player: " + door);
        check(world.getBlockState(origin.offset(4, 1, 3)).is(Blocks.STONE_BRICKS), "the house is east of the player");
        UndoService.undo(player);
        context.succeed();
    }
}
