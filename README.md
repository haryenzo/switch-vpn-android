# Switch VPN

[![Download APK](https://img.shields.io/github/v/release/haryenzo/switch-vpn-android?label=Download%20APK&style=for-the-badge)](https://github.com/haryenzo/switch-vpn-android/releases/latest/download/switch-vpn.apk)

An Android automation app in the spirit of iOS Shortcuts' VPN automations: pick an app,
create a rule like "opens → connect", and Switch VPN drives an already-installed VPN client
(Happ, Incy, v2RayTun) for you. It is **not a VPN itself** — it never creates a VPN connection
or handles any traffic. It only watches which app is in the foreground and sends
connect/disconnect/toggle commands to the VPN app you already have installed and configured.

## Features

- Per-app rules: "app X opens → connect/disconnect/toggle VPN Y", "app X closes → ..."
- Works with Happ, Incy, and v2RayTun out of the box
- Light / dark / system theme
- Interface available in 40+ languages
- Runs fully locally — no network access, no accounts, no analytics

## Screenshots

_Coming soon._

## Installation

This is not published on the Google Play Store — it's distributed as a direct APK download
("sideloading"), so Android will warn you it's from an unknown source. That's expected for a
small independent project; only install it if you trust the source you got it from.

1. Download the APK from the [latest release](https://github.com/haryenzo/switch-vpn-android/releases/latest) (open it on your phone and tap the `.apk` file under Assets).
2. Open it on your phone. If Android blocks the install, go to **Settings → Apps → Special
   access → Install unknown apps**, find the app you downloaded it with (e.g. your browser or
   Telegram), and allow it to install apps.
3. Open Switch VPN and grant the permissions it asks for on first launch — the app cannot work
   without them:
   - **Usage access** — required to detect which app is currently open. This is the only way
     to know when to trigger a rule, and the data never leaves your device.
   - **Notifications** — the background watcher runs as a foreground service, which Android
     requires to show a persistent notification while it's active.
   - **Battery optimization exemption** — without this, Android may kill the background
     watcher after a while and your rules will silently stop firing.
   - **Display over other apps** — some VPN apps (Happ, v2RayTun) are only reachable through a
     deep link that briefly opens their own screen; without this permission Android blocks that
     from a background service.
4. Make sure the VPN app you want to automate (Happ / Incy / v2RayTun) is already installed and
   logged in/configured — Switch VPN only sends it commands, it doesn't configure it.
5. Go to the Automation tab, tap **+**, and create your first rule.

## Privacy

Switch VPN does not collect, store, or transmit any data anywhere. It has no internet
permission and cannot physically send anything to a server. All rules and settings are stored
only locally on your device. See the in-app Privacy Policy for details.

## Support

Telegram: [@aeris_support](https://t.me/aeris_support)
