package dev.nullkeeperdev.nulltweaks.feature.quarry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
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
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
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
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class QuarryFeature extends Feature {
    private static final int DEFAULT_LARGE_TASK_THRESHOLD = 50_000;
    private static final int DEFAULT_DURABILITY_THRESHOLD_PERCENT = 10;
    private static final double REACH_DISTANCE = 4.5D;
    private static final int BARITONE_COMMAND_COOLDOWN_TICKS = 5;
    private static final float OVERLAY_LINE_WIDTH = 1.0F;
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
    private TaskState taskState = TaskState.STOPPED;
    private int cursorX;
    private int cursorY;
    private int cursorZ;
    private BlockPos currentTarget;
    private BlockPos breakingTarget;
    private Color overlayColor = DEFAULT_OVERLAY_COLOR;
    private boolean durabilityGuardEnabled = true;
    private int durabilityThresholdPercent = DEFAULT_DURABILITY_THRESHOLD_PERCENT;
    private boolean playerProximityAlertEnabled = true;
    private String alertSoundId = defaultAlertSoundId();
    private InventoryFullMode inventoryFullMode = InventoryFullMode.PAUSE;
    private int largeTaskThreshold = DEFAULT_LARGE_TASK_THRESHOLD;
    private final Set<Identifier> whitelist = new HashSet<>();
    private int baritoneCooldownTicks;
    private boolean missingBaritoneNotified;
    private boolean showMissingBaritoneWarning = true;
    private int pendingMissingBaritoneWarningTicks;
    private boolean runtimeDisabled;

    public QuarryFeature() {
        super("quarry", "Quarry");
        instance = this;
    }

    public static boolean isRunning() {
        QuarryFeature feature = instance;
        return feature != null && feature.isEnabled() && !feature.runtimeDisabled && feature.taskState == TaskState.RUNNING;
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

        if (pos1 == null || pos2 == null) {
            return;
        }

        Bounds bounds = bounds();
        if (bounds == null) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        double minX = bounds.minX() - camera.x;
        double minY = bounds.minY() - camera.y;
        double minZ = bounds.minZ() - camera.z;
        double maxX = bounds.maxX() + 1.0D - camera.x;
        double maxY = bounds.maxY() + 1.0D - camera.y;
        double maxZ = bounds.maxZ() + 1.0D - camera.z;
        int color = overlayColor.getRGB();
        int alpha = 255;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), OVERLAY_RENDER_TYPE, (pose, vertices) -> {
            try {
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
                .option(playerProximityAlertOption())
                .option(alertSoundOption())
                .option(inventoryModeOption())
                .option(largeTaskThresholdOption())
                .option(whitelistOption())
                .option(missingBaritoneWarningOption())
                .option(keybindButton("Start/resume keybind", START_RESUME_KEY))
                .option(keybindButton("Pause keybind", PAUSE_KEY))
                .option(keybindButton("Stop keybind", STOP_KEY));
    }

    @Override
    protected void loadSettings(JsonObject config) {
        pos1 = readPos(config.getAsJsonObject("pos1"));
        pos2 = readPos(config.getAsJsonObject("pos2"));
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
        playerProximityAlertEnabled = NullTweaksConfig.getBoolean(config, "playerProximityAlertEnabled", true);
        alertSoundId = NullTweaksConfig.getString(config, "alertSoundId", alertSoundId);
        inventoryFullMode = InventoryFullMode.fromConfig(NullTweaksConfig.getString(config, "inventoryFullMode", InventoryFullMode.PAUSE.configValue()));
        largeTaskThreshold = Math.max(1, NullTweaksConfig.getInt(config, "largeTaskThreshold", DEFAULT_LARGE_TASK_THRESHOLD));
        showMissingBaritoneWarning = NullTweaksConfig.getBoolean(config, "showMissingBaritoneWarning", true);
        whitelist.clear();
        JsonArray whitelistJson = config.getAsJsonArray("whitelist");
        if (whitelistJson != null) {
            for (JsonElement element : whitelistJson) {
                if (element.isJsonPrimitive()) {
                    Identifier id = Identifier.tryParse(element.getAsString());
                    if (id != null) {
                        whitelist.add(id);
                    }
                }
            }
        }
    }

    @Override
    protected void saveSettings(JsonObject config) {
        writePos(config, "pos1", pos1);
        writePos(config, "pos2", pos2);
        config.addProperty("taskState", taskState.configValue());
        config.addProperty("cursorX", cursorX);
        config.addProperty("cursorY", cursorY);
        config.addProperty("cursorZ", cursorZ);
        writePos(config, "currentTarget", currentTarget);
        config.addProperty("overlayColor", colorString(overlayColor));
        config.addProperty("durabilityGuardEnabled", durabilityGuardEnabled);
        config.addProperty("durabilityThresholdPercent", durabilityThresholdPercent);
        config.addProperty("playerProximityAlertEnabled", playerProximityAlertEnabled);
        config.addProperty("alertSoundId", alertSoundId);
        config.addProperty("inventoryFullMode", inventoryFullMode.configValue());
        config.addProperty("largeTaskThreshold", largeTaskThreshold);
        config.addProperty("showMissingBaritoneWarning", showMissingBaritoneWarning);
        JsonArray whitelistJson = new JsonArray();
        whitelist.stream().map(Identifier::toString).sorted().forEach(whitelistJson::add);
        config.add("whitelist", whitelistJson);
    }

    private static void registerCommandTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("quarry")
                .then(ClientCommands.literal("pos1").executes(context -> command(context, feature -> feature.capturePos(context, true))))
                .then(ClientCommands.literal("pos2").executes(context -> command(context, feature -> feature.capturePos(context, false))))
                .then(ClientCommands.literal("start").executes(context -> command(context, feature -> feature.start(context.getSource()))))
                .then(ClientCommands.literal("pause").executes(context -> command(context, feature -> feature.pause(context.getSource(), "Quarry paused."))))
                .then(ClientCommands.literal("resume").executes(context -> command(context, feature -> feature.resume(context.getSource()))))
                .then(ClientCommands.literal("stop").executes(context -> command(context, feature -> feature.stop(context.getSource()))))
                .then(ClientCommands.literal("clear").executes(context -> command(context, feature -> feature.clear(context.getSource()))))
                .then(ClientCommands.literal("whitelist")
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests(BLOCK_SUGGESTIONS)
                                        .executes(context -> command(context, feature -> feature.addWhitelist(context)))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("block", StringArgumentType.word())
                                        .suggests(BLOCK_SUGGESTIONS)
                                        .executes(context -> command(context, feature -> feature.removeWhitelist(context)))))
                        .then(ClientCommands.literal("list")
                                .executes(context -> command(context, feature -> feature.listWhitelist(context.getSource()))))));
    }

    private static int command(CommandContext<FabricClientCommandSource> context, QuarryCommand command) {
        QuarryFeature feature = instance;
        if (feature == null) {
            context.getSource().sendError(Component.literal("Null Tweaks is not initialized yet."));
            return 0;
        }

        if (!feature.isEnabled()) {
            context.getSource().sendError(Component.literal("Quarry is disabled in Null Tweaks settings."));
            return 0;
        }

        return command.run(feature);
    }

    private int capturePos(CommandContext<FabricClientCommandSource> context, boolean first) {
        BlockPos captured = context.getSource().getPlayer().blockPosition();
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

    private int start(FabricClientCommandSource source) {
        if (!ensureBaritone(source)) {
            return 0;
        }

        Bounds bounds = bounds();
        if (bounds == null) {
            source.sendError(Component.literal("Set both Quarry corners first with /quarry pos1 and /quarry pos2."));
            return 0;
        }

        cursorX = bounds.minX();
        cursorY = bounds.maxY();
        cursorZ = bounds.minZ();
        currentTarget = null;
        breakingTarget = null;
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
            source.sendError(Component.literal("Set both Quarry corners first with /quarry pos1 and /quarry pos2."));
            return 0;
        }

        if (taskState == TaskState.STOPPED) {
            return start(source);
        }

        taskState = TaskState.RUNNING;
        breakingTarget = null;
        missingBaritoneNotified = false;
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
        breakingTarget = null;
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry stopped."));
        return 1;
    }

    private int clear(FabricClientCommandSource source) {
        pos1 = null;
        pos2 = null;
        taskState = TaskState.STOPPED;
        cursorX = 0;
        cursorY = 0;
        cursorZ = 0;
        currentTarget = null;
        breakingTarget = null;
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        source.sendFeedback(Component.literal("Quarry selection cleared."));
        return 1;
    }

    private int addWhitelist(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null) {
            context.getSource().sendError(Component.literal("Unknown block."));
            return 0;
        }
        whitelist.add(id);
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Added " + id + " to the Quarry whitelist."));
        return 1;
    }

    private int removeWhitelist(CommandContext<FabricClientCommandSource> context) {
        Identifier id = resolveBlockId(StringArgumentType.getString(context, "block"));
        if (id == null || !whitelist.remove(id)) {
            context.getSource().sendError(Component.literal("That block is not in the Quarry whitelist."));
            return 0;
        }
        FeatureManager.INSTANCE.saveFeature(this);
        context.getSource().sendFeedback(Component.literal("Removed " + id + " from the Quarry whitelist."));
        return 1;
    }

    private int listWhitelist(FabricClientCommandSource source) {
        if (whitelist.isEmpty()) {
            source.sendFeedback(Component.literal("Quarry whitelist is empty."));
            return 1;
        }

        String blocks = whitelist.stream().map(Identifier::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("");
        source.sendFeedback(Component.literal("Quarry whitelist: " + blocks));
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
            return;
        }

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

        if (playerProximityAlertEnabled && otherPlayerInRenderDistance(client)) {
            taskState = TaskState.PAUSED;
            pauseMovement();
            playAlert(client);
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry paused: another player entered render distance.");
            return;
        }

        tickTask(client);
    }

    private void tickTask(Minecraft client) {
        Bounds bounds = bounds();
        if (bounds == null) {
            taskState = TaskState.STOPPED;
            FeatureManager.INSTANCE.saveFeature(this);
            sendPlayerMessage(client, "Quarry stopped: selection is incomplete.");
            return;
        }

        if (currentTarget == null || isSkippable(client.level, currentTarget)) {
            breakingTarget = null;
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
            advancePastCurrent(bounds);
            currentTarget = null;
            breakingTarget = null;
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

        double distance = client.player.position().distanceTo(Vec3.atCenterOf(currentTarget));
        if (distance > REACH_DISTANCE) {
            breakingTarget = null;
            sendPathCommand(currentTarget);
            return;
        }

        Direction direction = directionToward(client.player, currentTarget);
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

        for (int yy = y; yy >= bounds.minY(); yy--) {
            boolean zForward = isForwardLayer(bounds, yy);
            int startZ = yy == y ? z : layerStartZ(bounds, yy);
            for (int zz = startZ; isWithinLayer(bounds, zz, zForward); zz += zForward ? 1 : -1) {
                boolean forward = isForwardRow(bounds, yy, zz);
                int startX = yy == y && zz == z ? x : rowStartX(bounds, yy, zz);
                for (int xx = startX; isWithinRow(bounds, xx, forward); xx += forward ? 1 : -1) {
                    BlockPos candidate = new BlockPos(xx, yy, zz);
                    cursorX = xx;
                    cursorY = yy;
                    cursorZ = zz;
                    if (!isSkippable(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private void advancePastCurrent(Bounds bounds) {
        boolean forward = isForwardRow(bounds, cursorY, cursorZ);
        cursorX += forward ? 1 : -1;
        if (!isWithinRow(bounds, cursorX, forward)) {
            boolean zForward = isForwardLayer(bounds, cursorY);
            cursorZ += zForward ? 1 : -1;
            if (!isWithinLayer(bounds, cursorZ, zForward)) {
                cursorY--;
                if (cursorY < bounds.minY()) {
                    return;
                }
                cursorZ = layerStartZ(bounds, cursorY);
            }
            cursorX = rowStartX(bounds, cursorY, cursorZ);
        }
    }

    private static boolean isForwardLayer(Bounds bounds, int y) {
        return (layerIndex(bounds, y) & 1) == 0;
    }

    private static int layerStartZ(Bounds bounds, int y) {
        return isForwardLayer(bounds, y) ? bounds.minZ() : bounds.maxZ();
    }

    private static boolean isWithinLayer(Bounds bounds, int z, boolean forward) {
        return forward ? z <= bounds.maxZ() : z >= bounds.minZ();
    }

    private static boolean isForwardRow(Bounds bounds, int y, int z) {
        boolean layerStartsAtMinX = bounds.depth() % 2 == 0 || (layerIndex(bounds, y) & 1) == 0;
        boolean rowStartsAtMinX = (rowIndex(bounds, y, z) & 1) == 0 ? layerStartsAtMinX : !layerStartsAtMinX;
        return rowStartsAtMinX;
    }

    private static int rowStartX(Bounds bounds, int y, int z) {
        return isForwardRow(bounds, y, z) ? bounds.minX() : bounds.maxX();
    }

    private static int layerIndex(Bounds bounds, int y) {
        return bounds.maxY() - y;
    }

    private static int rowIndex(Bounds bounds, int y, int z) {
        return isForwardLayer(bounds, y) ? z - bounds.minZ() : bounds.maxZ() - z;
    }

    private static boolean isWithinRow(Bounds bounds, int x, boolean forward) {
        return forward ? x <= bounds.maxX() : x >= bounds.minX();
    }

    private boolean isSkippable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.liquid() || isIgnoredPlant(state) || whitelist.contains(blockId(state));
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

    private void sendPathCommand(BlockPos target) {
        if (baritoneCooldownTicks > 0) {
            baritoneCooldownTicks--;
            return;
        }

        BaritoneBridge.runCommand("goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        baritoneCooldownTicks = BARITONE_COMMAND_COOLDOWN_TICKS;
    }

    private void pauseMovement() {
        breakingTarget = null;
        BaritoneBridge.runCommand("pause");
        BaritoneBridge.runCommand("cancel");
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
            sendPlayerMessage(client, "Set both Quarry corners first with /quarry pos1 and /quarry pos2.");
            return;
        }
        cursorX = bounds.minX();
        cursorY = bounds.maxY();
        cursorZ = bounds.minZ();
        currentTarget = null;
        breakingTarget = null;
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
        breakingTarget = null;
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
        pauseMovement();
        FeatureManager.INSTANCE.saveFeature(this);
        sendPlayerMessage(client, "Quarry stopped.");
    }

    private void warnIfReady(FabricClientCommandSource source) {
        Bounds bounds = bounds();
        if (bounds == null) {
            return;
        }

        source.sendFeedback(Component.literal("Quarry box: " + bounds.width() + " x " + bounds.height() + " x " + bounds.depth() + " (" + bounds.volume() + " blocks)."));
        warnIfLarge(source, bounds);
    }

    private void warnIfLarge(FabricClientCommandSource source, Bounds bounds) {
        if (bounds.volume() > largeTaskThreshold) {
            source.sendFeedback(Component.literal("Warning: Quarry selection contains " + bounds.volume() + " blocks."));
        }
    }

    private void warnIfLarge(Minecraft client, Bounds bounds) {
        if (bounds.volume() > largeTaskThreshold) {
            sendPlayerMessage(client, "Warning: Quarry selection contains " + bounds.volume() + " blocks.");
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

    private static Direction directionToward(Player player, BlockPos target) {
        Vec3 eyes = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
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
                .description(description("Wireframe color for the selected Quarry box."))
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

    private Option<Boolean> playerProximityAlertOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Player proximity alert"))
                .description(description("Pauses Quarry and plays an alert when another player is visible to the client."))
                .binding(true, () -> playerProximityAlertEnabled, value -> {
                    playerProximityAlertEnabled = value;
                    FeatureManager.INSTANCE.saveFeature(this);
                })
                .controller(BooleanControllerBuilder::create)
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

    private Option<String> whitelistOption() {
        return Option.<String>createBuilder()
                .name(Component.literal("Whitelist block ids"))
                .description(description("Comma-separated block ids Quarry will never break. Example: minecraft:diamond_ore, ancient_debris"))
                .binding("", this::whitelistConfigValue, this::setWhitelistConfigValue)
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

    private String whitelistConfigValue() {
        return whitelist.stream()
                .map(Identifier::toString)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private void setWhitelistConfigValue(String value) {
        whitelist.clear();
        if (value != null && !value.isBlank()) {
            for (String raw : value.split(",")) {
                Identifier id = resolveBlockId(raw.trim());
                if (id != null) {
                    whitelist.add(id);
                }
            }
        }
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
    }

    private static final class BaritoneBridge {
        private static final boolean AVAILABLE = findApiClass().isPresent() || findInternalProviderClass().isPresent();

        private BaritoneBridge() {
        }

        private static boolean isAvailable() {
            return AVAILABLE;
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
