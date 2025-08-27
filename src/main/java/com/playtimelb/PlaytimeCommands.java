package com.playtimelb;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.playtimelb.config.ModConfig;
import com.playtimelb.export.InfluxExporter;
import com.playtimelb.store.PlaytimeStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlaytimeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal("playtime")
            .requires(src -> src.hasPermission(0))
            // Make /playtime show the top list by default
            .executes(ctx -> top(ctx.getSource(), 10))
            .then(net.minecraft.commands.Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> top(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "limit")))
            )
            .then(net.minecraft.commands.Commands.literal("show")
                .then(net.minecraft.commands.Commands.argument("playerName", StringArgumentType.string())
                    .executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "playerName")))
                )
            )
            .then(net.minecraft.commands.Commands.literal("export")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> export(ctx.getSource()))
            )
            .then(net.minecraft.commands.Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> reset(ctx.getSource()))
            )
        );
    }

    private static int top(CommandSourceStack src, int limit) {
        MinecraftServer server = src.getServer();
        try {
            Map<UUID, Long> map = PlaytimeStore.getTotalsIncludingActive(server);
            Map<UUID, String> names = PlaytimeStore.getNames(server);

            var sorted = map.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(limit)
                    .collect(Collectors.toList());

            src.sendSuccess(() -> Component.literal("§6== Top " + sorted.size() + " Server Playtime =="), false);
            int rank = 1;
            for (var e : sorted) {
                final int displayRank = rank++;
                String name = names.getOrDefault(e.getKey(), e.getKey().toString().substring(0, 8));
                String human = PlaytimeStore.formatDuration(e.getValue());
                Component line = Component.literal("§e" + displayRank + ". §b" + name + "§7 - §a" + human);
                src.sendSuccess(() -> line, false);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Failed to read playtime: " + e.getMessage()));
            return 0;
        }
    }

    private static int show(CommandSourceStack src, String playerName) {
        MinecraftServer server = src.getServer();
        try {
            UUID uuid = PlaytimeStore.lookupUUID(server, playerName);
            if (uuid == null) {
                src.sendFailure(Component.literal("Unknown player: " + playerName));
                return 0;
            }
            long seconds = PlaytimeStore.getTotalFor(server, uuid, true);
            String human = PlaytimeStore.formatDuration(seconds);
            src.sendSuccess(() -> Component.literal("§b" + playerName + "§7 has played §a" + human + " §7on this server"), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int export(CommandSourceStack src) {
        if (!ModConfig.influxEnabled.get()) {
            src.sendFailure(Component.literal("InfluxDB export is disabled in config."));
            return 0;
        }
        MinecraftServer server = src.getServer();
        try {
            Map<UUID, Long> seconds = PlaytimeStore.getTotalsIncludingActive(server);
            int sent = InfluxExporter.exportAllSeconds(seconds);
            src.sendSuccess(() -> Component.literal("Exported " + sent + " players to InfluxDB."), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Export failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int reset(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        try {
            PlaytimeStore.reset(server);
            src.sendSuccess(() -> Component.literal("Playtime store reset to zero."), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Failed to reset: " + e.getMessage()));
            return 0;
        }
    }
}
