package dev.nullkeeperdev.nulltweaks.feature.quarry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
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
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.feature.speednuker.SpeedNukerFeature;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import dev.nullkeeperdev.nulltweaks.util.ClientScreenState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class QuarryFeature extends Feature {
    private static final int DEFAULT_LARGE_TASK_THRESHOLD = 50_000;
    private static final int DEFAULT_DURABILITY_THRESHOLD_PERCENT = 10;
    private static final int MIN_SPHERE_RADIUS = 1;
    private static final int MAX_SPHERE_RADIUS = 64;
    private static final double REACH_DISTANCE = 3.0D;
    private static final double NAVIGATION_PROGRESS_EPSILON_SQR = 0.25D;
    private static final int NAVIGATION_CANDIDATE_RADIUS = 4;
    private static final int NAVIGATION_STUCK_TICKS = 160;
    private static final float OVERLAY_LINE_WIDTH = 1.0F;
    private static final int SPHERE_OVERLAY_SEGMENTS = 96;
    private static final Color DEFAULT_OVERLAY_COLOR = new Color(0x26C6DA);
    private static final RenderType OVERLAY_RENDER_TYPE = RenderTypes.lines();
    private static final KeyMapping START_RESUME_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.quarry.start_resume",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));
    private static final KeyMapping PAUSE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.quarry.pause",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));
    private static final KeyMapping STOP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.quarry.stop",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));
    private static final SuggestionProvider<FabricClientCommandSource> BLOCK_SUGGESTIONS = QuarryFeature::suggestBlocks;
    private static QuarryFeature instance;
    private static boolean commandsRegistered;
    private static boolean missingBaritoneWarningHooksRegistered;

    private BlockPos pos1;
    private BlockPos pos2;
    private SelectionShape selectionShape = SelectionShape.BOX;
    private BlockPos sphereCenter;
    private int sphereRadius = MIN_SPHERE_RADIUS;
    private TaskState taskState = TaskState.STOPPED;
    private int cursorX;
    private int cursorY;
    private int cursorZ;
    private BlockPos currentTarget;
    private BlockPos breakingTarget;
    private BlockPos navigationTarget;
    private double bestNavigationDistanceSqr = Double.MAX_VALUE;
    private int navigationNoProgressTicks;
    private Color overlayColor = DEFAULT_OVERLAY_COLOR;
    private boolean durabilityGuardEnabled = true;
    private int durabilityThresholdPercent = DEFAULT_DURABILITY_THRESHOLD_PERCENT;
    private PlayerProximityAction playerProximityAction = PlayerProximityAction.PAUSE;
    private String alertSoundId = defaultAlertSoundId();
    private InventoryFullMode inventoryFullMode = InventoryFullMode.PAUSE;
    private BlockListMode blockListMode = BlockListMode.BLACKLIST;
    private boolean baritoneChatLoggingEnabled;
    private int largeTaskThreshold = DEFAULT_LARGE_TASK_THRESHOLD;
    private final Set<Identifier> blockList = new HashSet<>();
    private final Set<BlockPos> skippedTargets = new HashSet<>();
    private boolean missingBaritoneNotified;
    private boolean showMissingBaritoneWarning = true;
    private int pendingMissingBaritoneWarningTicks;
    private boolean runtimeDisabled;
    private boolean pausedForScreen;

    public QuarryFeature() {
        super("quarry", "Quarry");
        instance = this;
    }

    public static boolean isRunning() {
        QuarryFeature feature = instance;
        return feature != null && feature.isEnabled() && !feature.runtimeDisabled && feature.taskState == TaskState.RUNNING;
    }

    public static boolean isEnabledForSpeedNuker() {
        QuarryFeature feature = instance;
        return feature != null && feature.isEnabled() && !feature.runtimeDisabled;
    }

    public static boolean allowsSpeedNukerTarget(ClientLevel level, BlockPos pos, BlockState state) {
        QuarryFeature feature = instance;
        if (feature == null || !isEnabledForSpeedNuker() || feature.isSkippedByBlockList(state)) {
            return false;
        }

        if (!isRunning()) {
            return true;
        }

        Bounds bounds = feature.bounds();
        return bounds != null && !feature.isUnavailableTarget(level, bounds, pos);
    }

    public static boolean shouldSuppressBaritoneMessage(Component message) {
        QuarryFeature feature = instance;
        return feature != null && !feature.baritoneChatLoggingEnabled && isBaritoneMessage(message);
    }

    public static boolean shouldPreventBlockBreaking(BlockPos pos) {
        QuarryFeature feature = instance;
        Minecraft client = Minecraft.getInstance();
        if (feature == null || !isRunning() || client.level == null || client.player == null) {
            return false;
        }

        try {
            BlockState state = client.level.getBlockState(pos);
            if (state.isAir()) {
                return false;
            }

            BlockBreakParameters parameters = blockBreakParameters(client.level, client.player.getEyePosition(), pos);
            return feature.isSkippedByBlockList(state)
                    || parameters == null
                    || parameters.distanceSqr() > REACH_DISTANCE * REACH_DISTANCE;
        } catch (RuntimeException exception) {
            feature.runtimeDisabled = true;
            feature.taskState = TaskState.PAUSED;
            feature.pauseMovement();
            NullTweaksClient.LOGGER.error(
                    "Disabling feature {} for this session after failure while enforcing block-breaking rules",
                    feature.id(),
                    exception);
            return false;
        }
    }

    public static void registerCommands() {
        if (commandsRegistered) {
            return;
        }

        commandsRegistered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommandTree(dispatcher));
    }

    public static void registerMissingBaritoneWarningHooks() {
        if (missingBaritoneWarningHooksRegistered) {
            return;
        }

        missingBaritoneWarningHooksRegistered = true;
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            QuarryFeature feature = instance;
            if (feature != null) {
                feature.queueMissingBaritoneWarning();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            QuarryFeature feature = instance;
            if (feature != null) {
                feature.tickMissingBaritoneWarning(client);
            }
        });
    }

    @Override
    public void onEnable() {
        runtimeDisabled = false;
    }

    @Override
    public void onDisable() {
        pauseMovement();
        pausedForScreen = false;
        runtimeDisabled = false;
    }

    @Override
    public boolean listensForClientTicks() {
        return true;
    }

    @Override
    public void onClientTick(Minecraft client) {
        if (runtimeDisabled) {
            return;
        }

        try {
            tick(client);
        } catch (RuntimeException exception) {
            runtimeDisabled = true;
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            NullTweaksClient.LOGGER.error("Disabling feature {} for this session after failure in client tick", id(), exception);
            sendPlayerMessage(client, "Quarry disabled for this session after an internal error. Check the log for details.");
        }
    }

    @Override
    public boolean listensForWorldRender() {
        return true;
    }

    @Override
    public void onWorldRender(LevelRenderContext context) {
        if (runtimeDisabled) {
            return;
        }

        Bounds bounds = bounds();
        if (bounds == null) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        int color = overlayColor.getRGB();
        int alpha = 255;
        SelectionShape shape = selectionShape;
        BlockPos center = sphereCenter;
        int radius = sphereRadius;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), OVERLAY_RENDER_TYPE, (pose, vertices) -> {
            try {
                if (shape == SelectionShape.SPHERE && center != null) {
                    renderSphereOverlay(vertices, pose, camera, center, radius, color, alpha);
                } else {
                    renderBoxOverlay(vertices, pose, camera, bounds, color, alpha);
                }
            } catch (RuntimeException exception) {
                runtimeDisabled = true;
                taskState = TaskState.PAUSED;
                pauseMovement();
                NullTweaksClient.LOGGER.error("Disabling feature {} for this session after failure in world render", id(), exception);
            }
        });
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(overlayColorOption())
                .option(durabilityGuardOption())
                .option(durabilityThresholdOption())
                .option(playerProximityActionOption())
                .option(alertSoundOption())
                .option(inventoryModeOption())
                .option(blockListModeOption())
                .option(baritoneChatLoggingOption())
                .option(largeTaskThresholdOption())
                .option(blockListOption())
                .option(missingBaritoneWarningOption())
                .option(keybindButton("Start/resume keybind", START_RESUME_KEY))
                .option(keybindButton("Pause keybind", PAUSE_KEY))
                .option(keybindButton("Stop keybind", STOP_KEY));
    }

    @Override
    protected void loadSettings(JsonObject config) {
        pos1 = readPos(config.getAsJsonObject("pos1"));
        pos2 = readPos(config.getAsJsonObject("pos2"));
        selectionShape = SelectionShape.fromConfig(NullTweaksConfig.getString(config, "selectionShape", SelectionShape.BOX.configValue()));
        sphereCenter = readPos(config.getAsJsonObject("sphereCenter"));
        sphereRadius = clampInt(NullTweaksConfig.getInt(config, "sphereRadius", MIN_SPHERE_RADIUS), MIN_SPHERE_RADIUS, MAX_SPHERE_RADIUS);
        if (selectionShape == SelectionShape.SPHERE && sphereCenter == null) {
            selectionShape = SelectionShape.BOX;
        }
        taskState = TaskState.fromConfig(NullTweaksConfig.getString(config, "taskState", TaskState.STOPPED.configValue()));
        if (taskState == TaskState.RUNNING) {
            taskState = TaskState.PAUSED;
        }
        cursorX = NullTweaksConfig.getInt(config, "cursorX", 0);
        cursorY = NullTweaksConfig.getInt(config, "cursorY", 0);
        cursorZ = NullTweaksConfig.getInt(config, "cursorZ", 0);
        currentTarget = readPos(config.getAsJsonObject("currentTarget"));
        overlayColor = parseColor(NullTweaksConfig.getString(config, "overlayColor", colorString(DEFAULT_OVERLAY_COLOR)), DEFAULT_OVERLAY_COLOR);
        durabilityGuardEnabled = NullTweaksConfig.getBoolean(config, "durabilityGuardEnabled", true);
        durabilityThresholdPercent = clampInt(NullTweaksConfig.getInt(config, "durabilityThresholdPercent", DEFAULT_DURABILITY_THRESHOLD_PERCENT), 1, 100);
        playerProximityAction = PlayerProximityAction.fromConfig(NullTweaksConfig.getString(config, "playerProximityAction",
                NullTweaksConfig.getBoolean(config, "playerProximityAlertEnabled", true) ? PlayerProximityAction.PAUSE.configValue() : PlayerProximityAction.IGNORE.configValue()));
        alertSoundId = NullTweaksConfig.getString(config, "alertSoundId", alertSoundId);
        inventoryFullMode = InventoryFullMode.fromConfig(NullTweaksConfig.getString(config, "inventoryFullMode", InventoryFullMode.PAUSE.configValue()));
        blockListMode = BlockListMode.fromConfig(NullTweaksConfig.getString(config, "blockListMode", BlockListMode.BLACKLIST.configValue()));
        baritoneChatLoggingEnabled = NullTweaksConfig.getBoolean(config, "baritoneChatLoggingEnabled", false);
        largeTaskThreshold = Math.max(1, NullTweaksConfig.getInt(config, "largeTaskThreshold", DEFAULT_LARGE_TASK_THRESHOLD));
        showMissingBaritoneWarning = NullTweaksConfig.getBoolean(config, "showMissingBaritoneWarning", true);
        blockList.clear();
        JsonArray blockListJson = config.getAsJsonArray("blockList");
        if (blockListJson == null) {
            blockListJson = config.getAsJsonArray("whitelist");
        }
        if (blockListJson != null) {
            for (JsonElement element : blockListJson) {
                if (element.isJsonPrimitive()) {
                    Identifier id = Identifier.tryParse(element.getAsString());
                    if (id != null) {
                        blockList.add(id);
                    }
                }
            }
        }
    }

    @Override
    protected void saveSettings(JsonObject config) {
        writePos(config, "pos1", pos1);
        writePos(config, "pos2", pos2);
        config.addProperty("selectionShape", selectionShape.configValue());
        writePos(config, "sphereCenter", sphereCenter);
        config.addProperty("sphereRadius", sphereRadius);
        config.addProperty("taskState", taskState.configValue());
        config.addProperty("cursorX", cursorX);
        config.addProperty("cursorY", cursorY);
        config.addProperty("cursorZ", cursorZ);
        writePos(config, "currentTarget", currentTarget);
        config.addProperty("overlayColor", colorString(overlayColor));
        config.addProperty("durabilityGuardEnabled", durabilityGuardEnabled);
        config.addProperty("durabilityThresholdPercent", durabilityThresholdPercent);
        config.addProperty("playerProximityAction", playerProximityAction.configValue());
        config.addProperty("alertSoundId", alertSoundId);
        config.addProperty("inventoryFullMode", inventoryFullMode.configValue());
        config.remove("collectDropsEnabled");
        config.remove("speedNukerEnabled");
        config.remove("speedNukerMaxBlocksPerTick");
        config.addProperty("blockListMode", blockListMode.configValue());
        config.addProperty("baritoneChatLoggingEnabled", baritoneChatLoggingEnabled);
        config.addProperty("largeTaskThreshold", largeTaskThreshold);
        config.addProperty("showMissingBaritoneWarning", showMissingBaritoneWarning);
        JsonArray blockListJson = new JsonArray();
        blockList.stream().map(Identifier::toString).sorted().forEach(blockListJson::add);
        config.add("blockList", blockListJson);
    }

    private static void registerCommandTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("quarry")
                .then(ClientCommands.argument("radius", IntegerArgumentType.integer(MIN_SPHERE_RADIUS, MAX_SPHERE_RADIUS))
                        .executes(context -> command(context, feature -> feature.captureSphere(context))))
                .then(ClientCommands.literal("pos1").executes(context -> command(context, feature -> feature.capturePos(context, true))))
                .then(ClientCommands.literal("pos2").executes(context -> command(context, feature -> feature.capturePos(context, false))))
                .then(ClientCommands.literal("start").executes(context -> command(context, feature -> feature.start(context.getSource()))))
                .then(ClientCommands.literal("pause").executes(context -> command(context, feature -> feature.pause(context.getSource(), "Quarry paused."))))
                .then(ClientCommands.literal("resume").executes(context -> command(context, feature -> feature.resume(context.getSource()))))
                .then(ClientCommands.literal("stop").executes(context -> command(context, feature -> feature.stop(context.getSource()))))
                .then(ClientCommands.literal("clear").executes(context -> command(context, feature -> feature.clear(context.getSource()), true)))
                .then(ClientCommands.literal("mode")
                        .then(ClientCommands.literal("blacklist")
                                .executes(context -> command(context, feature -> feature.setBlockListMode(context.getSource(), BlockListMode.BLACKLIST))))
                        .then(ClientCommands.literal("whitelist")
                                .executes(context -> command(context, feature -> feature.setBlockListMode(context.getSource(), BlockListMode.WHITELIST)))))
                .then(ClientCommands.literal("chatlogs")
                        .then(ClientCommands.literal("on")
                                .executes(context -> command(context, feature -> feature.setBaritoneChatLogging(context.getSource(), true))))
                        .then(ClientCommands.literal("off")
                                .executes(context -> command(context, feature -> feature.setBaritoneChatLogging(context.getSource(), false)))))
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
                                .executes(context -> command(context, feature -> feature.listBlockList(context.getSource())))))
                .then(ClientCommands.literal("whitelist")
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

    private static int command(CommandContext<FabricClientCommandSource> context, QuarryCommand command) {
        return command(context, command, false);
    }

    private static int command(CommandContext<FabricClientCommandSource> context, QuarryCommand command, boolean allowDisabled) {
        QuarryFeature feature = instance;
        if (feature == null) {
            context.getSource().sendError(Component.literal("Null Tweaks is not initialized yet."));
            return 0;
        }

        if (!allowDisabled && !feature.isEnabled()) {
            context.getSource().sendError(Component.literal("Quarry is disabled in Null Tweaks settings."));
            return 0;
        }

        return command.run(feature);
    }

    private int capturePos(CommandContext<FabricClientCommandSource> context, boolean first) {
        BlockPos captured = context.getSource().getPlayer().blockPosition();
        selectionShape = SelectionShape.BOX;
        sphereCenter = null;
        sphereRadius = MIN_SPHERE_RADIUS;
        if (first) {
            pos1 = captured;
        } else {
            pos2 = captured;
        }
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal((first ? "Quarry pos1" : "Quarry pos2") + " set to " + formatPos(captured)));
        warnIfReady(context.getSource());
        return 1;
    }

    private int captureSphere(CommandContext<FabricClientCommandSource> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = context.getSource().getPlayer().blockPosition();
        boolean taskWasActive = taskState != TaskState.STOPPED;
        selectionShape = SelectionShape.SPHERE;
        sphereCenter = center;
        sphereRadius = radius;
        pos1 = new BlockPos(center.getX() - radius, center.getY() - radius, center.getZ() - radius);
        pos2 = new BlockPos(center.getX() + radius, center.getY() + radius, center.getZ() + radius);
        resetTaskForNewSelection();
        if (taskWasActive) {
            pauseMovement();
        }
        FeatureManager.INSTANCE.saveFeature(this);

        Bounds bounds = bounds();
        long blockCount = bounds == null ? 0L : selectionBlockCount(bounds);
        context.getSource().sendFeedback(Component.literal("Quarry sphere: center " + formatPos(center)
                + ", radius " + radius + " (" + blockCount + " blocks). Use /quarry start to begin."));
        warnIfLarge(context.getSource(), blockCount);
        return 1;
    }

    private int start(FabricClientCommandSource source) {
        if (!ensureBaritone(source)) {
            return 0;
        }

        Bounds bounds = bounds();
        if (bounds == null) {
            source.sendError(Component.literal(missingSelectionMessage()));
            return 0;
        }

        applyBaritoneChatLogging();
        initializeTask(Minecraft.getInstance().level, source.getPlayer(), bounds);
        taskState = TaskState.RUNNING;
        missingBaritoneNotified = false;
        FeatureManager.INSTANCE.saveFeature(this);
        warnIfLarge(source, bounds);
        source.sendFeedback(Component.literal("Quarry started."));
        return 1;
    }

    private int resume(FabricClientCommandSource source) {
        if (!ensureBaritone(source)) {
            return 0;
        }

        if (bounds() == null) {
            source.sendError(Component.literal(missingSelectionMessage()));
            return 0;
        }

        if (taskState == TaskState.STOPPED) {
            return start(source);
        }

        taskState = TaskState.RUNNING;
        stopQuarryBreaking();
        missingBaritoneNotified = false;
        applyBaritoneChatLogging();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry resumed."));
        return 1;
    }

    private int pause(FabricClientCommandSource source, String message) {
        if (taskState == TaskState.RUNNING) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
        }
        source.sendFeedback(Component.literal(message));
        return 1;
    }

    private int stop(FabricClientCommandSource source) {
        taskState = TaskState.STOPPED;
        currentTarget = null;
        stopQuarryBreaking();
        navigationTarget = null;
        skippedTargets.clear();
        resetNavigationProgress();
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry stopped."));
        return 1;
    }

    private int clear(FabricClientCommandSource source) {
        pos1 = null;
        pos2 = null;
        selectionShape = SelectionShape.BOX;
        sphereCenter = null;
        sphereRadius = MIN_SPHERE_RADIUS;
        taskState = TaskState.STOPPED;
        cursorX = 0;
        cursorY = 0;
        cursorZ = 0;
        currentTarget = null;
        stopQuarryBreaking();
        navigationTarget = null;
        skippedTargets.clear();
        resetNavigationProgress();
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry selection cleared."));
        return 1;
    }

    private int setBlockListMode(FabricClientCommandSource source, BlockListMode mode) {
        blockListMode = mode;
        currentTarget = null;
        stopQuarryBreaking();
        cancelActivePath();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry block list mode set to " + mode.displayName() + "."));
        return 1;
    }

    private int setBaritoneChatLogging(FabricClientCommandSource source, boolean enabled) {
        baritoneChatLoggingEnabled = enabled;
        applyBaritoneChatLogging();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry Baritone chat logs " + (enabled ? "enabled." : "disabled.")));
        return 1;
    }

    private int addBlockList(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null) {
            context.getSource().sendError(Component.literal("Unknown block."));
            return 0;
        }
        blockList.add(id);
        currentTarget = null;
        stopQuarryBreaking();
        cancelActivePath();
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Added " + id + " to the Quarry block list."));
        return 1;
    }

    private int removeBlockList(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null || !blockList.remove(id)) {
            context.getSource().sendError(Component.literal("That block is not in the Quarry block list."));
            return 0;
        }
        currentTarget = null;
        stopQuarryBreaking();
        cancelActivePath();
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Removed " + id + " from the Quarry block list."));
        return 1;
    }

    private int listBlockList(FabricClientCommandSource source) {
        if (blockList.isEmpty()) {
            source.sendFeedback(Component.literal("Quarry block list is empty. Mode: " + blockListMode.displayName() + "."));
            return 1;
        }

        String blocks = blockList.stream().map(Identifier::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("");
        source.sendFeedback(Component.literal("Quarry block list (" + blockListMode.displayName() + "): " + blocks));
        return 1;
    }

    private void tick(Minecraft client) {
        while (START_RESUME_KEY.consumeClick()) {
            if (taskState == TaskState.RUNNING) {
                continue;
            }
            if (taskState == TaskState.PAUSED) {
                resumeFromKey(client);
            } else {
                startFromKey(client);
            }
        }
        while (PAUSE_KEY.consumeClick()) {
            pauseFromKey(client, "Quarry paused.");
        }
        while (STOP_KEY.consumeClick()) {
            stopFromKey(client);
        }

        if (taskState != TaskState.RUNNING || client.level == null || client.player == null || client.gameMode == null) {
            pausedForScreen = false;
            return;
        }

        if (ClientScreenState.hasOpenScreen(client)) {
            if (!pausedForScreen) {
                pauseMovement();
                pausedForScreen = true;
            }
            return;
        }
        pausedForScreen = false;

        if (!BaritoneBridge.isAvailable()) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            if (!missingBaritoneNotified) {
                missingBaritoneNotified = true;
                sendPlayerMessage(client, "Quarry paused: Baritone is required for this feature.");
            }
            return;
        }

        double targetReachDistance = SpeedNukerFeature.quarryTargetReachDistance(REACH_DISTANCE);
        if (!BaritoneBridge.applyQuarryControl(blockListMode, blockList, targetReachDistance)) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry paused: unable to apply absolute block-list rules to Baritone.");
            return;
        }

        if (playerProximityAction == PlayerProximityAction.PAUSE && otherPlayerInRenderDistance(client)) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            playAlert(client);
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry paused: another player entered render distance.");
            return;
        }

        tickTask(client, targetReachDistance);
    }

    private void tickTask(Minecraft client, double targetReachDistance) {
        Bounds bounds = bounds();
        if (bounds == null) {
            taskState = TaskState.STOPPED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry stopped: selection is incomplete.");
            return;
        }

        if (currentTarget != null && isUnavailableTarget(client.level, bounds, currentTarget)) {
            stopQuarryBreaking();
            currentTarget = null;
            FeatureManager.INSTANCE.saveFeature(this);
        }

        if (currentTarget == null) {
            currentTarget = findNextTarget(client.level, bounds);
            FeatureManager.INSTANCE.saveFeature(this);
        }

        if (currentTarget == null) {
            taskState = TaskState.STOPPED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry complete.");
            return;
        }

        BlockState state = client.level.getBlockState(currentTarget);
        Optional<Integer> toolSlot = findToolSlot(client.player, state);
        if (toolSlot.isEmpty()) {
            sendPlayerMessage(client, "Quarry skipped " + blockName(state) + ": no matching tool found.");
            skippedTargets.add(currentTarget);
            advancePastCurrent(bounds);
            currentTarget = null;
            stopQuarryBreaking();
            FeatureManager.INSTANCE.saveFeature(this);
            return;
        }

        int selectedToolSlot = selectOrSwapTool(client, toolSlot.get());
        ItemStack tool = client.player.getInventory().getItem(selectedToolSlot);
        if (durabilityGuardEnabled && belowDurabilityThreshold(tool)) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry paused: " + requiredToolName(state) + " durability below threshold.");
            return;
        }

        if (inventoryFullMode == InventoryFullMode.PAUSE && inventoryFull(client.player)) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry paused: inventory is full.");
            return;
        }

        BlockBreakParameters parameters = blockBreakParameters(client.level, client.player.getEyePosition(), currentTarget);
        if (parameters == null || parameters.distanceSqr() > targetReachDistance * targetReachDistance) {
            stopQuarryBreaking();
            if (!sendPathCommand(client, currentTarget, targetReachDistance)) {
                skipCurrentTargetAfterPathingFailure(client, bounds, state);
            }
            return;
        }

        cancelActivePath();
        if (SpeedNukerFeature.isHandlingQuarry()) {
            stopQuarryBreaking();
            return;
        }

        Direction direction = parameters.side();
        if (!currentTarget.equals(breakingTarget)) {
            client.gameMode.startDestroyBlock(currentTarget, direction);
            breakingTarget = currentTarget;
        }
        client.gameMode.continueDestroyBlock(currentTarget, direction);
    }

    private BlockPos findNextTarget(ClientLevel level, Bounds bounds) {
        int x = clampInt(cursorX, bounds.minX(), bounds.maxX());
        int y = clampInt(cursorY, bounds.minY(), bounds.maxY());
        int z = clampInt(cursorZ, bounds.minZ(), bounds.maxZ());
        long startIndex = traversalIndex(bounds, x, y, z);
        long volume = bounds.volume();

        for (long offset = 0; offset < volume; offset++) {
            BlockPos candidate = positionAtTraversalIndex(bounds, (startIndex + offset) % volume);
            setCursor(candidate);
            if (!isUnavailableTarget(level, bounds, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private void advancePastCurrent(Bounds bounds) {
        long volume = bounds.volume();
        if (volume <= 0) {
            return;
        }
        int x = clampInt(cursorX, bounds.minX(), bounds.maxX());
        int y = clampInt(cursorY, bounds.minY(), bounds.maxY());
        int z = clampInt(cursorZ, bounds.minZ(), bounds.maxZ());
        setCursor(positionAtTraversalIndex(bounds, (traversalIndex(bounds, x, y, z) + 1) % volume));
    }

    private void initializeTask(ClientLevel level, Player player, Bounds bounds) {
        skippedTargets.clear();
        navigationTarget = null;
        resetNavigationProgress();
        stopQuarryBreaking();
        currentTarget = null;

        BlockPos start = level == null || player == null ? null : findClosestTarget(level, bounds, player);
        if (start == null) {
            start = positionAtTraversalIndex(bounds, 0);
        } else {
            currentTarget = start;
        }
        setCursor(start);
    }

    private BlockPos findClosestTarget(ClientLevel level, Bounds bounds, Player player) {
        Vec3 origin = player.getEyePosition();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        long volume = bounds.volume();

        for (long index = 0; index < volume; index++) {
            BlockPos candidate = positionAtTraversalIndex(bounds, index);
            if (isUnavailableTarget(level, bounds, candidate)) {
                continue;
            }

            double distance = distanceToCenterSqr(origin, candidate);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private static long traversalIndex(Bounds bounds, int x, int y, int z) {
        long layer = layerIndex(bounds, y);
        long row = rowIndex(bounds, y, z);
        long column = isForwardRow(bounds, y, z) ? x - bounds.minX() : bounds.maxX() - x;
        return layer * bounds.width() * bounds.depth() + row * bounds.width() + column;
    }

    private static BlockPos positionAtTraversalIndex(Bounds bounds, long index) {
        long layerSize = (long) bounds.width() * bounds.depth();
        int layer = (int) (index / layerSize);
        long layerOffset = index % layerSize;
        int row = (int) (layerOffset / bounds.width());
        int column = (int) (layerOffset % bounds.width());
        int y = bounds.maxY() - layer;
        int z = (layer & 1) == 0 ? bounds.minZ() + row : bounds.maxZ() - row;
        int x = isForwardRow(bounds, y, z) ? bounds.minX() + column : bounds.maxX() - column;
        return new BlockPos(x, y, z);
    }

    private void setCursor(BlockPos pos) {
        cursorX = pos.getX();
        cursorY = pos.getY();
        cursorZ = pos.getZ();
    }

    private static double distanceToCenterSqr(Vec3 origin, BlockPos pos) {
        double dx = pos.getX() + 0.5D - origin.x;
        double dy = pos.getY() + 0.5D - origin.y;
        double dz = pos.getZ() + 0.5D - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isForwardLayer(Bounds bounds, int y) {
        return (layerIndex(bounds, y) & 1) == 0;
    }

    private static boolean isForwardRow(Bounds bounds, int y, int z) {
        boolean layerStartsAtMinX = bounds.depth() % 2 == 0 || (layerIndex(bounds, y) & 1) == 0;
        boolean rowStartsAtMinX = (rowIndex(bounds, y, z) & 1) == 0 ? layerStartsAtMinX : !layerStartsAtMinX;
        return rowStartsAtMinX;
    }

    private static int layerIndex(Bounds bounds, int y) {
        return bounds.maxY() - y;
    }

    private static int rowIndex(Bounds bounds, int y, int z) {
        return isForwardLayer(bounds, y) ? z - bounds.minZ() : bounds.maxZ() - z;
    }

    private boolean isSkippable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir()
                || state.liquid()
                || state.getDestroySpeed(level, pos) < 0.0F
                || isSkippedByBlockList(state)
                || blockListMode == BlockListMode.BLACKLIST && isIgnoredPlant(state);
    }

    private boolean isUnavailableTarget(ClientLevel level, Bounds bounds, BlockPos pos) {
        return !bounds.contains(pos)
                || !isInsideSelection(pos)
                || skippedTargets.contains(pos)
                || SpeedNukerFeature.shouldSkipQuarryTarget(pos)
                || isSkippable(level, pos);
    }

    private boolean isInsideSelection(BlockPos pos) {
        if (selectionShape != SelectionShape.SPHERE) {
            return true;
        }
        if (sphereCenter == null) {
            return false;
        }

        long dx = pos.getX() - sphereCenter.getX();
        long dy = pos.getY() - sphereCenter.getY();
        long dz = pos.getZ() - sphereCenter.getZ();
        long radiusSqr = (long) sphereRadius * sphereRadius;
        return dx * dx + dy * dy + dz * dz <= radiusSqr;
    }

    private boolean isSkippedByBlockList(BlockState state) {
        boolean listed = blockList.contains(blockId(state));
        return blockListMode == BlockListMode.BLACKLIST ? listed : !listed;
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

    private static boolean isIgnoredPlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.REPLACEABLE_BY_MUSHROOMS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.CAVE_VINES)
                || state.is(BlockTags.CORAL_PLANTS)
                || state.is(BlockTags.BAMBOO_BLOCKS);
    }

    private Optional<Integer> findToolSlot(Player player, BlockState state) {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isMatchingTool(stack, state)) {
                return Optional.of(slot);
            }
        }

        for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isMatchingTool(stack, state)) {
                return Optional.of(slot);
            }
        }

        return Optional.empty();
    }

    private boolean isMatchingTool(ItemStack stack, BlockState state) {
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

    private static int selectOrSwapTool(Minecraft client, int slot) {
        LocalPlayer player = client.player;
        if (player == null) {
            return 0;
        }

        if (slot < Inventory.getSelectionSize()) {
            player.getInventory().setSelectedSlot(slot);
            return slot;
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        if (client.gameMode != null) {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, selectedSlot, ContainerInput.SWAP, player);
        }
        player.getInventory().setSelectedSlot(selectedSlot);
        return selectedSlot;
    }

    private boolean belowDurabilityThreshold(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return false;
        }

        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return false;
        }

        int remaining = maxDamage - stack.getDamageValue();
        return remaining * 100 <= maxDamage * durabilityThresholdPercent;
    }

    private static boolean inventoryFull(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean otherPlayerInRenderDistance(Minecraft client) {
        if (client.level == null || client.player == null) {
            return false;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Player player && player != client.player) {
                return true;
            }
        }

        return false;
    }

    private void skipCurrentTargetAfterPathingFailure(Minecraft client, Bounds bounds, BlockState state) {
        if (currentTarget == null) {
            return;
        }

        BlockPos skipped = currentTarget;
        skippedTargets.add(skipped);
        advancePastCurrent(bounds);
        currentTarget = null;
        stopQuarryBreaking();
        cancelActivePath();
        FeatureManager.INSTANCE.saveFeature(this);
        sendPlayerMessage(client, "Quarry skipped " + blockName(state) + " at " + formatPos(skipped) + ": pathing did not make progress.");
    }

    private boolean sendPathCommand(Minecraft client, BlockPos target, double targetReachDistance) {
        if (client.level == null || client.player == null) {
            return false;
        }

        BlockPos destination = navigationDestinationForTarget(client.level, client.player, target, targetReachDistance);
        if (destination == null) {
            destination = target;
        }

        applyBaritoneChatLogging();
        if (destination.equals(navigationTarget)) {
            return !navigationIsStuck(client.player);
        }

        navigationTarget = destination;
        resetNavigationProgress();
        BaritoneBridge.runCommand("goto " + destination.getX() + " " + destination.getY() + " " + destination.getZ());
        return true;
    }

    private BlockPos navigationDestinationForTarget(
            ClientLevel level,
            Player player,
            BlockPos target,
            double targetReachDistance) {
        BlockPos best = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        Vec3 playerPosition = player.position();

        for (int dy = -2; dy <= 1; dy++) {
            for (int dz = -NAVIGATION_CANDIDATE_RADIUS; dz <= NAVIGATION_CANDIDATE_RADIUS; dz++) {
                for (int dx = -NAVIGATION_CANDIDATE_RADIUS; dx <= NAVIGATION_CANDIDATE_RADIUS; dx++) {
                    BlockPos candidate = target.offset(dx, dy, dz);
                    if (!canStandAt(level, candidate) || !isWithinReach(level, candidate, target, targetReachDistance)) {
                        continue;
                    }

                    double distanceSqr = playerPosition.distanceToSqr(bottomCenter(candidate));
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private static boolean canStandAt(ClientLevel level, BlockPos feet) {
        return isPassableForStanding(level, feet)
                && isPassableForStanding(level, feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below(), CollisionContext.empty()).isEmpty();
    }

    private static boolean isPassableForStanding(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.liquid() && state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    private static boolean isWithinReach(ClientLevel level, BlockPos feet, BlockPos target, double targetReachDistance) {
        Vec3 standingEyes = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
        BlockBreakParameters parameters = blockBreakParameters(level, standingEyes, target);
        return parameters != null && parameters.distanceSqr() <= targetReachDistance * targetReachDistance;
    }

    private static Vec3 bottomCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private boolean navigationIsStuck(Player player) {
        if (navigationTarget == null) {
            resetNavigationProgress();
            return false;
        }

        double distanceSqr = player.position().distanceToSqr(bottomCenter(navigationTarget));
        if (bestNavigationDistanceSqr == Double.MAX_VALUE || distanceSqr + NAVIGATION_PROGRESS_EPSILON_SQR < bestNavigationDistanceSqr) {
            bestNavigationDistanceSqr = distanceSqr;
            navigationNoProgressTicks = 0;
            return false;
        }

        navigationNoProgressTicks++;
        return navigationNoProgressTicks >= NAVIGATION_STUCK_TICKS;
    }

    private void resetNavigationProgress() {
        bestNavigationDistanceSqr = Double.MAX_VALUE;
        navigationNoProgressTicks = 0;
    }

    private void cancelActivePath() {
        if (navigationTarget == null) {
            return;
        }

        BaritoneBridge.runCommand("cancel");
        navigationTarget = null;
        resetNavigationProgress();
    }

    private void pauseMovement() {
        stopQuarryBreaking();
        navigationTarget = null;
        resetNavigationProgress();
        applyBaritoneChatLogging();
        BaritoneBridge.runCommand("cancel");
        BaritoneBridge.endQuarryControl();
    }

    private void stopQuarryBreaking() {
        if (breakingTarget == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
        breakingTarget = null;
    }

    private void applyBaritoneChatLogging() {
        BaritoneBridge.setChatLoggingEnabled(baritoneChatLoggingEnabled);
    }

    private void queueMissingBaritoneWarning() {
        if (BaritoneBridge.isAvailable() || !showMissingBaritoneWarning) {
            pendingMissingBaritoneWarningTicks = 0;
            return;
        }

        pendingMissingBaritoneWarningTicks = 40;
    }

    private void tickMissingBaritoneWarning(Minecraft client) {
        if (pendingMissingBaritoneWarningTicks <= 0) {
            return;
        }
        if (BaritoneBridge.isAvailable() || !showMissingBaritoneWarning || client.getConnection() == null) {
            pendingMissingBaritoneWarningTicks = 0;
            return;
        }
        if (pendingMissingBaritoneWarningTicks > 1) {
            pendingMissingBaritoneWarningTicks--;
            return;
        }
        if (client.player == null || !client.canInterruptScreen()) {
            pendingMissingBaritoneWarningTicks = 20;
            return;
        }

        pendingMissingBaritoneWarningTicks = 0;
        client.setScreenAndShow(new MissingBaritoneWarningScreen(this::dismissMissingBaritoneWarning));
    }

    private void dismissMissingBaritoneWarning() {
        showMissingBaritoneWarning = false;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean ensureBaritone(FabricClientCommandSource source) {
        if (BaritoneBridge.isAvailable()) {
            return true;
        }

        source.sendError(Component.literal("Baritone is required for Quarry. Install Baritone and reload the game."));
        return false;
    }

    private void startFromKey(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return;
        }
        if (!BaritoneBridge.isAvailable()) {
            sendPlayerMessage(client, "Baritone is required for Quarry. Install Baritone and reload the game.");
            return;
        }
        Bounds bounds = bounds();
        if (bounds == null) {
            sendPlayerMessage(client, missingSelectionMessage());
            return;
        }
        initializeTask(client.level, client.player, bounds);
        applyBaritoneChatLogging();
        taskState = TaskState.RUNNING;
        warnIfLarge(client, bounds);
        FeatureManager.INSTANCE.saveFeature(this);
        sendPlayerMessage(client, "Quarry started.");
    }

    private void resumeFromKey(Minecraft client) {
        if (!BaritoneBridge.isAvailable()) {
            sendPlayerMessage(client, "Baritone is required for Quarry. Install Baritone and reload the game.");
            return;
        }
        stopQuarryBreaking();
        navigationTarget = null;
        skippedTargets.clear();
        resetNavigationProgress();
        applyBaritoneChatLogging();
        taskState = TaskState.RUNNING;
        FeatureManager.INSTANCE.saveFeature(this);
        sendPlayerMessage(client, "Quarry resumed.");
    }

    private void pauseFromKey(Minecraft client, String message) {
        if (taskState == TaskState.RUNNING) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            FeatureManager.INSTANCE.saveFeature(this);
        }
        sendPlayerMessage(client, message);
    }

    private void stopFromKey(Minecraft client) {
        taskState = TaskState.STOPPED;
        currentTarget = null;
        stopQuarryBreaking();
        navigationTarget = null;
        skippedTargets.clear();
        resetNavigationProgress();
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        sendPlayerMessage(client, "Quarry stopped.");
    }

    private void warnIfReady(FabricClientCommandSource source) {
        Bounds bounds = bounds();
        if (bounds == null) {
            return;
        }

        long blockCount = selectionBlockCount(bounds);
        source.sendFeedback(Component.literal(selectionDescription(bounds, blockCount) + "."));
        warnIfLarge(source, blockCount);
    }

    private void warnIfLarge(FabricClientCommandSource source, Bounds bounds) {
        warnIfLarge(source, selectionBlockCount(bounds));
    }

    private void warnIfLarge(FabricClientCommandSource source, long blockCount) {
        if (blockCount > largeTaskThreshold) {
            source.sendFeedback(Component.literal("Warning: Quarry selection contains " + blockCount + " blocks."));
        }
    }

    private void warnIfLarge(Minecraft client, Bounds bounds) {
        long blockCount = selectionBlockCount(bounds);
        if (blockCount > largeTaskThreshold) {
            sendPlayerMessage(client, "Warning: Quarry selection contains " + blockCount + " blocks.");
        }
    }

    private void playAlert(Minecraft client) {
        if (client.player == null) {
            return;
        }

        Identifier id = Identifier.tryParse(alertSoundId);
        if (id == null) {
            return;
        }

        Optional<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.getOptional(id);
        sound.ifPresent(soundEvent -> client.player.playSound(soundEvent, 1.0F, 1.0F));
    }

    private Bounds bounds() {
        if (selectionShape == SelectionShape.SPHERE) {
            if (sphereCenter == null) {
                return null;
            }

            int radius = clampInt(sphereRadius, MIN_SPHERE_RADIUS, MAX_SPHERE_RADIUS);
            return new Bounds(
                    sphereCenter.getX() - radius,
                    sphereCenter.getY() - radius,
                    sphereCenter.getZ() - radius,
                    sphereCenter.getX() + radius,
                    sphereCenter.getY() + radius,
                    sphereCenter.getZ() + radius);
        }

        if (pos1 == null || pos2 == null) {
            return null;
        }

        return new Bounds(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));
    }

    private long selectionBlockCount(Bounds bounds) {
        if (selectionShape != SelectionShape.SPHERE || sphereCenter == null) {
            return bounds.volume();
        }

        return sphereBlockCount(sphereRadius);
    }

    private String selectionDescription(Bounds bounds, long blockCount) {
        if (selectionShape == SelectionShape.SPHERE && sphereCenter != null) {
            return "Quarry sphere: center " + formatPos(sphereCenter) + ", radius " + sphereRadius + " (" + blockCount + " blocks)";
        }

        return "Quarry box: " + bounds.width() + " x " + bounds.height() + " x " + bounds.depth() + " (" + blockCount + " blocks)";
    }

    private void resetTaskForNewSelection() {
        taskState = TaskState.STOPPED;
        cursorX = 0;
        cursorY = 0;
        cursorZ = 0;
        currentTarget = null;
        stopQuarryBreaking();
        navigationTarget = null;
        skippedTargets.clear();
        resetNavigationProgress();
    }

    private static Identifier resolveBlockId(String raw) {
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }

        return id;
    }

    private static Identifier blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static String blockName(BlockState state) {
        return blockId(state).toString();
    }

    private static String requiredToolName(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "hoe";
        }
        if (state.is(BlockTags.SWORD_EFFICIENT) || state.is(BlockTags.SWORD_INSTANTLY_MINES)) {
            return "sword";
        }
        return "tool";
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

    private static void sendPlayerMessage(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static boolean isBaritoneMessage(Component message) {
        if (message == null) {
            return false;
        }

        String text = ChatFormatting.stripFormatting(message.getString());
        if (text == null) {
            return false;
        }

        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("[baritone]")
                || normalized.startsWith("[b]")
                || normalized.startsWith("baritone ")
                || normalized.startsWith("baritone:")
                || normalized.contains("[baritone]");
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static BlockPos readPos(JsonObject object) {
        if (object == null) {
            return null;
        }
        return new BlockPos(
                NullTweaksConfig.getInt(object, "x", 0),
                NullTweaksConfig.getInt(object, "y", 0),
                NullTweaksConfig.getInt(object, "z", 0));
    }

    private static void writePos(JsonObject config, String key, BlockPos pos) {
        if (pos == null) {
            config.remove(key);
            return;
        }

        JsonObject object = new JsonObject();
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        config.add(key, object);
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer vertices, com.mojang.blaze3d.vertex.PoseStack.Pose pose, double x1, double y1, double z1, double x2, double y2, double z2, int color, int alpha) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        vertices.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(OVERLAY_LINE_WIDTH);
        vertices.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(OVERLAY_LINE_WIDTH);
    }

    private static void renderBoxOverlay(com.mojang.blaze3d.vertex.VertexConsumer vertices, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Vec3 camera, Bounds bounds, int color, int alpha) {
        double minX = bounds.minX() - camera.x;
        double minY = bounds.minY() - camera.y;
        double minZ = bounds.minZ() - camera.z;
        double maxX = bounds.maxX() + 1.0D - camera.x;
        double maxY = bounds.maxY() + 1.0D - camera.y;
        double maxZ = bounds.maxZ() + 1.0D - camera.z;

        line(vertices, pose, minX, minY, minZ, maxX, minY, minZ, color, alpha);
        line(vertices, pose, maxX, minY, minZ, maxX, minY, maxZ, color, alpha);
        line(vertices, pose, maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
        line(vertices, pose, minX, minY, maxZ, minX, minY, minZ, color, alpha);
        line(vertices, pose, minX, maxY, minZ, maxX, maxY, minZ, color, alpha);
        line(vertices, pose, maxX, maxY, minZ, maxX, maxY, maxZ, color, alpha);
        line(vertices, pose, maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        line(vertices, pose, minX, maxY, maxZ, minX, maxY, minZ, color, alpha);
        line(vertices, pose, minX, minY, minZ, minX, maxY, minZ, color, alpha);
        line(vertices, pose, maxX, minY, minZ, maxX, maxY, minZ, color, alpha);
        line(vertices, pose, maxX, minY, maxZ, maxX, maxY, maxZ, color, alpha);
        line(vertices, pose, minX, minY, maxZ, minX, maxY, maxZ, color, alpha);
    }

    private static void renderSphereOverlay(com.mojang.blaze3d.vertex.VertexConsumer vertices, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Vec3 camera, BlockPos center, int radius, int color, int alpha) {
        double centerX = center.getX() + 0.5D - camera.x;
        double centerY = center.getY() + 0.5D - camera.y;
        double centerZ = center.getZ() + 0.5D - camera.z;
        double visualRadius = radius + 0.5D;

        for (int segment = 0; segment < SPHERE_OVERLAY_SEGMENTS; segment++) {
            double angle1 = Math.PI * 2.0D * segment / SPHERE_OVERLAY_SEGMENTS;
            double angle2 = Math.PI * 2.0D * (segment + 1) / SPHERE_OVERLAY_SEGMENTS;
            double cos1 = Math.cos(angle1) * visualRadius;
            double sin1 = Math.sin(angle1) * visualRadius;
            double cos2 = Math.cos(angle2) * visualRadius;
            double sin2 = Math.sin(angle2) * visualRadius;

            line(vertices, pose, centerX + cos1, centerY + sin1, centerZ, centerX + cos2, centerY + sin2, centerZ, color, alpha);
            line(vertices, pose, centerX + cos1, centerY, centerZ + sin1, centerX + cos2, centerY, centerZ + sin2, color, alpha);
            line(vertices, pose, centerX, centerY + cos1, centerZ + sin1, centerX, centerY + cos2, centerZ + sin2, color, alpha);
        }
    }

    private static long sphereBlockCount(int radius) {
        long radiusSqr = (long) radius * radius;
        long count = 0L;
        for (int y = -radius; y <= radius; y++) {
            long ySqr = (long) y * y;
            for (int z = -radius; z <= radius; z++) {
                long yzSqr = ySqr + (long) z * z;
                for (int x = -radius; x <= radius; x++) {
                    long distanceSqr = yzSqr + (long) x * x;
                    if (distanceSqr <= radiusSqr) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String missingSelectionMessage() {
        return "Set a Quarry selection first with /quarry pos1 and /quarry pos2, or create a sphere with /quarry <radius>.";
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBlocks(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
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

    private Option<Color> overlayColorOption() {
        return Option.<Color>createBuilder()
                .name(Component.literal("Overlay color"))
                .description(description("Wireframe color for the selected Quarry shape."))
                .binding(DEFAULT_OVERLAY_COLOR, this::overlayColor, this::setOverlayColor)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(false))
                .instant(true)
                .build();
    }

    private Option<Boolean> durabilityGuardOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Durability guard"))
                .description(description("Pauses Quarry before the selected matching tool drops below the configured durability percentage."))
                .binding(true, () -> durabilityGuardEnabled, value -> {
                    durabilityGuardEnabled = value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<Integer> durabilityThresholdOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Durability threshold"))
                .description(description("Remaining durability percentage that pauses Quarry."))
                .binding(DEFAULT_DURABILITY_THRESHOLD_PERCENT, () -> durabilityThresholdPercent, value -> {
                    durabilityThresholdPercent = clampInt(value, 1, 100);
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(1, 100)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + "%")))
                .instant(true)
                .build();
    }

    private Option<PlayerProximityAction> playerProximityActionOption() {
        return Option.<PlayerProximityAction>createBuilder()
                .name(Component.literal("Player proximity behavior"))
                .description(description("Choose what Quarry does when another player is visible to the client."))
                .binding(PlayerProximityAction.PAUSE, () -> playerProximityAction, value -> {
                    playerProximityAction = value == null ? PlayerProximityAction.PAUSE : value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(PlayerProximityAction.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private Option<String> alertSoundOption() {
        return Option.<String>createBuilder()
                .name(Component.literal("Alert sound"))
                .description(description("Sound event id played when the player proximity alert pauses Quarry."))
                .binding(defaultAlertSoundId(), () -> alertSoundId, value -> {
                    alertSoundId = value == null || value.isBlank() ? defaultAlertSoundId() : value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(StringControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<InventoryFullMode> inventoryModeOption() {
        return Option.<InventoryFullMode>createBuilder()
                .name(Component.literal("Inventory full behavior"))
                .description(description("Choose whether Quarry pauses when your inventory has no empty slots."))
                .binding(InventoryFullMode.PAUSE, () -> inventoryFullMode, value -> {
                    inventoryFullMode = value == null ? InventoryFullMode.PAUSE : value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(InventoryFullMode.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private Option<Integer> largeTaskThresholdOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Large task warning threshold"))
                .description(description("Selections above this block count print a warning but are still allowed."))
                .binding(DEFAULT_LARGE_TASK_THRESHOLD, () -> largeTaskThreshold, value -> {
                    largeTaskThreshold = Math.max(1, value);
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> IntegerFieldControllerBuilder.create(option)
                        .min(1)
                        .valueFormatter(value -> Component.literal(value + " blocks")))
                .instant(true)
                .build();
    }

    private Option<BlockListMode> blockListModeOption() {
        return Option.<BlockListMode>createBuilder()
                .name(Component.literal("Block list mode"))
                .description(description("Absolute filter for Quarry and Baritone: blacklist never mines listed blocks; whitelist never mines unlisted blocks."))
                .binding(BlockListMode.BLACKLIST, () -> blockListMode, value -> {
                    blockListMode = value == null ? BlockListMode.BLACKLIST : value;
                    currentTarget = null;
                    stopQuarryBreaking();
                    cancelActivePath();
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(BlockListMode.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private Option<Boolean> baritoneChatLoggingOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Baritone chat logs"))
                .description(description("Shows or hides Baritone chat messages while Quarry controls Baritone."))
                .binding(false, () -> baritoneChatLoggingEnabled, value -> {
                    baritoneChatLoggingEnabled = value;
                    applyBaritoneChatLogging();
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<String> blockListOption() {
        return Option.<String>createBuilder()
                .name(Component.literal("Block list ids"))
                .description(description("Comma-separated block ids used by the selected block list mode. Example: minecraft:diamond_ore, ancient_debris"))
                .binding("", this::blockListConfigValue, this::setBlockListConfigValue)
                .controller(StringControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<Boolean> missingBaritoneWarningOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Missing Baritone warning"))
                .description(description("Shows a join popup when Quarry cannot use a compatible Baritone install."))
                .binding(true, () -> showMissingBaritoneWarning, value -> {
                    showMissingBaritoneWarning = value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(BooleanControllerBuilder::create)
                .instant(true)
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
        currentTarget = null;
        stopQuarryBreaking();
        cancelActivePath();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private ButtonOption keybindButton(String name, KeyMapping keyMapping) {
        return ButtonOption.createBuilder()
                .name(Component.literal(name))
                .description(description("Opens Minecraft's keybind screen for Quarry controls."))
                .text(keyMapping.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                .build();
    }

    private Color overlayColor() {
        return overlayColor;
    }

    private void setOverlayColor(Color color) {
        overlayColor = new Color(color.getRGB() & 0xFFFFFF);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static Color parseColor(String value, Color fallback) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        try {
            return new Color(Integer.parseInt(normalized, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String colorString(Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    private static String defaultAlertSoundId() {
        return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.EXPERIENCE_ORB_PICKUP).toString();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @FunctionalInterface
    private interface QuarryCommand {
        int run(QuarryFeature feature);
    }

    private enum TaskState {
        STOPPED("stopped"),
        RUNNING("running"),
        PAUSED("paused");

        private final String configValue;

        TaskState(String configValue) {
            this.configValue = configValue;
        }

        private String configValue() {
            return configValue;
        }

        private static TaskState fromConfig(String value) {
            for (TaskState state : values()) {
                if (state.configValue.equalsIgnoreCase(value)) {
                    return state;
                }
            }
            return STOPPED;
        }
    }

    private enum SelectionShape {
        BOX("box"),
        SPHERE("sphere");

        private final String configValue;

        SelectionShape(String configValue) {
            this.configValue = configValue;
        }

        private String configValue() {
            return configValue;
        }

        private static SelectionShape fromConfig(String value) {
            for (SelectionShape shape : values()) {
                if (shape.configValue.equalsIgnoreCase(value)) {
                    return shape;
                }
            }
            return BOX;
        }
    }

    public enum InventoryFullMode {
        PAUSE("Pause when full"),
        IGNORE("Ignore and continue");

        private final String displayName;

        InventoryFullMode(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static InventoryFullMode fromConfig(String value) {
            for (InventoryFullMode mode : values()) {
                if (mode.configValue().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return PAUSE;
        }
    }

    public enum PlayerProximityAction {
        PAUSE("Pause Quarry"),
        IGNORE("Ignore players");

        private final String displayName;

        PlayerProximityAction(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static PlayerProximityAction fromConfig(String value) {
            for (PlayerProximityAction action : values()) {
                if (action.configValue().equalsIgnoreCase(value)) {
                    return action;
                }
            }
            return PAUSE;
        }
    }

    public enum BlockListMode {
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

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }

        private long volume() {
            return (long) width() * height() * depth();
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    private static final class BaritoneBridge {
        private static final String ALLOW_BREAK = "allowBreak";
        private static final String ALLOW_BREAK_ANYWAY = "allowBreakAnyway";
        private static final String BLOCKS_TO_DISALLOW_BREAKING = "blocksToDisallowBreaking";
        private static final String BLOCK_REACH_DISTANCE = "blockReachDistance";
        private static final boolean AVAILABLE = findApiClass().isPresent() || findInternalProviderClass().isPresent();
        private static Boolean appliedChatLoggingEnabled;
        private static final Map<String, Object> quarrySettingSnapshot = new HashMap<>();
        private static Object quarrySettings;
        private static boolean quarryControlActive;

        private BaritoneBridge() {
        }

        private static boolean isAvailable() {
            return AVAILABLE;
        }

        private static boolean applyQuarryControl(BlockListMode mode, Set<Identifier> blockIds, double reachDistance) {
            Optional<Class<?>> apiClass = findApiClass();
            if (apiClass.isEmpty()) {
                return AVAILABLE;
            }

            try {
                Object settings = apiClass.get().getMethod("getSettings").invoke(null);
                if (!quarryControlActive) {
                    quarrySettings = settings;
                    quarrySettingSnapshot.clear();
                    snapshotSetting(settings, ALLOW_BREAK);
                    snapshotSetting(settings, ALLOW_BREAK_ANYWAY);
                    snapshotSetting(settings, BLOCKS_TO_DISALLOW_BREAKING);
                    snapshotSetting(settings, BLOCK_REACH_DISTANCE);
                    quarryControlActive = true;
                }

                List<Block> listedBlocks = new ArrayList<>();
                for (Identifier id : blockIds) {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(listedBlocks::add);
                }

                List<Object> originalAllowedAnyway = snapshotList(ALLOW_BREAK_ANYWAY);
                List<Object> originalDisallowed = snapshotList(BLOCKS_TO_DISALLOW_BREAKING);
                if (mode == BlockListMode.WHITELIST) {
                    setSettingValue(settings, ALLOW_BREAK, false);
                    setSettingValue(settings, ALLOW_BREAK_ANYWAY, new ArrayList<>(listedBlocks));
                    setSettingValue(settings, BLOCKS_TO_DISALLOW_BREAKING, originalDisallowed);
                } else {
                    originalAllowedAnyway.removeAll(listedBlocks);
                    for (Block block : listedBlocks) {
                        if (!originalDisallowed.contains(block)) {
                            originalDisallowed.add(block);
                        }
                    }
                    setSettingValue(settings, ALLOW_BREAK, quarrySettingSnapshot.get(ALLOW_BREAK));
                    setSettingValue(settings, ALLOW_BREAK_ANYWAY, originalAllowedAnyway);
                    setSettingValue(settings, BLOCKS_TO_DISALLOW_BREAKING, originalDisallowed);
                }
                setSettingValue(settings, BLOCK_REACH_DISTANCE, (float) reachDistance);
                return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                NullTweaksClient.LOGGER.warn("Failed to apply Quarry's absolute Baritone block rules", exception);
                endQuarryControl();
                return AVAILABLE;
            }
        }

        private static void endQuarryControl() {
            if (!quarryControlActive || quarrySettings == null) {
                return;
            }

            try {
                for (Map.Entry<String, Object> entry : quarrySettingSnapshot.entrySet()) {
                    setSettingValue(quarrySettings, entry.getKey(), copySettingValue(entry.getValue()));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                NullTweaksClient.LOGGER.warn("Failed to restore Baritone settings after Quarry stopped", exception);
            } finally {
                quarrySettingSnapshot.clear();
                quarrySettings = null;
                quarryControlActive = false;
            }
        }

        private static void snapshotSetting(Object settings, String name) throws ReflectiveOperationException {
            quarrySettingSnapshot.put(name, copySettingValue(getSettingValue(settings, name)));
        }

        private static List<Object> snapshotList(String name) {
            Object value = quarrySettingSnapshot.get(name);
            return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
        }

        private static Object copySettingValue(Object value) {
            return value instanceof List<?> list ? new ArrayList<>(list) : value;
        }

        private static Object getSettingValue(Object settings, String name) throws ReflectiveOperationException {
            Object setting = settings.getClass().getField(name).get(settings);
            Field valueField = setting.getClass().getField("value");
            return valueField.get(setting);
        }

        private static void setSettingValue(Object settings, String name, Object value) throws ReflectiveOperationException {
            Object setting = settings.getClass().getField(name).get(settings);
            Field valueField = setting.getClass().getField("value");
            valueField.set(setting, value);
        }

        private static void setChatLoggingEnabled(boolean enabled) {
            if (!AVAILABLE || Boolean.valueOf(enabled).equals(appliedChatLoggingEnabled)) {
                return;
            }

            Optional<Class<?>> apiClass = findApiClass();
            if (apiClass.isPresent() && setSettingsChatLogging(apiClass.get(), enabled)) {
                appliedChatLoggingEnabled = enabled;
                return;
            }

            runChatCommand("set echoCommands " + enabled);
            runChatCommand("set chatDebug " + enabled);
            runChatCommand("set logAsToast false");
            appliedChatLoggingEnabled = enabled;
        }

        private static void runCommand(String command) {
            if (!AVAILABLE) {
                return;
            }

            Optional<Class<?>> apiClass = findApiClass();
            if (apiClass.isEmpty()) {
                runChatCommand(command);
                return;
            }

            try {
                Object provider = apiClass.get().getMethod("getProvider").invoke(null);
                Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
                Object manager = primary.getClass().getMethod("getCommandManager").invoke(primary);
                Method execute = manager.getClass().getMethod("execute", String.class);
                execute.invoke(manager, command);
            } catch (ReflectiveOperationException | LinkageError exception) {
                NullTweaksClient.LOGGER.warn("Failed to execute Baritone command: {}", command, exception);
            }
        }

        private static boolean setSettingsChatLogging(Class<?> apiClass, boolean enabled) {
            try {
                Object settings = apiClass.getMethod("getSettings").invoke(null);
                boolean chatDebugSet = setBooleanSetting(settings, "chatDebug", enabled);
                boolean echoCommandsSet = setBooleanSetting(settings, "echoCommands", enabled);
                boolean logAsToastSet = setBooleanSetting(settings, "logAsToast", false);
                return chatDebugSet || echoCommandsSet || logAsToastSet;
            } catch (ReflectiveOperationException | LinkageError exception) {
                NullTweaksClient.LOGGER.warn("Failed to update Baritone chat log settings", exception);
                return false;
            }
        }

        private static boolean setBooleanSetting(Object settings, String name, boolean enabled) {
            try {
                setSettingValue(settings, name, enabled);
                return true;
            } catch (ReflectiveOperationException | LinkageError exception) {
                NullTweaksClient.LOGGER.debug("Baritone setting {} is not available", name, exception);
                return false;
            }
        }

        private static void runChatCommand(String command) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) {
                return;
            }

            client.getConnection().sendChat("#" + command);
        }

        private static Optional<Class<?>> findApiClass() {
            try {
                return Optional.of(Class.forName("baritone.api.BaritoneAPI"));
            } catch (ClassNotFoundException | LinkageError exception) {
                return Optional.empty();
            }
        }

        private static Optional<Class<?>> findInternalProviderClass() {
            try {
                return Optional.of(Class.forName("baritone.BaritoneProvider"));
            } catch (ClassNotFoundException | LinkageError exception) {
                return Optional.empty();
            }
        }
    }
}
