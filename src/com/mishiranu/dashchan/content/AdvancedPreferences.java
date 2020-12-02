package com.mishiranu.dashchan.content;

import chan.content.ChanManager;
import chan.http.CookieBuilder;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.net.UserAgentProvider;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AdvancedPreferences {
	private static final HashMap<String, String> USER_AGENTS = new HashMap<>();
	private static final HashSet<String> SINGLE_CONNECTIONS = new HashSet<>();
	private static final String GOOGLE_COOKIE;
	private static final int TAB_SIZE;

	static {
		CookieBuilder googleCookieBuilder = null;
		int tabSize = 0;
		File file = MainApplication.getInstance().getExternalCacheDir();
		if (file != null) {
			file = new File(file.getParentFile(), "files/advanced.json");
			if (file.exists()) {
				String jsonString = null;
				FileInputStream inputStream = null;
				try {
					inputStream = new FileInputStream(file);
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					IOUtils.copyStream(inputStream, outputStream);
					@SuppressWarnings("CharsetObjectCanBeUsed")
					String jsonStringSuppress = new String(outputStream.toByteArray(), "UTF-8");
					jsonString = jsonStringSuppress;
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					IOUtils.close(inputStream);
				}
				if (jsonString != null) {
					try {
						JSONObject jsonObject = new JSONObject(jsonString);
						JSONObject userAgentObject = jsonObject.optJSONObject("userAgent");
						if (userAgentObject != null) {
							for (Iterator<String> keys = userAgentObject.keys(); keys.hasNext();) {
								String chanName = keys.next();
								String userAgent = userAgentObject.getString(chanName);
								if (!StringUtils.isEmpty(userAgent)) {
									USER_AGENTS.put(chanName, userAgent);
								}
							}
						} else {
							String userAgent = jsonObject.optString("userAgent", null);
							if (!StringUtils.isEmpty(userAgent)) {
								USER_AGENTS.put(ChanManager.EXTENSION_NAME_CLIENT, userAgent);
							}
						}
						JSONArray singleConnectionArray = jsonObject.optJSONArray("singleConnection");
						if (singleConnectionArray != null) {
							for (int i = 0; i < singleConnectionArray.length(); i++) {
								SINGLE_CONNECTIONS.add(singleConnectionArray.getString(i));
							}
						}
						JSONObject googleCookieObject = jsonObject.optJSONObject("googleCookie");
						if (googleCookieObject != null) {
							for (Iterator<String> keys = googleCookieObject.keys(); keys.hasNext();) {
								String name = keys.next();
								String value = googleCookieObject.getString(name);
								if (!StringUtils.isEmpty(value)) {
									if (googleCookieBuilder == null) {
										googleCookieBuilder = new CookieBuilder();
									}
									googleCookieBuilder.append(name, value);
								}
							}
						} else {
							String googleCookie = jsonObject.optString("googleCookie", null);
							if (!StringUtils.isEmpty(googleCookie)) {
								googleCookieBuilder = new CookieBuilder().append(googleCookie);
							}
						}
						tabSize = jsonObject.optInt("tabSize");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}
		GOOGLE_COOKIE = googleCookieBuilder != null ? googleCookieBuilder.build() : null;
		TAB_SIZE = tabSize;
	}

	public static String getUserAgent(String chanName) {
		String userAgent = USER_AGENTS.get(chanName);
		if (userAgent == null) {
			userAgent = USER_AGENTS.get(ChanManager.EXTENSION_NAME_CLIENT);
		}
		if (userAgent == null) {
			userAgent = UserAgentProvider.getInstance().getUserAgent();
		}
		return userAgent;
	}

	public static boolean isSingleConnection(String chanName) {
		return SINGLE_CONNECTIONS.contains(chanName != null ? chanName : ChanManager.EXTENSION_NAME_CLIENT);
	}

	public static String getGoogleCookie() {
		// Google reCAPTCHA becomes easier with HSID, SSID, SID, NID cookies
		return GOOGLE_COOKIE;
	}

	public static int getTabSize() {
		return TAB_SIZE;
	}
}
