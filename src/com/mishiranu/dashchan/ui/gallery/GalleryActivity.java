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

package com.mishiranu.dashchan.ui.gallery;

import java.util.ArrayList;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.GalleryBackgroundDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.WindowControlFrameLayout;

public class GalleryActivity extends StateActivity implements GalleryInstance.Callback,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener {
	private static final String EXTRA_POSITION = "position";
	private static final String EXTRA_GALLERY_WINDOW = "galleryWindow";
	private static final String EXTRA_GALLERY_MODE = "galleryMode";
	private static final String EXTRA_SYSTEM_UI_VISIBILITY = "systemUIVisibility";

	private String threadTitle;
	private final GalleryInstance instance = new GalleryInstance(this, this);

	private View actionBar;
	private WindowControlFrameLayout rootView;

	private GalleryBackgroundDrawable backgroundDrawable;

	private boolean galleryWindow, galleryMode;
	private boolean allowNavigatePost;
	private boolean overrideUpButton = false;
	private final boolean scrollThread = Preferences.isScrollThreadGallery();

	private static final int ACTION_BAR_COLOR = 0xaa202020;
	private static final int BACKGROUND_COLOR = 0xf0101010;

	private PagerUnit pagerUnit;
	private ListUnit listUnit;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void applyStatusNavigationTranslucency() {
		if (C.API_LOLLIPOP) {
			Window window = getWindow();
			int color = ACTION_BAR_COLOR;
			window.setStatusBarColor(color);
			window.setNavigationBarColor(color);
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		int[] imageViewPosition = savedInstanceState == null ? getIntent()
				.getIntArrayExtra(C.EXTRA_VIEW_POSITION) : null;
		if (!C.API_LOLLIPOP || imageViewPosition == null) {
			overridePendingTransition(R.anim.fast_fade_in, 0);
		}
		applyStatusNavigationTranslucency();
		super.onCreate(savedInstanceState);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		ViewUtils.applyToolbarStyle(this, null);
		expandedScreen = getIntent().getBooleanExtra(C.EXTRA_ALLOW_EXPANDED_SCREEN, false);
		instance.actionBarColor = ACTION_BAR_COLOR;
		boolean obtainImages = getIntent().getBooleanExtra(C.EXTRA_OBTAIN_ITEMS, false);
		ArrayList<GalleryItem> galleryItems = obtainImages ? NavigationUtils.obtainImagesProvider(this) : null;
		String chanName = getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		Uri uri = getIntent().getData();
		int imagePosition = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_POSITION, -1) : -1;
		if (chanName == null && uri != null) {
			chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
		}
		ChanLocator locator = null;
		if (chanName != null) {
			locator = ChanLocator.get(chanName);
		}
		threadTitle = getIntent().getStringExtra(C.EXTRA_THREAD_TITLE);
		boolean defaultLocator = false;
		if (locator == null) {
			locator = ChanLocator.getDefault();
			defaultLocator = true;
		}
		if (uri != null && galleryItems == null) {
			galleryItems = new ArrayList<>(1);
			String boardName = null;
			String threadNumber = null;
			if (!defaultLocator) {
				boardName = locator.safe(true).getBoardName(uri);
				threadNumber = locator.safe(true).getThreadNumber(uri);
			}
			galleryItems.add(new GalleryItem(uri, boardName, threadNumber));
			overrideUpButton = true;
			imagePosition = 0;
		} else if (imagePosition == -1) {
			imagePosition = getIntent().getIntExtra(C.EXTRA_IMAGE_INDEX, 0);
		}
		instance.chanName = chanName;
		instance.locator = locator;
		instance.galleryItems = galleryItems;
		allowNavigatePost = getIntent().getBooleanExtra(C.EXTRA_ALLOW_NAVIGATE_POST, false);
		actionBar = findViewById(getResources().getIdentifier("action_bar", "id", "android"));
		rootView = new WindowControlFrameLayout(this);
		rootView.setOnApplyWindowPaddingsListener(this);
		rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		backgroundDrawable = new GalleryBackgroundDrawable(rootView, C.API_LOLLIPOP ? imageViewPosition : null,
				BACKGROUND_COLOR);
		rootView.setBackground(backgroundDrawable);
		setContentView(rootView);
		if (galleryItems == null || galleryItems.size() == 0) {
			View errorView = getLayoutInflater().inflate(R.layout.widget_error, rootView, false);
			TextView textView = (TextView) errorView.findViewById(R.id.error_text);
			textView.setText(R.string.message_empty_gallery);
			rootView.addView(errorView);
		} else {
			listUnit = new ListUnit(instance);
			pagerUnit = new PagerUnit(instance);
			rootView.addView(listUnit.getListView(), FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			rootView.addView(pagerUnit.getView(), FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			pagerUnit.addAndInitViews(rootView, imagePosition);
			if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_GALLERY_MODE)) {
				galleryMode = savedInstanceState.getBoolean(EXTRA_GALLERY_MODE);
				galleryWindow = savedInstanceState.getBoolean(EXTRA_GALLERY_WINDOW);
				switchMode(galleryMode, false);
				modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_USER, savedInstanceState
						.getBoolean(EXTRA_SYSTEM_UI_VISIBILITY));
			} else {
				galleryWindow = imagePosition < 0 || getIntent().getBooleanExtra(C.EXTRA_GALLERY_MODE, false);
				if (galleryWindow && imagePosition >= 0) {
					listUnit.setListSelection(imagePosition, false);
				}
				switchMode(galleryWindow, false);
			}
			pagerUnit.onViewsCreated(imageViewPosition);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (pagerUnit != null) {
			pagerUnit.onResume();
		}
		ForegroundManager.register(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (pagerUnit != null) {
			pagerUnit.onPause();
		}
		ForegroundManager.unregister(this);
	}

	@Override
	protected void onFinish() {
		super.onFinish();
		if (listUnit != null) {
			listUnit.onFinish();
		}
		if (pagerUnit != null) {
			pagerUnit.onFinish();
		}
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, R.anim.fast_fade_out);
	}

