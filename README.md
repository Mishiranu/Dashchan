# Dashchan

Imageboards client for Android.

Read the [project wiki](https://github.com/Mishiranu/Dashchan/wiki) for further information.

## Updating Guide

Go to Preferences → About → Check for updates. After fetching update data you can view a number of updates in the title
bar. Usually you just need to click on the download button and wait until download ends.

It is better to install extension packages and then install the application package. You will need torestart the application
after installing or updating extensions only.

## Building Guide

1. Install JDK 8 or higher
2. Install Android SDK, define `ANDROID_HOME` environment variable or set `sdk.dir` in `local.properties`
3. Run `./gradlew assembleRelease`

The resulting APK file will appear in `build/outputs/apk` directory.

### Build Signed Binary

You can create `keystore.properties` in the source code directory with the following properties:

```properties
store.file=%PATH_TO_KEYSTORE_FILE%
store.password=%KEYSTORE_PASSWORD%
key.alias=%KEY_ALIAS%
key.password=%KEY_PASSWORD%
```

### Building extenions

The source code of extensions is available in the
[Dashchan Extensions](https://github.com/Mishiranu/Dashchan-Extensions) repository.

The source code of the video player extension is available in the
[Dashchan Webm](https://github.com/Mishiranu/Dashchan-Webm) repository.

## License

Dashchan is available under the [GNU General Public License, version 3 or later](COPYING).
