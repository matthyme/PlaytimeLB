package com.playtimelb.backfill;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class VanillaStatsReader {
    private static final Gson GSON = new Gson();

    // Returns seconds per UUID for everyone in world/stats/
    public static Map<UUID, Long> loadAllSeconds(MinecraftServer server) throws java.io.IOException {
        Path statsDir = server.getWorldPath(LevelResource.ROOT).resolve("stats");
        Map<UUID,Long> out = new HashMap<>();
        if (!Files.isDirectory(statsDir)) return out;

        try (var stream = Files.list(statsDir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".json")).collect(Collectors.toList())) {
                String base = p.getFileName().toString();
                base = base.substring(0, base.length() - 5);
                try {
                    UUID uuid = UUID.fromString(base);
                    try (BufferedReader r = Files.newBufferedReader(p)) {
                        JsonObject root = GSON.fromJson(r, JsonObject.class);
                        if (root == null) continue;
                        JsonObject stats = root.getAsJsonObject("stats");
                        if (stats == null) continue;
                        JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                        if (custom == null) continue;
                        JsonElement val = custom.get("minecraft:play_time");
                        if (val == null) val = custom.get("minecraft:play_one_minute");
                        long ticks = val != null ? val.getAsLong() : 0L;
                        long seconds = ticks / 20L;
                        out.put(uuid, seconds);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
    }
}
