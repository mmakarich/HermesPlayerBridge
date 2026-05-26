# HermesPlayerBridge

Android companion app for Hermes Agent's Player Stats module.
Syncs Samsung Health data (via Health Connect) to your Hermes RPi
over Tailscale every 15 minutes.

## Architecture

```
S24 ─► Samsung Health ─► Health Connect API ─► HermesPlayerBridge App
                                                        │
                                               WorkManager (15min)
                                                        │
                                              POST /webhooks/health_connect
                                              HMAC-SHA256 auth
                                                        │
                                              Hermes RPi (:8645)
                                                        │
                                              player.db (vitals/sleep/daily_stats)
```

## Build Options

### Option A: GitHub Actions (recommended)

1. Push this repo to GitHub
2. Go to Actions → "Build HermesPlayerBridge APK"
3. Click "Run workflow" → wait ~5 min
4. Download artifact `HermesPlayerBridge-APK.zip`
5. Unzip → `HermesPlayerBridge.apk`

### Option B: Build on MacBook

```bash
# Prerequisites: Android Studio or standalone SDK + JDK 17
cd HermesPlayerBridge
chmod +x gradlew
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release-unsigned.apk
```

Then sign and align:
```bash
keytool -genkey -v -keystore debug.keystore -alias debug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android \
  -dname "CN=Hermes, OU=Player, O=Hermes, L=Unknown, ST=Unknown, C=PL"

jarsigner -keystore debug.keystore -storepass android -keypass android \
  app/build/outputs/apk/release/app-release-unsigned.apk debug

# Align (path to your Android SDK build-tools)
$ANDROID_HOME/build-tools/34.0.0/zipalign -f -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  HermesPlayerBridge.apk
```

### Option C: Codemagic / External Builder

Push to GitHub → Codemagic.io → Free tier Mac build → APK download.

---

## Installation on S24

### Step 1: Enable Developer Options
1. Settings → About phone → Software information
2. Tap "Build number" 7× until "Developer mode enabled"
3. Back to Settings → Developer options

### Step 2: Enable USB Debugging (for adb sideload)
1. Developer options → USB debugging → ON
2. Developer options → Install via USB → ON

### Step 3: Enable "Install unknown apps"
1. Settings → Security → Install unknown apps
2. Find "My Files" (or your file manager) → Allow

### Step 4: Transfer + install APK
```bash
# From MacBook:
adb install HermesPlayerBridge.apk
```
Or copy APK via USB/SD card → open in My Files → tap to install.

---

## Health Connect Permission Setup

### Prerequisite: Install Health Connect
- Already present on One UI 6. If not: Galaxy Store → "Health Connect"
- Or: Google Play → Health Connect by Google

### Step 1: Grant permissions from app
1. Open "Hermes Player" app
2. Tap **"Grant Health Permissions"**
3. If it opens Health Connect: check ALL checkboxes → Allow
4. If nothing happens: open Health Connect manually (next step)

### Step 2: Manual permission grant (fallback)
1. Open **Health Connect** app
2. Tap **App permissions** (bottom)
3. Find **"Hermes Player"** in the list
4. Tap and enable ALL data types:
   - [x] Steps
   - [x] Heart rate
   - [x] Heart rate variability
   - [x] Sleep
   - [x] Blood oxygen saturation (SpO2)
   - [x] Total calories burned
   - [x] Distance
   - [x] Weight
   - [x] Body fat
   - [x] Stress
   - [x] Exercise
5. Tap **Allow**

### Step 3: Verify permissions in app
Go back to Hermes Player app. The status should show:
- ✅ Health Connect — Connected
- ✅ All permissions granted
- Last sync: (timestamp after first manual sync)

---

## Verify Sync Works

### On phone:
1. Open Hermes Player → tap "Manual Sync Now"
2. Wait for "success" in sync log
3. Note the timestamp

### On Hermes RPi:
```bash
# Check vitals came through
python3 -c "
import sqlite3, json
db = '/home/pi/.hermes/player/db/player.db'
conn = sqlite3.connect(db)
conn.row_factory = sqlite3.Row
for r in conn.execute('SELECT * FROM vitals ORDER BY id DESC LIMIT 5'):
    print(dict(r))
conn.close()
"

# Check daily stats
python3 -c "
import sqlite3, json
db = '/home/pi/.hermes/player/db/player.db'
conn = sqlite3.connect(db)
conn.row_factory = sqlite3.Row
for r in conn.execute('SELECT * FROM daily_stats ORDER BY id DESC LIMIT 5'):
    print(dict(r))
conn.close()
"

# Check webhook log
tail -20 ~/.hermes/player/log/health-webhook.log
```

