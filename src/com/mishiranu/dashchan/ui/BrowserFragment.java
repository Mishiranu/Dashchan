package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.Chan;
import chan.content.ChanLocator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.WebViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.UUID;

public class BrowserFragment extends ContentFragment implements DownloadListener {
	private static final String EXTRA_URI = "uri";

	public BrowserFragment() {}

	public BrowserFragment(Uri uri) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_URI, uri);
		setArguments(args);
	}

	private WebView webView;
	private ProgressView progressView;

	private String navigationDrawerLocker;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ExpandedLayout layout = new ExpandedLayout(container.getContext(), true);
		webView = new WebView(layout.getContext());
		layout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
		progressView = new ProgressView(layout.getContext());
		float density = ResourceUtils.obtainDensity(this);
		layout.addView(progressView, FrameLayout.LayoutParams.MATCH_PARENT, (int) (3f * density + 0.5f));
		return layout;
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		navigationDrawerLocker = "browser-" + UUID.randomUUID();
		((FragmentHandler) requireActivity()).setNavigationAreaLocked(navigationDrawerLocker, true);

		WebSettings settings = webView.getSettings();
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		webView.setWebViewClient(new CustomWebViewClient());
		webView.setWebChromeClient(new CustomWebChromeClient());
		webView.setDownloadListener(this);
		webView.setOnLongClickListener(v -> {
			WebView.HitTestResult hitTestResult = webView.getHitTestResult();
			switch (hitTestResult.getType()) {
				case WebView.HitTestResult.IMAGE_TYPE:
				case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE: {
					Chan chan = Chan.getFallback();
					Uri uri = Uri.parse(hitTestResult.getExtra());
					if (chan.locator.isWebScheme(uri) && chan.locator.isImageExtension(uri.getPath())) {
						NavigationUtils.openImageVideo(requireContext(), uri);
					}
					return true;
				}
			}
			return false;
		});

		if (savedInstanceState != null) {
			webView.restoreState(savedInstanceState);
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		((FragmentHandler) requireActivity()).setNavigationAreaLocked(navigationDrawerLocker, false);
		webView.stopLoading();
		webView.destroy();
		webView = null;
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.web_browser), null);
		if (savedInstanceState == null) {
			WebViewUtils.clearAll(webView);
			webView.loadUrl(requireArguments().<Uri>getParcelable(EXTRA_URI).toString());
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		webView.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		webView.onResume();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (webView != null) {
			webView.saveState(outState);
		}
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_reload, 0, R.string.reload)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_copy_link, 0, R.string.copy_link);
		menu.add(0, R.id.menu_share_link, 0, R.string.share_link);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_reload: {
				webView.reload();
				break;
			}
			case R.id.menu_copy_link: {
				StringUtils.copyToClipboard(requireContext(), webView.getUrl());
				break;
			}
			case R.id.menu_share_link: {
				String uriString = webView.getUrl();
				if (!StringUtils.isEmpty(uriString)) {
					NavigationUtils.shareLink(requireContext(), null, Uri.parse(uriString));
				}
				break;
			}
		}
		return true;
	}

	@Override
	public boolean onHomePressed() {
		return false;
	}

	@Override
	public boolean onBackPressed() {
		if (webView.canGoBack()) {
			webView.goBack();
			return true;
		}
		return false;
	}

	@Override
	public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
			long contentLength) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (ActivityNotFoundException e) {
			ClickableToast.show(R.string.unknown_address);
		}
	}

	private class CustomWebViewClient extends WebViewClient {
		@SuppressWarnings("deprecation")
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Uri uri = Uri.parse(url);
			Chan chan = Chan.getPreferred(null, uri);
			if (chan.name != null) {
				ChanLocator.NavigationData navigationData;
				if (chan.locator.safe(true).isBoardUri(uri)) {
					navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.Target.THREADS,
							chan.locator.safe(true).getBoardName(uri), null, null, null);
				} else if (chan.locator.safe(true).isThreadUri(uri)) {
					navigationData = new ChanLocator.NavigationData(ChanLocator.NavigationData.Target.POSTS,
							chan.locator.safe(true).getBoardName(uri), chan.locator.safe(true).getThreadNumber(uri),
							chan.locator.safe(true).getPostNumber(uri), null);
				} else {
					navigationData = chan.locator.safe(true).handleUriClickSpecial(uri);
				}
				if (navigationData != null) {
					LinkDialog dialog = new LinkDialog(chan.name, navigationData);
					dialog.show(getChildFragmentManager(), LinkDialog.class.getName());
					return true;
				}
			}
			if (!chan.locator.isWebScheme(uri)) {
				Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri)
						.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				if (!requireContext().getPackageManager().queryIntentActivities(intent,
						PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
					requireContext().startActivity(intent);
					return true;
				}
			}
			view.loadUrl(url);
			return true;
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			String title = view.getTitle();
			((FragmentHandler) requireActivity()).setTitleSubtitle(StringUtils.isEmptyOrWhitespace(title)
					? getString(R.string.web_browser) : title, null);
		}

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			if (Preferences.isVerifyCertificate()) {
				ClickableToast.show(R.string.invalid_certificate);
				super.onReceivedSslError(view, handler, error);
			} else {
				handler.proceed();
			}
		}
	}

	public static class LinkDialog extends DialogFragment {
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_NAVIGATION_DATA = "navigationData";

		public LinkDialog() {}

		public LinkDialog(String chanName, ChanLocator.NavigationData navigationData) {
			Bundle args = new Bundle();
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putParcelable(EXTRA_NAVIGATION_DATA, navigationData);
			setArguments(args);
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String chanName = requireArguments().getString(EXTRA_CHAN_NAME);
			ChanLocator.NavigationData navigationData = requireArguments().getParcelable(EXTRA_NAVIGATION_DATA);
			return new AlertDialog.Builder(requireContext())
					.setMessage(R.string.follow_the_link__sentence)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, (dialog, which) -> ((FragmentHandler)
							requireActivity()).navigateTargetAllowReturn(chanName, navigationData))
					.create();
		}
	}

	private class CustomWebChromeClient extends WebChromeClient {
		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			progressView.setProgress(newProgress);
		}
	}

	private static class ProgressView extends View {
		private static final int TRANSIENT_TIME = 200;

		private final Paint paint = new Paint();

		private long progressSetTime;
		private float transientProgress = 0f;
		private int progress = 0;

		public ProgressView(Context context) {
			super(context);
			int color = ThemeEngine.getTheme(context).accent;
			paint.setColor(Color.BLACK | color);
		}

		public void setProgress(int progress) {
			transientProgress = calculateTransient();
			progressSetTime = SystemClock.elapsedRealtime();
			this.progress = progress;
			invalidate();
		}

		private float getTime() {
			return Math.min((float) (SystemClock.elapsedRealtime() - progressSetTime) / TRANSIENT_TIME, 1f);
		}

		private float calculateTransient() {
			return AnimationUtils.lerp(transientProgress, progress, getTime());
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			Paint paint = this.paint;
			int progress = this.progress;
			float transientProgress = calculateTransient();
			int alpha = 0xff;
			boolean needInvalidate = transientProgress != progress;
			if (progress == 100) {
				float t = getTime();
				alpha = (int) (0xff * (1f - t));
				needInvalidate |= t < 1f;
			}
			paint.setAlpha(alpha);
			if (transientProgress > 0 && alpha > 0x00) {
				float width = getWidth() * transientProgress / 100;
				canvas.drawRect(0, 0, width, getHeight(), paint);
			}
			if (needInvalidate) {
				invalidate();
			}
		}
	}
}
