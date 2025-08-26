package com.playtimelb.session;

import com.playtimelb.PlaytimeLeaderboardMod;
import com.playtimelb.config.ModConfig;
import com.playtimelb.export.InfluxExporter;
import com.playtimelb.store.PlaytimeStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class SessionTracker {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        UUID id = sp.getUUID();
        String name = sp.getGameProfile().getName();
        long nowNs = epochNowNs();

        try {
            PlaytimeStore.onLogin(server, id, name, nowNs);
        } catch (IOException ex) {
            PlaytimeLeaderboardMod.LOGGER.error("[PlaytimeLB] store login failed", ex);
        }

        if (ModConfig.sessionEventsEnabled.get() && ModConfig.influxEnabled.get()) {
            InfluxExporter.exportSessionEventAsync(id, name, "join", nowNs);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        UUID id = sp.getUUID();
        String name = sp.getGameProfile().getName();
        long endNs = epochNowNs();

        long added = 0L;
        try {
            added = PlaytimeStore.onLogout(server, id, name, endNs);
        } catch (IOException ex) {
            PlaytimeLeaderboardMod.LOGGER.error("[PlaytimeLB] store logout failed", ex);
        }

        if (ModConfig.sessionEventsEnabled.get() && ModConfig.influxEnabled.get()) {
            InfluxExporter.exportSessionEventAsync(id, name, "leave", endNs);
            if (added >= 0) {
                InfluxExporter.exportSessionSummaryAsync(id, name, endNs - added * 1_000_000_000L, endNs, added);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long endNs = epochNowNs();
        // Finalize all active sessions
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            UUID id = sp.getUUID();
            String name = sp.getGameProfile().getName();
            long added = 0L;
            try { added = PlaytimeStore.onLogout(server, id, name, endNs); } catch (IOException ex) {}
            if (ModConfig.sessionEventsEnabled.get() && ModConfig.influxEnabled.get()) {
                InfluxExporter.exportSessionEventAsync(id, name, "leave", endNs);
                if (added >= 0) {
                    InfluxExporter.exportSessionSummaryAsync(id, name, endNs - added * 1_000_000_000L, endNs, added);
                }
            }
        }
        try { PlaytimeStore.checkpoint(server); } catch (IOException ex) {}
    }

    private static long epochNowNs() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }
}
