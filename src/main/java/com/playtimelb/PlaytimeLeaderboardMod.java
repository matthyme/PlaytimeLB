package com.playtimelb;

import com.mojang.logging.LogUtils;
import com.playtimelb.config.ModConfig;
import com.playtimelb.export.InfluxExporter;
import com.playtimelb.session.SessionTracker;
import com.playtimelb.store.PlaytimeStore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

@Mod(PlaytimeLeaderboardMod.MODID)
public class PlaytimeLeaderboardMod {
    public static final String MODID = "playtimelb";
    public static final Logger LOGGER = LogUtils.getLogger();

    private long ticks = 0L;

    public PlaytimeLeaderboardMod() {
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new SessionTracker());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PlaytimeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // periodic checkpoint so crashes won't lose much
        if (ticks % (20 * 30) == 0) { // every ~30s
            try {
                PlaytimeStore.checkpoint(server);
            } catch (Exception e) {
                LOGGER.error("[PlaytimeLB] checkpoint failed", e);
            }
        }

        if (!ModConfig.autoExportEnabled.get()) { ticks++; return; }

        ticks++;
        long intervalTicks = ModConfig.autoExportMinutes.get() * 60L * 20L;
        if (intervalTicks <= 0) return;
        if (ticks % intervalTicks != 0) return;

        try {
            Map<UUID, Long> seconds = PlaytimeStore.getTotalsIncludingActive(server);
            if (ModConfig.influxEnabled.get()) {
                InfluxExporter.exportAllSeconds(seconds);
            }
        } catch (Exception e) {
            LOGGER.error("[PlaytimeLB] Auto-export failed", e);
        }
    }
}
