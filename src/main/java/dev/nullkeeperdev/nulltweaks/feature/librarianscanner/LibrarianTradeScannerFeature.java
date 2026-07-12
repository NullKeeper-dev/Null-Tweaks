package dev.nullkeeperdev.nulltweaks.feature.librarianscanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.UUID;

public final class LibrarianTradeScannerFeature extends Feature {
    private static final int DEFAULT_SCAN_RADIUS = 20;
    private static final int PENDING_TIMEOUT_TICKS = 60;
    private static final int MANUAL_INTERACTION_GRACE_TICKS = 40;
    private static final Color DEFAULT_SEARCH_HIGHLIGHT_COLOR = new Color(0x55D6FF);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LibrarianTradeScannerFeature instance;
    private static boolean commandsRegistered;
    private static final SuggestionProvider<FabricClientCommandSource> ENCHANTMENT_SUGGESTIONS = (context, builder) -> {
        LibrarianTradeScannerFeature feature = instance;
        if (feature != null) {
            feature.suggestEnchantments(context.getSource(), builder);
        }
        return builder.buildFuture();
    };

    private final Map<Integer, ScanRecord> records = new HashMap<>();
    private final Map<UUID, CachedVillagerTrades> persistentCache = new HashMap<>();
    private final Queue<Integer> scanQueue = new ArrayDeque<>();
    private final Set<Integer> queuedEntityIds = new HashSet<>();
    private int scanRadius = DEFAULT_SCAN_RADIUS;
    private Color labelTextColor = new Color(0xFFD966);
    private Color searchHighlightColor = DEFAULT_SEARCH_HIGHLIGHT_COLOR;
    private double labelScale = 1.0D;
    private SearchCriteria activeSearch;
    private Integer pendingEntityId;
    private UUID pendingEntityUuid;
    private int pendingContainerId = -1;
    private long pendingStartedTick;
    private long manualInteractionGraceUntilTick;
    private String cacheIdentity;
    private Path cachePath;
    private boolean cacheDirty;
    private long nextCacheSaveTick;
    private boolean runtimeDisabled;

    public LibrarianTradeScannerFeature() {
        super("librarian_trade_scanner", "Librarian Trade Scanner (Experimental)", false);
        instance = this;
    }

    public static LibrarianTradeScannerFeature instance() {
        return instance;
    }

