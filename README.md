# Dashchan

Android client for imageboards.

## Features

* Supports multiple forums using extensions
* Threads watcher and reply notifications
* Automatic filter using regular expressions
* Image gallery and video player
* Archiving in HTML format
* Configurable themes
* Fullscreen layout

Read the [project wiki](https://github.com/Mishiranu/Dashchan/wiki) for further information.

## Screenshots

<p>
<img src="metadata/en-US/images/phoneScreenshots/1.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/2.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/3.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/4.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/5.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/6.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/7.png" width="20%" />
<img src="metadata/en-US/images/phoneScreenshots/8.png" width="20%" />
</p>

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

### Building Extensions

The source code of extensions is available in
[Dashchan Extensions](https://github.com/Mishiranu/Dashchan-Extensions) repository.

The source code of the video player libraries extension is available in
[Dashchan Webm](https://github.com/Mishiranu/Dashchan-Webm) repository.

## License

Dashchan is available under the [GNU General Public License, version 3 or later](COPYING).
