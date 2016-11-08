/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.content.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.WebViewUtils;

public class RecaptchaReader implements Handler.Callback {
	private static final RecaptchaReader INSTANCE = new RecaptchaReader();

	public static RecaptchaReader getInstance() {
		return INSTANCE;
	}

	private final Handler mHandler = new Handler(Looper.getMainLooper(), this);

	private RecaptchaReader() {}

	private WebView mWebView;
	private RecaptchaClient mClient;

	private String mRecaptchaV1Html;
	private String mRecaptchaV2Html;

	private final Object mAccessLock = new Object();

	private static final Pattern RECAPTCHA_CHALLENGE_PATTERN = Pattern.compile("[\"'](.{100,}?)[\"']");
	private static final Pattern RECAPTCHA_FALLBACK_PATTERN = Pattern.compile("(?:(?:<label for=\"response\" " +
			"class=\"fbc-imageselect-message-text\">|<div class=\"fbc-imageselect-message-error\">)(.*?)" +
			"(?:</label>|</div>).*?)?value=\"(.{20,}?)\"");
	private static final Pattern RECAPTCHA_RESULT_PATTERN = Pattern.compile("<textarea.*?>(.*?)</textarea>");

	private static final byte[] STUB_IMAGE;

	static {
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
		STUB_IMAGE = output.toByteArray();
		bitmap.recycle();
	}

