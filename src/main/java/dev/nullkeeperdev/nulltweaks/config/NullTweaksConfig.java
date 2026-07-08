package dev.nullkeeperdev.nulltweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NullTweaksConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("nulltweaks.json");

    private final JsonObject root;

    private NullTweaksConfig(JsonObject root) {
        this.root = root;
    }

    public static NullTweaksConfig load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return new NullTweaksConfig(new JsonObject());
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return new NullTweaksConfig(parsed.getAsJsonObject());
            }
        } catch (IOException | JsonParseException exception) {
            NullTweaksClient.LOGGER.warn("Failed to load Null Tweaks config, falling back to defaults", exception);
        }

        return new NullTweaksConfig(new JsonObject());
    }

    public JsonObject getFeatureConfig(String featureId) {
        JsonElement existing = root.get(featureId);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }

        JsonObject featureConfig = new JsonObject();
        root.add(featureId, featureConfig);
        return featureConfig;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            NullTweaksClient.LOGGER.error("Failed to save Null Tweaks config", exception);
        }
    }

    public static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsBoolean();
        } catch (ClassCastException | IllegalStateException ignored) {
            return fallback;
        }
    }

    public static int getInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (ClassCastException | NumberFormatException | IllegalStateException ignored) {
            return fallback;
        }
    }

    public static double getDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsDouble();
        } catch (ClassCastException | NumberFormatException | IllegalStateException ignored) {
            return fallback;
        }
    }

    public static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsString();
        } catch (ClassCastException | IllegalStateException ignored) {
            return fallback;
        }
    }
}
