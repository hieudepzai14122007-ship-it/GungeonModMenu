# Gungeon Mod Menu

An in-game mod menu and gameplay expansion for **Enter the Gungeon**.

**Version: 0.13.7** · Windows PC · BepInEx plugin

Includes the Gungeon Codex skill tree, companions, gun progression and abilities, Black Flash, Infinity, elite enemies, contracts and trials. The installed build also announces Floor 4.5 Ascension, Cursed Floors, Elite Bounties and Hunter's Descent.

## Download

Download **GungeonModMenu-0.13.7-Windows.zip** from this repository's Releases page when available. You can also use **Code → Download ZIP**: the repository contains the ready-to-install mod under `BepInEx/plugins/GungeonModMenu/`.

You do not need to compile the mod or install a .NET SDK.

## Requirements

- A Windows PC installation of **Enter the Gungeon**.
- [BepInExPack EtG](https://thunderstore.io/c/enter-the-gungeon/p/BepInEx/BepInExPack_EtG/).
- [Mod the Gungeon API](https://thunderstore.io/c/enter-the-gungeon/p/MtG_API/Mod_the_Gungeon_API/). The existing local installation loads API **1.9.2** and BepInEx **5.4.23.5**.

The game and dependencies are separate downloads. Other platforms and dependency versions have not been verified for this build.

## Install on Windows

1. **Close the game.** In Steam, right-click **Enter the Gungeon → Manage → Browse local files**. This opens the folder containing `EtG.exe`.
2. **Install BepInExPack EtG** if you do not already have it. Download and extract its archive into a temporary folder, then copy the **contents** of `BepInExPack_EtG` into the game folder. Follow the [pack's installation instructions](https://thunderstore.io/c/enter-the-gungeon/p/BepInEx/BepInExPack_EtG/).
3. **Install Mod the Gungeon API.** Download its archive and extract its contents into the game's `BepInEx` folder, preserving its `plugins` and `monomod` folders. See the [API author's installation guide](https://github.com/SpecialAPI/ModTheGungeonAPI/wiki/BepInEx-and-Mod-the-Gungeon-API-installation-guide).
4. **Extract this mod's ZIP.** Open the extracted folder and copy its `BepInEx` folder into the game folder, merging folders when prompted. If you downloaded the repository ZIP, open its outer folder first.
5. **Launch the game and press F5** to open Gungeon Mod Menu.

The finished layout must look like this:

```text
Enter the Gungeon/
├── EtG.exe
└── BepInEx/
    ├── core/
    ├── monomod/
    └── plugins/
        ├── ... Mod the Gungeon API files ...
        └── GungeonModMenu/
            ├── GungeonModMenu.dll
            └── Sfx/
                └── ... 13 .wav files ...
```

Keep `Sfx` beside `GungeonModMenu.dll`. Avoid accidentally creating `BepInEx/BepInEx` or putting the mod inside an extra ZIP-name folder.

### If you use r2modman or Thunderstore Mod Manager

Install **BepInExPack EtG** and **Mod the Gungeon API** in your Enter the Gungeon profile. Open **Settings → Browse profile folder**, then copy this download's `BepInEx/plugins/GungeonModMenu` folder into the profile's `BepInEx/plugins` folder. Launch using **Start Modded** in your manager. See the [API author's mod-manager guide](https://github.com/SpecialAPI/ModTheGungeonAPI/wiki/BepInEx-and-Mod-the-Gungeon-API-installation-guide).

## Controls

| Default key | Action |
| --- | --- |
| F5 | Open/close the main menu, including DEBUG tools |
| F6 | Open the Gungeon Codex skill tree |
| F7 | Open the companion panel |
| G | Companion commands |
| V | Activate Infinity after unlocking its skill |

Configuration is generated on first launch at `BepInEx/config/nguyendieu.etg.gungeonmodmenu.cfg`. Close the game before editing it. Your existing key bindings may differ from the defaults above.

## Updating

Close the game, move the old `GungeonModMenu` plugin folder to a backup location **outside** `BepInEx/plugins`, and copy in the new folder. Keep your configuration if you want to retain settings. Do not leave old mod DLLs in a `_backup` subfolder inside `plugins`: BepInEx scans subfolders too.

## Uninstalling

Close the game and remove `BepInEx/plugins/GungeonModMenu`. Optionally remove the mod's configuration file to reset its preferences. Keep BepInEx and Mod the Gungeon API if other installed mods need them.

## Troubleshooting

- **F5 does nothing:** Check the folder layout, dependencies and configured menu key. Laptop keyboards may require **Fn + F5**. With a mod manager, start the correct profile using **Start Modded**.
- **Check whether it loaded:** Open `BepInEx/LogOutput.log` and look for `Loading [Gungeon Mod Menu 0.13.7]` and the mod's startup message. Dependency errors earlier in the log can explain a missing menu.
- **Duplicate/old-version warnings:** Move every older GungeonModMenu DLL outside the entire `BepInEx/plugins` tree.
- **Missing sounds:** Confirm all 13 WAV files are in `GungeonModMenu/Sfx`, then check the mod's Audio settings. The existing local log reports `Original SFX loaded: 0/13`; sound playback remains unverified in this package.

## Package and verification

This distribution contains the installed **0.13.7 binary** and its sound assets. Matching 0.13.7 source has not yet been located; the older 0.7.3 source is not included as if it were current.

The plugin version was checked from the DLL metadata. The existing game log confirms that 0.13.7 loads with Mod the Gungeon API 1.9.2. Packaging checks verify file hashes and archive contents; they are not a fresh gameplay or clean-install test.

`SHA256SUMS.txt` lists checksums for the distributed plugin and sound files.