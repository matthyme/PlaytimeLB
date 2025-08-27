package com.playtimelb.backfill;

import com.playtimelb.PlaytimeLeaderboardMod;
import com.playtimelb.config.ModConfig;
import com.playtimelb.export.InfluxExporter;
import com.playtimelb.store.PlaytimeStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BackfillRunner {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) {
        if (!ModConfig.backfillEnabled.get()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path marker = worldRoot.resolve("playtimelb-backfill.done");
            if (ModConfig.backfillOnlyOnce.get() && Files.exists(marker)) {
                PlaytimeLeaderboardMod.LOGGER.info("[PlaytimeLB] backfill: already done (marker exists)");
                return;
            }

            long asOf = parseAsOf(ModConfig.backfillAsOf.get());
            String src = ModConfig.backfillSource.get().toLowerCase(Locale.ROOT);

            Map<UUID, Long> seconds =
                src.equals("vanilla")
                    ? VanillaStatsReader.loadAllSeconds(server)
                    : PlaytimeStore.getTotalsIncludingActive(server);

            if (seconds.isEmpty()) {
                PlaytimeLeaderboardMod.LOGGER.info("[PlaytimeLB] backfill: no players found for source={}", src);
                return;
            }

            int sent = InfluxExporter.exportTotalsAtInstant(seconds, asOf);
            Files.writeString(marker, "sent=" + sent + " asOf=" + asOf);
            PlaytimeLeaderboardMod.LOGGER.info("[PlaytimeLB] backfill: wrote {} players at {}", sent, asOf);
        } catch (Exception ex) {
            PlaytimeLeaderboardMod.LOGGER.error("[PlaytimeLB] backfill failed", ex);
        }
    }

    private static long parseAsOf(String iso) {
        if (iso == null || iso.isBlank()) return Instant.now().getEpochSecond();
        try { return Instant.parse(iso).getEpochSecond(); }
        catch (Exception ignored) { return Instant.now().getEpochSecond(); }
    }
}
