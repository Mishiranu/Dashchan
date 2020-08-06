package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.webkit.WebView;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.StringUtils;
import com.mishiranu.dashchan.preference.Preferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StormWallResolver {
	private static final StormWallResolver INSTANCE = new StormWallResolver();

	private static final String COOKIE_STORMWALL = "swp_token";

	public static StormWallResolver getInstance() {
		return INSTANCE;
	}

	private StormWallResolver() {}

	private class Client extends RelayBlockResolver.Client {
		private volatile boolean wasChecked = false;
		private volatile boolean wasReloaded = false;

		@Override
		public boolean onPageFinished(WebView webView, String uriString) {
			if (wasChecked) {
				wasChecked = false;
				wasReloaded = true;
			} else if (wasReloaded) {
				wasReloaded = false;
				String cookie = StringUtils.nullIfEmpty(extractCookie(uriString, COOKIE_STORMWALL));
				storeCookie(getChanName(), cookie, uriString);
				notifyReady(true);
				return true;
			}
			return false;
		}

		@Override
		public boolean isUriAllowed(Uri uri) {
			if ("static.stormwall.pro".equals(uri.getHost())) {
				wasChecked = true;
				return true;
			}
			String path = uri.getPath();
			return path == null || path.isEmpty() || "/".equals(path);
		}
	}

	public RelayBlockResolver.Result checkResponse(RelayBlockResolver resolver,
			String chanName, HttpHolder holder) throws HttpException {
		List<String> contentType = holder.getHeaderFields().get("Content-Type");
		if (contentType != null && contentType.size() == 1 && contentType.get(0).startsWith("text/html")) {
			String responseText = holder.read().getString();
			if (responseText.contains("<script src=\"https://static.stormwall.pro")) {
				boolean success = resolver.runWebView(chanName, Client::new);
				return new RelayBlockResolver.Result(true, success, null);
			}
		}
		return new RelayBlockResolver.Result(false, false, null);
	}

	private void storeCookie(String chanName, String cookie, String uriString) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		configuration.storeCookie(COOKIE_STORMWALL, cookie, cookie != null ? "StormWall" : null);
		configuration.commit();
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null) {
			ChanLocator locator = ChanLocator.get(chanName);
			String host = uri.getHost();
			if (locator.isConvertableChanHost(host)) {
				locator.setPreferredHost(host);
			}
			Preferences.setUseHttps(chanName, "https".equals(uri.getScheme()));
		}
	}

	public Map<String, String> addCookies(String chanName, Map<String, String> cookies) {
		String cookie = ChanConfiguration.get(chanName).getCookie(COOKIE_STORMWALL);
		if (!StringUtils.isEmpty(cookie)) {
			if (cookies == null) {
				cookies = new HashMap<>();
			}
			cookies.put(COOKIE_STORMWALL, cookie);
		}
		return cookies;
	}
}
