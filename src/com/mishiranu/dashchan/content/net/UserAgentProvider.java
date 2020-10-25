package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Log;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UserAgentProvider {
	private static final UserAgentProvider INSTANCE = new UserAgentProvider();

	public static UserAgentProvider getInstance() {
		return INSTANCE;
	}

	private static final String PREFIX = "chromium.v1:";
	private static final String DEFAULT_REFERENCE = PREFIX + "74.0.3729.169.537.36";

	private String userAgent = "Mozilla/5.0";

	private UserAgentProvider() {
		String userAgent = formatUserAgentForReference(Preferences.getUserAgentReference());
		if (userAgent == null) {
			userAgent = formatUserAgentForReference(DEFAULT_REFERENCE);
		}
		if (userAgent != null) {
			this.userAgent = userAgent;
		}
		loadChromiumUserAgentReference();
	}

	private String formatUserAgentForReference(String reference) {
		if (reference != null && reference.startsWith(PREFIX)) {
			String version = reference.substring(PREFIX.length());
			return formatChromiumUserAgent(version);
		}
		return null;
	}

	private String formatChromiumUserAgent(String version) {
		try {
			String[] numbers = version.split("\\.");
			String chromiumVersion = numbers[0] + "." + numbers[1] + "." + numbers[2] + "." + numbers[3];
			String webKitVersion = numbers[4] + "." + numbers[5];
			String format = "Mozilla/5.0 (Linux; Android %s; wv) AppleWebKit/%s " +
					"(KHTML, like Gecko) Version/4.0 Chrome/%s Mobile Safari/%s";
			return String.format(Locale.US, format, Build.VERSION.RELEASE,
					webKitVersion, chromiumVersion, webKitVersion);
		} catch (Exception e) {
			return null;
		}
	}

	private void loadChromiumUserAgentReference() {
		Chan chan = Chan.getFallback();
		new HttpHolderTask<Void, String>(chan) {
			@Override
			protected String run(HttpHolder holder) {
				try {
					Uri uri = chan.locator.buildQueryWithHost("www.googleapis.com",
							"storage/v1/b/chromium-browser-snapshots/o",
							"prefix", "Linux_x64/LAST_CHANGE", "fields", "items(metadata)");
					JSONObject object = new JSONObject(new HttpRequest(uri, holder).perform().readString());
					JSONArray array = object != null ? object.optJSONArray("items") : null;
					object = array != null && array.length() > 0 ? array.optJSONObject(0) : null;
					object = object != null ? object.optJSONObject("metadata") : null;
					String commit = object != null ? object.optString("cr-git-commit") : null;
					if (StringUtils.isEmpty(commit)) {
						return null;
					}

					uri = chan.locator.buildQueryWithHost("chromium.googlesource.com",
							"chromium/src/+/" + commit + "/chrome/VERSION", "format", "TEXT");
					String chromeVersionResponse = new HttpRequest(uri, holder).perform().readString();
					if (StringUtils.isEmpty(chromeVersionResponse)) {
						return null;
					}
					uri = chan.locator.buildQueryWithHost("chromium.googlesource.com",
							"chromium/src/+/" + commit + "/build/util/webkit_version.h.in", "format", "TEXT");
					String webKitVersionResponse = new HttpRequest(uri, holder).perform().readString();
					if (StringUtils.isEmpty(webKitVersionResponse)) {
						return null;
					}

					String chromiumVersionData;
					try {
						chromiumVersionData = new String(Base64.decode(chromeVersionResponse, Base64.DEFAULT));
					} catch (Exception e) {
						Log.persistent().stack(e);
						return null;
					}
					String webKitVersionData;
					try {
						webKitVersionData = new String(Base64.decode(webKitVersionResponse, Base64.DEFAULT));
					} catch (Exception e) {
						Log.persistent().stack(e);
						return null;
					}
					HashMap<String, Integer> chromeMap = new HashMap<>();
					for (String line : chromiumVersionData.split("\n")) {
						int index = line.indexOf('=');
						if (index >= 0) {
							String key = line.substring(0, index);
							String valueString = line.substring(index + 1);
							try {
								int value = Integer.parseInt(valueString);
								chromeMap.put(key.toLowerCase(Locale.US), value);
							} catch (NumberFormatException e) {
								// Ignore
							}
						}
					}
					HashMap<String, Integer> webKitMap = new HashMap<>();
					for (String line : webKitVersionData.split("\n")) {
						if (line.startsWith("#define")) {
							String[] nameValue = line.substring(7).trim().split(" ");
							if (nameValue.length == 2 && nameValue[0].startsWith("WEBKIT_VERSION_")) {
								try {
									webKitMap.put(nameValue[0].substring(15).toLowerCase(Locale.US),
											Integer.parseInt(nameValue[1]));
								} catch (NumberFormatException e) {
									// Ignore
								}
							}
						}
					}
					Integer chromeMajor = chromeMap.get("major");
					Integer chromeMinor = chromeMap.get("minor");
					Integer chromeBuild = chromeMap.get("build");
					Integer chromePatch = chromeMap.get("patch");
					Integer webKitMajor = webKitMap.get("major");
					Integer webKitMinor = webKitMap.get("minor");
					if (chromeMajor == null || chromeMinor == null) {
						return  null;
					}
					if (chromeBuild == null) {
						chromeBuild = 0;
					}
					if (chromePatch == 0) {
						chromePatch = 0;
					}
					if (webKitMajor == null || webKitMinor == null) {
						webKitMajor = 537;
						webKitMinor = 36;
					}
					return chromeMajor + "." + chromeMinor + "." + chromeBuild + "." + chromePatch + "." +
							webKitMajor + "." + webKitMinor;
				} catch (HttpException | JSONException e) {
					Log.persistent().stack(e);
					return null;
				}
			}

			@Override
			protected void onComplete(String version) {
				if (version != null) {
					Preferences.setUserAgentReference(PREFIX + version);
					String userAgent = formatChromiumUserAgent(version);
					if (userAgent != null) {
						UserAgentProvider.this.userAgent = userAgent;
					}
				}
			}
		}.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	public String getUserAgent() {
		return userAgent;
	}
}
