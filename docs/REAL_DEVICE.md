# Running on a real phone with the real thermometer

The emulator cannot hear Bluetooth devices, so the Govee thermometer needs a physical
Android phone (Android 10 or newer). This page covers the phone, the thermometer and
the backend the phone can reach.

## 1. Install the app on the phone

1. On the phone: **Settings → About phone → tap Build number seven times**, then
   **Settings → Developer options → USB debugging: on**.
2. Plug the phone into the laptop and accept "Allow USB debugging".
3. In Android Studio choose the phone in the device list and press **Run**.

## 2. Pair the thermometer (Govee H5075)

1. Pull the battery tab so the thermometer's screen is on. Keep it within a few metres.
2. In the app: **More → Bluetooth LE thermometer → Find my thermometer**.
3. Allow **Nearby devices** and **Location (precise)**. Bluetooth and Location must be switched on.
4. The list shows each thermometer with its live reading. Tap the one whose temperature
   matches the screen on your device.
5. **Sensors → Start monitoring.** The first reading arrives within a minute; one reading is
   stored per minute after that. Home and Sensors now show the real thermometer.

If the thermometer is not listed: check that its screen is on, move closer, and press
**Scan again**. As a fallback, type the address printed on the device (or shown in the
Govee Home app) into "Or type its address".

### Why the app asks for Location

The thermometer also broadcasts an iBeacon-format packet. Android hides beacon-like
devices from apps that declare `neverForLocation`, so with that flag the sensor is never
found. Habitable therefore asks for Location in Bluetooth mode only, and never reads or
stores the phone's position. Simulated mode asks for neither permission.

### What happens when things go wrong

| Situation | What the app does |
| --- | --- |
| Bluetooth or Location switched off | Start is refused with a message saying what to turn on |
| Permission denied | Message explains which permission and where to allow it |
| Bluetooth switched off while recording | Monitoring stops; Sensors shows why |
| Thermometer out of range or phone off | No readings are stored. The next reading is flagged `GAP_BEFORE`, and an open cold period ends at the last real reading, so unmeasured time is never counted |
| Android restarts the service | It resumes with the paired thermometer (START_STICKY) |

### Battery

The thermometer broadcasts every two seconds. Habitable does not wake up for each one:

- The scan only listens for the paired thermometer (filtered inside the Bluetooth chip) and runs in Android's low-power scan mode.
- On most phones from the last several years the Bluetooth chip supports **batched scanning**: it collects the broadcasts itself and hands them to the app once per interval, so the phone's processor stays asleep in between. Logcat (`BleSensorSource`) shows `batching=true` when this is in use.
- **More → Bluetooth → Save a reading every 1 / 5 / 10 min** (default 5). Less often means less battery. Changing it stops monitoring; tap Start again on Sensors.
- For the most realistic battery, install the release build: `cd android` → `.\gradlew assembleRelease`, then install `app/build/outputs/apk/release/app-release.apk`.

## 3. Give the phone a backend it can reach

`10.0.2.2` only exists inside the emulator. A real phone needs one of these.
Set it in `android/local.properties` (git-ignored), then rebuild:

**Option A, same Wi-Fi as the laptop (quickest for testing)**

```properties
habitable.backendUrl=http://192.168.1.23:3000/
```

Use the laptop's own address (`ipconfig` → IPv4 Address) and keep `npm run dev` running.
Windows may ask to allow Node through the firewall: allow it on private networks.

**Option B, deployed backend (Render + Neon)**

1. Create a free PostgreSQL database at neon.tech and copy its connection string
   (it ends in `?sslmode=require`).
2. In Render: **New → Blueprint**, choose this repository. Render reads `render.yaml`.
3. Paste the Neon string when Render asks for `DATABASE_URL`. `JWT_SECRET` is generated
   by Render and is never in the repository.
4. When the deploy is live, open `https://<your-service>.onrender.com/health`.
5. Point the app at it:

```properties
habitable.backendUrl=https://<your-service>.onrender.com/
```

The free Render plan sleeps after a while without traffic, so the first Sync after a
pause can time out. Tap **Sync now** again.

## 4. Overnight test (before the final demo)

1. Pair the thermometer, start monitoring, lock the phone and leave it charging.
2. In the morning check: a reading every 5 minutes (or the interval chosen under More → Bluetooth) on the 24 h chart, no `GAP_BEFORE` flags
   in Sensors → Data checks, and the same temperature as the thermometer's screen.
3. Note the phone model, Android version and any gaps in `docs/UPDATE2.md` under known issues.
