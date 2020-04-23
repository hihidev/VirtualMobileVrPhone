# VrPhone

[![Build Status](https://travis-ci.org/hihidev/VirtualMobileVrPhone.svg?branch=master)](https://travis-ci.org/hihidev/VirtualMobileVrPhone.svg?branch=master)
[![Build Status](https://travis-ci.org/hihidev/VirtualMobileVrHeadset.svg?branch=master)](https://travis-ci.org/hihidev/VirtualMobileVrHeadset.svg?branch=master)

VrPhone is an app that can mirror and remote control your phone to Oculus Quest.

You can:
  - Play Android games / emulator games from your phone
  - Play movies from your phone
  - Reply messages / Check email
  - ...

### NOTICE
It's still a proof of concept app.
Many features are not fully completed yet, including security of this app.

### WARNING
Your phone data is AT RISK as the network protocol IS NOT ENCRYPTED / PASSWORD PROTECTED.
(Feel free to contribute any security improvement on this project)

### Requirement
* Oculus Quest
* Android Phone
    * Android 10 is recommended if you want to have native audio support
    * [Optional] Bluetooth earbuds, if you want to have audio and
        * Your phone running on Android <= 9
        * Or if your android game / app cannot support audio mirroring
* WiFi network

### Introduction
This project consists of 2 parts:
* VrPhone
    * https://github.com/hihidev/VirtualMobileVrPhone
    * The app that runs on your phone to mirror screen and audio to Oculus Quest.

* VrPhone Screen
    * https://github.com/hihidev/VirtualMobileVrHeadset
    * The app that on your Oculus Quest, to projection your phone's screen and audio, and send touches / clicks back to your phone.

### Download APKs
VrPhone (For Android Phone):
https://github.com/hihidev/VirtualMobileVrPhone/releases

VrPhone Screen (For Oculus Quest):
https://github.com/hihidev/VirtualMobileVrHeadset/releases

### Installation / Setup
1. Install VrPhone APK on your Android phone.
2. Install VrPhone Screen APK on your Oculus Quest.
3. Open VrPhone on your Android phone, click "Start Server" button and accept all permissions.
4. Open VrPhone Screen on your Oculus Quest, and wait until the screen shows up.
    a. First time may take like 10 seconds to pair devices.
    b. If screen doesn't show up after 10 seconds, try to click stop and start button on VrPhone on your Android phone.
5. Click "Stop server" on your Android Phone when you finish using it.


### Troubleshooting

* Why audio can't be streamed on some apps.
    * TLDR: Pair bluetooth earbuds to your Android to listen audio.
    * Due to Android API limitations, only the apps running on your phone needs to be targetSDK >= 29 to support audio capture by default, so old apps may not support this feature yet.
* Why XXX doesn't work?
    * Try restart the app / phone / Oculus Quest.
    * It works on my machine.

#### Building for source
Just normal gradle build as usual.
For VrPhone Screen, you need to run setup_sdk.py to setup Oculus SDK correctly.

