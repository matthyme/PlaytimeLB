package com.playtimelb.store;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlaytimeStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String FILE = "playtimelb-data.json";

    // In-memory state (server thread)
    private static Map<UUID, Long> totalsSec = new HashMap<>();   // total seconds
    private static Map<UUID, Long> activeStartNs = new HashMap<>(); // session start time (ns)
    private static Map<UUID, String> lastName = new HashMap<>();  // last seen name
    private static boolean loaded = false;
    private static boolean dirty = false;

    private static Path filePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    private static void ensureLoaded(MinecraftServer server) throws IOException {
        if (loaded) return;
        load(server);
        loaded = true;
    }

    public static synchronized void load(MinecraftServer server) throws IOException {
        totalsSec.clear(); activeStartNs.clear(); lastName.clear();
        Path p = filePath(server);
        if (!Files.exists(p)) return;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            JsonObject totals = root.getAsJsonObject("totals_sec");
            if (totals != null) {
                for (var e : totals.entrySet()) {
                    try { totalsSec.put(UUID.fromString(e.getKey()), e.getValue().getAsLong()); } catch (IllegalArgumentException ignored) {}
                }
            }
            JsonObject active = root.getAsJsonObject("active_ns");
            if (active != null) {
                for (var e : active.entrySet()) {
                    try { activeStartNs.put(UUID.fromString(e.getKey()), e.getValue().getAsLong()); } catch (IllegalArgumentException ignored) {}
                }
            }
            JsonObject names = root.getAsJsonObject("names");
            if (names != null) {
                for (var e : names.entrySet()) {
                    try { lastName.put(UUID.fromString(e.getKey()), e.getValue().getAsString()); } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    public static synchronized void save(MinecraftServer server) throws IOException {
        Path p = filePath(server);
        JsonObject root = new JsonObject();
        root.addProperty("updated", Instant.now().getEpochSecond());
        JsonObject totals = new JsonObject();
        for (var e : totalsSec.entrySet()) totals.addProperty(e.getKey().toString(), e.getValue());
        root.add("totals_sec", totals);

        JsonObject active = new JsonObject();
        for (var e : activeStartNs.entrySet()) active.addProperty(e.getKey().toString(), e.getValue());
        root.add("active_ns", active);

        JsonObject names = new JsonObject();
        for (var e : lastName.entrySet()) names.addProperty(e.getKey().toString(), e.getValue());
        root.add("names", names);

        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write(GSON.toJson(root));
        }
        dirty = false;
    }

    public static synchronized void checkpoint(MinecraftServer server) throws IOException {
        if (!loaded) return;
        if (dirty) save(server);
    }

    public static synchronized void reset(MinecraftServer server) throws IOException {
        ensureLoaded(server);
        totalsSec.clear();
        activeStartNs.clear();
        dirty = true;
        save(server);
    }

    public static synchronized void onLogin(MinecraftServer server, UUID uuid, String name, long nowNs) throws IOException {
        ensureLoaded(server);
        activeStartNs.put(uuid, nowNs);
        if (name != null) lastName.put(uuid, name);
        dirty = true;
        save(server); // persist start immediately in case of crash
    }

    public static synchronized long onLogout(MinecraftServer server, UUID uuid, String name, long endNs) throws IOException {
        ensureLoaded(server);
        Long start = activeStartNs.remove(uuid);
        if (name != null) lastName.put(uuid, name);
        long add = 0L;
        if (start != null && endNs >= start) {
            add = (endNs - start) / 1_000_000_000L;
            totalsSec.put(uuid, totalsSec.getOrDefault(uuid, 0L) + add);
        }
        dirty = true;
        save(server);
        return add;
    }

    public static synchronized Map<UUID, Long> getTotalsIncludingActive(MinecraftServer server) throws IOException {
        ensureLoaded(server);
        long nowNs = System.nanoTime(); // not epoch, but we'll estimate using System.nanoTime
        // Better: use epoch ns; but for display, difference works either way only if we had start from same base.
        // We used epoch ns for start; so use epoch now instead:
        long epochNowNs = java.time.Instant.now().getEpochSecond() * 1_000_000_000L + java.time.Instant.now().getNano();
        Map<UUID, Long> out = new HashMap<>(totalsSec);
        for (var e : activeStartNs.entrySet()) {
            long extra = Math.max(0L, (epochNowNs - e.getValue()) / 1_000_000_000L);
            out.put(e.getKey(), out.getOrDefault(e.getKey(), 0L) + extra);
        }
        return out;
    }

    public static synchronized long getTotalFor(MinecraftServer server, UUID uuid, boolean includeActive) throws IOException {
        ensureLoaded(server);
        long base = totalsSec.getOrDefault(uuid, 0L);
        if (includeActive && activeStartNs.containsKey(uuid)) {
            long epochNowNs = java.time.Instant.now().getEpochSecond() * 1_000_000_000L + java.time.Instant.now().getNano();
            base += Math.max(0L, (epochNowNs - activeStartNs.get(uuid)) / 1_000_000_000L);
        }
        return base;
    }

    public static synchronized Map<UUID, String> getNames(MinecraftServer server) throws IOException {
        ensureLoaded(server);
        // Also fill from online players
        server.getPlayerList().getPlayers().forEach(p -> lastName.put(p.getUUID(), p.getGameProfile().getName()));
        return new HashMap<>(lastName);
    }

    public static synchronized UUID lookupUUID(MinecraftServer server, String name) throws IOException {
        ensureLoaded(server);
        // online first
        var online = server.getPlayerList().getPlayers();
        for (var p : online) if (p.getGameProfile().getName().equalsIgnoreCase(name)) return p.getUUID();
        // stored names
        for (var e : lastName.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    public static String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%dd %dh %dm", days, hours, minutes);
    }
}
