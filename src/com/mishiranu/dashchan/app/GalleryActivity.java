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

package com.mishiranu.dashchan.app;

import java.io.File;
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
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.TextView;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.CaptchaManager;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.GalleryBackgroundDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.gallery.GalleryInstance;
import com.mishiranu.dashchan.ui.gallery.ListUnit;
import com.mishiranu.dashchan.ui.gallery.PagerUnit;
import com.mishiranu.dashchan.util.ActionMenuConfigurator;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.WindowControlFrameLayout;

public class GalleryActivity extends StateActivity implements GalleryInstance.Callback,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener
{
	private static final String EXTRA_POSITION = "position";
	private static final String EXTRA_GALLERY_WINDOW = "galleryWindow";
	private static final String EXTRA_GALLERY_MODE = "galleryMode";
	private static final String EXTRA_SYSTEM_UI_VISIBILITY = "systemUIVisibility";
	
	private String mThreadTitle;
	private final GalleryInstance mInstance = new GalleryInstance(this, this);
	
	private View mActionBar;
	private WindowControlFrameLayout mRootView;
	
	private GalleryBackgroundDrawable mBackgroundDrawable;
	
	private boolean mGalleryWindow, mGalleryMode;
	private boolean mAllowGoToPost;
	private boolean mOverrideUpButton = false;
	private final boolean mScrollThread = Preferences.isScrollThreadGallery();
	
	private static final int ACTION_BAR_COLOR = 0xaa202020;
	private static final int BACKGROUND_COLOR = 0xf0101010;
	
	private final ActionMenuConfigurator mActionMenuConfigurator = new ActionMenuConfigurator();

