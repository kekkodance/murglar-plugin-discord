# Murglar Discord Rich Presence Plugin

<img width="459" height="187" alt="{19C53BFE-B37D-4D0A-84BF-FD00AEBE1946}" src="https://github.com/user-attachments/assets/ce205719-fa5e-4ff1-9e71-0ca79f6743da" />  

A Murglar plugin (for Premium/Pass users only) that adds Discord Rich Presence (see music in your status).  

> [!IMPORTANT]  
> __It is the first plugin of it's kind__, as Murglar's Developers doesn't offer any kind of Global API for the app, having to scramble together a solution to fetch track data via Windows SMTC.  
> This also means the plugin does NOT support Android, Linux or macOS. It could be made to support Linux's MPRIS and macOS's MediaRemote.  

To work around Discord and Windows limitations, this plugin uploads Album Art to [**catbox.moe**](https://catbox.moe/) anonymously, with the optional choice of inputting your account if you wish.
Discord requires Album Art images to be publicly accessible via a link, and this is the only solution.

## Installation

1. Download the JAR file from the [**Releases**](https://github.com/kekkodance/murglar-plugin-discord/releases), make a `plugins` folder in your Murglar directory, next to the app and place the JAR file inside the folder.  
<img width="133" height="118" alt="{1BD841CF-B9AC-401F-B17F-3A13B4538AE2}" src="https://github.com/user-attachments/assets/10575a06-a877-427e-a830-6c4d74152f9e" />

2. Launch Murglar and check if the settings for "Discord" are present.  
<img width="538" height="75" alt="{B5C0E7D9-D58C-4B41-9235-C128C7617A11}" src="https://github.com/user-attachments/assets/c4188e7a-1ec5-49e6-9c0a-13467518f090" />

> [!NOTE]  
> As a side effect of the Murglar Developers hard-coding the Settings page to include some options that aren't needed (in this case), You will see settings similar to the ones you're already familiar with, ignore these as they do nothing, the only relevant ones are these:  
> <img width="683" height="312" alt="{0A8D62CF-053A-464E-ACF9-B2A8345A4060}" src="https://github.com/user-attachments/assets/44e3a226-82c8-48d3-8f38-50a6be0726b1" />

3. Play a track and check your profile, Done!  

## Build
If you wish, you can build the plugin using JDK 17:
```powershell
$env:JAVA_HOME="path/to/jdk-17"; .\gradlew :discord-core:build
```
The plugin JAR will be located at `discord-core/build/libs/murglar-plugin-discord-*.jar`.

## Credits

Thanks to [**CodeManDev**](https://github.com/CodeManDev/) for creating [SMTC4J](https://github.com/CodeManDev/SMTC4J)  
I backported it to Java 17 as it's the version that Murglar plugins use.
