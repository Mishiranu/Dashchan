package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.ForegroundManager;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloudFlareResolver {
	private static final CloudFlareResolver INSTANCE = new CloudFlareResolver();

	private static final Pattern PATTERN_FORBIDDEN = Pattern.compile("<form class=\\\"challenge-form\\\" " +
			"id=\\\"challenge-form\\\" action=\\\"(.*?)\\\" method=\\\"(.*?)\\\"");
	private static final Pattern PATTERN_UNAVAILABLE = Pattern.compile("<span " +
			"data-translate=\\\"checking_browser\\\">Checking your browser before accessing</span>");

	private static final Pattern ALLOWED_LINKS = Pattern.compile("/?(|cdn-cgi/l/.*)");
	private static final Pattern PATTERN_CAPTCHA = Pattern.compile("data-sitekey=\"(.*?)\"");

	private static final String COOKIE_CLOUDFLARE = "cf_clearance";

	public static CloudFlareResolver getInstance() {
		return INSTANCE;
	}

	private CloudFlareResolver() {}

	private class Client implements RelayBlockResolver.Client {
		private String finishUriString;

		@Override
		public String getCookieName() {
			return COOKIE_CLOUDFLARE;
		}

		@Override
		public void storeCookie(String chanName, String cookie) {
			CloudFlareResolver.this.storeCookie(chanName, cookie, finishUriString);
		}

		@Override
		public boolean onPageFinished(String uriString, String title) {
			if (!"Just a moment...".equals(title)) {
				finishUriString = uriString;
				return true;
			}
			return false;
		}

		@Override
		public boolean onLoad(Uri uri) {
			return ALLOWED_LINKS.matcher(uri.getPath()).matches();
		}
	}

	private static class CheckHolder {
		public boolean ready = false;
		public boolean success = false;
	}

	private final HashMap<String, CheckHolder> captchaHolders = new HashMap<>();
	private final HashMap<String, Long> captchaLastCancel = new HashMap<>();

	private RelayBlockResolver.Result handleCaptcha(String chanName, Uri requestedUri,
			Uri specialUri, String recaptchaApiKey) throws HttpException {
		CheckHolder checkHolder = null;
		synchronized (captchaLastCancel) {
			Long lastCancelTime = captchaLastCancel.get(chanName);
			if (lastCancelTime != null && System.currentTimeMillis() - lastCancelTime < 5000) {
				return new RelayBlockResolver.Result(true, false, null);
			}
		}
		if (specialUri == null) {
			boolean handle;
			synchronized (captchaHolders) {
				checkHolder = captchaHolders.get(chanName);
				if (checkHolder == null) {
					checkHolder = new CheckHolder();
					captchaHolders.put(chanName, checkHolder);
					handle = true;
				} else {
					handle = false;
				}
			}
			if (!handle) {
				synchronized (checkHolder) {
					while (!checkHolder.ready) {
						try {
							checkHolder.wait();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return new RelayBlockResolver.Result(true, false, null);
						}
					}
				}
				return new RelayBlockResolver.Result(true, checkHolder.success, null);
			}
		}
		HttpHolder holder = new HttpHolder();
		try {
			boolean retry = false;
			while (true) {
				ChanPerformer.CaptchaData captchaData = ForegroundManager.getInstance().requireUserCaptcha
						(new CloudFlareCaptchaReader(recaptchaApiKey, requestedUri.toString()),
						ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2, null, null, null, null,
						R.string.message_cloudflate_block, retry);
				if (captchaData == null) {
					synchronized (captchaLastCancel) {
						captchaLastCancel.put(chanName, System.currentTimeMillis());
					}
					return new RelayBlockResolver.Result(true, false, null);
				}
				if (Thread.currentThread().isInterrupted()) {
					return new RelayBlockResolver.Result(true, false, null);
				}
				String recaptchaResponse = captchaData.get(ChanPerformer.CaptchaData.INPUT);
				ChanLocator locator = ChanLocator.get(chanName);
				if (specialUri != null) {
					new HttpRequest(specialUri, holder).setRedirectHandler(HttpRequest.RedirectHandler.NONE)
							.setPostMethod(new UrlEncodedEntity("g-recaptcha-response", recaptchaResponse))
							.setSuccessOnly(false).setCheckRelayBlock(false).read();
				} else {
					Uri uri = locator.buildQuery("cdn-cgi/l/chk_captcha", "g-recaptcha-response", recaptchaResponse)
							.buildUpon().scheme(requestedUri.getScheme())
							.authority(requestedUri.getAuthority()).build();
					new HttpRequest(uri, holder).setRedirectHandler(HttpRequest.RedirectHandler.NONE)
							.setSuccessOnly(false).setCheckRelayBlock(false).read();
				}
				String cookie = holder.getCookieValue(COOKIE_CLOUDFLARE);
				if (cookie != null) {
					storeCookie(chanName, cookie, null);
					if (checkHolder != null) {
						checkHolder.success = true;
					}
					return new RelayBlockResolver.Result(true, true, specialUri != null ? holder : null);
				}
				retry = true;
			}
		} finally {
			holder.cleanup();
			if (checkHolder != null) {
				synchronized (captchaHolders) {
					captchaHolders.remove(chanName);
				}
				synchronized (checkHolder) {
					checkHolder.ready = true;
					checkHolder.notifyAll();
				}
			}
		}
	}

	private static class CloudFlareCaptchaReader implements ReadCaptchaTask.CaptchaReader {
		private final String recaptchaApiKey;
		private final String referer;

		public CloudFlareCaptchaReader(String recaptchaApiKey, String referer) {
			this.recaptchaApiKey = recaptchaApiKey;
			this.referer = referer;
		}

		@Override
		public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data) {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, recaptchaApiKey);
			captchaData.put(ChanPerformer.CaptchaData.REFERER, referer);
			return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.CAPTCHA, captchaData);
		}
	}

	public RelayBlockResolver.Result checkResponse(RelayBlockResolver resolver,
			String chanName, Uri uri, HttpHolder holder) throws HttpException {
		int responseCode = holder.getResponseCode();
		if ((responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAVAILABLE)
				&& holder.getHeaderFields().containsKey("CF-RAY")) {
			String responseText = holder.read().getString();
			switch (responseCode) {
				case HttpURLConnection.HTTP_FORBIDDEN: {
					Matcher matcher = PATTERN_FORBIDDEN.matcher(responseText);
					if (matcher.find()) {
						Uri specialUri = null;
						if ("post".equals(matcher.group(2))) {
							specialUri = Uri.parse(matcher.group(1));
							specialUri = specialUri.buildUpon().scheme(uri.getScheme())
									.authority(uri.getAuthority()).build();
						}
						matcher = PATTERN_CAPTCHA.matcher(responseText);
						if (matcher.find()) {
							String captchaApiKey = matcher.group(1);
							return handleCaptcha(chanName, uri, specialUri, captchaApiKey);
						}
					}
					break;
				}
				case HttpURLConnection.HTTP_UNAVAILABLE: {
					Matcher matcher = PATTERN_UNAVAILABLE.matcher(responseText);
					if (matcher.find()) {
						boolean success = resolver.runWebView(chanName, Client::new);
						return new RelayBlockResolver.Result(true, success, null);
					}
					break;
				}
			}
		}
		return new RelayBlockResolver.Result(false, false, null);
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