	private PagerUnit mPagerUnit;
	private ListUnit mListUnit;
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void applyStatusNavigationTranslucency()
	{
		if (C.API_LOLLIPOP)
		{
			Window window = getWindow();
			int color = ACTION_BAR_COLOR;
			window.setStatusBarColor(color);
			window.setNavigationBarColor(color);
			window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		int[] imageViewPosition = savedInstanceState == null ? getIntent()
				.getIntArrayExtra(C.EXTRA_VIEW_POSITION) : null;
		if (!C.API_LOLLIPOP || imageViewPosition == null) overridePendingTransition(R.anim.fast_fade_in, 0);
		applyStatusNavigationTranslucency();
		super.onCreate(savedInstanceState);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mExpandedScreen = getIntent().getBooleanExtra(C.EXTRA_ALLOW_EXPANDED_SCREEN, false);
		mInstance.actionBarColor = ACTION_BAR_COLOR;
		boolean obtainImages = getIntent().getBooleanExtra(C.EXTRA_OBTAIN_ITEMS, false);
		ArrayList<GalleryItem> galleryItems = obtainImages ? NavigationUtils.obtainImagesProvider(this) : null;
		String chanName = getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		Uri uri = getIntent().getData();
		int imagePosition = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_POSITION, -1) : -1;
		if (chanName == null && uri != null)
		{
			chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
		}
		ChanLocator locator = null;
		if (chanName != null) locator = ChanLocator.get(chanName);
		mThreadTitle = getIntent().getStringExtra(C.EXTRA_THREAD_TITLE);
		boolean defaultLocator = false;
		if (locator == null)
		{
			locator = ChanLocator.getDefault();
			defaultLocator = true;
		}
		if (uri != null && galleryItems == null)
		{
			galleryItems = new ArrayList<>(1);
			String boardName = null;
			String threadNumber = null;
			if (!defaultLocator)
			{
				boardName = locator.safe(true).getBoardName(uri);
				threadNumber = locator.safe(true).getThreadNumber(uri);
			}
			galleryItems.add(new GalleryItem(uri, boardName, threadNumber));
			mOverrideUpButton = true;
			imagePosition = 0;
		}
		else if (imagePosition == -1) imagePosition = getIntent().getIntExtra(C.EXTRA_IMAGE_INDEX, 0);
		mInstance.chanName = chanName;
		mInstance.locator = locator;
		mInstance.galleryItems = galleryItems;
		mAllowGoToPost = getIntent().getBooleanExtra(C.EXTRA_ALLOW_GO_TO_POST, false);
		mActionBar = findViewById(getResources().getIdentifier("action_bar", "id", "android"));
		mRootView = new WindowControlFrameLayout(this);
		mRootView.setOnApplyWindowPaddingsListener(this);
		mRootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		mBackgroundDrawable = new GalleryBackgroundDrawable(mRootView, C.API_LOLLIPOP ? imageViewPosition : null,
				BACKGROUND_COLOR);
		mRootView.setBackground(mBackgroundDrawable);
		setContentView(mRootView);
		if (galleryItems == null || galleryItems.size() == 0)
		{
			View errorView = getLayoutInflater().inflate(R.layout.widget_error, mRootView, false);
			TextView textView = (TextView) errorView.findViewById(R.id.error_text);
			textView.setText(R.string.message_empty_gallery);
			mRootView.addView(errorView);
		}
		else
		{
			mListUnit = new ListUnit(mInstance);
			mPagerUnit = new PagerUnit(mInstance);
			AbsListView listView = mListUnit.getListView();
			registerForContextMenu(listView);
			mRootView.addView(listView, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			mRootView.addView(mPagerUnit.getView(), FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			mPagerUnit.addAndInitViews(mRootView, imagePosition);
			if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_GALLERY_MODE))
			{
				mGalleryMode = savedInstanceState.getBoolean(EXTRA_GALLERY_MODE);
				mGalleryWindow = savedInstanceState.getBoolean(EXTRA_GALLERY_WINDOW);
				switchMode(mGalleryMode, false);
				modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_USER, savedInstanceState
						.getBoolean(EXTRA_SYSTEM_UI_VISIBILITY));
			}
			else
			{
				mGalleryWindow = imagePosition < 0 || getIntent().getBooleanExtra(C.EXTRA_GALLERY_MODE, false);
				if (mGalleryWindow && imagePosition >= 0) mListUnit.setListSelection(imagePosition, false);
				switchMode(mGalleryWindow, false);
			}
			mPagerUnit.onViewsCreated(imageViewPosition);
		}
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		if (mPagerUnit != null) mPagerUnit.onResume();
		CaptchaManager.registerForeground(this);
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		if (mPagerUnit != null) mPagerUnit.onPause();
	}
	
	@Override
	protected void onFinish()
	{
		super.onFinish();
		CaptchaManager.unregisterForeground(this);
		if (mPagerUnit != null) mPagerUnit.onFinish();
	}
	
	@Override
	public void finish()
	{
		super.finish();
		overridePendingTransition(0, R.anim.fast_fade_out);
	}
	
	private void invalidateListPosition()
	{
		mListUnit.setListSelection(mPagerUnit.getCurrentIndex(), true);
	}
	
	private Runnable mReturnToGalleryRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			switchMode(true, true);
			invalidateListPosition();
		}
	};
	
	private boolean returnToGallery()
	{
		if (mGalleryWindow && !mGalleryMode)
		{
			mPagerUnit.onBackToGallery();
			mRootView.post(mReturnToGalleryRunnable);
			return true;
		}
		return false;
	}
	
	@Override
	public void onBackPressed()
	{
		if (!returnToGallery()) super.onBackPressed();
	}
	
	private static final int OPTIONS_MENU_SAVE = 0;
	private static final int OPTIONS_MENU_GALLERY = 1;
	private static final int OPTIONS_MENU_REFRESH = 2;
	private static final int OPTIONS_MENU_TECHNICAL_INFO = 3;
	private static final int OPTIONS_MENU_SEARCH_IMAGE = 4;
	private static final int OPTIONS_MENU_COPY_LINK = 5;
	private static final int OPTIONS_MENU_NAVIGATE_POST = 6;
	private static final int OPTIONS_MENU_EXTERNAL_BROWSER = 7;
	private static final int OPTIONS_MENU_SHARE_LINK = 8;
	private static final int OPTIONS_MENU_SHARE_FILE = 9;
	private static final int OPTIONS_MENU_SELECT = 10;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		ActionIconSet set = new ActionIconSet(this);
		menu.add(0, OPTIONS_MENU_SAVE, 0, R.string.action_save).setIcon(set.getId(R.attr.actionSave))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_MENU_GALLERY, 0, R.string.action_gallery);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(set.getId(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_MENU_TECHNICAL_INFO, 0, R.string.action_technical_info);
		menu.add(0, OPTIONS_MENU_SEARCH_IMAGE, 0, R.string.action_search_image);
		menu.add(0, OPTIONS_MENU_COPY_LINK, 0, R.string.action_copy_link);
		menu.add(0, OPTIONS_MENU_NAVIGATE_POST, 0, R.string.action_go_to_post);
		menu.add(0, OPTIONS_MENU_EXTERNAL_BROWSER, 0, R.string.action_external_browser);
		menu.add(0, OPTIONS_MENU_SHARE_LINK, 0, R.string.action_share_link);
		menu.add(0, OPTIONS_MENU_SHARE_FILE, 0, R.string.action_share_file);
		menu.add(0, OPTIONS_MENU_SELECT, 0, R.string.action_select).setIcon(set.getId(R.attr.actionSelect))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		mActionMenuConfigurator.onAfterCreateOptionsMenu(menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		for (int i = 0; i < menu.size(); i++) menu.getItem(i).setVisible(false);
		if (!mGalleryMode)
		{
			PagerUnit.OptionsMenuCapabilities capabilities = mPagerUnit != null
					? mPagerUnit.obtainOptionsMenuCapabilities() : null;
			if (capabilities != null && capabilities.available)
			{
				menu.findItem(OPTIONS_MENU_SAVE).setVisible(capabilities.save);
				menu.findItem(OPTIONS_MENU_GALLERY).setVisible(!mGalleryWindow && mInstance.galleryItems.size() > 1);
				menu.findItem(OPTIONS_MENU_REFRESH).setVisible(capabilities.refresh);
				menu.findItem(OPTIONS_MENU_TECHNICAL_INFO).setVisible(capabilities.viewTechnicalInfo);
				menu.findItem(OPTIONS_MENU_SEARCH_IMAGE).setVisible(capabilities.searchImage);
				menu.findItem(OPTIONS_MENU_COPY_LINK).setVisible(true);
				menu.findItem(OPTIONS_MENU_NAVIGATE_POST).setVisible(mAllowGoToPost && !mScrollThread
						&& capabilities.navigatePost);
				menu.findItem(OPTIONS_MENU_EXTERNAL_BROWSER).setVisible(true);
				menu.findItem(OPTIONS_MENU_SHARE_LINK).setVisible(true);
				menu.findItem(OPTIONS_MENU_SHARE_FILE).setVisible(capabilities.shareFile);
			}
		}
		else menu.findItem(OPTIONS_MENU_SELECT).setVisible(mListUnit.areItemsSelectable());
		mActionMenuConfigurator.onAfterPrepareOptionsMenu(menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		GalleryItem galleryItem = mPagerUnit != null ? mPagerUnit.getCurrentGalleryItem() : null;
		switch (item.getItemId())
		{
			case android.R.id.home:
			{
				NavigationUtils.handleGalleryUpButtonClick(this, mOverrideUpButton, mInstance.chanName, galleryItem);
				break;
			}
			case OPTIONS_MENU_SAVE:
			{
				downloadGalleryItem(galleryItem);
				break;
			}
			case OPTIONS_MENU_GALLERY:
			{
				mGalleryWindow = true;
				switchMode(true, true);
				invalidateListPosition();
				break;
			}
			case OPTIONS_MENU_REFRESH:
			{
				mPagerUnit.refreshCurrent();
				break;
			}
			case OPTIONS_MENU_TECHNICAL_INFO:
			{
				mPagerUnit.viewTechnicalInfo();
				break;
			}
			case OPTIONS_MENU_SEARCH_IMAGE:
			{
				mPagerUnit.forcePauseVideo();
				NavigationUtils.searchImage(this, mInstance.chanName,
						galleryItem.getDisplayImageUri(mInstance.locator));
				break;
			}
			case OPTIONS_MENU_COPY_LINK:
			{
				StringUtils.copyToClipboard(this, galleryItem.getFileUri(mInstance.locator).toString());
				break;
			}
			case OPTIONS_MENU_NAVIGATE_POST:
			{
				navigatePost(galleryItem, true);
				break;
			}
			case OPTIONS_MENU_EXTERNAL_BROWSER:
			{
				mPagerUnit.forcePauseVideo();
				NavigationUtils.handleUri(this, mInstance.chanName, galleryItem.getFileUri(mInstance.locator),
						NavigationUtils.BrowserType.EXTERNAL);
				break;
			}
			case OPTIONS_MENU_SHARE_LINK:
			{
				mPagerUnit.forcePauseVideo();
				NavigationUtils.share(this, galleryItem.getFileUri(mInstance.locator).toString());
				break;
			}
			case OPTIONS_MENU_SHARE_FILE:
			{
				mPagerUnit.forcePauseVideo();
				Uri uri = galleryItem.getFileUri(mInstance.locator);
				File file = CacheManager.getInstance().getMediaFile(uri, false);
				if (file == null) ToastUtils.show(this, R.string.message_cache_unavailable);
				else NavigationUtils.shareFile(this, file, uri);
				break;
			}
			case OPTIONS_MENU_SELECT:
			{
				mListUnit.startSelectionMode();
				break;
			}
		}
		return true;
	}
	
	@Override
	public void downloadGalleryItem(GalleryItem galleryItem)
	{
		galleryItem.downloadStorage(this, mInstance.locator, mThreadTitle);
	}
	
	@Override
	public void downloadGalleryItems(ArrayList<GalleryItem> galleryItems)
	{
		String boardName = null;
		String threadNumber = null;
		ArrayList<DownloadManager.RequestItem> requestItems = new ArrayList<>();
		for (GalleryItem galleryItem : galleryItems)
		{
			if (requestItems.size() == 0)
			{
				boardName = galleryItem.boardName;
				threadNumber = galleryItem.threadNumber;
			}
			else if (boardName != null || threadNumber != null)
			{
				if (!StringUtils.equals(boardName, galleryItem.boardName) ||
						!StringUtils.equals(threadNumber, galleryItem.threadNumber))
				{
					// Images from different threads, so don't use them to mark files and folders
					boardName = null;
					threadNumber = null;
				}
			}
			requestItems.add(new DownloadManager.RequestItem(galleryItem.getFileUri(mInstance.locator),
					galleryItem.getFileName(mInstance.locator), galleryItem.originalName));
		}
		if (requestItems.size() > 0)
		{
			DownloadManager.getInstance().downloadStorage(this, requestItems, mInstance.chanName,
					boardName, threadNumber, mThreadTitle, true);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		AbsListView.AdapterContextMenuInfo info = (AbsListView.AdapterContextMenuInfo) menuInfo;
		mListUnit.onCreateContextMenu(menu, info);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AbsListView.AdapterContextMenuInfo info = (AbsListView.AdapterContextMenuInfo) item.getMenuInfo();
		if (mListUnit.onContextItemSelected(item, info)) return true;
		return super.onContextItemSelected(item);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (mPagerUnit != null) outState.putInt(EXTRA_POSITION, mPagerUnit.getCurrentIndex());
		outState.putBoolean(EXTRA_GALLERY_WINDOW, mGalleryWindow);
		outState.putBoolean(EXTRA_GALLERY_MODE, mGalleryMode);
		outState.putBoolean(EXTRA_SYSTEM_UI_VISIBILITY, FlagUtils.get(mSystemUiVisibilityFlags,
				GalleryInstance.FLAG_LOCKED_USER));
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		ViewUtils.fixActionBar(this, null);
		if (mListUnit != null) mListUnit.onConfigurationChanged(newConfig);
		if (mPagerUnit != null) mPagerUnit.onConfigurationChanged(newConfig);
		mActionMenuConfigurator.onConfigurationChanged(newConfig);
		invalidateSystemUiVisibility();
	}
	
	private static final int GALLERY_TRANSITION_DURATION = 150;
	
	private void switchMode(boolean galleryMode, boolean animated)
	{
		int duration = animated ? GALLERY_TRANSITION_DURATION : 0;
		mPagerUnit.switchMode(galleryMode, duration);
		mListUnit.switchMode(galleryMode, duration);
		if (galleryMode)
		{
			int count = mInstance.galleryItems.size();
			setTitle(R.string.action_gallery);
			getActionBar().setSubtitle(getResources().getQuantityString(R.plurals.text_several_files_count_format,
					count, count));
		}
		modifySystemUiVisibility(GalleryInstance.FLAG_LOCKED_GRID, galleryMode);
		mGalleryMode = galleryMode;
		if (galleryMode) new CornerAnimator(0xa0, 0xc0); else
		{
			int alpha = Color.alpha(ACTION_BAR_COLOR);
			new CornerAnimator(alpha, alpha);
		}
		invalidateOptionsMenu();
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private class CornerAnimator implements Runnable
	{
		private final long mStartTime = System.currentTimeMillis();

		private final int mFromActionBarAlpha;
		private final int mToActionBarAlpha;
		private final int mFromStatusBarAlpha;
		private final int mToStatusBarAlpha;
		
		private static final int INTERVAL = 200;
		
		public CornerAnimator(int actionBarAlpha, int statusBarAlpha)
		{
			Drawable drawable = mActionBar.getBackground();
			mFromActionBarAlpha = Color.alpha(drawable instanceof ColorDrawable
					? ((ColorDrawable) drawable).getColor() : statusBarAlpha);
			mToActionBarAlpha = actionBarAlpha;
			if (C.API_LOLLIPOP)
			{
				mFromStatusBarAlpha = Color.alpha(getWindow().getStatusBarColor());
				mToStatusBarAlpha = statusBarAlpha;
			}
			else
			{
				mFromStatusBarAlpha = 0x00;
				mToStatusBarAlpha = 0x00;
			}
			if (mFromActionBarAlpha != mToActionBarAlpha || mFromStatusBarAlpha != mToStatusBarAlpha) run();
		}
		
		@Override
		public void run()
		{
			float t = Math.min((float) (System.currentTimeMillis() - mStartTime) / INTERVAL, 1f);
			int actionBarColorAlpha = (int) AnimationUtils.lerp(mFromActionBarAlpha, mToActionBarAlpha, t);
			mActionBar.setBackgroundColor((actionBarColorAlpha << 24) | (0x00ffffff & ACTION_BAR_COLOR));
			if (C.API_LOLLIPOP)
			{
				int statusBarColorAlpha = (int) AnimationUtils.lerp(mFromStatusBarAlpha, mToStatusBarAlpha, t);
				int color = (statusBarColorAlpha << 24) | (0x00ffffff & ACTION_BAR_COLOR);
				Window window = getWindow();
				window.setStatusBarColor(color);
				window.setNavigationBarColor(color);
			}
			if (t < 1f) mRootView.postOnAnimation(this);
		}
	}
	
	@Override
	public void modifyVerticalSwipeState(float value)
	{
		if (!mGalleryWindow) mBackgroundDrawable.setAlpha((int) (0xff * (1f - value)));
	}
	
	@Override
	public void updateTitle()
	{
		GalleryItem galleryItem = mPagerUnit.getCurrentGalleryItem();
		if (galleryItem != null) setTitle(galleryItem, mPagerUnit.getCurrentIndex(), galleryItem.size);
	}
	
	private void setTitle(GalleryItem galleryItem, int position, int size)
	{
		String fileName = galleryItem.getFileName(mInstance.locator);
		String originalName = galleryItem.originalName;
		if (originalName != null) fileName = originalName;
		setTitle(fileName);
		int count = mInstance.galleryItems.size();
		StringBuilder builder = new StringBuilder().append(position + 1).append('/').append(count);
		if (size > 0) builder.append(", ").append(AttachmentItem.formatSize(size));
		getActionBar().setSubtitle(builder);
	}
	
	@Override
	public void navigateGalleryOrFinish()
	{
		if (!returnToGallery()) finish();
	}
	
	@Override
	public void navigatePageFromList(int position)
	{
		switchMode(false, true);
		mPagerUnit.navigatePageFromList(position, GALLERY_TRANSITION_DURATION);
	}
	
	@Override
	public void navigatePost(GalleryItem galleryItem, boolean force)
	{
		if (mAllowGoToPost && (mScrollThread || force))
		{
			Intent intent = new Intent(C.ACTION_GALLERY_GO_TO_POST).putExtra(C.EXTRA_CHAN_NAME, mInstance.chanName)
					.putExtra(C.EXTRA_BOARD_NAME, galleryItem.boardName).putExtra(C.EXTRA_THREAD_NUMBER,
					galleryItem.threadNumber).putExtra(C.EXTRA_POST_NUMBER, galleryItem.postNumber);
			LocalBroadcastManager.getInstance(GalleryActivity.this).sendBroadcast(intent);
			if (force) finish();
		}
	}
	
	@Override
	public void setScreenOnFixed(boolean fixed)
	{
		Window window = getWindow();
		if (fixed) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	@Override
	public boolean isGalleryWindow()
	{
		return mGalleryWindow;
	}
	
	@Override
	public boolean isGalleryMode()
	{
		return mGalleryMode;
	}
	
	private boolean mExpandedScreen;
	private int mSystemUiVisibilityFlags = GalleryInstance.FLAG_LOCKED_USER;
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void invalidateSystemUiVisibility()
	{
		ActionBar actionBar = getActionBar();
		boolean visible = isSystemUiVisible();
		if (visible) actionBar.show(); else actionBar.hide();
		if (C.API_LOLLIPOP && mExpandedScreen)
		{
			View decorView = getWindow().getDecorView();
			int visibility = decorView.getSystemUiVisibility();
			visibility = FlagUtils.set(visibility, View.SYSTEM_UI_FLAG_FULLSCREEN |
					View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, !visible);
			decorView.setSystemUiVisibility(visibility);
		}
		if (mPagerUnit != null) mPagerUnit.invalidateControlsVisibility();
	}
	
	private void postInvalidateSystemUIVisibility()
	{
		mRootView.post(new Runnable()
		{
			@Override
			public void run()
			{
				invalidateSystemUiVisibility();
			}
		});
	}
	
	@Override
	public boolean isSystemUiVisible()
	{
		return mSystemUiVisibilityFlags != 0;
	}
	
	@Override
	public void modifySystemUiVisibility(int flag, boolean value)
	{
		mSystemUiVisibilityFlags = FlagUtils.set(mSystemUiVisibilityFlags, flag, value);
		invalidateSystemUiVisibility();
	}
	
	@Override
	public void toggleSystemUIVisibility(int flag)
	{
		modifySystemUiVisibility(flag, !FlagUtils.get(mSystemUiVisibilityFlags, flag));
	}
	
	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect)
	{
		if (mListUnit != null)
		{
			boolean invalidate = mListUnit.onApplyWindowPaddings(rect);
			if (invalidate) postInvalidateSystemUIVisibility();
		}
		if (mPagerUnit != null) mPagerUnit.onApplyWindowPaddings(rect);
	}
}