    public static void registerCommands() {
        if (commandsRegistered) {
            return;
        }

        commandsRegistered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommandTree(dispatcher));
    }

    @Override
    public void onEnable() {
        runtimeDisabled = false;
    }

    @Override
    public void onDisable() {
        savePersistentCacheNow();
        closePendingContainer();
        clearState();
        clearCacheState();
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
            tickScanner(client);
        } catch (RuntimeException exception) {
            disableForSession("client tick", exception);
        }
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Librarian Trade Scanner (Experimental)"))
                .description(description("This feature intercepts trade data in the background and may occasionally be slow or briefly inaccurate, especially near many villagers at once. If a trade looks wrong, manually opening that villager's trading menu will always show the correct data."))
                .collapsed(false)
                .option(scanRadiusOption())
                .option(labelTextColorOption())
                .option(labelScaleOption())
                .option(searchHighlightColorOption())
                .option(activeSearchLabelOption())
                .option(clearSearchButtonOption())
                .build());
    }

    private static void registerCommandTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("nulltweaks")
                .then(ClientCommands.literal("enchantfind")
                        .then(ClientCommands.literal("clear")
                                .executes(LibrarianTradeScannerFeature::clearSearchCommand))
                        .then(ClientCommands.argument("enchantment", StringArgumentType.word())
                                .suggests(ENCHANTMENT_SUGGESTIONS)
                                .executes(context -> setSearchCommand(context, false))
                                .then(ClientCommands.argument("level", StringArgumentType.word())
                                        .executes(context -> setSearchCommand(context, true))))));

        dispatcher.register(ClientCommands.literal("findenchant")
                .then(ClientCommands.literal("clear")
                        .executes(LibrarianTradeScannerFeature::clearSearchCommand))
                .then(ClientCommands.argument("enchantment", StringArgumentType.word())
                        .suggests(ENCHANTMENT_SUGGESTIONS)
                        .executes(context -> setSearchCommand(context, false))
                        .then(ClientCommands.argument("level", StringArgumentType.word())
                                .executes(context -> setSearchCommand(context, true)))));
    }

    private static int setSearchCommand(CommandContext<FabricClientCommandSource> context, boolean hasLevel) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null) {
            context.getSource().sendError(Component.literal("Null Tweaks is not initialized yet."));
            return 0;
        }

        String enchantment = StringArgumentType.getString(context, "enchantment");
        Integer level = null;
        if (hasLevel) {
            level = parseLevel(StringArgumentType.getString(context, "level"));
            if (level == null || level <= 0) {
                context.getSource().sendError(Component.literal("Invalid enchantment level."));
                return 0;
            }
        }

        SearchCriteria criteria = feature.resolveSearch(context.getSource(), enchantment, level);
        if (criteria == null) {
            context.getSource().sendError(Component.literal("Unknown enchantment: " + enchantment));
            return 0;
        }

        feature.setActiveSearch(criteria);
        context.getSource().sendFeedback(Component.literal("Searching librarian trades for " + criteria.displayName()));
        return 1;
    }

    private static int clearSearchCommand(CommandContext<FabricClientCommandSource> context) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature != null) {
            feature.clearActiveSearch();
        }

        context.getSource().sendFeedback(Component.literal("Cleared librarian enchant search."));
        return 1;
    }

    public static boolean shouldSuppressOpenScreen(ClientboundOpenScreenPacket packet) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.activeForScannerPackets() || feature.manualInteractionGraceActive()) {
            return false;
        }

        try {
            if (packet.getType() != MenuType.MERCHANT) {
                return false;
            }

            feature.pendingContainerId = packet.getContainerId();
            return true;
        } catch (RuntimeException exception) {
            feature.disableForSession("open-screen packet", exception);
            return false;
        }
    }

    public static boolean handleMerchantOffers(ClientboundMerchantOffersPacket packet) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.isEnabled() || feature.runtimeDisabled) {
            return false;
        }

        try {
            if (feature.pendingEntityId != null && !feature.manualInteractionGraceActive()) {
                feature.captureScannerOffers(packet);
                return true;
            }

            feature.captureManualOffers(packet);
            return false;
        } catch (RuntimeException exception) {
            feature.disableForSession("merchant-offers packet", exception);
            return false;
        }
    }

    public static void handleManualInteraction(Player player, Entity entity) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.isEnabled() || feature.runtimeDisabled || !(entity instanceof Villager villager)) {
            return;
        }

        try {
            feature.preemptForManualInteraction(player, villager);
        } catch (RuntimeException exception) {
            feature.disableForSession("manual interaction", exception);
        }
    }

    public static List<Component> labelsForEntity(int entityId) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.isEnabled() || feature.runtimeDisabled) {
            return List.of();
        }

        int color = feature.labelTextColor.getRGB() & 0xFFFFFF;
        if (feature.pendingEntityId != null && feature.pendingEntityId == entityId) {
            return List.of(Component.literal("Scanning...").withStyle(style -> style.withColor(color)));
        }

        ScanRecord record = feature.records.get(entityId);
        if (record == null || record.trades.isEmpty()) {
            return List.of();
        }

        return record.trades.stream()
                .map(trade -> (Component) Component.literal(trade.label(currentHeroOfTheVillageLevel())).withStyle(style -> style.withColor(color)))
                .toList();
    }

    public static float labelScale() {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.isEnabled() || feature.runtimeDisabled) {
            return 1.0F;
        }

        return (float) clampDouble(feature.labelScale, 0.5D, 2.0D);
    }

    public static Integer searchOutlineColorFor(LivingEntity entity) {
        LibrarianTradeScannerFeature feature = instance;
        if (feature == null || !feature.shouldHighlightEntity(entity)) {
            return null;
        }

        return net.minecraft.util.ARGB.opaque(feature.searchHighlightColor.getRGB() & 0xFFFFFF);
    }

    public static boolean shouldAppearGlowing(Entity entity) {
        LibrarianTradeScannerFeature feature = instance;
        return feature != null && feature.shouldHighlightEntity(entity);
    }

    @Override
    protected void loadSettings(JsonObject config) {
        scanRadius = clampInt(NullTweaksConfig.getInt(config, "scanRadius", DEFAULT_SCAN_RADIUS), 1, 64);
        labelTextColor = parseColor(NullTweaksConfig.getString(config, "labelTextColor", "#ffd966"), new Color(0xFFD966));
        searchHighlightColor = parseColor(NullTweaksConfig.getString(config, "searchHighlightColor", colorString(DEFAULT_SEARCH_HIGHLIGHT_COLOR)), DEFAULT_SEARCH_HIGHLIGHT_COLOR);
        labelScale = clampDouble(NullTweaksConfig.getDouble(config, "labelScale", 1.0D), 0.5D, 2.0D);
        activeSearch = loadSearch(config);
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("scanRadius", scanRadius);
        config.addProperty("labelTextColor", colorString(labelTextColor));
        config.addProperty("searchHighlightColor", colorString(searchHighlightColor));
        config.addProperty("labelScale", labelScale);
        if (activeSearch != null) {
            config.addProperty("searchEnchantment", activeSearch.enchantmentId().toString());
            if (activeSearch.level() != null) {
                config.addProperty("searchLevel", activeSearch.level());
            }
        } else {
            config.addProperty("searchEnchantment", "");
        }
    }

    private void tickScanner(Minecraft client) {
        if (client.level == null || client.player == null || client.getConnection() == null) {
            savePersistentCacheNow();
            clearState();
            clearCacheState();
            return;
        }

        ensureWorldCacheLoaded(client);
        ClientLevel level = client.level;
        long now = level.getGameTime();
        flushCacheIfDue(now);
        expirePendingScan(now);
        removeUnloadedEntities(level);
        discoverQueueCandidates(client, level);

        if (pendingEntityId == null) {
            startNextQueuedScan(client, level);
        }
    }

    private void expirePendingScan(long now) {
        if (pendingEntityId == null || now - pendingStartedTick <= PENDING_TIMEOUT_TICKS) {
            return;
        }

        closePendingContainer();
        pendingEntityId = null;
        pendingEntityUuid = null;
        pendingContainerId = -1;
        pendingStartedTick = 0L;
    }

    private void removeUnloadedEntities(ClientLevel level) {
        queuedEntityIds.removeIf(id -> level.getEntity(id) == null);
        scanQueue.removeIf(id -> level.getEntity(id) == null);
    }

    private void discoverQueueCandidates(Minecraft client, ClientLevel level) {
        double radiusSqr = (double) scanRadius * scanRadius;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }

            int entityId = villager.getId();
            ScanRecord record = records.computeIfAbsent(entityId, ignored -> new ScanRecord());
            record.uuid = villager.getUUID();
            Optional<ResourceKey<VillagerProfession>> professionKey = villager.getVillagerData().profession().unwrapKey();
            boolean librarian = villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
            if (librarian && record.lastProfessionKey.isEmpty() && applyCachedTrades(villager, record)) {
                record.lastProfessionKey = professionKey;
            }

            boolean professionChanged = record.lastProfessionKey
                    .map(previous -> !professionKey.map(previous::equals).orElse(false))
                    .orElse(true);
            record.lastProfessionKey = professionKey;

            if (!librarian) {
                record.trades = List.of();
                record.scanAttempted = false;
                removeCachedTrades(villager.getUUID());
                removeFromQueue(entityId);
                if (pendingEntityId != null && pendingEntityId == entityId) {
                    abandonPendingScan(true);
                }
                continue;
            }

            if (professionChanged) {
                record.trades = List.of();
                record.scanAttempted = false;
                removeCachedTrades(villager.getUUID());
                if (pendingEntityId != null && pendingEntityId == entityId) {
                    abandonPendingScan(true);
                }
            }

            if (!record.scanAttempted && villager.distanceToSqr(client.player) <= radiusSqr) {
                enqueue(entityId);
            }
        }
    }

    private void startNextQueuedScan(Minecraft client, ClientLevel level) {
        Integer nextEntityId = pollClosestQueuedVillager(client, level);
        if (nextEntityId == null) {
            return;
        }

        Entity entity = level.getEntity(nextEntityId);
        if (!(entity instanceof Villager villager)) {
            return;
        }

        ScanRecord record = records.computeIfAbsent(nextEntityId, ignored -> new ScanRecord());
        record.scanAttempted = true;
        pendingEntityId = nextEntityId;
        pendingEntityUuid = villager.getUUID();
        pendingContainerId = -1;
        pendingStartedTick = level.getGameTime();
        sendScanInteract(client, villager);
    }

    private Integer pollClosestQueuedVillager(Minecraft client, ClientLevel level) {
        List<Integer> candidates = new ArrayList<>();
        Integer id;
        while ((id = scanQueue.poll()) != null) {
            queuedEntityIds.remove(id);
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager villager && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                candidates.add(id);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(candidateId -> {
            Entity entity = level.getEntity(candidateId);
            return entity == null || client.player == null ? Double.MAX_VALUE : entity.distanceToSqr(client.player);
        }));

        for (int index = 1; index < candidates.size(); index++) {
            enqueue(candidates.get(index));
        }

        return candidates.get(0);
    }

    private void sendScanInteract(Minecraft client, Villager villager) {
        Vec3 hitLocation = new Vec3(0.0D, villager.getBbHeight() * 0.5D, 0.0D);
        client.getConnection().getConnection().send(new ServerboundInteractPacket(villager.getId(), InteractionHand.MAIN_HAND, hitLocation, false));
    }

    private void captureScannerOffers(ClientboundMerchantOffersPacket packet) {
        if (pendingEntityId == null) {
            return;
        }

        if (pendingContainerId < 0 || packet.getContainerId() != pendingContainerId) {
            abandonPendingScan(false);
            return;
        }

        ScanRecord record = records.computeIfAbsent(pendingEntityId, ignored -> new ScanRecord());
        record.trades = extractBookTrades(packet.getOffers());
        record.scanAttempted = true;
        record.uuid = pendingEntityUuid;
        if (pendingEntityUuid != null) {
            updatePersistentCache(pendingEntityUuid, record.trades);
        }
        closePendingContainer();
        pendingEntityId = null;
        pendingEntityUuid = null;
        pendingContainerId = -1;
        pendingStartedTick = 0L;
    }

    private void captureManualOffers(ClientboundMerchantOffersPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !(client.hitResult instanceof EntityHitResult hitResult)) {
            return;
        }

        if (!(hitResult.getEntity() instanceof Villager villager) || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        if (villager.distanceToSqr(client.player) > (double) scanRadius * scanRadius) {
            return;
        }

        ScanRecord record = records.computeIfAbsent(villager.getId(), ignored -> new ScanRecord());
        record.uuid = villager.getUUID();
        record.lastProfessionKey = villager.getVillagerData().profession().unwrapKey();
        record.trades = extractBookTrades(packet.getOffers());
        record.scanAttempted = true;
        updatePersistentCache(villager.getUUID(), record.trades);
        removeFromQueue(villager.getId());
    }

    private void preemptForManualInteraction(Player player, Villager villager) {
        ScanRecord record = records.get(villager.getId());
        if (record == null || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return;
        }

        if (player != null && villager.distanceToSqr(player) > (double) scanRadius * scanRadius) {
            return;
        }

        removeFromQueue(villager.getId());
        abandonPendingScan(true);
        manualInteractionGraceUntilTick = currentGameTime() + MANUAL_INTERACTION_GRACE_TICKS;
    }

    private void ensureWorldCacheLoaded(Minecraft client) {
        String identity = cacheIdentity(client);
        if (identity == null || identity.equals(cacheIdentity)) {
            return;
        }

        savePersistentCacheNow();
        clearState();
        persistentCache.clear();
        cacheIdentity = identity;
        cachePath = cacheDirectory().resolve(sanitizeCacheFileName(identity) + ".json");
        cacheDirty = false;
        nextCacheSaveTick = 0L;
        loadPersistentCache();
    }

    private String cacheIdentity(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            return "server_" + serverData.ip;
        }

        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            try {
                Path worldRoot = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
                Path fileName = worldRoot.getFileName();
                if (fileName != null) {
                    return "singleplayer_" + fileName + "_" + levelName;
                }
            } catch (RuntimeException ignored) {
                return "singleplayer_" + levelName;
            }

            return "singleplayer_" + levelName;
        }

        if (client.level != null) {
            return "unknown_" + client.level.dimension().identifier();
        }

        return null;
    }

    private void loadPersistentCache() {
        if (cachePath == null || !Files.isRegularFile(cachePath)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(cachePath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject villagers = root.has("villagers") && root.get("villagers").isJsonObject()
                    ? root.getAsJsonObject("villagers")
                    : root;
            for (Entry<String, JsonElement> entry : villagers.entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                CachedVillagerTrades cached = readCachedVillager(entry.getValue());
                if (cached != null) {
                    persistentCache.put(uuid, cached);
                }
            }
        } catch (RuntimeException | IOException exception) {
            persistentCache.clear();
            NullTweaksClient.LOGGER.warn("Failed to load librarian trade cache from {}", cachePath, exception);
        }
    }

    private CachedVillagerTrades readCachedVillager(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        JsonArray tradesJson = object.has("trades") && object.get("trades").isJsonArray()
                ? object.getAsJsonArray("trades")
                : new JsonArray();
        List<BookTrade> trades = new ArrayList<>();
        for (JsonElement tradeElement : tradesJson) {
            BookTrade trade = readBookTrade(tradeElement);
            if (trade != null) {
                trades.add(trade);
            }
        }

        String lastScanned = object.has("lastScanned") ? object.get("lastScanned").getAsString() : "";
        return new CachedVillagerTrades(List.copyOf(trades), lastScanned);
    }

    private BookTrade readBookTrade(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        try {
            Identifier enchantmentId = Identifier.tryParse(object.get("enchantment").getAsString());
            int level = object.get("level").getAsInt();
            int basePrice = object.has("basePrice") ? object.get("basePrice").getAsInt() : object.has("price") ? object.get("price").getAsInt() : 0;
            String enchantmentName = object.has("name") ? object.get("name").getAsString() : searchDisplayName(enchantmentId, level);
            if (enchantmentId == null || level <= 0 || basePrice <= 0) {
                return null;
            }

            return new BookTrade(enchantmentId, level, basePrice, enchantmentName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean applyCachedTrades(Villager villager, ScanRecord record) {
        CachedVillagerTrades cached = persistentCache.get(villager.getUUID());
        if (cached == null) {
            return false;
        }

        record.uuid = villager.getUUID();
        record.trades = cached.trades();
        record.scanAttempted = true;
        return true;
    }

    private void updatePersistentCache(UUID uuid, List<BookTrade> trades) {
        if (uuid == null) {
            return;
        }

        persistentCache.put(uuid, new CachedVillagerTrades(List.copyOf(trades), Instant.now().toString()));
        markCacheDirty();
    }

    private void removeCachedTrades(UUID uuid) {
        if (uuid != null && persistentCache.remove(uuid) != null) {
            markCacheDirty();
        }
    }

    private void markCacheDirty() {
        cacheDirty = true;
        long now = currentGameTime();
        if (nextCacheSaveTick == 0L) {
            nextCacheSaveTick = now + 40L;
        }
    }

    private void flushCacheIfDue(long now) {
        if (cacheDirty && nextCacheSaveTick > 0L && now >= nextCacheSaveTick) {
            savePersistentCacheNow();
        }
    }

    private void savePersistentCacheNow() {
        if (!cacheDirty || cachePath == null) {
            return;
        }

        try {
            Files.createDirectories(cachePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("world", cacheIdentity == null ? "" : cacheIdentity);
            JsonObject villagers = new JsonObject();
            for (Entry<UUID, CachedVillagerTrades> entry : persistentCache.entrySet()) {
                villagers.add(entry.getKey().toString(), writeCachedVillager(entry.getValue()));
            }
            root.add("villagers", villagers);
            Files.writeString(cachePath, GSON.toJson(root), StandardCharsets.UTF_8);
            cacheDirty = false;
            nextCacheSaveTick = 0L;
        } catch (IOException | RuntimeException exception) {
            NullTweaksClient.LOGGER.warn("Failed to save librarian trade cache to {}", cachePath, exception);
            nextCacheSaveTick = currentGameTime() + 200L;
        }
    }

    private JsonObject writeCachedVillager(CachedVillagerTrades cached) {
        JsonObject object = new JsonObject();
        object.addProperty("lastScanned", cached.lastScanned());
        JsonArray tradesJson = new JsonArray();
        for (BookTrade trade : cached.trades()) {
            JsonObject tradeJson = new JsonObject();
            tradeJson.addProperty("enchantment", trade.enchantmentId().toString());
            tradeJson.addProperty("level", trade.level());
            tradeJson.addProperty("basePrice", trade.basePrice());
            tradeJson.addProperty("name", trade.enchantmentName());
            tradesJson.add(tradeJson);
        }
        object.add("trades", tradesJson);
        return object;
    }

    private void clearCacheState() {
        persistentCache.clear();
        cacheIdentity = null;
        cachePath = null;
        cacheDirty = false;
        nextCacheSaveTick = 0L;
    }

    private static Path cacheDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("nulltweaks").resolve("librarian_cache");
    }

    private static String sanitizeCacheFileName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private boolean shouldHighlightEntity(Entity entity) {
        if (!isEnabled() || runtimeDisabled || activeSearch == null || !(entity instanceof Villager villager)) {
            return false;
        }

        if (!villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            return false;
        }

        ScanRecord record = records.get(villager.getId());
        return record != null && record.trades.stream().anyMatch(activeSearch::matches);
    }

    private void setActiveSearch(SearchCriteria criteria) {
        activeSearch = criteria;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private void clearActiveSearch() {
        activeSearch = null;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private SearchCriteria resolveSearch(FabricClientCommandSource source, String rawEnchantment, Integer level) {
        Identifier id = resolveEnchantmentId(source, rawEnchantment);
        if (id == null) {
            return null;
        }

        String displayName = displayNameForSearch(source, id, level);
        return new SearchCriteria(id, level, displayName);
    }

    private Identifier resolveEnchantmentId(FabricClientCommandSource source, String rawEnchantment) {
        String normalized = normalizeSearchName(rawEnchantment);
        return source.getLevel().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> {
                    Identifier parsed = Identifier.tryParse(rawEnchantment.contains(":") ? rawEnchantment : "minecraft:" + rawEnchantment);
                    if (parsed != null && registry.containsKey(parsed)) {
                        return Optional.of(parsed);
                    }

                    for (Entry<ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
                        Identifier id = entry.getKey().identifier();
                        if (normalizeSearchName(id.getPath()).equals(normalized) || normalizeSearchName(id.toString()).equals(normalized)) {
                            return Optional.of(id);
                        }

                        Holder<Enchantment> holder = registry.wrapAsHolder(entry.getValue());
                        String displayName = Enchantment.getFullname(holder, Math.max(1, entry.getValue().getMaxLevel() > 1 ? 1 : entry.getValue().getMaxLevel())).getString();
                        if (normalizeSearchName(displayName).equals(normalized)) {
                            return Optional.of(id);
                        }
                    }

                    return Optional.empty();
                })
                .orElse(null);
    }

    private String displayNameForSearch(FabricClientCommandSource source, Identifier enchantmentId, Integer level) {
        return source.getLevel().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getOptional(enchantmentId)
                        .map(enchantment -> Enchantment.getFullname(registry.wrapAsHolder(enchantment), level == null ? 1 : level).getString()))
                .orElseGet(() -> enchantmentId.getPath());
    }

    private void suggestEnchantments(FabricClientCommandSource source, SuggestionsBuilder builder) {
        source.getLevel().registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(registry -> {
            for (ResourceKey<Enchantment> key : registry.registryKeySet()) {
                Identifier id = key.identifier();
                builder.suggest(id.getPath());
                builder.suggest(id.toString());
            }
        });
    }

    private static Integer parseLevel(String rawLevel) {
        try {
            return Integer.parseInt(rawLevel);
        } catch (NumberFormatException ignored) {
            return switch (rawLevel.toUpperCase(Locale.ROOT)) {
                case "I" -> 1;
                case "II" -> 2;
                case "III" -> 3;
                case "IV" -> 4;
                case "V" -> 5;
                case "VI" -> 6;
                case "VII" -> 7;
                case "VIII" -> 8;
                case "IX" -> 9;
                case "X" -> 10;
                default -> null;
            };
        }
    }

    private static String normalizeSearchName(String value) {
        return value.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_", "").replace(" ", "");
    }

    private SearchCriteria loadSearch(JsonObject config) {
        String enchantmentId = NullTweaksConfig.getString(config, "searchEnchantment", "");
        if (enchantmentId.isBlank()) {
            return null;
        }

        Identifier id = Identifier.tryParse(enchantmentId);
        if (id == null) {
            return null;
        }

        int level = NullTweaksConfig.getInt(config, "searchLevel", 0);
        Integer optionalLevel = level > 0 ? level : null;
        return new SearchCriteria(id, optionalLevel, searchDisplayName(id, optionalLevel));
    }

    private String activeSearchDisplay() {
        return activeSearch == null ? "No active search" : "Currently searching: " + activeSearch.displayName();
    }

    private String searchDisplayName(Identifier id, Integer level) {
        String baseName = id.getPath().replace('_', ' ');
        String[] words = baseName.split(" ");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!display.isEmpty()) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        if (level != null) {
            display.append(' ').append(romanNumeral(level));
        }

        return display.toString();
    }

    private static String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }

    private List<BookTrade> extractBookTrades(MerchantOffers offers) {
        List<BookTrade> trades = new ArrayList<>();
        int heroLevel = currentHeroOfTheVillageLevel();
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (!result.is(Items.ENCHANTED_BOOK)) {
                continue;
            }

            int observedPrice = emeraldPrice(offer);
            if (observedPrice <= 0) {
                continue;
            }

            int basePrice = reconstructBasePrice(observedPrice, heroLevel);

            ItemEnchantments enchantments = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
                if (key.isEmpty()) {
                    continue;
                }

                int level = entry.getIntValue();
                String name = Enchantment.getFullname(entry.getKey(), level).getString();
                trades.add(new BookTrade(key.get().identifier(), level, basePrice, name));
            }
        }

        return List.copyOf(trades);
    }

    private int emeraldPrice(MerchantOffer offer) {
        ItemStack costA = offer.getCostA();
        if (costA.is(Items.EMERALD)) {
            return costA.getCount();
        }

        ItemStack costB = offer.getCostB();
        if (costB.is(Items.EMERALD)) {
            return costB.getCount();
        }

        return 0;
    }

    private static int currentHeroOfTheVillageLevel() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return 0;
        }

        MobEffectInstance effect = client.player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        return effect == null ? 0 : Math.max(1, effect.getAmplifier() + 1);
    }

    private static int reconstructBasePrice(int observedPrice, int heroLevel) {
        if (heroLevel <= 0) {
            return observedPrice;
        }

        double discountFraction = heroDiscountFraction(heroLevel);
        int estimate = Math.max(observedPrice, (int) Math.round(observedPrice / Math.max(0.1D, 1.0D - discountFraction)));
        int maxCandidate = Math.max(estimate + 16, observedPrice + 64);
        for (int candidate = observedPrice; candidate <= maxCandidate; candidate++) {
            if (discountedPrice(candidate, heroLevel) == observedPrice) {
                return candidate;
            }
        }

        return Math.max(observedPrice, estimate);
    }

    private static int discountedPrice(int basePrice, int heroLevel) {
        if (heroLevel <= 0) {
            return basePrice;
        }

        int discount = Math.max(1, (int) Math.floor(basePrice * heroDiscountFraction(heroLevel)));
        return Math.max(1, basePrice - discount);
    }

    private static double heroDiscountFraction(int heroLevel) {
        int clampedLevel = clampInt(heroLevel, 1, 5);
        return 0.3D + 0.0625D * (clampedLevel - 1);
    }

    private void enqueue(int entityId) {
        if (pendingEntityId != null && pendingEntityId == entityId) {
            return;
        }

        if (queuedEntityIds.add(entityId)) {
            scanQueue.add(entityId);
        }
    }

    private void removeFromQueue(int entityId) {
        if (queuedEntityIds.remove(entityId)) {
            scanQueue.remove(entityId);
        }
    }

    private boolean activeForScannerPackets() {
        return isEnabled() && !runtimeDisabled && pendingEntityId != null;
    }

    private boolean manualInteractionGraceActive() {
        return currentGameTime() <= manualInteractionGraceUntilTick;
    }

    private long currentGameTime() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0L : client.level.getGameTime();
    }

    private void abandonPendingScan(boolean closeContainer) {
        if (closeContainer) {
            closePendingContainer();
        }

        pendingEntityId = null;
        pendingEntityUuid = null;
        pendingContainerId = -1;
        pendingStartedTick = 0L;
    }

    private void closePendingContainer() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null && pendingContainerId >= 0) {
            client.getConnection().getConnection().send(new ServerboundContainerClosePacket(pendingContainerId));
        }
    }

    private void clearState() {
        records.clear();
        scanQueue.clear();
        queuedEntityIds.clear();
        pendingEntityId = null;
        pendingEntityUuid = null;
        pendingContainerId = -1;
        pendingStartedTick = 0L;
        manualInteractionGraceUntilTick = 0L;
    }

    private void disableForSession(String hook, RuntimeException exception) {
        runtimeDisabled = true;
        closePendingContainer();
        clearState();
        NullTweaksClient.LOGGER.error("Disabling feature {} for this session after failure in {}", id(), hook, exception);
    }

    private Option<Integer> scanRadiusOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Scan radius"))
                .description(description("Maximum distance in blocks for automatic librarian trade scans."))
                .binding(DEFAULT_SCAN_RADIUS, this::scanRadius, this::setScanRadius)
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(1, 64)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + " blocks")))
                .instant(true)
                .build();
    }

    private Option<Color> labelTextColorOption() {
        return Option.<Color>createBuilder()
                .name(Component.literal("Label text color"))
                .description(description("Text color for enchanted book labels."))
                .binding(new Color(0xFFD966), this::labelTextColor, this::setLabelTextColor)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(false))
                .instant(true)
                .build();
    }

    private Option<Double> labelScaleOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Label scale"))
                .description(description("Multiplies the vanilla label size."))
                .binding(1.0D, this::configuredLabelScale, this::setLabelScale)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(0.5D, 2.0D)
                        .step(0.05D)
                        .valueFormatter(value -> Component.literal(String.format("%.2fx", value))))
                .instant(true)
                .build();
    }

    private Option<Color> searchHighlightColorOption() {
        return Option.<Color>createBuilder()
                .name(Component.literal("Enchant search highlight color"))
                .description(description("Through-wall glow color for librarians matching the active enchant search."))
                .binding(DEFAULT_SEARCH_HIGHLIGHT_COLOR, this::searchHighlightColor, this::setSearchHighlightColor)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(false))
                .instant(true)
                .build();
    }

    private LabelOption activeSearchLabelOption() {
        return LabelOption.create(Component.literal(activeSearchDisplay()));
    }

    private ButtonOption clearSearchButtonOption() {
        return ButtonOption.createBuilder()
                .name(Component.literal("Clear search"))
                .description(description("Clears the active enchant search highlight."))
                .text(Component.literal("Clear"))
                .action(screen -> clearActiveSearch())
                .build();
    }

    private int scanRadius() {
        return scanRadius;
    }

    private void setScanRadius(int value) {
        scanRadius = clampInt(value, 1, 64);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color labelTextColor() {
        return labelTextColor;
    }

    private void setLabelTextColor(Color color) {
        labelTextColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color searchHighlightColor() {
        return searchHighlightColor;
    }

    private void setSearchHighlightColor(Color color) {
        searchHighlightColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private double configuredLabelScale() {
        return labelScale;
    }

    private void setLabelScale(double value) {
        labelScale = clampDouble(value, 0.5D, 2.0D);
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

    private static Color withoutAlpha(Color color) {
        return new Color(color.getRGB() & 0xFFFFFF);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ScanRecord {
        private UUID uuid;
        private Optional<ResourceKey<VillagerProfession>> lastProfessionKey = Optional.empty();
        private boolean scanAttempted;
        private List<BookTrade> trades = List.of();
    }

    private record CachedVillagerTrades(List<BookTrade> trades, String lastScanned) {
    }

    private record BookTrade(Identifier enchantmentId, int level, int basePrice, String enchantmentName) {
        private String label(int heroLevel) {
            return enchantmentName + ": " + discountedPrice(basePrice, heroLevel);
        }
    }

    private record SearchCriteria(Identifier enchantmentId, Integer level, String displayName) {
        private boolean matches(BookTrade trade) {
            return trade.enchantmentId().equals(enchantmentId) && (level == null || trade.level() == level);
        }
    }
}
