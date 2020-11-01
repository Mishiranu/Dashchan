package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StormWallResolver {
	private static final StormWallResolver INSTANCE = new StormWallResolver();

	private static final String COOKIE_STORMWALL = "swp_token";

	public static StormWallResolver getInstance() {
		return INSTANCE;
	}

	private StormWallResolver() {}

	private static class CookieResult {
		public final String cookie;
		public final String uriString;

		public CookieResult(String cookie, String uriString) {
			this.cookie = cookie;
			this.uriString = uriString;
		}
	}

	private static class WebViewClient implements RelayBlockResolver.WebViewClient<CookieResult> {
		private volatile boolean wasChecked = false;
		private volatile boolean wasReloaded = false;

		private volatile CookieResult result;

		@Override
		public String getName() {
			return "StormWall";
		}

		@Override
		public CookieResult takeResult() {
			return result;
		}

		@Override
		public boolean onPageFinished(String uriString, Map<String, String> cookies, String title) {
			if (wasChecked) {
				wasChecked = false;
				wasReloaded = true;
			} else if (wasReloaded) {
				String cookie = cookies.get(COOKIE_STORMWALL);
				result = cookie != null ? new CookieResult(cookie, uriString) : null;
				wasReloaded = false;
				return true;
			}
			return false;
		}

		@Override
		public boolean onLoad(Uri initialUri, Uri uri) {
			if ("static.stormwall.pro".equals(uri.getHost())) {
				wasChecked = true;
				return true;
			}
			String path = uri.getPath();
			return path == null || path.isEmpty() || "/".equals(path) || path.equals(initialUri.getPath());
		}
	}

	private static final Pattern PATTERN_CE = Pattern.compile(" cE ?= ?(['\"])(.*?)\\1");
	private static final Pattern PATTERN_CK = Pattern.compile(" cK ?= ?(?:(['\"])|)(.*?)(?:\\1|;)");

	private static String calculateCookie(String ce, int ck) {
		StringBuilder result = new StringBuilder();
		String alphabet = "0123456789qwertyuiopasdfghjklzxcvbnm:?!";
		int length = alphabet.length();
		for (int i = 0; i < ce.length(); i++) {
			char c = ce.charAt(i);
			int index = alphabet.indexOf(c);
			result.append(index >= 0 ? alphabet.charAt((index - ((ck + i) % length) + length) % length) : c);
		}
		return result.toString();
	}

	private class Resolver implements RelayBlockResolver.Resolver {
		public final String responseText;

		public Resolver(String responseText) {
			this.responseText = responseText;
		}

		@Override
		public boolean resolve(RelayBlockResolver resolver, RelayBlockResolver.Session session)
				throws RelayBlockResolver.CancelException, HttpException, InterruptedException {
			Matcher ceMatcher = PATTERN_CE.matcher(responseText);
			Matcher ckMatcher = PATTERN_CK.matcher(responseText);
			if (ceMatcher.find() && ckMatcher.find()) {
				String ce = StringUtils.emptyIfNull(ceMatcher.group(2));
				String ckString = StringUtils.emptyIfNull(ckMatcher.group(2));
				Integer ck = null;
				try {
					ck = Integer.parseInt(ckString);
				} catch (NumberFormatException e) {
					// Ignore
				}
				if (!ce.isEmpty() && ck != null) {
					String calculatedCookie = calculateCookie(ce, ck);
					HttpResponse response = new HttpRequest(session.uri, session.holder)
							.setHeadMethod().setSuccessOnly(false)
							.setCheckRelayBlock(HttpRequest.CheckRelayBlock.SKIP)
							.addCookie(COOKIE_STORMWALL, calculatedCookie)
							.perform();
					try {
						if (!isBlocked(response)) {
							storeCookie(session.chan, calculatedCookie, session.uri.toString());
							return true;
						}
					} finally {
						response.cleanupAndDisconnect();
					}
				}
			}
			CookieResult result = resolver.resolveWebView(session, new WebViewClient());
			if (result != null) {
				storeCookie(session.chan, result.cookie, result.uriString);
				return true;
			}
			return false;
		}
	}

	private boolean isBlocked(HttpResponse response) {
		List<String> headers = response.getHeaderFields().get("X-FireWall-Protection");
		return headers != null && !headers.isEmpty();
	}

	public RelayBlockResolver.Result checkResponse(RelayBlockResolver resolver,
			Chan chan, Uri uri, HttpHolder holder, HttpResponse response, boolean resolve)
			throws HttpException, InterruptedException {
		if (isBlocked(response)) {
			boolean success = false;
			if (resolve) {
				List<String> contentType = response.getHeaderFields().get("Content-Type");
				if (contentType != null && contentType.size() == 1 && contentType.get(0).startsWith("text/html")) {
					String responseText = response.readString();
					success = resolver.runExclusive(chan, uri, holder, () -> new Resolver(responseText));
				}
			}
			return new RelayBlockResolver.Result(true, success);
		}
		return new RelayBlockResolver.Result(false, false);
	}

	private void storeCookie(Chan chan, String cookie, String uriString) {
		chan.configuration.storeCookie(COOKIE_STORMWALL, cookie, cookie != null ? "StormWall" : null);
		chan.configuration.commit();
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null) {
			String host = uri.getHost();
			if (chan.locator.isConvertableChanHost(host)) {
				chan.locator.setPreferredHost(host);
			}
			Preferences.setUseHttps(chan, "https".equals(uri.getScheme()));
		}
	}

	public Map<String, String> addCookies(Chan chan, Map<String, String> cookies) {
		String cookie = chan.configuration.getCookie(COOKIE_STORMWALL);
		if (!StringUtils.isEmpty(cookie)) {
			if (cookies == null) {
				cookies = new HashMap<>();
			}
			cookies.put(COOKIE_STORMWALL, cookie);
		}
		return cookies;
	}
}
