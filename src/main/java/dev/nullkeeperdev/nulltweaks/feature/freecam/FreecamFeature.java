package dev.nullkeeperdev.nulltweaks.feature.freecam;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class FreecamFeature extends Feature {
    private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.freecam.toggle",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));

    private static FreecamFeature instance;

    private double movementSpeed = 0.3D;
    private boolean active;
    private Marker cameraEntity;
    private Entity previousCameraEntity;
    private FrozenPlayerState frozenPlayerState;

    public FreecamFeature() {
        super("freecam", "Freecam", true);
        instance = this;
    }

    public static boolean isActive() {
        FreecamFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.active;
    }

    public static void turnCamera(double deltaX, double deltaY) {
        FreecamFeature feature = instance;
        if (feature != null && feature.active && feature.cameraEntity != null) {
            feature.cameraEntity.turn(deltaX, deltaY);
        }
    }

    public static void freezeLocalPlayer(LocalPlayer player) {
        FreecamFeature feature = instance;
        if (feature != null && feature.active && feature.frozenPlayerState != null) {
            feature.frozenPlayerState.applyIfMatches(player);
        }
    }

    @Override
    public void onDisable() {
        deactivate(Minecraft.getInstance());
    }

    @Override
    public boolean listensForClientTicks() {
        return true;
    }

    @Override
    public void onClientTick(Minecraft client) {
        while (TOGGLE_KEY.consumeClick()) {
            if (active) {
                deactivate(client);
            } else {
                activate(client);
            }
        }

        if (!active) {
            return;
        }

        if (!isUsable(client)) {
            deactivate(client);
            return;
        }

        frozenPlayerState.applyIfMatches(client.player);
        moveCamera(client);
        frozenPlayerState.applyIfMatches(client.player);
    }

    @Override
    public boolean listensForHudRender() {
        return true;
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        if (!active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        graphics.text(client.font, "Freecam", 8, 8, 0xE6FFFFFF);
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Freecam"))
                .collapsed(false)
                .option(speedOption())
                .option(keybindButton())
                .build());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        movementSpeed = clampDouble(NullTweaksConfig.getDouble(config, "movementSpeed", 0.3D), 0.05D, 3.0D);
        active = false;
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("movementSpeed", movementSpeed);
    }

    private void activate(Minecraft client) {
        if (!isUsable(client)) {
            return;
        }

        LocalPlayer player = client.player;
        active = true;
        previousCameraEntity = client.getCameraEntity();
        frozenPlayerState = FrozenPlayerState.capture(player);

        cameraEntity = new Marker(markerEntityType(), client.level);
        Vec3 cameraStart = player.getEyePosition();
        cameraEntity.setPos(cameraStart);
        cameraEntity.xo = cameraStart.x;
        cameraEntity.yo = cameraStart.y;
        cameraEntity.zo = cameraStart.z;
        cameraEntity.xOld = cameraStart.x;
        cameraEntity.yOld = cameraStart.y;
        cameraEntity.zOld = cameraStart.z;
        cameraEntity.noPhysics = true;
        cameraEntity.setNoGravity(true);
        cameraEntity.setYRot(player.getYRot());
        cameraEntity.setXRot(player.getXRot());
        cameraEntity.yRotO = player.getYRot();
        cameraEntity.xRotO = player.getXRot();
        cameraEntity.setDeltaMovement(Vec3.ZERO);
        cameraEntity.setOldPosAndRot();

        clearPlayerInput(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0D;
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }

        client.setCameraEntity(cameraEntity);
        frozenPlayerState.applyIfMatches(player);
    }

    private void deactivate(Minecraft client) {
        if (!active) {
            clearRuntimeState();
            return;
        }

        LocalPlayer player = client.player;
        if (player != null && frozenPlayerState != null) {
            frozenPlayerState.applyIfMatches(player);
            clearPlayerInput(player);
        }

        if (client != null && player != null) {
            Entity restoreTarget = previousCameraEntity;
            if (restoreTarget == null || restoreTarget == cameraEntity || restoreTarget.isRemoved()) {
                restoreTarget = player;
            }
            client.setCameraEntity(restoreTarget);
        }

        clearRuntimeState();
    }

    private void clearRuntimeState() {
        active = false;
        cameraEntity = null;
        previousCameraEntity = null;
        frozenPlayerState = null;
    }

    private boolean isUsable(Minecraft client) {
        return client != null && client.level != null && client.player != null;
    }

    private void moveCamera(Minecraft client) {
        if (cameraEntity == null) {
            return;
        }

        Vec3 movement = movementVector(client);
        cameraEntity.setOldPosAndRot();
        cameraEntity.setDeltaMovement(movement);
        if (movement.lengthSqr() > 0.0D) {
            cameraEntity.setPos(cameraEntity.position().add(movement));
        }
        cameraEntity.noPhysics = true;
        cameraEntity.setNoGravity(true);
        cameraEntity.fallDistance = 0.0D;
    }

    private Vec3 movementVector(Minecraft client) {
        double forwardInput = (client.options.keyUp.isDown() ? 1.0D : 0.0D)
                - (client.options.keyDown.isDown() ? 1.0D : 0.0D);
        double strafeInput = (client.options.keyRight.isDown() ? 1.0D : 0.0D)
                - (client.options.keyLeft.isDown() ? 1.0D : 0.0D);
        double verticalInput = (client.options.keyJump.isDown() ? 1.0D : 0.0D)
                - (client.options.keyShift.isDown() ? 1.0D : 0.0D);

        if (forwardInput == 0.0D && strafeInput == 0.0D && verticalInput == 0.0D) {
            return Vec3.ZERO;
        }

        Vec3 forward = cameraEntity.getLookAngle();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        if (right.lengthSqr() > 1.0E-7D) {
            right = right.normalize();
        } else {
            right = Vec3.ZERO;
        }

        Vec3 movement = forward.scale(forwardInput)
                .add(right.scale(strafeInput))
                .add(0.0D, verticalInput, 0.0D);
        if (movement.lengthSqr() <= 1.0E-7D) {
            return Vec3.ZERO;
        }

        return movement.normalize().scale(movementSpeed);
    }

    private Option<Double> speedOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Movement speed"))
                .binding(0.3D, this::movementSpeed, this::setMovementSpeed)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(0.05D, 3.0D)
                        .step(0.05D)
                        .valueFormatter(value -> Component.literal(String.format("%.2f blocks/tick", value))))
                .instant(true)
                .build();
    }

    private ButtonOption keybindButton() {
        return ButtonOption.createBuilder()
                .name(Component.literal("Toggle keybind"))
                .text(TOGGLE_KEY.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                .build();
    }

    private double movementSpeed() {
        return movementSpeed;
    }

    private void setMovementSpeed(double value) {
        movementSpeed = clampDouble(value, 0.05D, 3.0D);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static void clearPlayerInput(LocalPlayer player) {
        if (player.input != null) {
            player.input.keyPresses = Input.EMPTY;
            if (player.input instanceof FreecamClientInputAccess access) {
                access.nulltweaks$clearFreecamMovement();
            }
        }
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static EntityType<?> markerEntityType() {
        for (String className : new String[]{"net.minecraft.world.entity.EntityTypes", "net.minecraft.world.entity.EntityType"}) {
            try {
                Object marker = Class.forName(className).getField("MARKER").get(null);
                if (marker instanceof EntityType<?> markerType) {
                    return markerType;
                }
            } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException ignored) {
            }
        }

        throw new IllegalStateException("Unable to resolve marker entity type");
    }

    private record FrozenPlayerState(LocalPlayer player, Vec3 position, float yRot, float xRot) {
        static FrozenPlayerState capture(LocalPlayer player) {
            return new FrozenPlayerState(player, player.position(), player.getYRot(), player.getXRot());
        }

        void applyIfMatches(LocalPlayer currentPlayer) {
            if (currentPlayer != player) {
                return;
            }

            currentPlayer.setPos(position);
            currentPlayer.xo = position.x;
            currentPlayer.yo = position.y;
            currentPlayer.zo = position.z;
            currentPlayer.xOld = position.x;
            currentPlayer.yOld = position.y;
            currentPlayer.zOld = position.z;
            currentPlayer.setYRot(yRot);
            currentPlayer.setXRot(xRot);
            currentPlayer.yRotO = yRot;
            currentPlayer.xRotO = xRot;
            currentPlayer.setDeltaMovement(Vec3.ZERO);
            currentPlayer.fallDistance = 0.0D;
            clearPlayerInput(currentPlayer);
        }
    }
}
