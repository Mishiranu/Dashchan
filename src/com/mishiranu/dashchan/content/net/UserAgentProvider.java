package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.util.Base64;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Log;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class UserAgentProvider {
	private static final UserAgentProvider INSTANCE = new UserAgentProvider();

	public static UserAgentProvider getInstance() {
		return INSTANCE;
	}

	private UserAgentProvider() {
		String reference = Preferences.getUserAgentReference();
		if (reference == null) {
			reference = "chromium:74.0.3729.169";
		}
		if (reference.startsWith("chromium:")) {
			String version = reference.substring(9);
			userAgent = formatChromiumUserAgent(version);
		}
		loadChromiumUserAgentReference();
	}

	private String userAgent = "Mozilla/5.0";

	private String formatChromiumUserAgent(String version) {
		return String.format(Locale.US, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/%s Safari/537.36", version);
	}

	private void loadChromiumUserAgentReference() {
		new HttpHolderTask<Void, Void, String>() {
			@Override
			protected String doInBackground(HttpHolder holder, Void... params) {
				JSONObject object;
				try {
					Uri uri = ChanLocator.getDefault().buildQueryWithHost("www.googleapis.com",
							"storage/v1/b/chromium-browser-snapshots/o",
							"prefix", "Linux_x64/LAST_CHANGE", "fields", "items(metadata)");
					object = new HttpRequest(uri, holder).read().getJsonObject();
				} catch (HttpException e) {
					return null;
				}
				JSONArray array = object != null ? object.optJSONArray("items") : null;
				object = array != null && array.length() > 0 ? array.optJSONObject(0) : null;
				object = object != null ? object.optJSONObject("metadata") : null;
				String commit = object != null ? object.optString("cr-git-commit") : null;

				if (commit != null) {
					String response;
					try {
						Uri uri = ChanLocator.getDefault().buildQueryWithHost("chromium.googlesource.com",
								"chromium/src/+/" + commit + "/chrome/VERSION", "format", "TEXT");
						response = new HttpRequest(uri, holder).read().getString();
					} catch (HttpException e) {
						return null;
					}
					if (response != null) {
						String versionData;
						try {
							versionData = new String(Base64.decode(response, Base64.DEFAULT));
						} catch (Exception e) {
							Log.persistent().stack(e);
							return null;
						}
						HashMap<String, Integer> map = new HashMap<>();
						for (String line : versionData.split("\n")) {
							int index = line.indexOf('=');
							if (index >= 0) {
								String key = line.substring(0, index);
								String valueString = line.substring(index + 1);
								try {
									int value = Integer.parseInt(valueString);
									map.put(key.toLowerCase(Locale.US), value);
								} catch (NumberFormatException e) {
									// Ignore
								}
							}
						}
						Integer major = map.get("major");
						Integer minor = map.get("minor");
						Integer build = map.get("build");
						Integer patch = map.get("patch");
						if (major != null && minor != null) {
							return major + "." + minor + "." + (build != null ? build : 0) + "." +
									(patch != null ? patch : 0);
						}
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(String version) {
				if (version != null) {
					Preferences.setUserAgentReference("chromium:" + version);
					userAgent = formatChromiumUserAgent(version);
				}
			}
		}.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	public String getUserAgent() {
		return userAgent;
	}
}
