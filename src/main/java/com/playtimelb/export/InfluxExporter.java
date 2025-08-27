package com.playtimelb.export;

import com.playtimelb.PlaytimeLeaderboardMod;
import com.playtimelb.config.ModConfig;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InfluxExporter {

    // ==== Totals (seconds) ====
    public static int exportAllSeconds(Map<UUID, Long> secondsByPlayer) throws Exception {
        if (!ModConfig.influxEnabled.get()) return 0;

        String measurement = ModConfig.measurement.get();
        String serverTag = ModConfig.serverTag.get();

        String body = secondsByPlayer.entrySet().stream().map(e -> {
            String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                    + "uuid=" + e.getKey();
            return String.format("%s,%s duration=%d", sanitize(measurement), tags, e.getValue());
        }).collect(Collectors.joining("\n"));

        CompletableFuture.runAsync(() -> {
            try {
                sendRequest(body);
            } catch (Exception ex) {
                PlaytimeLeaderboardMod.LOGGER.error("could not export seconds", ex);
            }
        });

        return secondsByPlayer.size();
    }

    // ==== Session events (join/leave) ====
    public static CompletableFuture<Void> exportSessionEventAsync(UUID uuid, String name, String event, long whenNs) {
        if (!ModConfig.sessionEventsEnabled.get() || !ModConfig.influxEnabled.get()) {
            return CompletableFuture.completedFuture(null);
        }
        String meas = ModConfig.sessionEventMeasurement.get();
        String serverTag = ModConfig.serverTag.get();
        boolean includeName = ModConfig.includeNameTag.get();

        String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                + "uuid=" + uuid
                + (includeName && name != null && !name.isEmpty() ? ",username=" + sanitize(name) : "")
                + ",event=" + sanitize(event);

        String body = String.format("%s,%s duration=%d %d", sanitize(meas), tags, whenNs / 1_000_000_000, whenNs);
        return CompletableFuture.runAsync(() -> {
            try {
                sendRequest(body);
            } catch (Exception ex) {
                PlaytimeLeaderboardMod.LOGGER.error("could not export session event", ex);
            }
        });
    }

    // ==== Session summary ====
    public static CompletableFuture<Void> exportSessionSummaryAsync(UUID uuid, String name, long startNs, long endNs, long durationSec) {
        if (!ModConfig.sessionEventsEnabled.get() || !ModConfig.influxEnabled.get()) {
            return CompletableFuture.completedFuture(null);
        }
        String meas = ModConfig.sessionMeasurement.get();
        String serverTag = ModConfig.serverTag.get();
        boolean includeName = ModConfig.includeNameTag.get();

        String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                + "uuid=" + uuid
                + (includeName && name != null && !name.isEmpty() ? ",username=" + sanitize(name) : "");

        String body = String.format("%s,%s duration=%d %d", sanitize(meas), tags, durationSec, endNs);
        PlaytimeLeaderboardMod.LOGGER.info("writing data point to influx {}", body);

        return CompletableFuture.runAsync(() -> {
            try {
                sendRequest(body);
            } catch (Exception ex) {
                PlaytimeLeaderboardMod.LOGGER.error("could not export session summary", ex);
            }
        });
    }

    public static int exportTotalsAtInstant(Map<UUID, Long> secondsByPlayer, long epochSeconds) throws Exception {
    if (!ModConfig.influxEnabled.get()) return 0;

    String bucket = ModConfig.influxBucket.get(), org = ModConfig.influxOrg.get();
    String token = ModConfig.influxToken.get(), url = ModConfig.influxUrl.get();
    String measurement = ModConfig.measurement.get(), serverTag = ModConfig.serverTag.get();

    String body = secondsByPlayer.entrySet().stream().map(e -> {
        String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                    + "uuid=" + e.getKey();
        // integer field "seconds", timestamp supplied below with precision=s
        return String.format("%s,%s seconds=%di %d", sanitize(measurement), tags, e.getValue(), epochSeconds);
    }).collect(java.util.stream.Collectors.joining("\n"));

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=s"))
        .header("Authorization", "Token " + token)
        .header("Content-Type", "text/plain; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    PlaytimeLeaderboardMod.LOGGER.info("[PlaytimeLB] backfill write status={} body={}", resp.statusCode(), resp.body());
    if (resp.statusCode() / 100 != 2) throw new RuntimeException("Influx write failed: " + resp.statusCode() + " " + resp.body());
    return secondsByPlayer.size();
}


    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[ ,]", "_");
    }

    private static int sendRequest(String body) throws Exception {
        String bucket = ModConfig.influxBucket.get();
        String org = ModConfig.influxOrg.get();
        String token = ModConfig.influxToken.get();
        String url = ModConfig.influxUrl.get();

        URL endpoint = new URL(url + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=ns");
        HttpsURLConnection conn = (HttpsURLConnection) endpoint.openConnection();

        // plug in trust-all SSL + hostname verifier
        conn.setSSLSocketFactory(InsecureSsl.trustAllSslSocketFactory());
        conn.setHostnameVerifier(InsecureSsl.trustAllHosts());

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Token " + token);
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        return conn.getResponseCode();
    }
}
