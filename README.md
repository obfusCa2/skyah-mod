# SkyAH Mod

Fabric mod for Hypixel SkyBlock. Shows the auction deals [SkyAH](https://skyah.net) finds, live in your chat.

## Download

- Minecraft 26.3: [skyah-mod-0.3.1-mc26.3.jar](https://github.com/obfusCa2/skyah-mod/releases/download/v0.3.1/skyah-mod-0.3.1-mc26.3.jar)
- Minecraft 26.2: [skyah-mod-0.3.1-mc26.2.jar](https://github.com/obfusCa2/skyah-mod/releases/download/v0.3.1/skyah-mod-0.3.1-mc26.2.jar)
- Minecraft 26.1.2: [skyah-mod-0.3.1-mc26.1.2.jar](https://github.com/obfusCa2/skyah-mod/releases/download/v0.3.1/skyah-mod-0.3.1-mc26.1.2.jar)

Same files as on [skyah.net/api](https://skyah.net/api). SHA-256 checksums are on the [release page](https://github.com/obfusCa2/skyah-mod/releases).

## Install

1. Get a free key on [skyah.net/api](https://skyah.net/api). You need a linked Minecraft account.
2. Put the jar for your Minecraft version in your `mods` folder. Updating? Delete the old SkyAH jar first.
   You also need Fabric Loader 0.19.5+, Fabric API and Java 25.
3. Copy your key, start the game, press `K`, open **Account & key** and click **Paste key**.

Commands: `/skyah test` shows two example deals, `/skyah status`, `/skyah logs`, `/skyah reconnect`.

Use at your own risk.

## Build it yourself

Needs JDK 25.

```
./gradlew build
./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
./gradlew build -Pminecraft_version=26.1.2 -Pfabric_api_version=0.155.3+26.1.2
```

The first line builds for Minecraft 26.3, the others for 26.2 and 26.1.2. The jar ends up in `build/libs/`.

Builds are reproducible. With Temurin 25.0.3 you get the same jar byte for byte, so its SHA-256 matches the checksum listed on skyah.net/api.

## License

MIT, see [LICENSE](LICENSE).
