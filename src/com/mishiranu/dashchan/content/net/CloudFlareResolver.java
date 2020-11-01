package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.os.Parcel;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;
import com.mishiranu.dashchan.util.IOUtils;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

public class CloudFlareResolver {
	private static final CloudFlareResolver INSTANCE = new CloudFlareResolver();

	private static final String COOKIE_CLOUDFLARE = "cf_clearance";

	private static final String[] TITLES = {"Attention Required! | Cloudflare", "Just a moment..."};

	public static CloudFlareResolver getInstance() {
		return INSTANCE;
	}

	private CloudFlareResolver() {}

	private static class CookieResult {
		public final String cookie;
		public final String uriString;

		public CookieResult(String cookie, String uriString) {
			this.cookie = cookie;
			this.uriString = uriString;
		}
	}

	private static class Extra implements WebViewExtra {
		@Override
		public String getInjectJavascript() {
			return IOUtils.readRawResourceString(MainApplication.getInstance().getResources(),
					R.raw.web_cloudflare_inject);
		}

		public static final Creator<Extra> CREATOR = new Creator<Extra>() {
			@Override
			public Extra createFromParcel(Parcel in) {
				return new Extra();
			}

			@Override
			public Extra[] newArray(int size) {
				return new Extra[0];
			}
		};
	}

	private static class WebViewClient implements RelayBlockResolver.WebViewClient<CookieResult> {
		private final Extra extra = new Extra();
		private final String title;

		public WebViewClient(String title) {
			this.title = title;
		}

		private volatile String finishUriString;
		private volatile String cookie;

		@Override
		public String getName() {
			return "CloudFlare";
		}

		@Override
		public CookieResult takeResult() {
			return finishUriString != null && cookie != null ? new CookieResult(cookie, finishUriString) : null;
		}

		@Override
		public boolean onPageFinished(String uriString, Map<String, String> cookies, String title) {
			if (title != null && title.equals(this.title)) {
				return false;
			}
			for (String checkTitle : TITLES) {
				if (checkTitle.equals(title)) {
					return false;
				}
			}
			finishUriString = uriString;
			cookie = cookies.get(COOKIE_CLOUDFLARE);
			return true;
		}

		@Override
		public boolean onLoad(Uri initialUri, Uri uri) {
			String path = uri.getPath();
			return path == null || path.isEmpty() || "/".equals(path) ||
					path.equals(initialUri.getPath()) || path.startsWith("/cdn-cgi/");
		}

		@Override
		public WebViewExtra getExtra() {
			return extra;
		}
	}

	private class Resolver implements RelayBlockResolver.Resolver {
		public final String title;

		public Resolver(String title) {
			this.title = title;
		}

		@Override
		public boolean resolve(RelayBlockResolver resolver, RelayBlockResolver.Session session)
				throws RelayBlockResolver.CancelException, InterruptedException {
			CookieResult result = resolver.resolveWebView(session, new WebViewClient(title));
			if (result != null) {
				storeCookie(session.chan, result.cookie, result.uriString);
				return true;
			}
			return false;
		}
	}

	public RelayBlockResolver.Result checkResponse(RelayBlockResolver resolver,
			Chan chan, Uri uri, HttpHolder holder, HttpResponse response, boolean resolve)
			throws HttpException, InterruptedException {
		int responseCode = response.getResponseCode();
		if ((responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAVAILABLE)
				&& response.getHeaderFields().containsKey("CF-RAY")) {
			String responseText = response.readString();
			switch (responseCode) {
				case HttpURLConnection.HTTP_FORBIDDEN:
				case HttpURLConnection.HTTP_UNAVAILABLE: {
					String title = null;
					for (String checkTitle : TITLES) {
						if (responseText.contains("<title>" + checkTitle + "</title>")) {
							title = checkTitle;
						}
					}
					if (responseText.contains("<form class=\"challenge-form\" id=\"challenge-form\"") &&
							responseText.contains("__cf_chl_captcha_tk__")) {
						int start = responseText.indexOf("<title>");
						if (start >= 0) {
							int end = responseText.indexOf("</title>", start);
							if (end > start) {
								title = responseText.substring(start + 7, end);
							}
						}
					}
					if (title != null) {
						String titleFinal = title;
						boolean success = resolve && resolver
								.runExclusive(chan, uri, holder, () -> new Resolver(titleFinal));
						return new RelayBlockResolver.Result(true, success);
					}
				}
			}
		}
		return new RelayBlockResolver.Result(false, false);
	}

	private void storeCookie(Chan chan, String cookie, String uriString) {
		chan.configuration.storeCookie(COOKIE_CLOUDFLARE, cookie, cookie != null ? "CloudFlare" : null);
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
		String cookie = chan.configuration.getCookie(COOKIE_CLOUDFLARE);
		if (!StringUtils.isEmpty(cookie)) {
			if (cookies == null) {
				cookies = new HashMap<>();
			}
			cookies.put(COOKIE_CLOUDFLARE, cookie);
		}
		return cookies;
	}
}
