package com.mishiranu.dashchan.content.net;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ScaledWebView;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecaptchaReader {
	private static final float EXTRA_SCALE_FOR_SYSTEM_PADDING = 0.8f;

	private static final RecaptchaReader INSTANCE = new RecaptchaReader();

	public static RecaptchaReader getInstance() {
		return INSTANCE;
	}

	private RecaptchaReader() {}

	private final Object accessLock = new Object();

	private static final Pattern RECAPTCHA_FALLBACK_PATTERN = Pattern.compile("(?:(?:<div " +
			"class=\"(?:rc-imageselect-desc(?:-no-canonical)?|fbc-imageselect-message-error)\">)(.*?)" +
			"</div>.*?)?value=\"(.{20,}?)\"");
	private static final Pattern RECAPTCHA_RESULT_PATTERN = Pattern.compile("<textarea.*?>(.*?)</textarea>");

	public static class ChallengeExtra {
		private interface ForegroundSolver {
			String solve(HttpHolder holder, ChallengeExtra challengeExtra)
					throws CancelException, HttpException, InterruptedException;
		}

		private final ForegroundSolver solver;
		public final String response;
		private WebViewHolder holder;

		public ChallengeExtra(ForegroundSolver solver, String response, WebViewHolder holder) {
			this.solver = solver;
			this.response = response;
			this.holder = holder;
		}

		public String getResponse(HttpHolder holder) throws CancelException, HttpException, InterruptedException {
			if (response != null) {
				return response;
			} else {
				return solver.solve(holder, this);
			}
		}

		public void cleanup() {
			if (holder != null) {
				ConcurrentUtils.HANDLER.post(() -> {
					if (holder != null) {
						holder.destroy();
						holder = null;
					}
				});
			}
		}
	}

	private static Pair<String, String> parseResponse2(String responseText) {
		Matcher matcher = RECAPTCHA_FALLBACK_PATTERN.matcher(responseText);
		if (matcher.find()) {
			String imageSelectorDescription = matcher.group(1);
			String challenge = matcher.group(2);
			return new Pair<>(imageSelectorDescription, challenge);
		}
		return null;
	}

	public ChallengeExtra getChallenge2(HttpHolder initialHolder, String apiKey, boolean invisible,
			String referer, boolean useJavaScript, boolean solveInBackground, boolean solveAutomatically)
			throws CancelException, HttpException {
		String refererFinal = referer != null ? referer : "https://www.google.com/";
		if (solveAutomatically) {
			String autoResponse = CaptchaSolving.getInstance().solveCaptcha(initialHolder,
					invisible ? CaptchaSolving.CaptchaType.RECAPTCHA_2_INVISIBLE :
							CaptchaSolving.CaptchaType.RECAPTCHA_2, apiKey, refererFinal);
			if (autoResponse != null) {
				return new ChallengeExtra(null, autoResponse, null);
			}
		}
		if (useJavaScript && C.API_KITKAT) {
			ChallengeExtra.ForegroundSolver solver = (newHolder, challengeExtra) -> {
				synchronized (accessLock) {
					String response = ForegroundManager.getInstance()
							.requireUserRecaptchaV2(refererFinal, apiKey, invisible, false, challengeExtra);
					if (response == null) {
						throw new CancelException();
					}
					return response;
				}
			};
			synchronized (accessLock) {
				if (solveInBackground) {
					return new BackgroundSolver(solver, refererFinal, apiKey, invisible, false).await();
				} else {
					return new ChallengeExtra(solver, null, null);
				}
			}
		} else {
			Chan chan = Chan.getFallback();
			Uri uri = chan.locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
			String acceptLanguage = "en-US,en;q=0.5";
			String initialResponseText = new HttpRequest(uri, initialHolder)
					.addCookie(AdvancedPreferences.getGoogleCookie())
					.addHeader("Accept-Language", acceptLanguage)
					.addHeader("Referer", refererFinal)
					.perform().readString();
			if (initialResponseText == null) {
				throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
			}
			Pair<String, String> initialResponse = parseResponse2(initialResponseText);
			if (initialResponse == null) {
				if (initialResponseText
						.contains("Please enable JavaScript to get a reCAPTCHA challenge")) {
					if (C.API_KITKAT) {
						return getChallenge2(initialHolder, apiKey, invisible, refererFinal,
								true, solveInBackground, false);
					} else {
						throw new HttpException(ErrorItem.Type.UNSUPPORTED_RECAPTCHA, false, false);
					}
				} else {
					throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
				}
			}
			boolean[] consumed = {false};
			ChallengeExtra.ForegroundSolver solver = (holder, challengeExtra) -> {
				Bitmap captchaImage = null;
				Pair<String, String> response;
				if (consumed[0]) {
					String responseText = new HttpRequest(uri, holder)
							.addCookie(AdvancedPreferences.getGoogleCookie())
							.addHeader("Accept-Language", acceptLanguage)
							.addHeader("Referer", refererFinal)
							.perform().readString();
					response = parseResponse2(responseText);
				} else {
					consumed[0] = true;
					response = initialResponse;
				}
				while (true) {
					if (response != null) {
						if (captchaImage != null) {
							captchaImage.recycle();
						}
						captchaImage = getImage2(holder, apiKey, response.second, null, false).first;
						boolean[] result = ForegroundManager.getInstance().requireUserImageMultipleChoice(3, null,
								splitImages(captchaImage, 3, 3), HtmlParser.clear(response.first), null);
						if (result != null) {
							boolean hasSelected = false;
							UrlEncodedEntity entity = new UrlEncodedEntity("c", response.second);
							for (int i = 0; i < result.length; i++) {
								if (result[i]) {
									entity.add("response", Integer.toString(i));
									hasSelected = true;
								}
							}
							if (!hasSelected) {
								continue;
							}
							String responseText = new HttpRequest(uri, holder).setPostMethod(entity)
									.addCookie(AdvancedPreferences.getGoogleCookie())
									.setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
									.addHeader("Accept-Language", acceptLanguage)
									.addHeader("Referer", referer)
									.perform().readString();
							Matcher matcher = RECAPTCHA_RESULT_PATTERN.matcher(responseText);
							if (matcher.find()) {
								return matcher.group(1);
							}
							response = parseResponse2(responseText);
							continue;
						}
						throw new CancelException();
					} else {
						throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
					}
				}
			};
			return new ChallengeExtra(solver, null, null);
		}
	}

	public ChallengeExtra getChallengeHcaptcha(HttpHolder initialHolder, String apiKey, String referer,
			boolean solveInBackground, boolean solveAutomatically) throws CancelException, HttpException {
		String refererFinal = referer != null ? referer : "https://www.hcaptcha.com/";
		if (solveAutomatically) {
			String autoResponse = CaptchaSolving.getInstance().solveCaptcha(initialHolder,
					CaptchaSolving.CaptchaType.HCAPTCHA, apiKey, refererFinal);
			if (autoResponse != null) {
				return new ChallengeExtra(null, autoResponse, null);
			}
		}
		ChallengeExtra.ForegroundSolver solver = (holder, challengeExtra) -> {
			synchronized (accessLock) {
				String response = ForegroundManager.getInstance()
						.requireUserRecaptchaV2(refererFinal, apiKey, false, true, challengeExtra);
				if (response == null) {
					throw new CancelException();
				}
				return response;
			}
		};
		synchronized (accessLock) {
			if (solveInBackground) {
				return new BackgroundSolver(solver, refererFinal, apiKey, false, true).await();
			} else {
				return new ChallengeExtra(solver, null, null);
			}
		}
	}

	private Pair<Bitmap, Boolean> getImage2(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite) throws HttpException {
		Chan chan = Chan.getFallback();
		Uri uri = chan.locator.buildQueryWithHost("www.google.com", "recaptcha/api2/payload",
				"c", challenge, "k", apiKey, "id", StringUtils.emptyIfNull(id));
		Bitmap image = new HttpRequest(uri, holder).perform().readBitmap();
		if (transformBlackAndWhite) {
			transformBlackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
		}
		return transformBlackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image) : new Pair<>(image, false);
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
		public CancelException() {}
	}

	private static class WebViewHolder {
		public static class Arguments {
			public final String referer;
			public final String apiKey;
			public final boolean invisible;
			public final boolean hcaptcha;

			public Arguments(String referer, String apiKey, boolean invisible, boolean hcaptcha) {
				this.referer = referer;
				this.apiKey = apiKey;
				this.invisible = invisible;
				this.hcaptcha = hcaptcha;
			}
		}

		public interface ArgumentsProvider {
			Arguments create();
		}

		public interface Callback {
			void onLoad();
			void onCancel();
			void onResponse(String response);
			void onError(HttpException exception);
		}

		private ScaledWebView webView;

		private float scale = 1f;
		private float extraScale = 1f;
		public int lastWidthUnscaled = 0;
		public int lastHeightUnscaled = 0;

		private WebViewHolder.Callback callback;
		private boolean loaded = false;
		private boolean cancel = false;
		private String response;
		private HttpException exception;

		private void destroy() {
			if (webView != null) {
				webView.destroy();
				webView = null;
			}
		}

		@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
		private WebView obtainWebView(Context context,
				ViewGroup newParent, int index, ArgumentsProvider argumentsProvider) {
			context = context.getApplicationContext();
			int widthUnscaled = 300;
			int maxHeightUnscaled = 580;
			int minHeightUnscaled = 250;
			Configuration configuration = context.getResources().getConfiguration();
			float density = ResourceUtils.obtainDensity(context);
			int dialogPaddingDp = 16;
			int screenWidthDp = configuration.screenWidthDp - 2 * dialogPaddingDp;
			int screenHeightDp = configuration.screenHeightDp - 2 * dialogPaddingDp;
			float minScaleMultiplier = Float.MAX_VALUE;
			for (float size : Arrays.asList(screenWidthDp, screenHeightDp)) {
				for (float max : Arrays.asList(widthUnscaled, maxHeightUnscaled)) {
					minScaleMultiplier = Math.min(minScaleMultiplier, size / max);
				}
			}
			float scaleMultiplier = Math.min((float) screenWidthDp / widthUnscaled,
					(float) screenHeightDp / maxHeightUnscaled);
			float minScale = density * minScaleMultiplier;
			float scale = density * scaleMultiplier;

			boolean load = false;
			if (webView == null) {
				load = true;
				webView = new ScaledWebView(context, minScale, EXTRA_SCALE_FOR_SYSTEM_PADDING);
				webView.getSettings().setJavaScriptEnabled(true);
				webView.getSettings().setBuiltInZoomControls(false);
				webView.setHorizontalScrollBarEnabled(false);
				webView.setVerticalScrollBarEnabled(false);
				webView.addJavascriptInterface(javascriptInterface, "jsi");
				webView.setWebViewClient(client);
				webView.setWebChromeClient(new WebChromeClient() {
					@SuppressWarnings("deprecation")
					@Override
					public void onConsoleMessage(String message, int lineNumber, String sourceID) {
						Log.persistent().write("recaptcha js log", lineNumber, sourceID, message);
						super.onConsoleMessage(message, lineNumber, sourceID);
					}
				});
			}
			this.scale = scale;
			webView.setScale(getTotalScale());
			if (webView.getParent() != null) {
				((ViewGroup) webView.getParent()).removeView(webView);
			}

			int defaultWidth = (int) ((lastWidthUnscaled > 0
					? lastWidthUnscaled : widthUnscaled) * getTotalScale());
			int defaultHeight = (int) ((lastHeightUnscaled > 0
					? lastHeightUnscaled : minHeightUnscaled) * getTotalScale());
			webView.setLayoutParams(new FrameLayout.LayoutParams(defaultWidth, defaultHeight));
			if (newParent == null) {
				layout(webView);
			} else {
				newParent.addView(webView, index);
			}

			if (load) {
				Arguments arguments = argumentsProvider.create();
				String data = IOUtils.readRawResourceString(webView.getResources(), R.raw.web_recaptcha_v2)
						.replace("__REPLACE_API_KEY__", arguments.apiKey)
						.replace("__REPLACE_INVISIBLE__", arguments.invisible ? "true" : "false")
						.replace("__REPLACE_HCAPTCHA__", arguments.hcaptcha ? "true" : "false");
				webView.loadDataWithBaseURL(arguments.referer, data, "text/html", "UTF-8", null);
			}
			return webView;
		}

		private static void layout(WebView webView) {
			ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
			webView.measure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY));
			webView.layout(0, 0, webView.getMeasuredWidth(), webView.getMeasuredHeight());
		}

		private float getTotalScale() {
			return scale * extraScale;
		}

		private void setCallback(WebViewHolder.Callback callback) {
			this.callback = callback;
			if (callback != null) {
				boolean cancel = this.cancel;
				String response = this.response;
				HttpException exception = this.exception;
				this.cancel = false;
				this.response = null;
				this.exception = null;
				if (cancel) {
					callback.onCancel();
				} else if (response != null) {
					callback.onResponse(response);
				} else if (exception != null) {
					callback.onError(exception);
				}
			}
		}

		@SuppressWarnings("unused")
		private final Object javascriptInterface = new Object() {
			@JavascriptInterface
			public void onResponse(String response) {
				ConcurrentUtils.HANDLER.post(() -> {
					if (webView != null) {
						HttpException exception = !StringUtils.isEmpty(response) ? null :
								new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
						if (callback != null) {
							if (exception != null) {
								callback.onError(exception);
							} else {
								callback.onResponse(response);
							}
						} else {
							WebViewHolder.this.response = StringUtils.nullIfEmpty(response);
							if (exception != null) {
								WebViewHolder.this.exception = exception;
							}
						}
					}
				});
			}

			@JavascriptInterface
			public void onError() {
				ConcurrentUtils.HANDLER.post(() -> {
					if (webView != null) {
						HttpException exception = new HttpException(ErrorItem.Type.UNKNOWN, false, false);
						if (callback != null) {
							callback.onError(exception);
						} else {
							WebViewHolder.this.exception = exception;
						}
					}
				});
			}

			@JavascriptInterface
			public void onSizeChanged(int width, int height) {
				ConcurrentUtils.HANDLER.post(() -> {
					if (webView != null) {
						boolean hasContent = width > 0 && height > 0;
						if (hasContent) {
							boolean wasLoaded = loaded;
							loaded = true;
							if (!wasLoaded && callback != null) {
								callback.onLoad();
							}
							lastWidthUnscaled = width;
							lastHeightUnscaled = height;
							extraScale = Math.min(1f, 300f / width);
							int newWidth = (int) (getTotalScale() * width);
							int newHeight = (int) (getTotalScale() * height);
							webView.setScale(getTotalScale());
							ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
							if (layoutParams.width != newWidth || layoutParams.height != newHeight) {
								layoutParams.width = newWidth;
								layoutParams.height = newHeight;
								if (webView.getParent() != null) {
									webView.requestLayout();
								} else {
									layout(webView);
								}
							}
						} else if ((lastWidthUnscaled > 0 && lastHeightUnscaled > 0) && !hasContent) {
							if (callback != null) {
								callback.onCancel();
							} else {
								cancel = true;
							}
						}
					}
				});
			}
		};

		private final WebViewClient client = new WebViewClient() {
			@Override
			public void onScaleChanged(WebView view, float oldScale, float newScale) {
				if (webView != null) {
					webView.notifyClientScaleChanged(newScale);
				}
			}

			@SuppressWarnings("deprecation")
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				return true;
			}

			@SuppressWarnings("deprecation")
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
				Uri uri = Uri.parse(url);
				if ("favicon.ico".equals(uri.getLastPathSegment())) {
					return new WebResourceResponse("text/plain", "ISO-8859-1", null);
				} else {
					return super.shouldInterceptRequest(view, url);
				}
			}

			@SuppressWarnings("deprecation")
			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				super.onReceivedError(view, errorCode, description, failingUrl);

				if (failingUrl != null) {
					Uri uri = Uri.parse(failingUrl);
					if ("google.com".equals(uri.getHost()) || "www.google.com".equals(uri.getHost())) {
						ConcurrentUtils.HANDLER.post(() -> {
							if (webView != null) {
								HttpException exception = new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
								if (callback != null) {
									callback.onError(exception);
								} else {
									WebViewHolder.this.exception = exception;
								}
							}
						});
					}
				}
			}
		};
	}

	private static class BackgroundSolver implements WebViewHolder.Callback {
		private final ChallengeExtra.ForegroundSolver solver;
		private final WebViewHolder holder;

		private ChallengeExtra challengeExtra;
		private boolean error;
		private boolean cancel;

		private BackgroundSolver(ChallengeExtra.ForegroundSolver solver,
				String referer, String apiKey, boolean invisible, boolean hcaptcha) {
			this.solver = solver;
			holder = ConcurrentUtils.mainGet(() -> {
				WebViewHolder holder = new WebViewHolder();
				holder.obtainWebView(MainApplication.getInstance(), null, 0, () -> new WebViewHolder
						.Arguments(referer, apiKey, invisible, hcaptcha));
				holder.callback = BackgroundSolver.this;
				return holder;
			});
		}

		@Override
		public void onLoad() {
			synchronized (this) {
				challengeExtra = new ChallengeExtra(solver, null, holder);
				notifyAll();
			}
		}

		@Override
		public void onCancel() {
			synchronized (this) {
				cancel = true;
				notifyAll();
			}
		}

		@Override
		public void onResponse(String response) {
			synchronized (this) {
				holder.destroy();
				challengeExtra = new ChallengeExtra(solver, response, null);
				notifyAll();
			}
		}

		@Override
		public void onError(HttpException exception) {
			synchronized (this) {
				error = true;
				notifyAll();
			}
		}

		public ChallengeExtra await() throws CancelException, HttpException {
			synchronized (this) {
				while (challengeExtra == null && !error && !cancel) {
					try {
						wait();
					} catch (InterruptedException e) {
						ConcurrentUtils.HANDLER.post(holder::destroy);
						Thread.currentThread().interrupt();
						throw new CancelException();
					}
				}
				if (error) {
					throw new HttpException(ErrorItem.Type.UNKNOWN, false, false);
				}
				if (cancel) {
					throw new CancelException();
				}
				return challengeExtra;
			}
		}
	}

	public static class WebViewViewModel extends ViewModel {
		private WebViewHolder holder;
		private boolean clicked = false;
		private Runnable destroyCallback;
		private boolean published = false;

		public void initHolder(WebViewHolder holder) {
			this.holder = holder != null ? holder : new WebViewHolder();
		}

		@Override
		protected void onCleared() {
			holder.destroy();
			if (destroyCallback != null) {
				destroyCallback.run();
			}
		}
	}

	public static abstract class V2Dialog extends DialogFragment {
		private static final String EXTRA_REFERER = "referer";
		private static final String EXTRA_API_KEY = "apiKey";
		private static final String EXTRA_INVISIBLE = "invisible";
		private static final String EXTRA_HCAPTCHA = "hcaptcha";

		public V2Dialog() {}

		public V2Dialog(String referer, String apiKey, boolean invisible, boolean hcaptcha,
				ChallengeExtra challengeExtra) {
			Bundle args = new Bundle();
			args.putString(EXTRA_REFERER, referer);
			args.putString(EXTRA_API_KEY, apiKey);
			args.putBoolean(EXTRA_INVISIBLE, invisible);
			args.putBoolean(EXTRA_HCAPTCHA, hcaptcha);
			setArguments(args);
			this.challengeExtra = challengeExtra;
		}

		private WebViewViewModel webView;
		private ChallengeExtra challengeExtra;

		private boolean started = false;
		private boolean shown = false;

		private final WebViewHolder.Callback callback = new WebViewHolder.Callback() {
			@Override
			public void onLoad() {
				showDialog();
				performClick();
			}

			@Override
			public void onCancel() {
				dismiss();
				publishResponseInternal(null, null);
			}

			@Override
			public void onResponse(String response) {
				dismiss();
				publishResponseInternal(response, null);
			}

			@Override
			public void onError(HttpException exception) {
				dismiss();
				publishResponseInternal(null, exception);
			}
		};

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			webView = new ViewModelProvider(this).get(WebViewViewModel.class);
			if (webView.holder == null) {
				webView.initHolder(challengeExtra.holder);
				if (challengeExtra != null) {
					challengeExtra.holder = null;
					challengeExtra = null;
				}
			}

			Dialog dialog = new Dialog(requireActivity());
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			FrameLayout layout = new FrameLayout(dialog.getContext());
			dialog.setContentView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			if (webView.holder.loaded) {
				showDialog();
				performClick();
			}

			webView.destroyCallback = this::publishDestroyInternal;
			webView.holder.obtainWebView(requireContext().getApplicationContext(), layout, 0, () -> new WebViewHolder
					.Arguments(requireArguments().getString(EXTRA_REFERER), requireArguments().getString(EXTRA_API_KEY),
					requireArguments().getBoolean(EXTRA_INVISIBLE), requireArguments().getBoolean(EXTRA_HCAPTCHA)));
			return dialog;
		}

		@Override
		public void onStart() {
			super.onStart();

			started = true;
			Dialog dialog = getDialog();
			if (dialog != null && !shown) {
				dialog.hide();
			}
		}

		@Override
		public void onResume() {
			super.onResume();

			if (webView != null) {
				webView.holder.setCallback(callback);
				if (!shown && webView.holder.loaded) {
					showDialog();
					performClick();
				}
			}
		}

		@Override
		public void onPause() {
			super.onPause();

			if (webView != null) {
				webView.holder.setCallback(null);
			}
		}

		@Override
		public void onStop() {
			super.onStop();
			started = false;
		}

		private void showDialog() {
			if (!shown) {
				shown = true;
				if (started) {
					getDialog().show();
				}
			}
		}

		private void performClick() {
			if (!webView.clicked) {
				webView.clicked = true;
				if (!requireArguments().getBoolean(EXTRA_INVISIBLE)) {
					ConcurrentUtils.HANDLER.postDelayed(() -> {
						if (webView != null) {
							int x = (int) (webView.holder.getTotalScale() * (Math.random() * 100 + 10));
							int y = (int) (webView.holder.getTotalScale() * (Math.random() * 30 + 20));
							MotionEvent motionEvent;
							motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(),
									MotionEvent.ACTION_DOWN, x, y, 0);
							webView.holder.webView.onTouchEvent(motionEvent);
							motionEvent.recycle();
							motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(),
									MotionEvent.ACTION_UP, x, y, 0);
							webView.holder.webView.onTouchEvent(motionEvent);
							motionEvent.recycle();
						}
					}, 500);
				}
			}
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			publishResponseInternal(null, null);
		}

		private void publishResponseInternal(String response, HttpException exception) {
			if (webView != null && !webView.published) {
				webView.published = true;
				webView.holder.setCallback(null);
				webView = null;
				publishResult(response, exception);
			}
		}

		private void publishDestroyInternal() {
			if (webView != null && !webView.published) {
				webView.published = true;
				publishResult(null, null);
			}
		}

		public abstract void publishResult(String response, HttpException exception);
	}
}