### Test webhook directly:
```bash
SECRET="hermes-player-bridge-2026"
PAYLOAD='{"device_id":"test","timestamp":'$(date +%s)',"records":[{"type":"daily","date":"2026-05-26","steps":10000}]}'
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | cut -d' ' -f2)

curl -s -X POST http://127.0.0.1:8645/webhooks/health_connect \
  -H "X-Hub-Signature-256: $SIG" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
# Returns: {"ok":true,"processed":1,...}
```

---

## Battery Optimization (Critical for One UI 6)

Samsung One UI 6 kills background apps aggressively. Must disable:

1. Settings → Apps → **Hermes Player**
2. Tap **Battery**
3. Set to **"Unrestricted"** (not Optimized, not Restricted)
4. Back → tap **"Allow background activity"** → ON

### Additional:
5. Settings → Device care → Battery → Background usage limits
6. Find Hermes Player → **"Never sleeping apps"** → Add

### Doze mode:
7. Settings → Device care → Battery → More battery settings
8. **"Adaptive battery"** → OFF (optional, improves sync reliability)

Note: Without these, Android may delay background sync by up to 2 hours.
The foreground service notification (persistent icon) helps but isn't enough on One UI.

---

## Tailscale

- S24 must have **Tailscale connected** for webhook to reach RPi
- RPi Tailscale IP: `100.118.219.23`
- Port `8645` must be accessible (Tailscale encrypts automatically)
- Webhook uses HTTPS-style auth (HMAC-SHA256), so no HTTPS cert needed
- If Tailscale is off, sync fails → app caches in Room DB until next successful sync

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| App shows "Health Connect not available" | Install Health Connect from Galaxy/Play Store |
| Permissions not sticking | Open Health Connect → App Permissions → Hermes Player → re-enable |
| Sync always fails with "HTTP 401" | Check `webhook_secret.txt` on RPi matches app's secret setting |
| Background sync never runs | Battery optimization override (see above) + reboot phone |
| Webhook error "invalid signature" | HMAC secret mismatch between app and `~/.hermes/player/db/webhook_secret.txt` |
| "Connection refused" on sync | Tailscale disconnected OR health_webhook.py not running on RPi |
| Can't install APK | Enable "Install unknown apps" for your file manager app |

## Checklist do przeklikania na phone (10 punktów)

- [ ] **Zainstaluj Health Connect** — Galaxy Store → "Health Connect" (lub jest preinstalowane)
- [ ] **Sideload APK** — przez ADB lub plik → My Files → tap → Install
- [ ] **Otwórz "Hermes Player"** — powinieneś zobaczyć status: "Health Connect — Connected"
- [ ] **Nadaj uprawnienia** — tap "Grant Health Permissions" → zaznacz WSZYSTKO → Allow
- [ ] **Wyłącz optymalizację baterii** — Ustawienia → Aplikacje → Hermes Player → Bateria → "Nieograniczona"
- [ ] **Dodaj do Never Sleeping Apps** — Ustawienia → Pielęgnacja urządzenia → Bateria → Wyjątki → +
- [ ] **Włącz Tailscale** — żeby telefon widział Hermesa na RPi
- [ ] **Tap "Manual Sync Now"** — sprawdź czy sync log pokazuje "SUCCESS"
- [ ] **Zweryfikuj na RPi** — `tail -5 ~/.hermes/player/log/health-webhook.log` — zobacz "Processed batch"
- [ ] **Tap "Start Background Sync"** — zniknie powiadomienie persistent w shade -> co 15min sync

## Files

```
HermesPlayerBridge/
├── app/build.gradle.kts          # Dependencies: Health Connect, Room, WorkManager, Compose
├── app/src/main/AndroidManifest.xml
├── gradle/libs.versions.toml     # Version catalog
├── gradlew                       # Wrapper script
├── gradle.properties
├── settings.gradle.kts
├── .github/workflows/build-apk.yml  # GitHub Actions
└── app/src/main/java/com/hermes/playerbridge/
    ├── MainActivity.kt           # Single Activity + Compose UI
    ├── HealthConnectManager.kt   # Health Connect read API
    ├── HermesApiClient.kt        # HTTP + HMAC-SHA256 client
    ├── SettingsManager.kt        # EncryptedSharedPreferences
    ├── SyncWorker.kt             # WorkManager periodic worker
    ├── SyncService.kt            # Foreground service (persistent notif)
    ├── BootReceiver.kt           # Auto-start after reboot
    ├── data/
    │   ├── Database.kt           # Room database
    │   ├── dao/Daos.kt           # DAOs (vitals, sleep, daily, sync_log)
    │   └── entities/Entities.kt  # Room entities
    └── ui/StatusScreen.kt        # Compose UI
```

## Hermes RPi — running services

| Service | Port | Description |
|---------|------|-------------|
| Player API | 8643 | REST API for quick-log events |
| Health Webhook | 8645 | Health Connect data receiver |
| Hermes Gateway | 8642 | Main AI agent API |
| Hermes Gateway Webhooks | 8644 | General gateway webhooks |
