# Itachi TV

A personal Android TV home screen for a Xiaomi MiTV (Android TV 11), built on
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT), plus a Claude voice assistant.

## What's inside

| Folder | What it is |
|---|---|
| `itachi-launcher/` | A fork of the CleanYourTV launcher with an Itachi mode: living Mangekyō eyes over the wallpaper that follow the selected app and blink, light rain that leans toward the focus, crows when an app opens, a parallax wallpaper, a clean five-logo dock with an All apps mark, and original glyphs for apps without a logo. |
| `itachi-voice/` | **Itachi Voice**, a voice assistant app: press OK, speak in Tamil or English, and Claude (Haiku 5.5) answers out loud. It opens apps and changes the volume locally, and shows a reactive Sharingan while it listens and thinks. |
| `itachi-inspired/` | The theme: `config.json` and notes. The wallpapers are not included (the artwork is not mine to redistribute); add your own 1920 × 1080 images to `itachi-inspired/wallpapers/`. |

## Build and install (Windows)

1. Get a CleanYourTV checkout and set up its Python tools (`tools/venv`, see its README).
2. Copy this folder's three folders into the checkout's `work/` folder. The scripts use `work/../tools`.
3. Put the TV's IP address in `work/tv-ip.txt` (or the scripts ask for it). Allow ADB on the TV.
4. Double-click `itachi-launcher/build-install.bat`, then `itachi-voice/build-install.bat`.
5. Double-click `itachi-voice/set-claude-key.bat` and paste a Claude API key from
   console.anthropic.com. The key goes straight to the TV and is never written to a file.

Needs JDK 17 and the Android SDK (platform 34); the scripts look in the usual places.

When you're done, turn off USB/network debugging on the TV.

## Licence

The launcher is a fork of CleanYourTV and keeps its MIT licence and font/icon licences (see `itachi-launcher/`).
