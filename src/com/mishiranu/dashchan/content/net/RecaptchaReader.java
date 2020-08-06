package com.mishiranu.dashchan.content.net;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Gravity;
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
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.UrlEncodedEntity;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecaptchaReader {
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

	public String getResponse2(HttpHolder holder, String apiKey, boolean invisible,
			String referer, boolean useJavaScript) throws CancelException, HttpException {
		if (useJavaScript && C.API_KITKAT) {
			if (referer == null) {
				referer = "https://www.google.com/";
			}
			synchronized (accessLock) {
				String response = ForegroundManager.getInstance().requireUserRecaptchaV2(referer, apiKey, invisible);
				if (response == null) {
					throw new CancelException();
				}
				return response;
			}
		} else {
			ChanLocator locator = ChanLocator.getDefault();
			Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api/fallback", "k", apiKey);
			if (referer == null) {
				referer = uri.toString();
			}
			String acceptLanguage = "en-US,en;q=0.5";
			Bitmap captchaImage = null;
			String responseText = new HttpRequest(uri, holder)
					.addCookie(AdvancedPreferences.getGoogleCookie())
					.addHeader("Accept-Language", acceptLanguage)
					.addHeader("Referer", referer)
					.read().getString();
			while (true) {
				String imageSelectorDescription = null;
				String challenge = null;
				Matcher matcher = RECAPTCHA_FALLBACK_PATTERN.matcher(responseText);
				if (matcher.find()) {
					imageSelectorDescription = matcher.group(1);
					challenge = matcher.group(2);
				}
				if (imageSelectorDescription != null && challenge != null) {
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
								.addHeader("Accept-Language", acceptLanguage)
								.addHeader("Referer", referer)
								.read().getString();
						matcher = RECAPTCHA_RESULT_PATTERN.matcher(responseText);
						if (matcher.find()) {
							return matcher.group(1);
						}
						continue;
					}
					throw new CancelException();
				} else {
					if (responseText.contains("Please enable JavaScript to get a reCAPTCHA challenge")) {
						if (C.API_KITKAT) {
							return getResponse2(holder, apiKey, invisible, referer, true);
						} else {
							throw new HttpException(ErrorItem.Type.UNSUPPORTED_RECAPTCHA, false, false);
						}
					} else {
						throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
					}
				}
			}
		}
	}

	private Pair<Bitmap, Boolean> getImage2(HttpHolder holder, String apiKey, String challenge, String id,
			boolean transformBlackAndWhite) throws HttpException {
		ChanLocator locator = ChanLocator.getDefault();
		Uri uri = locator.buildQueryWithHost("www.google.com", "recaptcha/api2/payload", "c", challenge, "k", apiKey,
				"id", StringUtils.emptyIfNull(id));
		Bitmap image = new HttpRequest(uri, holder).read().getBitmap();
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

	private interface DialogCallback {
		void onLoad();
		void onCancel();
		void onResponse(String response);
		void onError(HttpException exception);
	}

	public static class WebViewHolder extends Fragment {
		private WebView webView;

		private float scale = 1f;
		public int lastWidthUnscaled = 0;
		public int lastHeightUnscaled = 0;

		private DialogCallback dialogCallback;
		private boolean loaded = false;
		private boolean cancel = false;
		private String response;
		private HttpException exception;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();

			if (webView != null) {
				webView.destroy();
				webView = null;
			}
		}

		@SuppressLint("SetJavaScriptEnabled")
		public WebView obtainWebView(Context context, float scale) {
			if (webView == null) {
				webView = new WebView(context.getApplicationContext());
				webView.getSettings().setJavaScriptEnabled(true);
				webView.getSettings().setBuiltInZoomControls(false);
				webView.setHorizontalScrollBarEnabled(false);
				webView.setVerticalScrollBarEnabled(false);
				webView.addJavascriptInterface(javaScriptInterface, "jsi");
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
			webView.setInitialScale((int) (100 * scale));
			if (webView.getParent() != null) {
				((ViewGroup) webView.getParent()).removeView(webView);
			}
			return webView;
		}

		public void setDialogCallback(DialogCallback dialogCallback) {
			this.dialogCallback = dialogCallback;
			if (dialogCallback != null) {
				boolean cancel = this.cancel;
				String response = this.response;
				HttpException exception = this.exception;
				this.cancel = false;
				this.response = null;
				this.exception = null;
				if (cancel) {
					dialogCallback.onCancel();
				} else if (response != null) {
					dialogCallback.onResponse(response);
				} else if (exception != null) {
					dialogCallback.onError(exception);
				}
			}
		}

		private void postEvent(Runnable runnable) {
			WebView webView = this.webView;
			if (webView != null) {
				webView.post(runnable);
			}
		}

		@SuppressWarnings("unused")
		private final Object javaScriptInterface = new Object() {
			@JavascriptInterface
			public void onResponse(String response) {
				postEvent(() -> {
					if (webView != null) {
						HttpException exception = !StringUtils.isEmpty(response) ? null :
								new HttpException(ErrorItem.Type.INVALID_RESPONSE, false, false);
						if (dialogCallback != null) {
							if (exception != null) {
								dialogCallback.onError(exception);
							} else {
								dialogCallback.onResponse(response);
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
				postEvent(() -> {
					if (webView != null) {
						HttpException exception = new HttpException(ErrorItem.Type.UNKNOWN, false, false);
						if (dialogCallback != null) {
							dialogCallback.onError(exception);
						} else {
							WebViewHolder.this.exception = exception;
						}
					}
				});
			}

			@JavascriptInterface
			public void onSizeChanged(int width, int height) {
				postEvent(() -> {
					if (webView != null) {
						boolean hasContent = width > 0 && height > 0;
						if (hasContent) {
							boolean wasLoaded = loaded;
							loaded = true;
							if (!wasLoaded && dialogCallback != null) {
								dialogCallback.onLoad();
							}
							lastWidthUnscaled = width;
							lastHeightUnscaled = height;
							int newWidth = (int) (scale * width);
							int newHeight = (int) (scale * height);
							ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
							if (layoutParams.width != newWidth || layoutParams.height != newHeight) {
								layoutParams.width = (int) (scale * width);
								layoutParams.height = (int) (scale * height);
								webView.requestLayout();
							}
						} else if ((lastWidthUnscaled > 0 && lastHeightUnscaled > 0) && !hasContent) {
							if (dialogCallback != null) {
								dialogCallback.onCancel();
							} else {
								cancel = true;
							}
						}
					}
				});
			}
		};

		private final WebViewClient client = new WebViewClient() {
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
					return new WebResourceResponse("text/plain", "ISO-8859-1", new ByteArrayInputStream(new byte[0]));
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
						postEvent(() -> {
							if (webView != null) {
								HttpException exception = new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
								if (dialogCallback != null) {
									dialogCallback.onError(exception);
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

	public static abstract class V2Dialog extends DialogFragment {
		private static final String EXTRA_REFERER = "referer";
		private static final String EXTRA_API_KEY = "apiKey";
		private static final String EXTRA_INVISIBLE = "invisible";

		private static final String EXTRA_WEB_VIEW_ID = "webViewId";

		public V2Dialog() {}

		public V2Dialog(String referer, String apiKey, boolean invisible) {
			Bundle args = new Bundle();
			args.putString(EXTRA_REFERER, referer);
			args.putString(EXTRA_API_KEY, apiKey);
			args.putBoolean(EXTRA_INVISIBLE, invisible);
			setArguments(args);
		}

		private String webViewId;
		private WebViewHolder webViewHolder;
		private FrameLayout loading;

		private final DialogCallback dialogCallback = new DialogCallback() {
			@Override
			public void onLoad() {
				if (loading != null) {
					loading.setVisibility(View.GONE);
				}
				if (!requireArguments().getBoolean(EXTRA_INVISIBLE)) {
					getDialog().getWindow().getDecorView().postDelayed(() -> {
						if (webViewHolder != null) {
							int x = (int) (webViewHolder.scale * (Math.random() * 100 + 10));
							int y = (int) (webViewHolder.scale * (Math.random() * 30 + 20));
							MotionEvent motionEvent;
							motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(),
									MotionEvent.ACTION_DOWN, x, y, 0);
							webViewHolder.webView.onTouchEvent(motionEvent);
							motionEvent.recycle();
							motionEvent = MotionEvent.obtain(0, SystemClock.uptimeMillis(),
									MotionEvent.ACTION_UP, x, y, 0);
							webViewHolder.webView.onTouchEvent(motionEvent);
							motionEvent.recycle();
						}
					}, 500);
				}
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
			if (savedInstanceState == null) {
				webViewId = UUID.randomUUID().toString();
				webViewHolder = new WebViewHolder();
				getParentFragmentManager().beginTransaction()
						.add(webViewHolder, webViewId).commit();
			} else {
				webViewId = savedInstanceState.getString(EXTRA_WEB_VIEW_ID);
				webViewHolder = (WebViewHolder) getParentFragmentManager().findFragmentByTag(webViewId);
			}

			Dialog dialog = new Dialog(requireActivity());
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			float density = ResourceUtils.obtainDensity(dialog.getContext());
			FrameLayout layout = new FrameLayout(dialog.getContext());
			dialog.setContentView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));

			loading = new FrameLayout(layout.getContext());
			loading.setVisibility(webViewHolder.loaded ? View.GONE : View.VISIBLE);
			loading.setPadding((int) (16f * density), (int) (16f * density),
					(int) (16f * density), (int) (16f * density));
			layout.addView(loading, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
			((FrameLayout.LayoutParams) layout.getLayoutParams()).gravity = Gravity.CENTER;
			ProgressBar progressBar = new ProgressBar(loading.getContext());
			loading.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
			((FrameLayout.LayoutParams) loading.getLayoutParams()).gravity = Gravity.CENTER;

			int widthUnscaled = 300;
			int maxHeightUnscaled = 580;
			int minHeightUnscaled = 250;
			float scaleMultiplier = (float) getResources().getConfiguration().screenHeightDp / maxHeightUnscaled;
			float scale = getResources().getDisplayMetrics().density * scaleMultiplier;
			int defaultWidth = (int) ((webViewHolder.lastWidthUnscaled > 0
					? webViewHolder.lastWidthUnscaled : widthUnscaled) * scale);
			int defaultHeight = (int) ((webViewHolder.lastHeightUnscaled > 0
					? webViewHolder.lastHeightUnscaled : minHeightUnscaled) * scale);
			boolean load = webViewHolder.webView == null;
			WebView webView = webViewHolder.obtainWebView(requireContext(), scale);
			layout.addView(webView, 0, new FrameLayout.LayoutParams(defaultWidth, defaultHeight));

			if (load) {
				InputStream input = null;
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				try {
					input = getResources().openRawResource(R.raw.recaptcha_v2);
					IOUtils.copyStream(input, output);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					IOUtils.close(input);
				}
				String data = new String(output.toByteArray())
						.replace("__REPLACE_API_KEY__", requireArguments().getString(EXTRA_API_KEY))
						.replace("__REPLACE_INVISIBLE__", requireArguments().getBoolean(EXTRA_INVISIBLE)
								? "true" : "false");
				webView.loadDataWithBaseURL(requireArguments().getString(EXTRA_REFERER),
						data, "text/html", "UTF-8", null);
			}

			return dialog;
		}

		@Override
		public void onDestroyView() {
			super.onDestroyView();
			loading = null;
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putString(EXTRA_WEB_VIEW_ID, webViewId);
		}

		@Override
		public void onResume() {
			super.onResume();

			if (webViewHolder != null) {
				webViewHolder.setDialogCallback(dialogCallback);
			}
		}

		@Override
		public void onPause() {
			super.onPause();

			if (webViewHolder != null) {
				webViewHolder.setDialogCallback(null);
			}
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			publishResponseInternal(null, null);
		}

		private void publishResponseInternal(String response, HttpException exception) {
			if (webViewHolder != null) {
				getParentFragmentManager().beginTransaction().remove(webViewHolder).commit();
				webViewHolder.setDialogCallback(null);
				webViewHolder = null;
				publishResponse(response, exception);
			}
		}

		public abstract void publishResponse(String response, HttpException exception);
	}
}
