package com.mishiranu.dashchan.content.net.firewall;

import android.net.Uri;
import android.os.Parcel;
import chan.content.Chan;
import chan.http.CookieBuilder;
import chan.http.FirewallResolver;
import chan.http.HttpException;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.service.webview.WebViewExtra;
import com.mishiranu.dashchan.util.IOUtils;
import java.net.HttpURLConnection;
import java.util.Map;

public class CloudFlareResolver extends FirewallResolver {
	private static final String COOKIE_CLOUDFLARE = "cf_clearance";
	private static final String[] TITLES = {"Attention Required! | Cloudflare", "Just a moment..."};

	private static class CookieResult {
		public final String cookie;
		public final Uri uri;

		public CookieResult(String cookie, Uri uri) {
			this.cookie = cookie;
			this.uri = uri;
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

	private static class WebViewClient extends FirewallResolvers.WebViewClientWithExtra<CookieResult> {
		private final String title;

		public WebViewClient(String title) {
			super("CloudFlare", new Extra());
			this.title = title;
		}

		@Override
		public boolean onPageFinished(Uri uri, Map<String, String> cookies, String title) {
			if (title != null && title.equals(this.title)) {
				return false;
			}
			for (String checkTitle : TITLES) {
				if (checkTitle.equals(title)) {
					return false;
				}
			}
			String cookie = cookies.get(COOKIE_CLOUDFLARE);
			setResult(cookie != null ? new CookieResult(cookie, uri) : null);
			return true;
		}

		@Override
		public boolean onLoad(Uri initialUri, Uri uri) {
			String path = uri.getPath();
			return path == null || path.isEmpty() || "/".equals(path) ||
					path.equals(initialUri.getPath()) || path.startsWith("/cdn-cgi/");
		}
	}

	private class Exclusive implements FirewallResolver.Exclusive {
		public final String title;

		public Exclusive(String title) {
			this.title = title;
		}

		@Override
		public boolean resolve(Session session, Key key) throws CancelException, InterruptedException {
			CookieResult result = session.resolveWebView(new WebViewClient(title));
			if (result != null) {
				storeCookie(session, key, result.cookie, result.uri);
				return true;
			}
			return false;
		}
	}

	private Exclusive.Key toKey(Session session) {
		return session.getKey(Identifier.Flag.USER_AGENT);
	}

	@Override
	public CheckResponseResult checkResponse(Session session, HttpResponse response) throws HttpException {
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
						return new CheckResponseResult(toKey(session), new Exclusive(title));
					}
				}
			}
		}
		return null;
	}

	private void storeCookie(Session session, Exclusive.Key key, String cookie, Uri uri) {
		Chan chan = session.getChan();
		chan.configuration.storeCookie(key.formatKey(COOKIE_CLOUDFLARE), cookie,
				cookie != null ? key.formatTitle("CloudFlare") : null);
		chan.configuration.commit();
		if (uri != null) {
			String host = uri.getHost();
			if (chan.locator.isConvertableChanHost(host)) {
				chan.locator.setPreferredHost(host);
			}
			Preferences.setUseHttps(chan, "https".equals(uri.getScheme()));
		}
	}

	@Override
	public void collectCookies(Session session, CookieBuilder cookieBuilder) {
		Chan chan = session.getChan();
		Exclusive.Key key = toKey(session);
		String cookie = chan.configuration.getCookie(key.formatKey(COOKIE_CLOUDFLARE));
		if (!StringUtils.isEmpty(cookie)) {
			cookieBuilder.append(COOKIE_CLOUDFLARE, cookie);
		}
	}
}
