package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.os.Parcel;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
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

	private class Client implements RelayBlockResolver.Client {
		private final Extra extra = new Extra();

		private volatile String finishUriString;
		private volatile String cookie;

		@Override
		public String getName() {
			return "CloudFlare";
		}

		@Override
		public boolean handleResult(String chanName) {
			if (finishUriString != null && cookie != null) {
				storeCookie(chanName, cookie, finishUriString);
				return true;
			}
			return false;
		}

		@Override
		public boolean onPageFinished(String uriString, Map<String, String> cookies, String title) {
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

	public RelayBlockResolver.Result checkResponse(RelayBlockResolver resolver,
			String chanName, Uri uri, HttpHolder holder) throws HttpException {
		int responseCode = holder.getResponseCode();
		if ((responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAVAILABLE)
				&& holder.getHeaderFields().containsKey("CF-RAY")) {
			String responseText = holder.readDirect().getString();
			switch (responseCode) {
				case HttpURLConnection.HTTP_FORBIDDEN:
				case HttpURLConnection.HTTP_UNAVAILABLE: {
					for (String checkTitle : TITLES) {
						if (responseText.contains("<title>" + checkTitle + "</title>")) {
							boolean success = resolver.runWebView(chanName, uri, Client::new);
							return new RelayBlockResolver.Result(true, success);
						}
					}
				}
			}
		}
		return new RelayBlockResolver.Result(false, false);
	}

	private void storeCookie(String chanName, String cookie, String uriString) {
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		configuration.storeCookie(COOKIE_CLOUDFLARE, cookie, cookie != null ? "CloudFlare" : null);
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
		String cookie = ChanConfiguration.get(chanName).getCookie(COOKIE_CLOUDFLARE);
		if (!StringUtils.isEmpty(cookie)) {
			if (cookies == null) {
				cookies = new HashMap<>();
			}
			cookies.put(COOKIE_CLOUDFLARE, cookie);
		}
		return cookies;
	}
}
