package com.playtimelb.export;

import com.playtimelb.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InfluxExporter {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // ==== Totals (seconds) ====
    public static int exportAllSeconds(Map<UUID, Long> secondsByPlayer) throws Exception {
        if (!ModConfig.influxEnabled.get()) return 0;

        String bucket = ModConfig.influxBucket.get();
        String org = ModConfig.influxOrg.get();
        String token = ModConfig.influxToken.get();
        String url = ModConfig.influxUrl.get();
        String measurement = ModConfig.measurement.get();
        String serverTag = ModConfig.serverTag.get();

        String body = secondsByPlayer.entrySet().stream().map(e -> {
            String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                        + "uuid=" + e.getKey();
            return String.format("%s,%s seconds=%d", sanitize(measurement), tags, e.getValue());
        }).collect(Collectors.joining("\n"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=s"))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return secondsByPlayer.size();
        } else {
            throw new RuntimeException("InfluxDB write failed: HTTP " + resp.statusCode() + " - " + resp.body());
        }
    }

    // ==== Session events (join/leave) ====
    public static CompletableFuture<Void> exportSessionEventAsync(UUID uuid, String name, String event, long whenNs) {
        if (!ModConfig.sessionEventsEnabled.get() || !ModConfig.influxEnabled.get()) {
            return CompletableFuture.completedFuture(null);
        }
        String bucket = ModConfig.influxBucket.get();
        String org = ModConfig.influxOrg.get();
        String token = ModConfig.influxToken.get();
        String url = ModConfig.influxUrl.get();
        String meas = ModConfig.sessionEventMeasurement.get();
        String serverTag = ModConfig.serverTag.get();
        boolean includeName = ModConfig.includeNameTag.get();

        String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                    + "uuid=" + uuid
                    + (includeName && name != null && !name.isEmpty() ? ",name=" + sanitize(name) : "")
                    + ",event=" + sanitize(event);

        String body = String.format("%s,%s value=1i %d", sanitize(meas), tags, whenNs);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=ns"))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                System.err.println("Influx session event write failed: " + resp.statusCode() + " - " + resp.body());
            }
        });
    }

    // ==== Session summary ====
    public static CompletableFuture<Void> exportSessionSummaryAsync(UUID uuid, String name, long startNs, long endNs, long durationSec) {
        if (!ModConfig.sessionEventsEnabled.get() || !ModConfig.influxEnabled.get()) {
            return CompletableFuture.completedFuture(null);
        }
        String bucket = ModConfig.influxBucket.get();
        String org = ModConfig.influxOrg.get();
        String token = ModConfig.influxToken.get();
        String url = ModConfig.influxUrl.get();
        String meas = ModConfig.sessionMeasurement.get();
        String serverTag = ModConfig.serverTag.get();
        boolean includeName = ModConfig.includeNameTag.get();

        String tags = (serverTag != null && !serverTag.isEmpty() ? "server=" + sanitize(serverTag) + "," : "")
                    + "uuid=" + uuid
                    + (includeName && name != null && !name.isEmpty() ? ",name=" + sanitize(name) : "");

        String body = String.format("%s,%s duration_sec=%ds,start_ns=%dns,end_ns=%dns %d",
                sanitize(meas), tags, durationSec, startNs, endNs, endNs);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/v2/write?org=" + encode(org) + "&bucket=" + encode(bucket) + "&precision=ns"))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                System.err.println("Influx session summary write failed: " + resp.statusCode() + " - " + resp.body());
            }
        });
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[ ,]", "_");
    }
}
