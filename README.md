# Playtime Leaderboard (Forge 1.20.1) — v0.4.0 (Manual Tracking)

This build drops vanilla stats and tracks **server-only playtime** using **join/leave events**.
It also exports **totals** (seconds) and **session events/summaries** to InfluxDB.

## Commands
- `/playtime top [limit]`
- `/playtime show <player>`
- `/playtime export` (OP) — push current totals to Influx
- `/playtime reset` (OP) — zero out all tracked totals

## Config (`config/playtimelb-common.toml`)
```toml
[influx2]
enabled = true
url = "http://localhost:8086"
token = ""
org = "minecraft"
bucket = "playtime"
measurement = "player_playtime"
serverTag = "survival-1"

[sessions]
enabled = true
measurement = "player_session"
eventMeasurement = "player_session_event"
includeNameTag = true

[export]
enabled = false
minutes = 5
```

## Persistence
Data is stored in `world/playtimelb-data.json`:
- `totals_sec` — cumulative seconds per UUID
- `active_ns` — in-progress session start times (ns)
- `names` — last seen name per UUID

On **server stop**, in-progress sessions are finalized to the stop time.

## Build
```
./gradlew build   # Windows PowerShell: .\gradlew build
```
Jar: `build/libs/playtimelb-0.4.0.jar`
