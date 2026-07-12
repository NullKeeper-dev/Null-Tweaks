package dev.nullkeeperdev.nulltweaks.feature.freecam;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
    private boolean showHand = true;
    private boolean active;
    private Marker cameraEntity;
    private Boolean previousSmartCull;

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
        if (feature != null && feature.isEnabled() && feature.active && feature.cameraEntity != null) {
            feature.cameraEntity.turn(deltaX, deltaY);
        }
    }

    public static Entity cameraEntity() {
        FreecamFeature feature = instance;
        if (feature != null && feature.isEnabled() && feature.active) {
            return feature.cameraEntity;
        }

        return null;
    }

    public static CameraState cameraState() {
        return cameraState(1.0F);
    }

    public static CameraState cameraState(float tickDelta) {
        FreecamFeature feature = instance;
        if (feature == null || !feature.isEnabled() || !feature.active || feature.cameraEntity == null) {
            return null;
        }

        float partialTick = Mth.clamp(tickDelta, 0.0F, 1.0F);
        Vec3 position = new Vec3(
                Mth.lerp((double) partialTick, feature.cameraEntity.xo, feature.cameraEntity.getX()),
                Mth.lerp((double) partialTick, feature.cameraEntity.yo, feature.cameraEntity.getY()),
                Mth.lerp((double) partialTick, feature.cameraEntity.zo, feature.cameraEntity.getZ()));
        return new CameraState(position, feature.cameraEntity.getYRot(), feature.cameraEntity.getXRot());
    }

    public static boolean shouldRenderHand() {
        FreecamFeature feature = instance;
        return feature == null || !feature.isEnabled() || !feature.active || feature.showHand;
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

        client.smartCull = false;
        moveCamera(client);
        clearPlayerInput(client.player);
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Freecam"))
                .collapsed(false)
                .option(speedOption())
                .option(showHandOption())
                .option(keybindButton())
                .build());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        movementSpeed = clampDouble(NullTweaksConfig.getDouble(config, "movementSpeed", 0.3D), 0.05D, 3.0D);
        showHand = NullTweaksConfig.getBoolean(config, "showHand", true);
        active = false;
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("movementSpeed", movementSpeed);
        config.addProperty("showHand", showHand);
    }

    private void activate(Minecraft client) {
        if (!isUsable(client)) {
            return;
        }

        LocalPlayer player = client.player;
        active = true;
        previousSmartCull = client.smartCull;
        client.smartCull = false;

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

        resetPlayerMovementState(player);
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private void deactivate(Minecraft client) {
        if (!active) {
            clearRuntimeState();
            return;
        }

        if (client.player != null) {
            resetPlayerMovementState(client.player);
        }

        restoreSmartCull(client);
        clearRuntimeState();
    }

    private void clearRuntimeState() {
        active = false;
        cameraEntity = null;
        previousSmartCull = null;
    }

    private void restoreSmartCull(Minecraft client) {
        if (client != null && previousSmartCull != null) {
            client.smartCull = previousSmartCull;
        }
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

        double yawRadians = Math.toRadians(cameraEntity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
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
                .description(description("Controls how fast the detached camera moves each client tick."))
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
                .description(description("Opens Minecraft's keybind screen so you can bind the live Freecam toggle."))
                .text(TOGGLE_KEY.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                .build();
    }

    private Option<Boolean> showHandOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Show hand while in freecam"))
                .description(description("Keeps the first-person hand visible while the camera is detached."))
                .binding(true, this::showHand, this::setShowHand)
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private double movementSpeed() {
        return movementSpeed;
    }

    private void setMovementSpeed(double value) {
        movementSpeed = clampDouble(value, 0.05D, 3.0D);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean showHand() {
        return showHand;
    }

    private void setShowHand(boolean value) {
        showHand = value;
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

    private static void resetPlayerMovementState(LocalPlayer player) {
        clearPlayerInput(player);
        player.xxa = 0.0F;
        player.yya = 0.0F;
        player.zza = 0.0F;
        player.setJumping(false);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
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

    public record CameraState(Vec3 position, float yRot, float xRot) {
    }
}
