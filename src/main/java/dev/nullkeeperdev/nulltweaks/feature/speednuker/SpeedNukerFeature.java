/* SPDX-License-Identifier: GPL-3.0-only */
package dev.nullkeeperdev.nulltweaks.feature.speednuker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.feature.quarry.QuarryFeature;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import dev.nullkeeperdev.nulltweaks.util.ClientScreenState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SpeedNukerFeature extends Feature {
    private static final int DEFAULT_MAX_BLOCKS_PER_TICK = 8;
    private static final int MIN_MAX_BLOCKS_PER_TICK = 2;
    private static final int MAX_MAX_BLOCKS_PER_TICK = 64;
    private static final double DEFAULT_REACH_DISTANCE = 3.0D;
    private static final double MIN_REACH_DISTANCE = 1.0D;
    private static final double MAX_REACH_DISTANCE = 6.0D;
    private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.speed_nuker.toggle",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));
    private static final SuggestionProvider<FabricClientCommandSource> BLOCK_SUGGESTIONS = SpeedNukerFeature::suggestBlocks;
    private static SpeedNukerFeature instance;
    private static boolean commandsRegistered;

    private boolean useWithQuarry = true;
    private int maxBlocksPerTick = DEFAULT_MAX_BLOCKS_PER_TICK;
    private double reachDistance = DEFAULT_REACH_DISTANCE;
    private MiningMode miningMode = MiningMode.FULL;
    private BlockListMode blockListMode = BlockListMode.BLACKLIST;
    private final Set<Identifier> blockList = new HashSet<>();
    private boolean activeForSession;

    public SpeedNukerFeature() {
        super("speed_nuker", "Speed Nuker");
        instance = this;
    }

    public static void registerCommands() {
        if (commandsRegistered) {
            return;
        }

        commandsRegistered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommandTree(dispatcher));
    }

    public static boolean isHandlingQuarry() {
        SpeedNukerFeature feature = instance;
        return feature != null
                && feature.isEnabled()
                && feature.activeForSession
                && feature.useWithQuarry
                && QuarryFeature.isRunning()
                && !ClientScreenState.hasOpenScreen(Minecraft.getInstance());
    }

    public static double quarryTargetReachDistance(double quarryReachDistance) {
        SpeedNukerFeature feature = instance;
        if (feature == null || !isHandlingQuarry()) {
            return quarryReachDistance;
        }
        return Math.min(quarryReachDistance, feature.reachDistance);
    }

    public static boolean shouldSkipQuarryTarget(BlockPos pos) {
        SpeedNukerFeature feature = instance;
        Minecraft client = Minecraft.getInstance();
        return feature != null
                && isHandlingQuarry()
                && client.player != null
                && !feature.miningMode.allows(client.player.blockPosition(), pos);
    }

    @Override
    public void onEnable() {
        activeForSession = true;
    }

    @Override
    public void onDisable() {
        activeForSession = false;
    }

    @Override
    public boolean listensForClientTicks() {
        return true;
    }

    @Override
    public boolean listensForGlobalClientTicks() {
        return true;
    }

    @Override
    public void onGlobalClientTick(Minecraft client) {
        while (TOGGLE_KEY.consumeClick()) {
            boolean enabled = !isEnabled();
            FeatureManager.INSTANCE.setEnabled(id(), enabled);
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("Speed Nuker " + (enabled ? "enabled." : "disabled.")));
            }
        }
    }

    @Override
    public void onClientTick(Minecraft client) {
        if (ClientScreenState.hasOpenScreen(client)
                || client.level == null
                || client.player == null
                || client.getConnection() == null) {
            return;
        }

        boolean useQuarryRules = useWithQuarry && QuarryFeature.isEnabledForSpeedNuker();
        packetMineNearbyTargets(client, useQuarryRules);
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(useWithQuarryOption())
                .option(miningModeOption())
                .option(reachDistanceOption())
                .option(maxBlocksPerTickOption())
                .option(blockListModeOption())
                .option(blockListOption())
                .option(keybindButton());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        useWithQuarry = NullTweaksConfig.getBoolean(config, "useWithQuarry", true);
        maxBlocksPerTick = clampInt(
                NullTweaksConfig.getInt(config, "maxBlocksPerTick", DEFAULT_MAX_BLOCKS_PER_TICK),
                MIN_MAX_BLOCKS_PER_TICK,
                MAX_MAX_BLOCKS_PER_TICK);
        reachDistance = snapReach(NullTweaksConfig.getDouble(config, "reachDistance", DEFAULT_REACH_DISTANCE));
        miningMode = MiningMode.fromConfig(
                NullTweaksConfig.getString(config, "miningMode", MiningMode.FULL.configValue()));
        blockListMode = BlockListMode.fromConfig(
                NullTweaksConfig.getString(config, "blockListMode", BlockListMode.BLACKLIST.configValue()));
        blockList.clear();
        JsonArray blockListJson = config.getAsJsonArray("blockList");
        if (blockListJson != null) {
            for (JsonElement element : blockListJson) {
                if (element.isJsonPrimitive()) {
                    Identifier id = Identifier.tryParse(element.getAsString());
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                        blockList.add(id);
                    }
                }
            }
        }
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("useWithQuarry", useWithQuarry);
        config.addProperty("maxBlocksPerTick", maxBlocksPerTick);
        config.addProperty("reachDistance", reachDistance);
        config.addProperty("miningMode", miningMode.configValue());
        config.addProperty("blockListMode", blockListMode.configValue());
        JsonArray blockListJson = new JsonArray();
        blockList.stream().map(Identifier::toString).sorted().forEach(blockListJson::add);
        config.add("blockList", blockListJson);
    }

    private static void registerCommandTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("speednuker")
                .then(ClientCommands.literal("on")
                        .executes(context -> command(context, feature -> feature.setEnabled(context.getSource(), true))))
                .then(ClientCommands.literal("off")
                        .executes(context -> command(context, feature -> feature.setEnabled(context.getSource(), false))))
                .then(ClientCommands.literal("quarry")
                        .then(ClientCommands.literal("on")
                                .executes(context -> command(context, feature -> feature.setUseWithQuarry(context.getSource(), true))))
                        .then(ClientCommands.literal("off")
                                .executes(context -> command(context, feature -> feature.setUseWithQuarry(context.getSource(), false)))))
                .then(ClientCommands.literal("max")
                        .then(ClientCommands.argument("blocks", IntegerArgumentType.integer(
                                        MIN_MAX_BLOCKS_PER_TICK,
                                        MAX_MAX_BLOCKS_PER_TICK))
                                .executes(context -> command(context, feature -> feature.setMaxBlocksPerTick(context)))))
                .then(ClientCommands.literal("reach")
                        .then(ClientCommands.argument("blocks", DoubleArgumentType.doubleArg(
                                        MIN_REACH_DISTANCE,
                                        MAX_REACH_DISTANCE))
                                .executes(context -> command(context, feature -> feature.setReachDistance(context)))))
                .then(ClientCommands.literal("miningmode")
                        .then(ClientCommands.literal("protect_below")
                                .executes(context -> command(context, feature -> feature.setMiningMode(context.getSource(), MiningMode.PROTECT_BELOW))))
                        .then(ClientCommands.literal("protect_y_level")
                                .executes(context -> command(context, feature -> feature.setMiningMode(context.getSource(), MiningMode.PROTECT_Y_LEVEL))))
                        .then(ClientCommands.literal("full")
                                .executes(context -> command(context, feature -> feature.setMiningMode(context.getSource(), MiningMode.FULL)))))
                .then(ClientCommands.literal("mode")
                        .then(ClientCommands.literal("blacklist")
                                .executes(context -> command(context, feature -> feature.setBlockListMode(context.getSource(), BlockListMode.BLACKLIST))))
                        .then(ClientCommands.literal("whitelist")
                                .executes(context -> command(context, feature -> feature.setBlockListMode(context.getSource(), BlockListMode.WHITELIST)))))
                .then(ClientCommands.literal("blocklist")
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests(BLOCK_SUGGESTIONS)
                                        .executes(context -> command(context, feature -> feature.addBlockList(context)))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests(BLOCK_SUGGESTIONS)
                                        .executes(context -> command(context, feature -> feature.removeBlockList(context)))))
                        .then(ClientCommands.literal("list")
                                .executes(context -> command(context, feature -> feature.listBlockList(context.getSource()))))));
    }

    private static int command(CommandContext<FabricClientCommandSource> context, SpeedNukerCommand command) {
        SpeedNukerFeature feature = instance;
        if (feature == null) {
            context.getSource().sendError(Component.literal("Null Tweaks is not initialized yet."));
            return 0;
        }
        return command.run(feature);
    }

    private int setEnabled(FabricClientCommandSource source, boolean enabled) {
        FeatureManager.INSTANCE.setEnabled(id(), enabled);
        source.sendFeedback(Component.literal("Speed Nuker " + (enabled ? "enabled." : "disabled.")));
        return 1;
    }

    private int setUseWithQuarry(FabricClientCommandSource source, boolean enabled) {
        useWithQuarry = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Speed Nuker Quarry integration " + (enabled ? "enabled." : "disabled.")));
        return 1;
    }

    private int setMaxBlocksPerTick(CommandContext<FabricClientCommandSource> context) {
        maxBlocksPerTick = IntegerArgumentType.getInteger(context, "blocks");
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal(
                "Speed Nuker maximum set to " + maxBlocksPerTick + " blocks per tick."));
        return 1;
    }

    private int setReachDistance(CommandContext<FabricClientCommandSource> context) {
        reachDistance = snapReach(DoubleArgumentType.getDouble(context, "blocks"));
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal(
                "Speed Nuker reach set to " + formatReach(reachDistance) + " blocks."));
        return 1;
    }

    private int setMiningMode(FabricClientCommandSource source, MiningMode mode) {
        miningMode = mode;
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Speed Nuker mining mode set to " + mode.displayName() + "."));
        return 1;
    }

    private int setBlockListMode(FabricClientCommandSource source, BlockListMode mode) {
        blockListMode = mode;
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Speed Nuker block list mode set to " + mode.displayName() + "."));
        return 1;
    }

    private int addBlockList(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null) {
            context.getSource().sendError(Component.literal("Unknown block."));
            return 0;
        }
        blockList.add(id);
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Added " + id + " to the Speed Nuker block list."));
        return 1;
    }

    private int removeBlockList(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null || !blockList.remove(id)) {
            context.getSource().sendError(Component.literal("That block is not in the Speed Nuker block list."));
            return 0;
        }
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Removed " + id + " from the Speed Nuker block list."));
        return 1;
    }

    private int listBlockList(FabricClientCommandSource source) {
        if (blockList.isEmpty()) {
            source.sendFeedback(Component.literal(
                    "Speed Nuker block list is empty. Mode: " + blockListMode.displayName() + "."));
            return 1;
        }

        String blocks = blockList.stream()
                .map(Identifier::toString)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        source.sendFeedback(Component.literal(
                "Speed Nuker block list (" + blockListMode.displayName() + "): " + blocks));
        return 1;
    }

    private void packetMineNearbyTargets(Minecraft client, boolean useQuarryRules) {
        ClientLevel level = client.level;
        ItemStack selectedTool = client.player.getMainHandItem();
        Vec3 eyes = client.player.getEyePosition();
        BlockPos eyeBlock = BlockPos.containing(eyes);
        BlockPos playerBlock = client.player.blockPosition();
        int scanRadius = (int) Math.ceil(reachDistance) + 1;
        List<BlockBreakParameters> candidates = new ArrayList<>();

        for (int dy = -scanRadius; dy <= scanRadius; dy++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                    BlockPos pos = eyeBlock.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!isEligibleTarget(level, pos, state, selectedTool, playerBlock, useQuarryRules)) {
                        continue;
                    }

                    BlockBreakParameters parameters = blockBreakParameters(level, eyes, pos);
                    if (parameters != null && parameters.distanceSqr() <= reachDistance * reachDistance) {
                        candidates.add(parameters);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(BlockBreakParameters::distanceSqr));
        int mined = 0;
        for (BlockBreakParameters parameters : candidates) {
            if (mined >= maxBlocksPerTick) {
                break;
            }

            client.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    parameters.pos(),
                    parameters.side()));
            client.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    parameters.pos(),
                    parameters.side()));
            mined++;
        }
    }

    private boolean isEligibleTarget(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack selectedTool,
            BlockPos playerBlock,
            boolean useQuarryRules) {
        if (state.isAir()
                || state.liquid()
                || state.getDestroySpeed(level, pos) < 0.0F
                || !isMatchingTool(selectedTool, state)
                || !miningMode.allows(playerBlock, pos)) {
            return false;
        }

        if (useQuarryRules) {
            return QuarryFeature.allowsSpeedNukerTarget(level, pos, state);
        }

        boolean listed = blockList.contains(blockId(state));
        return blockListMode == BlockListMode.BLACKLIST ? !listed : listed;
    }

    private Option<Boolean> useWithQuarryOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Use with Quarry"))
                .description(description("When Quarry is enabled, uses Quarry's block list. While Quarry is running, Speed Nuker also stays inside its selection and replaces its normal mining action."))
                .binding(true, () -> useWithQuarry, value -> {
                    useWithQuarry = value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<Integer> maxBlocksPerTickOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Blocks per tick"))
                .description(description("Maximum eligible blocks attempted each tick. Each block sends START and STOP packets. High values can disconnect you or get you banned. This feature does not try to bypass or evade detection."))
                .binding(DEFAULT_MAX_BLOCKS_PER_TICK, () -> maxBlocksPerTick, value -> {
                    maxBlocksPerTick = clampInt(value, MIN_MAX_BLOCKS_PER_TICK, MAX_MAX_BLOCKS_PER_TICK);
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(MIN_MAX_BLOCKS_PER_TICK, MAX_MAX_BLOCKS_PER_TICK)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + " blocks")))
                .instant(true)
                .build();
    }

    private Option<MiningMode> miningModeOption() {
        return Option.<MiningMode>createBuilder()
                .name(Component.literal("Mining mode"))
                .description(description("Protect Below skips only the block directly beneath your feet. Protect Y Level skips every block below your current block Y. Full applies no positional protection."))
                .binding(MiningMode.FULL, () -> miningMode, value -> {
                    miningMode = value == null ? MiningMode.FULL : value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(MiningMode.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private Option<Double> reachDistanceOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Reach"))
                .description(description("Speed Nuker packet-mining reach. Servers may reject or flag values beyond their allowed interaction distance."))
                .binding(DEFAULT_REACH_DISTANCE, () -> reachDistance, value -> {
                    reachDistance = snapReach(value);
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(MIN_REACH_DISTANCE, MAX_REACH_DISTANCE)
                        .step(0.5D)
                        .valueFormatter(value -> Component.literal(formatReach(value) + " blocks")))
                .instant(true)
                .build();
    }

    private Option<BlockListMode> blockListModeOption() {
        return Option.<BlockListMode>createBuilder()
                .name(Component.literal("Standalone block list mode"))
                .description(description("Absolute standalone filter: blacklist never mines listed blocks; whitelist never mines unlisted blocks. Quarry's list is used instead when Quarry integration is active."))
                .binding(BlockListMode.BLACKLIST, () -> blockListMode, value -> {
                    blockListMode = value == null ? BlockListMode.BLACKLIST : value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(BlockListMode.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private Option<String> blockListOption() {
        return Option.<String>createBuilder()
                .name(Component.literal("Standalone block list ids"))
                .description(description("Comma-separated block ids used when Quarry integration is not active. Example: minecraft:diamond_ore, ancient_debris"))
                .binding("", this::blockListConfigValue, this::setBlockListConfigValue)
                .controller(StringControllerBuilder::create)
                .instant(true)
                .build();
    }

    private ButtonOption keybindButton() {
        return ButtonOption.createBuilder()
                .name(Component.literal("Toggle keybind"))
                .description(description("Opens Minecraft's keybind screen so you can bind the Speed Nuker on/off toggle."))
                .text(TOGGLE_KEY.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                .build();
    }

    private String blockListConfigValue() {
        return blockList.stream()
                .map(Identifier::toString)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private void setBlockListConfigValue(String value) {
        blockList.clear();
        if (value != null && !value.isBlank()) {
            for (String raw : value.split(",")) {
                Identifier id = resolveBlockId(raw.trim());
                if (id != null) {
                    blockList.add(id);
                }
            }
        }
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static BlockBreakParameters blockBreakParameters(ClientLevel level, Vec3 eyes, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D) : shape.bounds();
        Vec3 center = Vec3.atLowerCornerOf(pos).add(box.getCenter());
        Vec3 halfSize = new Vec3(
                (box.maxX - box.minX) * 0.5D,
                (box.maxY - box.minY) * 0.5D,
                (box.maxZ - box.minZ) * 0.5D);
        double centerDistanceSqr = eyes.distanceToSqr(center);

        Direction bestSide = null;
        Vec3 bestHit = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            Vec3 direction = Vec3.atLowerCornerOf(side.getUnitVec3i());
            Vec3 hit = center.add(
                    halfSize.x * direction.x,
                    halfSize.y * direction.y,
                    halfSize.z * direction.z);
            double distanceSqr = eyes.distanceToSqr(hit);
            if (distanceSqr < centerDistanceSqr && distanceSqr < bestDistanceSqr) {
                bestSide = side;
                bestHit = hit;
                bestDistanceSqr = distanceSqr;
            }
        }

        if (bestSide == null) {
            bestSide = directionToward(eyes, center);
            bestHit = center;
            bestDistanceSqr = centerDistanceSqr;
        }
        return new BlockBreakParameters(pos.immutable(), bestSide, bestHit, bestDistanceSqr);
    }

    private static boolean isMatchingTool(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) {
            return false;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return stack.is(ItemTags.PICKAXES);
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return stack.is(ItemTags.AXES);
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return stack.is(ItemTags.SHOVELS);
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return stack.is(ItemTags.HOES);
        }
        if (state.is(BlockTags.SWORD_EFFICIENT) || state.is(BlockTags.SWORD_INSTANTLY_MINES)) {
            return stack.is(ItemTags.SWORDS);
        }
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }

    private static Direction directionToward(Vec3 eyes, Vec3 center) {
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);
        if (absY >= absX && absY >= absZ) {
            return dy > 0.0D ? Direction.DOWN : Direction.UP;
        }
        if (absX >= absZ) {
            return dx > 0.0D ? Direction.WEST : Direction.EAST;
        }
        return dz > 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static Identifier blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static Identifier resolveBlockId(String raw) {
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return id;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBlocks(
            CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {
        List<Identifier> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ids.add(BuiltInRegistries.BLOCK.getKey(block));
        }
        ids.stream().sorted(Comparator.comparing(Identifier::toString)).forEach(id -> {
            builder.suggest(id.toString());
            if ("minecraft".equals(id.getNamespace())) {
                builder.suggest(id.getPath());
            }
        });
        return builder.buildFuture();
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double snapReach(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_REACH_DISTANCE;
        }
        double clamped = clampDouble(value, MIN_REACH_DISTANCE, MAX_REACH_DISTANCE);
        return Math.round(clamped * 2.0D) / 2.0D;
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatReach(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @FunctionalInterface
    private interface SpeedNukerCommand {
        int run(SpeedNukerFeature feature);
    }

    private enum MiningMode {
        PROTECT_BELOW("Protect Below", "protect_below") {
            @Override
            boolean allows(BlockPos playerBlock, BlockPos target) {
                return !target.equals(playerBlock.below());
            }
        },
        PROTECT_Y_LEVEL("Protect Y Level", "protect_y_level") {
            @Override
            boolean allows(BlockPos playerBlock, BlockPos target) {
                return target.getY() >= playerBlock.getY();
            }
        },
        FULL("Full", "full") {
            @Override
            boolean allows(BlockPos playerBlock, BlockPos target) {
                return true;
            }
        };

        private final String displayName;
        private final String configValue;

        MiningMode(String displayName, String configValue) {
            this.displayName = displayName;
            this.configValue = configValue;
        }

        abstract boolean allows(BlockPos playerBlock, BlockPos target);

        private String displayName() {
            return displayName;
        }

        private String configValue() {
            return configValue;
        }

        private static MiningMode fromConfig(String value) {
            for (MiningMode mode : values()) {
                if (mode.configValue.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return FULL;
        }
    }

    private enum BlockListMode {
        BLACKLIST("Blacklist"),
        WHITELIST("Whitelist");

        private final String displayName;

        BlockListMode(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static BlockListMode fromConfig(String value) {
            for (BlockListMode mode : values()) {
                if (mode.configValue().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return BLACKLIST;
        }
    }

    private record BlockBreakParameters(BlockPos pos, Direction side, Vec3 hit, double distanceSqr) {
    }
}