	public void preloadNewWidget(String apiKey) {
		LoadingHolder loadingHolder = new LoadingHolder(apiKey, true, null);
		loadingHolder.preload = PRELOAD_STATE_ENABLED;
		mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOAD, loadingHolder));
	}

	public String getChallenge2(HttpHolder holder, String apiKey, boolean useJavaScript) throws SkipException,
			CancelException, HttpException {
		if (useJavaScript) {
			synchronized (mAccessLock) {
				LoadingHolder loadingHolder = new LoadingHolder(apiKey, true, null);
				mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOAD, loadingHolder));
				if (loadingHolder.waitForExpiredOrReady()) {
					mHandler.sendEmptyMessage(MESSAGE_CANCEL);
					return null;
				}
				if (loadingHolder.imageSelector) {
					boolean reset = true;
					ComplexChallenge challenge = loadingHolder.challenge;
					Bitmap[] captchaImages = null;
					int lastReplaceIndex = -1;
					while (true) {
						if (reset) {
							if (captchaImages != null) {
								for (Bitmap captchaImage : captchaImages) {
									captchaImage.recycle();
								}
							}
							Bitmap fullCaptchaImage = getImage2(holder, apiKey, challenge.challenge,
									challenge.id, false).first;
							captchaImages = splitImages(fullCaptchaImage, loadingHolder.imageSelectorSizeX,
									loadingHolder.imageSelectorSizeY);
							loadingHolder.replace = null;
							reset = false;
						}
						String description = loadingHolder.imageSelectorDescription;
						int willReplaceIndex = description != null ? description
								.indexOf("Click verify once there are none left.") : -1;
						boolean willReplace = willReplaceIndex >= 0;
						if (loadingHolder.replace != null && lastReplaceIndex >= 0) {
							Bitmap replaceCaptchaImage = getImage2(holder, apiKey, loadingHolder.replace.challenge,
									loadingHolder.replace.id, false).first;
							loadingHolder.replace = null;
							captchaImages[lastReplaceIndex].recycle();
							captchaImages[lastReplaceIndex] = replaceCaptchaImage;
						}
						lastReplaceIndex = -1;
						int countX = Math.max(loadingHolder.imageSelectorSizeX, 3);
						boolean[] previous = loadingHolder.imageSelectorPreviousSelected;
						boolean[] result;
						if (willReplace) {
							Integer singleResult = ForegroundManager.getInstance().requireUserImageSingleChoice(countX,
									-1, captchaImages, description.substring(0, willReplaceIndex).trim(), null);
							if (singleResult != null) {
								lastReplaceIndex = singleResult;
								result = new boolean[captchaImages.length];
								if (lastReplaceIndex >= 0) {
									result[lastReplaceIndex] = true;
								}
							} else {
								result = null;
							}
						} else {
							result = ForegroundManager.getInstance().requireUserImageMultipleChoice(countX, previous,
									captchaImages, description, null);
						}
						loadingHolder.imageSelectorPreviousSelected = null;
						if (result != null) {
							for (int i = 0; i < result.length; i++) {
								boolean changed = result[i] ^ (previous != null ? previous[i] : false);
								if (changed) {
									onImageSelectionImageToggle(i, loadingHolder.imageSelectorSizeX);
								}
							}
							loadingHolder.ready = false;
							loadingHolder.expired = false;
							loadingHolder.updateTime();
							if (willReplace && lastReplaceIndex >= 0) {
								// Wait until new replace image loaded
								boolean hasReplace;
								synchronized (loadingHolder) {
									hasReplace = loadingHolder.replace != null;
								}
								if (!hasReplace && loadingHolder.waitForExpiredOrReady()) {
									mHandler.sendEmptyMessage(MESSAGE_CANCEL);
									return null;
								}
								continue;
							}
							mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_VERIFY, loadingHolder));
							if (loadingHolder.waitForExpiredOrReady()) {
								mHandler.sendEmptyMessage(MESSAGE_CANCEL);
								return null;
							}
							if (loadingHolder.response != null) {
								mHandler.sendEmptyMessage(MESSAGE_CANCEL);
								throw new SkipException(loadingHolder.response);
							}
							if (loadingHolder.expired) {
								throw new HttpException(ErrorItem.TYPE_CAPTCHA_EXPIRED, false, false);
							}
							if (!challenge.equals(loadingHolder.challenge)) {
								challenge = loadingHolder.challenge;
								reset = true;
								continue;
							}
							if (loadingHolder.imageSelectorTooFew) {
								loadingHolder.imageSelectorTooFew = false;
								continue;
							}
							throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, false);
						}
						throw new CancelException();
					}
				}
				if (loadingHolder.challenge != null) {
					return loadingHolder.challenge.challenge;
				}
				if (loadingHolder.response != null) {
					throw new SkipException(loadingHolder.response);
				}
				mHandler.sendEmptyMessage(MESSAGE_CANCEL);
				throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, false);
			}
		} else {
			ChanLocator locator = ChanLocator.getDefault();
			Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
			Bitmap captchaImage = null;
			String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
					.addHeader("Accept-Language", "en-US").addHeader("Referer", "https://www.google.com/")
					.read().getString();
			while (true) {
				Matcher matcher = RECAPTCHA_FALLBACK_PATTERN.matcher(responseText);
				if (matcher.find()) {
					String imageSelectorDescription = matcher.group(1);
					String challenge = matcher.group(2);
					if (imageSelectorDescription != null) {
						if (captchaImage != null) {
							captchaImage.recycle();
						}
						captchaImage = getImage2(holder, apiKey, challenge, null, false).first;
						boolean[] result = ForegroundManager.getInstance().requireUserImageMultipleChoice(3, null,
								splitImages(captchaImage, 3, 3), HtmlParser.clear(imageSelectorDescription), null);
						if (result != null) {
							boolean hasSelected = false;
							UrlEncodedEntity entity = new UrlEncodedEntity("c", challenge);
							for (int i = 0; i < result.length; i++) {
								if (result[i]) {
									entity.add("response", Integer.toString(i));
									hasSelected = true;
								}
							}
							if (!hasSelected) {
								continue;
							}
							responseText = new HttpRequest(uri, holder).setPostMethod(entity)
									.addCookie(AdvancedPreferences.getGoogleCookie())
									.setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
									.addHeader("Accept-Language", "en-US")
									.addHeader("Referer", "https://www.google.com/").read().getString();
							matcher = RECAPTCHA_RESULT_PATTERN.matcher(responseText);
							if (matcher.find()) {
								String response = matcher.group(1);
								throw new SkipException(response);
							}
							continue;
						}
						throw new CancelException();
					}
					return challenge;
				} else {
					return null;
				}
			}
		}
	}

	private final HashMap<String, Pair<Long, String>> mLastChallenges1 = new HashMap<>();

	public String getChallenge1(HttpHolder holder, String apiKey, boolean useJavaScript) throws HttpException {
		if (useJavaScript) {
			synchronized (mAccessLock) {
				LoadingHolder loadingHolder = new LoadingHolder(apiKey, false, null);
				mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOAD, loadingHolder));
				if (loadingHolder.waitForExpiredOrReady()) {
					mHandler.sendEmptyMessage(MESSAGE_CANCEL);
					return null;
				}
				if (loadingHolder.challenge != null) {
					return loadingHolder.challenge.challenge;
				}
				mHandler.sendEmptyMessage(MESSAGE_CANCEL);
				throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, false);
			}
		} else {
			ChanLocator locator = ChanLocator.getDefault();
			String challenge = null;
			synchronized (mLastChallenges1) {
				Pair<Long, String> pair = mLastChallenges1.get(apiKey);
				if (pair != null && System.currentTimeMillis() - pair.first < 10 * 60 * 1000) {
					challenge = pair.second;
				}
			}
			if (challenge == null) {
				Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/challenge", "k", apiKey);
				String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
						.read().getString();
				Matcher matcher = RECAPTCHA_CHALLENGE_PATTERN.matcher(responseText);
				if (!matcher.find()) {
					return null;
				}
				challenge = matcher.group(1);
			}
			Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/reload", "c", challenge,
					"k", apiKey, "type", "image");
			String responseText = new HttpRequest(uri, holder).addCookie(AdvancedPreferences.getGoogleCookie())
					.read().getString();
			Matcher matcher = RECAPTCHA_CHALLENGE_PATTERN.matcher(responseText);
			if (matcher.find()) {
				challenge = matcher.group(1);
				synchronized (mLastChallenges1) {
					mLastChallenges1.put(apiKey, new Pair<>(System.currentTimeMillis(), challenge));
				}
				return challenge;
			}
			return null;
		}
	}

	public Pair<Bitmap, Boolean> getImage1(HttpHolder holder, String challenge, boolean transformBlackAndWhite)
			throws HttpException {
		return getImage(holder, null, challenge, null, transformBlackAndWhite, false);
	}


	public Pair<Bitmap, Boolean> getImage2(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite) throws HttpException {
		return getImage(holder, apiKey, challenge, id, transformBlackAndWhite, true);
	}
	private Pair<Bitmap, Boolean> getImage(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite, boolean recaptcha2) throws HttpException {
		ChanLocator locator = ChanLocator.getDefault();
		Uri uri;
		if (recaptcha2) {
			uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api2/payload", "c", challenge, "k", apiKey,
					"id", StringUtils.emptyIfNull(id));
		} else {
			uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/image", "c", challenge);
		}
		Bitmap image = new HttpRequest(uri, holder).read().getBitmap();
		if (transformBlackAndWhite) {
			transformBlackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
		}
		return transformBlackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image) : new Pair<>(image, false);
	}

	// Returns g-recaptcha-response field, or null if captcha invalid.
	public String getResponseField2(HttpHolder holder, String apiKey, String challenge, String input,
			boolean useJavaScript) throws HttpException {
		if (apiKey == null || challenge == null) {
			return null;
		}
		if (useJavaScript) {
			if (mClient != null && mClient.getLoadingHolder() != null) {
				LoadingHolder loadingHolder = mClient.getLoadingHolder();
				if (loadingHolder.recaptcha2 && apiKey.equals(loadingHolder.apiKey) &&
						challenge.equals(loadingHolder.challenge != null ? loadingHolder.challenge.challenge : null)) {
					loadingHolder = new LoadingHolder(apiKey, true, input);
					mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_VERIFY, loadingHolder));
					if (loadingHolder.waitForExpiredOrReady()) {
						mHandler.sendEmptyMessage(MESSAGE_CANCEL);
						return null;
					}
					if (loadingHolder.response != null) {
						mHandler.sendEmptyMessage(MESSAGE_CANCEL);
						return loadingHolder.response;
					}
					if (loadingHolder.expired) {
						throw new HttpException(ErrorItem.TYPE_CAPTCHA_EXPIRED, false, false);
					}
					return null;
				}
			}
			throw new HttpException(ErrorItem.TYPE_UNKNOWN, false, false);
		} else {
			ChanLocator locator = ChanLocator.getDefault();
			Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
			UrlEncodedEntity entity = new UrlEncodedEntity("c", challenge, "response", input);
			String data = new HttpRequest(uri, holder).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
			Matcher matcher = RECAPTCHA_RESULT_PATTERN.matcher(data);
			return matcher.find() ? matcher.group(1) : null;
		}
	}

	public void preloadWebView() {
		if (ConcurrentUtils.isMain()) {
			initWebView();
		}
	}

	private static final int PRELOAD_STATE_NONE = 0;
	private static final int PRELOAD_STATE_ENABLED = 1;
	private static final int PRELOAD_STATE_COMPLETE = 2;

	private static final int WAIT_TIMEOUT = 15000;

	private static class ComplexChallenge {
		public final String challenge;
		public final String id;

		public ComplexChallenge(String challenge, String id) {
			this.challenge = challenge;
			this.id = id;
		}

		public ComplexChallenge(Uri uri) {
			this(uri.getQueryParameter("c"), uri.getQueryParameter("id"));
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof ComplexChallenge) {
				ComplexChallenge co = (ComplexChallenge) o;
				return StringUtils.equals(co.challenge, challenge) && StringUtils.equals(co.id, id);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + (challenge != null ? challenge.hashCode() : 0);
			result = prime * result + (id != null ? id.hashCode() : 0);
			return result;
		}
	}

	private static class LoadingHolder {
		public final String apiKey;
		public final boolean recaptcha2;
		public final String input;

		public ComplexChallenge challenge;
		public ComplexChallenge replace;
		public boolean queuedReplace;
		public String response;
		public boolean expired;
		public int preload = PRELOAD_STATE_NONE;

		public boolean imageSelector = false;
		public int imageSelectorSizeX;
		public int imageSelectorSizeY;
		public boolean imageSelectorTooFew = false;
		public boolean[] imageSelectorPreviousSelected;
		public String imageSelectorDescription;

		public boolean ready = false;
		public long time;

		public LoadingHolder(String apiKey, boolean recaptcha2, String input) {
			this.apiKey = apiKey;
			this.recaptcha2 = recaptcha2;
			this.input = input;
			updateTime();
		}

		public void applyReady() {
			synchronized (this) {
				ready = true;
				updateTime();
				notifyAll();
			}
		}

		public void updateTime() {
			synchronized (this) {
				time = System.currentTimeMillis();
			}
		}

		public long getTimeFromLastUpdate() {
			synchronized (this) {
				return System.currentTimeMillis() - time;
			}
		}

		public boolean waitForExpiredOrReady() {
			synchronized (this) {
				while (true) {
					if (ready) {
						break;
					}
					long time = WAIT_TIMEOUT - getTimeFromLastUpdate();
					if (time <= 0L) {
						break;
					}
					try {
						wait(time);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return true;
					}
				}
			}
			return false;
		}

		public boolean hasValidResult() {
			return challenge != null || response != null || expired || preload != PRELOAD_STATE_NONE;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof LoadingHolder) {
				LoadingHolder loadingHolder = (LoadingHolder) o;
				return loadingHolder.apiKey.equals(apiKey) && loadingHolder.recaptcha2 == recaptcha2;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return apiKey.hashCode() + 31 * (recaptcha2 ? 1 : 0);
		}
	}

	private static final int MESSAGE_LOAD = 1;
	private static final int MESSAGE_CANCEL = 2;
	private static final int MESSAGE_URI = 3;
	private static final int MESSAGE_VERIFY = 4;
	private static final int MESSAGE_VERIFY_CONTINUE = 5;
	private static final int MESSAGE_CHECK_IMAGE_SELECT = 6;
	private static final int MESSAGE_CHECK_IMAGE_SELECT_CONTINUE = 7;
	private static final int MESSAGE_CHECK_IMAGE_TOO_FEW = 8;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public boolean handleMessage(Message msg) {
		initWebView();
		switch (msg.what) {
			case MESSAGE_LOAD: {
				LoadingHolder loadingHolder = (LoadingHolder) msg.obj;
				LoadingHolder oldLoadingHolder = mClient.getLoadingHolder();
				// I can refresh (simulate click or call js method, without reload) if script worked correctly last time
				boolean canRefresh = loadingHolder.equals(oldLoadingHolder) && oldLoadingHolder.hasValidResult();
				if (canRefresh && oldLoadingHolder.recaptcha2 && oldLoadingHolder.response != null
						&& oldLoadingHolder.challenge == null) {
					// I can't simulate click to new reCAPTCHA if previous result was without captcha (green checkbox)
					canRefresh = false;
				}
				if (canRefresh) {
					if (loadingHolder.preload == PRELOAD_STATE_ENABLED &&
							oldLoadingHolder.preload != PRELOAD_STATE_NONE) {
						// I needn't to preload
						break;
					}
					if (oldLoadingHolder.preload == PRELOAD_STATE_ENABLED) {
						// Will start automatically after old preload complete
						mClient.setLoadingHolder(loadingHolder);
						break;
					}
				}
				if (canRefresh) {
					// If this is preload request, I mustn't refresh
					if (loadingHolder.preload != PRELOAD_STATE_ENABLED) {
						long time = oldLoadingHolder.getTimeFromLastUpdate();
						time = 1000 - time;
						if (time > 0) {
							// You can reload captcha only once per second (recaptcha restriction)
							mHandler.sendMessageDelayed(Message.obtain(msg), time);
						} else {
							mClient.setLoadingHolder(loadingHolder);
							if (loadingHolder.recaptcha2) {
								loadingHolder.updateTime();
								if (oldLoadingHolder.preload == PRELOAD_STATE_COMPLETE) {
									mWebView.loadUrl("javascript:recaptchaStartCheck()");
								} else {
									mWebView.loadUrl("javascript:recaptchaStartReload()");
								}
							} else {
								mWebView.loadUrl("javascript:recaptchaReload()");
							}
						}
					}
				} else {
					mWebView.stopLoading();
					WebViewUtils.clearAll(mWebView);
					CookieManager cookieManager = CookieManager.getInstance();
					String cookies = AdvancedPreferences.getGoogleCookie();
					if (cookies != null) {
						WebViewUtils.setThirdPartyCookiesEnabled(mWebView);
						for (String cookie : cookies.split("; *")) {
							cookieManager.setCookie("google.com", cookie + "; path=/; domain=.google.com");
						}
					}
					mClient.setLoadingHolder(loadingHolder);
					ChanLocator locator = ChanLocator.getDefault();
					String uriString = locator.buildQueryWithSchemeHost(true, "www.google.com", null).toString();
					String data;
					if (loadingHolder.recaptcha2) {
						if (mRecaptchaV2Html == null) {
							mRecaptchaV2Html = readHtmlAsset("recaptcha-v2.html");
						}
						data = mRecaptchaV2Html.replace("__REPLACE_API_KEY__", loadingHolder.apiKey);
					} else {
						if (mRecaptchaV1Html == null) {
							mRecaptchaV1Html = readHtmlAsset("recaptcha-v1.html");
						}
						data = mRecaptchaV1Html.replace("__REPLACE_API_KEY__", loadingHolder.apiKey);
					}
					mWebView.loadDataWithBaseURL(uriString, data, "text/html", "UTF-8", null);
				}
				return true;
			}
			case MESSAGE_CANCEL: {
				mWebView.stopLoading();
				mClient.setLoadingHolder(null);
				return true;
			}
			case MESSAGE_URI: {
				String uriString = (String) msg.obj;
				mWebView.loadUrl(uriString);
				return true;
			}
			case MESSAGE_VERIFY: {
				LoadingHolder loadingHolder = (LoadingHolder) msg.obj;
				mClient.setLoadingHolder(loadingHolder);
				mClient.requestCheckCaptchaExpired(MESSAGE_VERIFY_CONTINUE);
				break;
			}
			case MESSAGE_VERIFY_CONTINUE: {
				LoadingHolder loadingHolder = mClient.getLoadingHolder();
				String expired = (String) msg.obj;
				if ("false".equals(expired)) {
					if (!loadingHolder.imageSelector) {
						String input = StringUtils.emptyIfNull(loadingHolder.input)
								.replace("\\", "\\\\").replace("'", "\\'");
						mWebView.loadUrl("javascipt:recaptchaPerformInput('" + input + "')");
					}
					mWebView.loadUrl("javascript:recaptchaStartVerify()");
					if (loadingHolder.imageSelector) {
						mClient.requestCheckImageSelectedTooFew(MESSAGE_CHECK_IMAGE_TOO_FEW);
					}
				} else {
					loadingHolder.expired = true;
					loadingHolder.applyReady();
				}
				break;
			}
			case MESSAGE_CHECK_IMAGE_SELECT: {
				mClient.requestCheckImageSelect(MESSAGE_CHECK_IMAGE_SELECT_CONTINUE);
				break;
			}
			case MESSAGE_CHECK_IMAGE_SELECT_CONTINUE: {
				LoadingHolder loadingHolder = mClient.getLoadingHolder();
				String[] data = (String[]) msg.obj;
				String imageSelector = data[0];
				String description = data[1];
				String sizeX = data[2];
				String sizeY = data[3];
				loadingHolder.imageSelector = !StringUtils.isEmpty(imageSelector);
				if (loadingHolder.imageSelector) {
					loadingHolder.imageSelectorDescription = HtmlParser.clear(description);
					loadingHolder.imageSelectorSizeX = parseSizeInt(sizeX, 2);
					loadingHolder.imageSelectorSizeY = parseSizeInt(sizeY, 2);
				}
				loadingHolder.applyReady();
				break;
			}
			case MESSAGE_CHECK_IMAGE_TOO_FEW: {
				LoadingHolder loadingHolder = mClient.getLoadingHolder();
				String[] data = (String[]) msg.obj;
				boolean few = "true".equals(data[0]);
				if (few) {
					String sstr = data[1];
					int count = loadingHolder.imageSelectorSizeX * loadingHolder.imageSelectorSizeY;
					boolean[] selected = new boolean[count];
					int length = Math.min(count, sstr.length());
					for (int i = 0; i < length; i++) {
						selected[i] = sstr.charAt(i) == '1';
					}
					loadingHolder.imageSelectorTooFew = true;
					loadingHolder.imageSelectorPreviousSelected = selected;
					loadingHolder.applyReady();
				}
				break;
			}
		}
		return false;
	}

	private static int parseSizeInt(String size, int min) {
		if (size != null) {
			try {
				return Math.max(Integer.parseInt(size), min);
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		return min;
	}

	private class RecaptchaClient extends WebViewClient {
		private LoadingHolder mLoadingHolder;

		private final Object mNextRequestLock = new Object();
		private int mNextRequestType;
		private int mNextRequestMessage;

		private static final int REQUEST_CHECK_CAPTCHA_EXPIRED = 1;
		private static final int REQUEST_CHECK_IMAGE_SELECT = 2;
		private static final int REQUEST_CHECK_IMAGE_SELECTED_TOO_FEW = 3;

		@SuppressWarnings("unused")
		private final Object mJavascriptInterface = new Object() {
			@JavascriptInterface
			public void onSuccess(String response) {
				if (mLoadingHolder != null) {
					mLoadingHolder.response = response;
					mLoadingHolder.applyReady();
				}
			}

			@JavascriptInterface
			public void onCheckCaptchaExpired(String expired) {
				notifyHandler(REQUEST_CHECK_CAPTCHA_EXPIRED, expired);
			}

			@JavascriptInterface
			public void onCheckImageSelect(String imageSelector, String description, String sizeX, String sizeY) {
				notifyHandler(REQUEST_CHECK_IMAGE_SELECT, new String[] {imageSelector, description, sizeX, sizeY});
			}

			@JavascriptInterface
			public void onCheckImageSelectedTooFew(String few, String checked) {
				notifyHandler(REQUEST_CHECK_IMAGE_SELECTED_TOO_FEW, new String[] {few, checked});
			}
		};

		private void notifyHandler(int checkType, Object object) {
			synchronized (mNextRequestLock) {
				if (mNextRequestType == checkType) {
					mHandler.sendMessage(mHandler.obtainMessage(mNextRequestMessage, object));
					mNextRequestType = 0;
					mNextRequestMessage = 0;
				}
			}
		}

		@SuppressLint({"JavascriptInterface", "AddJavascriptInterface"})
		public RecaptchaClient(WebView webView) {
			webView.addJavascriptInterface(mJavascriptInterface, "jsi");
		}

		public void setLoadingHolder(LoadingHolder loadingHolder) {
			mLoadingHolder = loadingHolder;
		}

		public LoadingHolder getLoadingHolder() {
			return mLoadingHolder;
		}

		public void requestCheckCaptchaExpired(int message) {
			request(REQUEST_CHECK_CAPTCHA_EXPIRED, message, "javascript:recaptchaCheckCaptchaExpired()");
		}

		public void requestCheckImageSelect(int message) {
			request(REQUEST_CHECK_IMAGE_SELECT, message, "javascript:recaptchaCheckImageSelect()");
		}

		public void requestCheckImageSelectedTooFew(int message) {
			request(REQUEST_CHECK_IMAGE_SELECTED_TOO_FEW, message, "javascript:recaptchaCheckImageSelectedTooFew()");
		}

		private void request(int type, int message, String url) {
			synchronized (mNextRequestLock) {
				mNextRequestType = type;
				mNextRequestMessage = message;
			}
			mWebView.loadUrl(url);
		}

		private static final int INTERCEPT_NONE = 0;
		private static final int INTERCEPT_STUB = 1;
		private static final int INTERCEPT_IMAGE = 2;

		private int interceptRequest(String uriString) {
			if (mLoadingHolder != null) {
				mLoadingHolder.updateTime();
			}
			Uri uri = Uri.parse(uriString);
			String host = uri.getAuthority();
			if (host == null) {
				return INTERCEPT_STUB;
			}
			boolean google = host.contains("google");
			boolean gstatic = host.equals("www.gstatic.com");
			if (!google && !gstatic || gstatic && !uriString.endsWith(".js")) {
				return INTERCEPT_STUB;
			}
			if (google && uri.getPath().startsWith("/js/bg")) {
				// New recaptcha: make click on "I'm not a robot" card or mark preload complete
				if (mLoadingHolder != null) {
					if (mLoadingHolder.preload != PRELOAD_STATE_NONE) {
						mLoadingHolder.preload = PRELOAD_STATE_COMPLETE;
					} else {
						mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_URI,
								"javascript:recaptchaStartCheck()"), 200);
					}
				}
				return INTERCEPT_NONE;
			}
			String path = uri.getPath();
			if ("/recaptcha/api/image".equals(path) || "/recaptcha/api2/payload".equals(path)) {
				if (mLoadingHolder != null) {
					ComplexChallenge challenge = new ComplexChallenge(uri);
					ComplexChallenge oldChallenge = mLoadingHolder.challenge;
					if (!challenge.equals(oldChallenge)) {
						if (mLoadingHolder.recaptcha2 && oldChallenge != null && mLoadingHolder.queuedReplace) {
							synchronized (mLoadingHolder) {
								mLoadingHolder.queuedReplace = false;
								mLoadingHolder.replace = challenge;
								mLoadingHolder.applyReady();
							}
						} else {
							mLoadingHolder.challenge = challenge;
						}
						if (mLoadingHolder.recaptcha2) {
							mHandler.sendEmptyMessage(MESSAGE_CHECK_IMAGE_SELECT);
						} else {
							mLoadingHolder.applyReady();
						}
					}
				}
				return INTERCEPT_IMAGE;
			}
			if ("/recaptcha/api2/replaceimage".equals(path)) {
				if (mLoadingHolder != null) {
					mLoadingHolder.queuedReplace = true;
				}
			}
			return INTERCEPT_NONE;
		}

		@SuppressWarnings("deprecation")
		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
			Log.persistent().write("recaptcha intercept", url.length() > 100 ? url.substring(0, 100) + "..." : url);
			int intercept = interceptRequest(url);
			switch (intercept) {
				case INTERCEPT_NONE: {
					return null;
				}
				case INTERCEPT_STUB: {
					return new WebResourceResponse("text/html", "UTF-8", null);
				}
				case INTERCEPT_IMAGE: {
					// 1x1 image is better than text, sometimes recaptcha can be jammed if I return text
					return new WebResourceResponse("image/png", null, new ByteArrayInputStream(STUB_IMAGE));
				}
			}
			throw new IllegalStateException();
		}
	}

	private void onImageSelectionImageToggle(int index, int sizeX) {
		mHandler.obtainMessage(MESSAGE_URI, "javascript:recaptchaToogleImageChoice(" + index + ", " + sizeX + ")")
				.sendToTarget();
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	private void initWebView() {
		if (mWebView == null) {
			Context context = MainApplication.getInstance();
			if (!C.API_LOLLIPOP) {
				android.webkit.CookieSyncManager.createInstance(context);
			}
			mWebView = new WebView(context);
			mClient = new RecaptchaClient(mWebView);
			mWebView.setWebViewClient(mClient);
			mWebView.setWebChromeClient(new WebChromeClient() {
				@Override
				@Deprecated
				public void onConsoleMessage(String message, int lineNumber, String sourceID) {
					Log.persistent().write("recaptcha js log", lineNumber, sourceID, message);
					super.onConsoleMessage(message, lineNumber, sourceID);
				}
			});
			WebSettings settings = mWebView.getSettings();
			settings.setJavaScriptEnabled(true);
			settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
			settings.setAppCacheEnabled(false);
			WebViewUtils.clearAll(mWebView);
		}
	}

	private String readHtmlAsset(String fileName) {
		InputStream input = null;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			input = mWebView.getContext().getAssets().open("html/" + fileName);
			IOUtils.copyStream(input, output);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(input);
		}
		return new String(output.toByteArray());
	}

	private static Bitmap[] splitImages(Bitmap image, int sizeX, int sizeY) {
		Bitmap[] images = new Bitmap[sizeX * sizeY];
		int width = image.getWidth() / sizeX;
		int height = image.getHeight() / sizeY;
		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				new Canvas(bitmap).drawBitmap(image, -x * width, -y * height, null);
				images[y * sizeX + x] = bitmap;
			}
		}
		return images;
	}

	public static class CancelException extends Exception {
		private static final long serialVersionUID = 1L;

		public CancelException() {}
	}

	public class SkipException extends Exception {
		private static final long serialVersionUID = 1L;

		private final String mResponse;

		public SkipException(String response) {
			mResponse = response;
		}

		public String getResponse() {
			return mResponse;
		}
	}
}