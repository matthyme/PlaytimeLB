package com.playtimelb.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;

    // Influx totals export
    public static final ForgeConfigSpec.BooleanValue influxEnabled;
    public static final ForgeConfigSpec.ConfigValue<String> influxUrl;
    public static final ForgeConfigSpec.ConfigValue<String> influxToken;
    public static final ForgeConfigSpec.ConfigValue<String> influxOrg;
    public static final ForgeConfigSpec.ConfigValue<String> influxBucket;
    public static final ForgeConfigSpec.ConfigValue<String> measurement;
    public static final ForgeConfigSpec.ConfigValue<String> serverTag;

    // Session events
    public static final ForgeConfigSpec.BooleanValue sessionEventsEnabled;
    public static final ForgeConfigSpec.ConfigValue<String> sessionMeasurement;
    public static final ForgeConfigSpec.ConfigValue<String> sessionEventMeasurement;
    public static final ForgeConfigSpec.BooleanValue includeNameTag;

    // Export scheduler
    public static final ForgeConfigSpec.IntValue autoExportMinutes;
    public static final ForgeConfigSpec.BooleanValue autoExportEnabled;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("InfluxDB 2.x totals export (seconds)").push("influx2");
        influxEnabled = b.define("enabled", false);
        influxUrl = b.define("url", "http://localhost:8086");
        influxToken = b.define("token", "");
        influxOrg = b.define("org", "minecraft");
        influxBucket = b.define("bucket", "playtime");
        measurement = b.define("measurement", "player_playtime");
        serverTag = b.define("serverTag", "");
        b.pop();

        b.comment("Session events export (join/leave + summary)").push("sessions");
        sessionEventsEnabled = b.define("enabled", true);
        sessionMeasurement = b.define("measurement", "player_session");
        sessionEventMeasurement = b.define("eventMeasurement", "player_session_event");
        includeNameTag = b.define("includeNameTag", true);
        b.pop();

        b.comment("Auto-export of totals (server tick based)").push("export");
        autoExportEnabled = b.define("enabled", false);
        autoExportMinutes = b.defineInRange("minutes", 5, 1, 1440);
        b.pop();

        SPEC = b.build();
    }
}
