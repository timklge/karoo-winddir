# Karoo Headwind Extension

This extension for Karoo devices adds a graphical data field that shows the current headwind direction as an arrow.
It also provides data fields for relative humidity, cloud coverage, wind speed, wind gust speed, surface pressure, and rainfall at the current location.

Compatible with Karoo 2 and Karoo 3 devices.

![Settings](preview0.png)
![Field setup](preview1.png)
![Data page](preview2.png)

## Installation

If you are using a Karoo 3, you can use [Hammerhead's sideloading procedure](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading) to install the app:

1. Open the [releases page](https://github.com/timklge/karoo-headwind/releases) on your phone's browser, long-press the `app-release.apk` link and share it with the Hammerhead Companion app.
2. Your karoo should show an info screen about the app now. Press "Install".

If you are using a Karoo 2, you can use manual sideloading:

1. Download the apk from the [releases page](https://github.com/timklge/karoo-headwind/releases) (or build it from source)
2. Set up your Karoo for sideloading. DC Rainmaker has a great [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Install the app by running `adb install app-release.apk`.

## Usage

After installing this app on your Karoo, you can add a data field showing the headwind direction or one of the auxiliary fields to your data pages. The headwind direction will be shown as an arrow image, with an optional overlay of the wind speed in your chosen unit of measurement (default is kilometers per hour).

The app will automatically attempt to download weather data for your current approximate location from the [open-meteo.com](https://open-meteo.com) API once your device has acquired a GPS fix. The API service is free for non-commercial use. Your location is rounded to approximately two kilometers to maintain privacy. The data is updated when you ride more than two kilometers from the location where the weather data was downloaded or after one hour at the latest. If the app cannot connect to the weather service, it will retry the download every minute. Downloading weather data should work on Karoo 2 if you have a SIM card inserted or on Karoo 3 via your phone's internet connection if you have the Karoo companion app installed.

## Credits

- Icons are from [boxicons.com](https://boxicons.com) (MIT-licensed)
- Made possible by the generous usage terms of [open-meteo.com](https://open-meteo.com)

## Links

[karoo-ext source](https://github.com/hammerheadnav/karoo-ext)