	private void invalidateListPosition() {
		listUnit.setListSelection(pagerUnit.getCurrentIndex(), true);
	}

	private final Runnable returnToGalleryRunnable = () -> {
		switchMode(true, true);
		invalidateListPosition();
	};

	private boolean returnToGallery() {
		if (galleryWindow && !galleryMode) {
			pagerUnit.onBackToGallery();
			rootView.post(returnToGalleryRunnable);
			return true;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		if (!returnToGallery()) {
			super.onBackPressed();
		}
	}

	private static final int OPTIONS_MENU_SAVE = 0;
	private static final int OPTIONS_MENU_REFRESH = 1;
	private static final int OPTIONS_MENU_SELECT = 2;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		ActionIconSet set = new ActionIconSet(this);
		menu.add(0, OPTIONS_MENU_SAVE, 0, R.string.action_save).setIcon(set.getId(R.attr.actionSave))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(set.getId(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_SELECT, 0, R.string.action_select).setIcon(set.getId(R.attr.actionSelect))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			menu.getItem(i).setVisible(false);
		}
		if (!galleryMode) {
			PagerUnit.OptionsMenuCapabilities capabilities = pagerUnit != null
					? pagerUnit.obtainOptionsMenuCapabilities() : null;
			if (capabilities != null && capabilities.available) {
				menu.findItem(OPTIONS_MENU_SAVE).setVisible(capabilities.save);
				menu.findItem(OPTIONS_MENU_REFRESH).setVisible(capabilities.refresh);
			}
			if (pagerUnit != null) {
				pagerUnit.invalidatePopupMenu();
			}
		} else {
			menu.findItem(OPTIONS_MENU_SELECT).setVisible(listUnit.areItemsSelectable());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		GalleryItem galleryItem = pagerUnit != null ? pagerUnit.getCurrentGalleryItem() : null;
		switch (item.getItemId()) {
			case android.R.id.home: {
				NavigationUtils.handleGalleryUpButtonClick(this, overrideUpButton, instance.chanName, galleryItem);
				break;
			}
			case OPTIONS_MENU_SAVE: {
				downloadGalleryItem(galleryItem);
				break;
			}
			case OPTIONS_MENU_REFRESH: {
				pagerUnit.refreshCurrent();
				break;
			}
			case OPTIONS_MENU_SELECT: {
				listUnit.startSelectionMode();
				break;
			}
		}
		return true;
	}

	@Override
	public void downloadGalleryItem(GalleryItem galleryItem) {
		galleryItem.downloadStorage(this, instance.locator, threadTitle);
	}

	@Override
	public void downloadGalleryItems(ArrayList<GalleryItem> galleryItems) {
		String boardName = null;
		String threadNumber = null;
		ArrayList<DownloadManager.RequestItem> requestItems = new ArrayList<>();
		for (GalleryItem galleryItem : galleryItems) {
			if (requestItems.size() == 0) {
				boardName = galleryItem.boardName;
				threadNumber = galleryItem.threadNumber;
			} else if (boardName != null || threadNumber != null) {
				if (!StringUtils.equals(boardName, galleryItem.boardName) ||
						!StringUtils.equals(threadNumber, galleryItem.threadNumber)) {
					// Images from different threads, so don't use them to mark files and folders
					boardName = null;
					threadNumber = null;
				}
			}
			requestItems.add(new DownloadManager.RequestItem(galleryItem.getFileUri(instance.locator),
					galleryItem.getFileName(instance.locator), galleryItem.originalName));
		}
		if (requestItems.size() > 0) {
			DownloadManager.getInstance().downloadStorage(this, requestItems, instance.chanName,
					boardName, threadNumber, threadTitle, true);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (pagerUnit != null) {
			outState.putInt(EXTRA_POSITION, pagerUnit.getCurrentIndex());
		}
		outState.putBoolean(EXTRA_GALLERY_WINDOW, galleryWindow);
		outState.putBoolean(EXTRA_GALLERY_MODE, galleryMode);
		outState.putBoolean(EXTRA_SYSTEM_UI_VISIBILITY, FlagUtils.get(systemUiVisibilityFlags,
				GalleryInstance.FLAG_LOCKED_USER));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		ViewUtils.applyToolbarStyle(this, null);
		if (listUnit != null) {
			listUnit.onConfigurationChanged(newConfig);
		}
		if (pagerUnit != null) {
			pagerUnit.onConfigurationChanged(newConfig);
		}
		invalidateSystemUiVisibility();
	}

	private static final int GALLERY_TRANSITION_DURATION = 150;

	private void switchMode(boolean galleryMode, boolean animated) {
		int duration = animated ? GALLERY_TRANSITION_DURATION : 0;
		pagerUnit.switchMode(galleryMode, duration);
		listUnit.switchMode(galleryMode, duration);
		if (galleryMode) {
			int count = instance.galleryItems.size();
			setTitle(R.string.action_gallery);
			getActionBar().setSubtitle(getResources().getQuantityString(R.plurals.text_several_files_count_format,
					count, count));
		}
		modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_GRID, galleryMode);
		this.galleryMode = galleryMode;
		if (galleryMode) {
			new CornerAnimator(0xa0, 0xc0);
		} else {
			int alpha = Color.alpha(ACTION_BAR_COLOR);
			new CornerAnimator(alpha, alpha);
		}
		invalidateOptionsMenu();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private class CornerAnimator implements Runnable {
		private final long startTime = System.currentTimeMillis();

		private final int fromActionBarAlpha;
		private final int toActionBarAlpha;
		private final int fromStatusBarAlpha;
		private final int toStatusBarAlpha;

		private static final int INTERVAL = 200;

		public CornerAnimator(int actionBarAlpha, int statusBarAlpha) {
			Drawable drawable = actionBar.getBackground();
			fromActionBarAlpha = Color.alpha(drawable instanceof ColorDrawable
					? ((ColorDrawable) drawable).getColor() : statusBarAlpha);
			toActionBarAlpha = actionBarAlpha;
			if (C.API_LOLLIPOP) {
				fromStatusBarAlpha = Color.alpha(getWindow().getStatusBarColor());
				toStatusBarAlpha = statusBarAlpha;
			} else {
				fromStatusBarAlpha = 0x00;
				toStatusBarAlpha = 0x00;
			}
			if (fromActionBarAlpha != toActionBarAlpha || fromStatusBarAlpha != toStatusBarAlpha) {
				run();
			}
		}

		@Override
		public void run() {
			float t = Math.min((float) (System.currentTimeMillis() - startTime) / INTERVAL, 1f);
			int actionBarColorAlpha = (int) AnimationUtils.lerp(fromActionBarAlpha, toActionBarAlpha, t);
			actionBar.setBackgroundColor((actionBarColorAlpha << 24) | (0x00ffffff & ACTION_BAR_COLOR));
			if (C.API_LOLLIPOP) {
				int statusBarColorAlpha = (int) AnimationUtils.lerp(fromStatusBarAlpha, toStatusBarAlpha, t);
				int color = (statusBarColorAlpha << 24) | (0x00ffffff & ACTION_BAR_COLOR);
				Window window = getWindow();
				window.setStatusBarColor(color);
				window.setNavigationBarColor(color);
			}
			if (t < 1f) {
				rootView.postOnAnimation(this);
			}
		}
	}

	@Override
	public void modifyVerticalSwipeState(boolean ignoreIfGallery, float value) {
		if (!ignoreIfGallery && !galleryWindow) {
			backgroundDrawable.setAlpha((int) (0xff * (1f - value)));
		}
	}

	@Override
	public void updateTitle() {
		GalleryItem galleryItem = pagerUnit.getCurrentGalleryItem();
		if (galleryItem != null) {
			setTitle(galleryItem, pagerUnit.getCurrentIndex(), galleryItem.size);
		}
	}

	private void setTitle(GalleryItem galleryItem, int position, int size) {
		String fileName = galleryItem.getFileName(instance.locator);
		String originalName = galleryItem.originalName;
		if (originalName != null) {
			fileName = originalName;
		}
		setTitle(fileName);
		int count = instance.galleryItems.size();
		StringBuilder builder = new StringBuilder().append(position + 1).append('/').append(count);
		if (size > 0) {
			builder.append(", ").append(AttachmentItem.formatSize(size));
		}
		getActionBar().setSubtitle(builder);
	}

	@Override
	public void navigateGalleryOrFinish(boolean enableGalleryMode) {
		if (enableGalleryMode && !galleryWindow) {
			galleryWindow = true;
		}
		if (!returnToGallery()) {
			finish();
		}
	}

	@Override
	public void navigatePageFromList(int position) {
		switchMode(false, true);
		pagerUnit.navigatePageFromList(position, GALLERY_TRANSITION_DURATION);
	}

	@Override
	public void navigatePost(GalleryItem galleryItem, boolean force) {
		if (allowNavigatePost && (scrollThread || force)) {
			Intent intent = new Intent(C.ACTION_GALLERY_NAVIGATE_POST).putExtra(C.EXTRA_CHAN_NAME, instance.chanName)
					.putExtra(C.EXTRA_BOARD_NAME, galleryItem.boardName).putExtra(C.EXTRA_THREAD_NUMBER,
					galleryItem.threadNumber).putExtra(C.EXTRA_POST_NUMBER, galleryItem.postNumber);
			LocalBroadcastManager.getInstance(GalleryActivity.this).sendBroadcast(intent);
			if (force) {
				finish();
			}
		}
	}

	@Override
	public boolean isAllowNavigatePost(boolean fromPager) {
		// Don't allow navigate to post from pager if thread is scrolling automatically with pager
		return allowNavigatePost && (!scrollThread || !fromPager);
	}

	@Override
	public void setScreenOnFixed(boolean fixed) {
		Window window = getWindow();
		if (fixed) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	@Override
	public boolean isGalleryWindow() {
		return galleryWindow;
	}

	@Override
	public boolean isGalleryMode() {
		return galleryMode;
	}

	private boolean expandedScreen;
	private int systemUiVisibilityFlags = GalleryInstance.FLAG_LOCKED_USER;

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void invalidateSystemUiVisibility() {
		ActionBar actionBar = getActionBar();
		boolean visible = isSystemUiVisible();
		boolean changed = visible != actionBar.isShowing();
		if (visible) {
			actionBar.show();
		} else {
			actionBar.hide();
		}
		if (C.API_LOLLIPOP && expandedScreen) {
			View decorView = getWindow().getDecorView();
			int visibility = decorView.getSystemUiVisibility();
			visibility = FlagUtils.set(visibility, View.SYSTEM_UI_FLAG_FULLSCREEN |
					View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, !visible);
			decorView.setSystemUiVisibility(visibility);
		}
		if (pagerUnit != null) {
			pagerUnit.invalidateControlsVisibility();
			if (changed) {
				pagerUnit.invalidatePopupMenu();
			}
		}
	}

	private void postInvalidateSystemUIVisibility() {
		rootView.post(() -> invalidateSystemUiVisibility());
	}

	@Override
	public boolean isSystemUiVisible() {
		return systemUiVisibilityFlags != 0;
	}

	@Override
	public void modifySystemUiVisibility(int flag, boolean value) {
		systemUiVisibilityFlags = FlagUtils.set(systemUiVisibilityFlags, flag, value);
		invalidateSystemUiVisibility();
	}

	@Override
	public void toggleSystemUIVisibility(int flag) {
		modifySystemUiVisibility(flag, !FlagUtils.get(systemUiVisibilityFlags, flag));
	}

	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect) {
		if (listUnit != null) {
			boolean invalidate = listUnit.onApplyWindowPaddings(rect);
			if (invalidate) {
				postInvalidateSystemUIVisibility();
			}
		}
		if (pagerUnit != null) {
			pagerUnit.onApplyWindowPaddings(rect);
		}
	}
}