# RT-N16 Mobile

Android aplikacija za ASUS RT-N16 z FreshTomato v konfiguraciji:

- `wl0` = Wireless Client / UPLINK
- `wl0.1` = Access Point `ASUS-MOBILE`
- LAN = `192.168.50.0/24`
- router = `192.168.50.1`

## Funkcije V0.1

- prikaz trenutnega UPLINK SSID, WAN IP in RSSI
- scan Wi-Fi omrežij prek radia RT-N16
- preklop UPLINK na izbrani Wi-Fi (OPEN ali WPA2/AES)
- prikaz naprav, asociiranih na `wl0.1`
- gumb za captive portal / javni Wi-Fi
- FreshTomato admin geslo je lokalno šifrirano z Android Keystore

## Pomembno

Aplikacija uporablja lokalni FreshTomato HTTP vmesnik `shell.cgi`, enako kot
FreshTomato GUI uporablja za Tools -> System Commands. Telefon mora biti povezan
na `ASUS-MOBILE`.

Pri preklopu UPLINK se isti 2.4 GHz radio ponovno zažene, zato `ASUS-MOBILE`
za nekaj sekund izgine in se vrne na kanal novega UPLINK-a.

## Build

Projekt odpri v Android Studio in izberi:

Build -> Build APK(s)

APK bo v:

`app/build/outputs/apk/debug/app-debug.apk`

V0.1 je namenoma brez zunanjih knjižnic.
