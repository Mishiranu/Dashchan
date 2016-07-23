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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toolbar;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.PostingService;
import com.mishiranu.dashchan.app.service.WatcherService;
import com.mishiranu.dashchan.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.ForegroundManager;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.ThemeChoiceDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.DrawerManager;
import com.mishiranu.dashchan.ui.LocalNavigator;
import com.mishiranu.dashchan.ui.Replyable;
import com.mishiranu.dashchan.ui.UiManager;
import com.mishiranu.dashchan.ui.page.ListPage;
import com.mishiranu.dashchan.ui.page.PageHolder;
import com.mishiranu.dashchan.ui.page.PageManager;
import com.mishiranu.dashchan.util.ActionMenuConfigurator;
import com.mishiranu.dashchan.util.DrawerToggle;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ExpandedScreen;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.SortableListView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

public class MainActivity extends StateActivity implements BusyScrollListener.Callback, DrawerManager.Callback,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, PullableWrapper.PullCallback,
		ListPage.Callback, PullableWrapper.PullStateListener, SortableListView.OnStateChangedListener,
		FavoritesStorage.Observer, WatcherService.Client.Callback, LocalNavigator, ReadUpdateTask.Callback
{
	private UiManager mUiManager;
	private PageManager mPageManager;
	private ListPage<?> mPage;
	
	private Preferences.Holder mCurrentPreferences;
	private ActionIconSet mActionIconSet;
	private final WatcherService.Client mWatcherServiceClient = new WatcherService.Client(this);
	
	private SortableListView mDrawerListView;
	private DrawerManager mDrawerManager;
	private FrameLayout mDrawerParent;
	private DrawerLayout mDrawerLayout;
	private DrawerToggle mDrawerToggle;
	
	private ExpandedScreen mExpandedScreen;
	private View mToolbarView;
	
	private ViewGroup mDrawerCommon, mDrawerWide;
	
	private PullableListView mListView;
	private View mProgressView;
	private View mErrorView;
	private TextView mErrorText;
	private boolean mWideMode;
	
	private ReadUpdateTask mReadUpdateTask;
	
	private static final String LOCKER_HANDLE = "handle";
	private static final String LOCKER_DRAWER = "drawer";
	private static final String LOCKER_SEARCH = "search";
	private static final String LOCKER_PULL = "pull";
	private static final String LOCKER_CUSTOM = "custom";
	
	private final ActionMenuConfigurator mActionMenuConfigurator = new ActionMenuConfigurator();
	private final ClickableToast.Holder mClickableToastHolder = new ClickableToast.Holder(this);
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		mCurrentPreferences = Preferences.getCurrent();
		if (C.API_LOLLIPOP)
		{
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
		}
		ResourceUtils.applyPreferredTheme(this);
		mExpandedScreen = new ExpandedScreen(this, Preferences.isExpandedScreen());
		super.onCreate(savedInstanceState);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		float density = ResourceUtils.obtainDensity(this);
		setContentView(R.layout.activity_main);
		ClickableToast.register(mClickableToastHolder);
		FavoritesStorage.getInstance().getObservable().register(this);
		mWatcherServiceClient.bind(this);
		mPageManager = new PageManager();
		mActionIconSet = new ActionIconSet(this);
		mProgressView = findViewById(R.id.progress);
		mErrorView = findViewById(R.id.error);
		mErrorText = (TextView) findViewById(R.id.error_text);
		mListView = (PullableListView) findViewById(android.R.id.list);
		registerForContextMenu(mListView);
		mDrawerCommon = (ViewGroup) findViewById(R.id.drawer_common);
		mDrawerWide = (ViewGroup) findViewById(R.id.drawer_wide);
		TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.styleDrawerSpecial});
		int drawerResId = typedArray.getResourceId(0, 0);
		typedArray.recycle();
		ContextThemeWrapper styledContext = drawerResId != 0 ? new ContextThemeWrapper(this, drawerResId) : this;
		int drawerBackground = ResourceUtils.getColor(styledContext, R.attr.backgroundDrawer);
		mDrawerCommon.setBackgroundColor(drawerBackground);
		mDrawerWide.setBackgroundColor(drawerBackground);
		mDrawerListView = new SortableListView(styledContext, this);
		mDrawerListView.setId(android.R.id.tabcontent);
		mDrawerListView.setOnSortingStateChangedListener(this);
		mDrawerManager = new DrawerManager(styledContext, this, this, mWatcherServiceClient);
		mDrawerManager.bind(mDrawerListView);
		mDrawerParent = new FrameLayout(this);
		mDrawerParent.addView(mDrawerListView);
		mDrawerCommon.addView(mDrawerParent);
		mUiManager = new UiManager(this, this, mExpandedScreen);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (C.API_LOLLIPOP)
		{
			FrameLayout foreground = new FrameLayout(this);
			mDrawerLayout.addView(foreground, mDrawerLayout.indexOfChild(mDrawerCommon), new DrawerLayout.LayoutParams
					(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT));
			getLayoutInflater().inflate(R.layout.widget_toolbar, foreground);
			Toolbar toolbar = (Toolbar) foreground.findViewById(R.id.toolbar);
			setActionBar(toolbar);
			mToolbarView = toolbar;
			mExpandedScreen.setToolbar(toolbar, foreground);
		}
		else getActionBar().setIcon(R.drawable.ic_logo); // Show white logo on search
		mDrawerToggle = new DrawerToggle(this, mDrawerLayout);
		if (C.API_LOLLIPOP)
		{
			mDrawerCommon.setElevation(6f * density);
			mDrawerWide.setElevation(4f * density);
		}
		else mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);
		mDrawerLayout.addDrawerListener(mDrawerToggle);
		mDrawerLayout.addDrawerListener(mDrawerManager);
		if (mToolbarView == null) mDrawerLayout.addDrawerListener(new ExpandedScreenDrawerLocker());
		if (Preferences.isActiveScrollbar())
		{
			mListView.setFastScrollEnabled(true);
			if (!C.API_LOLLIPOP) ListViewUtils.colorizeListThumb4(mListView);
		}
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		mListView.getWrapper().setOnPullListener(this);
		mListView.getWrapper().setPullStateListener(this);
		mListView.setClipToPadding(false);
		ScrollListenerComposite scrollListenerComposite = new ScrollListenerComposite();
		mListView.setOnScrollListener(scrollListenerComposite);
		scrollListenerComposite.add(new BusyScrollListener(this));
		updateWideConfiguration(true);
		mExpandedScreen.setDrawerOverToolbarEnabled(!mWideMode);
		mExpandedScreen.setContentListView(mListView, scrollListenerComposite);
		mExpandedScreen.setDrawerListView(mDrawerParent, mDrawerListView, mDrawerManager.getHeaderView());
		mExpandedScreen.addAdditionalView(mProgressView, true);
		mExpandedScreen.addAdditionalView(mErrorView, true);
		mExpandedScreen.finishInitialization();
		LocalBroadcastManager.getInstance(this).registerReceiver(mNewPostReceiver,
				new IntentFilter(C.ACTION_POST_SENT));
		if (savedInstanceState == null) savedInstanceState = mPageManager.readFromStorage();
		PageHolder savedCurrentPage = mPageManager.restore(savedInstanceState);
		if (savedCurrentPage != null) handleData(savedCurrentPage, false); else handleIntent(getIntent(), false);
		startUpdateTask();
	}
	
	@Override
	protected void onNewIntent(Intent intent)
	{
		if (intent.getBooleanExtra(C.EXTRA_LAUNCHER, false)) return;
		handleIntent(intent, intent.getBooleanExtra(C.EXTRA_ANIMATED_TRANSITION, false));
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		writePagesState(outState);
	}
	
	private void writePagesState(Bundle outState)
	{
		requestStoreExtraAndPosition();
		mPageManager.save(outState);
	}
	
	@Override
	public void navigateBoardsOrThreads(String chanName, String boardName, boolean navigateTop, boolean fromCache)
	{
		handleIntentData(chanName, boardName, null, null, null, null, navigateTop, fromCache, true);
	}
	
	@Override
	public void navigatePosts(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache)
	{
		handleIntentData(chanName, boardName, threadNumber, postNumber, threadTitle, null,
				false, fromCache, true);
	}
	
	@Override
	public void navigateSearch(String chanName, String boardName, String searchQuery)
	{
		handleIntentData(chanName, boardName, null, null, null, searchQuery, false, false, true);
	}
	
	@Override
	public void navigateArchive(String chanName, String boardName)
	{
		performNavigation(PageHolder.Content.ARCHIVE, chanName, boardName, null, null, null, null, false, true);
	}
	
	@Override
	public void navigateTarget(String chanName, ChanLocator.NavigationData data, boolean fromCache)
	{
		switch (data.target)
		{
			case ChanLocator.NavigationData.TARGET_THREADS:
			{
				navigateBoardsOrThreads(chanName, data.boardName, false, fromCache);
				break;
			}
			case ChanLocator.NavigationData.TARGET_POSTS:
			{
				navigatePosts(chanName, data.boardName, data.threadNumber, data.postNumber, null, fromCache);
				break;
			}
			case ChanLocator.NavigationData.TARGET_SEARCH:
			{
				navigateSearch(chanName, data.boardName, data.searchQuery);
				break;
			}
			default:
			{
				throw new UnsupportedOperationException();
			}
		}
	}
	
	@Override
	public void navigatePosting(String chanName, String boardName, String threadNumber, Replyable.ReplyData... data)
	{
		Intent intent = new Intent(getApplicationContext(), PostingActivity.class);
		intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
		intent.putExtra(C.EXTRA_BOARD_NAME, boardName);
		intent.putExtra(C.EXTRA_THREAD_NUMBER, threadNumber);
		intent.putExtra(C.EXTRA_REPLY_DATA, data);
		startActivity(intent);
	}
	
	private void handleIntent(Intent intent, boolean animated)
	{
		String chanName = intent.getStringExtra(C.EXTRA_CHAN_NAME);
		String boardName = intent.getStringExtra(C.EXTRA_BOARD_NAME);
		String threadNumber = intent.getStringExtra(C.EXTRA_THREAD_NUMBER);
		boolean navigateTop = intent.getBooleanExtra(C.EXTRA_NAVIGATE_TOP, false);
		String postNumber = intent.getStringExtra(C.EXTRA_POST_NUMBER);
		String threadTitle = intent.getStringExtra(C.EXTRA_THREAD_TITLE);
		String searchQuery = intent.getStringExtra(C.EXTRA_SEARCH_QUERY);
		boolean fromCache = intent.getBooleanExtra(C.EXTRA_FROM_CACHE, false);
		handleIntentData(chanName, boardName, threadNumber, postNumber, threadTitle, searchQuery, navigateTop,
				fromCache, animated);
	}
	
	private void handleIntentData(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, String searchQuery, boolean navigateTop, boolean fromCache, boolean animated)
	{
		PageHolder pageHolder = mPageManager.getCurrentPage();
		String oldChanName = pageHolder != null ? pageHolder.chanName : null;
		if (chanName == null) return; // Void intent
		if (navigateTop) mPageManager.clearStack();
		boolean forceBoardPage = false;
		if (mPageManager.isSingleBoardMode(chanName))
		{
			boardName = mPageManager.getSingleBoardName(chanName);
			forceBoardPage = true;
		}
		if (boardName != null || threadNumber != null || forceBoardPage)
		{
			if (navigateTop && threadNumber == null && searchQuery == null)
			{
				fromCache |= mPageManager.get(chanName, boardName, threadNumber, PageHolder.Content.THREADS) != null;
			}
			PageHolder.Content content = searchQuery != null ? PageHolder.Content.SEARCH
					: threadNumber == null ? PageHolder.Content.THREADS : PageHolder.Content.POSTS;
			performNavigation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
					searchQuery, fromCache, animated);
		}
		else if (mPageManager.getStackSize(chanName) == 0 || !chanName.equals(oldChanName))
		{
			performNavigation(PageHolder.Content.ALL_BOARDS, chanName, null, null, null, null, null,
					false, animated);
		}
	}
	
	private Runnable mQueuedHandler;
	private final Handler mHandler = new Handler();
	private boolean mAllowScaleAnimation = false;
	
	private void handleData(PageHolder pageHolder, boolean animated)
	{
		performNavigation(pageHolder.content, pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber, null,
				pageHolder.threadTitle, pageHolder.searchQuery, true, animated);
	}
	
	private void performNavigation(final PageHolder.Content content, final String chanName, final String boardName,
			final String threadNumber, final String postNumber, final String threadTitle, final String searchQuery,
			final boolean fromCache, boolean animated)
	{
		PageHolder pageHolder = mPageManager.getCurrentPage();
		if (pageHolder != null && pageHolder.is(chanName, boardName, threadNumber, content) && searchQuery == null)
		{
			// Page could be deleted from stack during clearStack (when home button pressed, for example)
			mPageManager.moveCurrentPageTop();
			mPage.updatePageConfiguration(postNumber, threadTitle);
			invalidateHomeUpState();
			invalidateDrawerItems(true, false);
			return;
		}
		switchView(ListPage.ViewType.LIST, null);
		mListView.getWrapper().cancelBusyState();
		mListView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		ClickableToast.cancel(this);
		requestStoreExtraAndPosition();
		cleanupPage();
		mHandler.removeCallbacks(mQueuedHandler);
		setActionBarLocked(LOCKER_HANDLE, true);
		setActionBarLocked(LOCKER_SEARCH, false);
		setActionBarLocked(false);
		if (animated)
		{
			mQueuedHandler = new Runnable()
			{
				@Override
				public void run()
				{
					mQueuedHandler = null;
					if (mListView.getAnimation() != null) mListView.getAnimation().cancel();
					mListView.setAlpha(1f);
					handleDataAfterAnimation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
							searchQuery, fromCache, true);
				}
			};
			mHandler.postDelayed(mQueuedHandler, 300);
			ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mListView, View.ALPHA, 1f, 0f);
			alphaAnimator.setupStartValues();
			alphaAnimator.setStartDelay(150);
			alphaAnimator.setDuration(150);
			startListAnimator(alphaAnimator);
		}
		else
		{
			handleDataAfterAnimation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
					searchQuery, fromCache, false);
		}
	}
	
	private void requestStoreExtraAndPosition()
	{
		if (mPage != null)
		{
			if (mListView.getChildCount() > 0) mPageManager.getCurrentPage().position = ListPosition.obtain(mListView);
			mPage.onRequestStoreExtra();
		}
	}
	
	private void handleDataAfterAnimation(PageHolder.Content content, String chanName, String boardName,
			String threadNumber, String postNumber, String threadTitle, String searchQuery,
			boolean fromCache, boolean animated)
	{
		clearListAnimator();
		mAllowScaleAnimation = animated;
		setActionBarLocked(LOCKER_HANDLE, false);
		mWatcherServiceClient.updateConfiguration(chanName);
		mDrawerManager.updateConfiguration(chanName);
		mPage = mPageManager.newPage(content);
		mSendPrepareMenuToPage = false; // Will be changed in onCreateOptionsMenu
		PageHolder pageHolder = null;
		switch (content)
		{
			case THREADS:
			{
				pageHolder = mPageManager.add(content, chanName, boardName, null, null, null)
						.setInitialThreadsData(fromCache);
				break;
			}
			case POSTS:
			{
				pageHolder = mPageManager.add(content, chanName, boardName, threadNumber, threadTitle, null)
						.setInitialPostsData(fromCache, postNumber);
				break;
			}
			case SEARCH:
			{
				pageHolder = mPageManager.add(content, chanName, boardName, null, null, searchQuery)
						.setInitialSearchData(fromCache);
				break;
			}
			case ARCHIVE:
			case ALL_BOARDS:
			case USER_BOARDS:
			case HISTORY:
			{
				pageHolder = mPageManager.add(content, chanName, boardName, null, null, null);
				break;
			}
		}
		if (pageHolder == null) throw new RuntimeException();
		mUiManager.view().resetPages();
		mPage.init(this, this, pageHolder, mListView, mUiManager, mActionIconSet);
		mDrawerManager.invalidateItems(true, false);
		if (!mWideMode && !mDrawerLayout.isDrawerOpen(Gravity.START)) mDrawerListView.setSelection(0);
		invalidateOptionsMenu();
		invalidateHomeUpState();
		mAllowScaleAnimation = true;
	}
	
	private void cleanupPage()
	{
		if (mPage != null)
		{
			PostingService.clearNewThreadData();
			mPage.cleanup();
			mPage = null;
		}
	}
	
	private void invalidateHomeUpState()
	{
		if (mPage != null)
		{
			boolean displayUp = false;
			PageHolder pageHolder = mPageManager.getCurrentPage();
			switch (pageHolder.content)
			{
				case THREADS:
				{
					displayUp = mPageManager.getStackSize() > 1;
					break;
				}
				case POSTS:
				case SEARCH:
				case ARCHIVE:
				{
					displayUp = true;
					break;
				}
				case ALL_BOARDS:
				case USER_BOARDS:
				case HISTORY:
				{
					displayUp = pageHolder.boardName != null || mPageManager.getStackSize() > 1;
					break;
				}
			}
			mDrawerToggle.setDrawerIndicatorMode(displayUp ? DrawerToggle.MODE_UP : mWideMode
					? DrawerToggle.MODE_DISABLED : DrawerToggle.MODE_DRAWER);
		}
		else mDrawerToggle.setDrawerIndicatorMode(DrawerToggle.MODE_DISABLED);
	}
	
	private void updateWideConfiguration(boolean forced)
	{
		Configuration configuration = getResources().getConfiguration();
		boolean newWideMode = ViewUtils.isDrawerLockable(configuration) && Preferences.isDrawerLocked();
		if (mWideMode != newWideMode || forced)
		{
			mWideMode = newWideMode;
			if (!forced) mExpandedScreen.setDrawerOverToolbarEnabled(!mWideMode);
			mDrawerLayout.setDrawerLockMode(mWideMode ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
					: DrawerLayout.LOCK_MODE_UNLOCKED);
			mDrawerWide.setVisibility(mWideMode ? View.VISIBLE : View.GONE);
			ViewUtils.removeFromParent(mDrawerParent);
			(mWideMode ? mDrawerWide : mDrawerCommon).addView(mDrawerParent);
			invalidateHomeUpState();
		}
		float density = ResourceUtils.obtainDensity(this);
		int actionBarSize = getResources().getDimensionPixelSize(ResourceUtils.getResourceId(this,
				android.R.attr.actionBarSize, 0));
		int drawerWidth = Math.min((int) (configuration.screenWidthDp * density + 0.5f) - actionBarSize,
				(int) (320 * density + 0.5f));
		mDrawerCommon.getLayoutParams().width = mDrawerWide.getLayoutParams().width = drawerWidth;
	}
	
	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView)
	{
		if (mPage != null) mPage.setListViewBusy(isBusy, listView);
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		Preferences.Holder newPreferences = Preferences.getCurrent();
		if (mCurrentPreferences.isNeedRestartActivity(newPreferences))
		{
			// Recreate after onResume
			postRecreate();
			return;
		}
		else if (mCurrentPreferences.isNeedRefreshList(newPreferences))
		{
			((BaseAdapter) mListView.getAdapter()).notifyDataSetChanged();
		}
		mDrawerManager.invalidateItems(true, true);
		mCurrentPreferences = newPreferences;
		updateWideConfiguration(false);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		mExpandedScreen.onResume();
		mDrawerManager.performResume();
		mWatcherServiceClient.start();
		if (mPage != null) mPage.resume();
		mClickableToastHolder.onResume();
		showRestartDialogIfNeeded();
		ChanManager.getInstance().getInstallationObservable().register(mInstallationCallback);
		ForegroundManager.register(this);
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		mWatcherServiceClient.stop();
		if (mPage != null) mPage.pause();
		mClickableToastHolder.onPause();
		ForegroundManager.unregister(this);
	}
	
	@Override
	protected void onStop()
	{
		super.onStop();
		ChanManager.getInstance().getInstallationObservable().unregister(mInstallationCallback);
	}
	
	@Override
	protected void onFinish()
	{
		super.onFinish();
		if (mReadUpdateTask != null)
		{
			mReadUpdateTask.cancel();
			mReadUpdateTask = null;
		}
		mUiManager.onFinish();
		mWatcherServiceClient.unbind(this);
		ClickableToast.unregister(mClickableToastHolder);
		FavoritesStorage.getInstance().getObservable().unregister(this);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mNewPostReceiver);
		cleanupPage();
		for (String chanName : ChanManager.getInstance().getAvailableChanNames())
		{
			ChanConfiguration.get(chanName).commit();
		}
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(C.NOTIFICATION_TAG_UPDATE, 0);
		FavoritesStorage.getInstance().await(true);
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		ViewUtils.fixActionBar(this, mToolbarView);
		mDrawerToggle.onConfigurationChanged(newConfig);
		updateWideConfiguration(false);
		mExpandedScreen.onConfigurationChanged(newConfig);
		updateOptionsMenu(false);
	}
	
	@Override
	public boolean onSearchRequested()
	{
		if (mSearchMenuItem != null)
		{
			mSearchMenuItem.expandActionView();
			return true;
		}
		return false;
	}
	
	private long mBackPressed = 0;
	
	@Override
	public void onBackPressed()
	{
		if (!mWideMode && mDrawerLayout.isDrawerOpen(Gravity.START)) mDrawerLayout.closeDrawers();
		else if (mPage != null)
		{
			if (mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded())
			{
				// Fix back button on 5.0
				mSearchMenuItem.collapseActionView();
				return;
			}
			if (mPage.onBackPressed()) return;
			PageHolder previousPageHolder = mPageManager.getTargetPreviousPage();
			if (previousPageHolder != null)
			{
				mPageManager.removeCurrentPageFromStack();
				handleData(previousPageHolder, true);
			}
			else
			{
				if (System.currentTimeMillis() - mBackPressed > 2000)
				{
					ClickableToast.show(this, R.string.message_press_again_to_exit);
					mBackPressed = System.currentTimeMillis();
				}
				else super.onBackPressed();
			}
		}
	}
	
	private final Runnable mInstallationCallback = new Runnable()
	{
		@Override
		public void run()
		{
			showRestartDialogIfNeeded();
		}
	};
	
	private boolean mMayShowRestartDialog = true;
	
	private void showRestartDialogIfNeeded()
	{
		if (ChanManager.getInstance().checkNewExtensionsInstalled() && mMayShowRestartDialog)
		{
			mMayShowRestartDialog = false;
			AlertDialog dialog = new AlertDialog.Builder(this).setMessage(R.string.message_packages_installed)
					.setPositiveButton(R.string.action_restart, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					Bundle outState = new Bundle();
					writePagesState(outState);
					mPageManager.writeToStorage(outState);
					NavigationUtils.restartApplication(MainActivity.this);
				}
			}).setNegativeButton(android.R.string.cancel, null).create();
			dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
			{
				@Override
				public void onDismiss(DialogInterface dialog)
				{
					mMayShowRestartDialog = true;
				}
			});
			dialog.show();
			mUiManager.dialog().notifySwitchBackground();
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if (mPage != null) mPage.onItemClick(view, position, id);
	}
	
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		if (mPage != null) return mPage.onItemLongClick(view, position, id);
		return false;
	}
	
	@Override
	public void onActionModeStarted(ActionMode mode)
	{
		super.onActionModeStarted(mode);
		mExpandedScreen.setActionModeState(true);
	}
	
	@Override
	public void onActionModeFinished(ActionMode mode)
	{
		super.onActionModeFinished(mode);
		mExpandedScreen.setActionModeState(false);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		mClickableToastHolder.onWindowFocusChanged(hasFocus);
	}
	
	private class SearchViewController implements SearchView.OnQueryTextListener, MenuItem.OnActionExpandListener
	{
		private boolean mSearchExpanded = false;
		private boolean mWasSubmit = false;
		private boolean mMenuUpdated = false;
		
		private final Runnable mRestoreLastSearchQueryRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				mSearchView.setQuery(mLastSearchQuery, false);
			}
		};
		
		/*
		 * In Marshmallow keyboard is not showing when action menu item is not shown as action. Fix it here.
		 */
		private final Runnable mMarshmallowShowKeyboardRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				View textView = null;
				try
				{
					Field field = SearchView.class.getDeclaredField("mSearchSrcTextView");
					field.setAccessible(true);
					textView = (View) field.get(mSearchView);
				}
				catch (Exception e)
				{
					
				}
				if (textView != null)
				{
					InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
					if (inputMethodManager != null)
					{
						inputMethodManager.showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT);
					}
				}
			}
		};
		
		@Override
		public boolean onQueryTextSubmit(String query)
		{
			if (mPage != null && query.length() > 0)
			{
				mWasSubmit = true;
				mMenuUpdated = false;
				boolean keepExpanded = mPage.onStartSearch(query);
				// mMenuUpdated can be changed in onStartSearch if it calls updateOptionsMenu
				if (keepExpanded)
				{
					if (mMenuUpdated) mExpandSearchViewOnCreate = true;
				}
				else mSearchMenuItem.collapseActionView();
			}
			return true;
		}
		
		@Override
		public boolean onQueryTextChange(String newText)
		{
			if (mSearchExpanded)
			{
				if (!mWasSubmit || !StringUtils.isEmpty(newText)) mLastSearchQuery = newText;
				mWasSubmit = false;
			}
			if (mPage != null) mPage.onSearchTextChange(newText);
			return false;
		}
		
		@Override
		public boolean onMenuItemActionExpand(MenuItem item)
		{
			mSearchExpanded = true;
			setActionBarLocked(LOCKER_SEARCH, true);
			mSearchView.post(mRestoreLastSearchQueryRunnable);
			if (C.API_MARSHMALLOW) mSearchView.postDelayed(mMarshmallowShowKeyboardRunnable, 250);
			return true;
		}
		
		@Override
		public boolean onMenuItemActionCollapse(MenuItem item)
		{
			mSearchExpanded = false;
			setActionBarLocked(LOCKER_SEARCH, false);
			if (!mExpandSearchViewOnCreate && mPage != null) mPage.onStopSearch();
			return true;
		}
	}

	private final SearchViewController mSearchViewController = new SearchViewController();
	private SearchView mSearchView;
	private MenuItem mSearchMenuItem;
	
	private boolean mExpandSearchViewOnCreate = false;
	private String mLastSearchQuery;
	
	private Menu mCurrentMenu;
	private boolean mSendPrepareMenuToPage = false;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		mCurrentMenu = menu;
		if (mSearchView == null)
		{
			mSearchView = new SearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(this, R.style.Theme_Special_White)
					: getActionBar().getThemedContext());
			mSearchView.setOnQueryTextListener(mSearchViewController);
		}
		if (mPage != null)
		{
			mPage.onCreateOptionsMenu(menu);
			mSendPrepareMenuToPage = true;
		}
		
		MenuItem appearanceOptionsItem = menu.findItem(ListPage.OPTIONS_MENU_APPEARANCE);
		if (appearanceOptionsItem != null)
		{
			Menu appearanceOptionsMenu = appearanceOptionsItem.getSubMenu();
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_CHANGE_THEME, 0, R.string.action_change_theme);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_EXPANDED_SCREEN, 0, R.string.action_expanded_screen)
					.setCheckable(true);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_SPOILERS, 0, R.string.action_spoilers)
					.setCheckable(true);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_MY_POSTS, 0, R.string.action_my_posts)
					.setCheckable(true);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_DRAWER, 0, R.string.action_lock_drawer)
					.setCheckable(true);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_THREADS_GRID, 0, R.string.action_threads_grid)
					.setCheckable(true);
			appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_SFW_MODE, 0, R.string.action_sfw_mode)
					.setCheckable(true);
		}
		
		mSearchMenuItem = menu.findItem(ListPage.OPTIONS_MENU_SEARCH);
		if (mSearchMenuItem != null)
		{
			mSearchMenuItem.setActionView(mSearchView);
			mSearchMenuItem.setOnActionExpandListener(mSearchViewController);
			if (mExpandSearchViewOnCreate)
			{
				mSearchMenuItem.expandActionView();
				mSearchView.clearFocus();
				mExpandSearchViewOnCreate = false;
			}
		}
		mActionMenuConfigurator.onAfterCreateOptionsMenu(menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		if (mPage != null && mSendPrepareMenuToPage) mPage.onPrepareOptionsMenu(menu);
		MenuItem appearanceOptionsItem = menu.findItem(ListPage.OPTIONS_MENU_APPEARANCE);
		if (appearanceOptionsItem != null)
		{
			Menu appearanceOptionsMenu = appearanceOptionsItem.getSubMenu();
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_EXPANDED_SCREEN)
					.setChecked(Preferences.isExpandedScreen());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_SPOILERS).setChecked(Preferences.isShowSpoilers());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_MY_POSTS).setChecked(Preferences.isShowMyPosts());
			boolean lockable = ViewUtils.isDrawerLockable(getResources().getConfiguration());
			boolean locked = Preferences.isDrawerLocked();
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_DRAWER).setVisible(lockable).setChecked(locked);
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_THREADS_GRID)
					.setChecked(Preferences.isThreadsGridMode());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_SFW_MODE).setChecked(Preferences.isSfwMode());
		}
		mActionMenuConfigurator.onAfterPrepareOptionsMenu(menu);
		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (mDrawerToggle.onOptionsItemSelected(item)) return true;
		if (mPage != null)
		{
			if (item.getItemId() == ListPage.OPTIONS_MENU_SEARCH)
			{
				mSearchView.setQueryHint(item.getTitle());
				return false;
			}
			if (mPage.onOptionsItemSelected(item)) return true;
			switch (item.getItemId())
			{
				case android.R.id.home:
				{
					mDrawerLayout.closeDrawers();
					PageHolder pageHolder = mPageManager.getCurrentPage();
					String newChanName = pageHolder.chanName;
					String newBoardName = pageHolder.boardName;
					if (pageHolder.content == PageHolder.Content.THREADS)
					{
						// Up button must navigate to main page in threads list
						newBoardName = Preferences.getDefaultBoardName(pageHolder.chanName);
						if (Preferences.isMergeChans() && StringUtils.equals(pageHolder.boardName, newBoardName))
						{
							newChanName = ChanManager.getInstance().getDefaultChanName();
							newBoardName = Preferences.getDefaultBoardName(newChanName);
						}
					}
					navigateBoardsOrThreads(newChanName, newBoardName, true, false);
					return true;
				}
				case ListPage.APPEARANCE_MENU_CHANGE_THEME:
				case ListPage.APPEARANCE_MENU_EXPANDED_SCREEN:
				case ListPage.APPEARANCE_MENU_SPOILERS:
				case ListPage.APPEARANCE_MENU_MY_POSTS:
				case ListPage.APPEARANCE_MENU_DRAWER:
				case ListPage.APPEARANCE_MENU_THREADS_GRID:
				case ListPage.APPEARANCE_MENU_SFW_MODE:
				{
					try
					{
						switch (item.getItemId())
						{
							case ListPage.APPEARANCE_MENU_CHANGE_THEME:
							{
								showThemeDialog();
								return true;
							}
							case ListPage.APPEARANCE_MENU_EXPANDED_SCREEN:
							{
								Preferences.setExpandedScreen(!item.isChecked());
								recreate();
								return true;
							}
							case ListPage.APPEARANCE_MENU_SPOILERS:
							{
								Preferences.setShowSpoilers(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_MY_POSTS:
							{
								Preferences.setShowMyPosts(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_DRAWER:
							{
								Preferences.setDrawerLocked(!item.isChecked());
								updateWideConfiguration(false);
								return true;
							}
							case ListPage.APPEARANCE_MENU_THREADS_GRID:
							{
								Preferences.setThreadsGridMode(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_SFW_MODE:
							{
								Preferences.setSfwMode(!item.isChecked());
								return true;
							}
						}
					}
					finally
					{
						if (mPage != null) mPage.onAppearanceOptionChanged(item.getItemId());
					}
				}
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		if (mPage != null)
		{
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			mPage.onCreateContextMenu(menu, v, info.position, info.targetView);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		if (mPage != null)
		{
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
			return mPage.onContextItemSelected(item, info.position, info.targetView);
		}
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showThemeDialog()
	{
		final int checkedItem = Arrays.asList(Preferences.VALUES_THEME).indexOf(Preferences.getTheme());
		Resources resources = getResources();
		float density = ResourceUtils.obtainDensity(resources);
		ScrollView scrollView = new ScrollView(this);
		LinearLayout outer = new LinearLayout(this);
		outer.setOrientation(LinearLayout.VERTICAL);
		int outerPadding = (int) (16f * density);
		outer.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
		scrollView.addView(outer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.action_change_theme)
				.setView(scrollView).setNegativeButton(android.R.string.cancel, null).create();
		View.OnClickListener listener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				int index = (int) v.getTag();
				if (index != checkedItem)
				{
					Preferences.setTheme(Preferences.VALUES_THEME[index]);
					recreate();
				}
				dialog.dismiss();
			}
		};
		int circleSize = (int) (56f * density);
		int itemPadding = (int) (12f * density);
		LinearLayout inner = null;
		for (int i = 0; i < Preferences.ENTRIES_THEME.length; i++)
		{
			if (i % 3 == 0)
			{
				inner = new LinearLayout(this);
				inner.setOrientation(LinearLayout.HORIZONTAL);
				outer.addView(inner, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			}
			LinearLayout layout = new LinearLayout(this);
			layout.setOrientation(LinearLayout.VERTICAL);
			layout.setGravity(Gravity.CENTER);
			layout.setBackgroundResource(ResourceUtils.getResourceId(this, android.R.attr.selectableItemBackground, 0));
			layout.setPadding(0, itemPadding, 0, itemPadding);
			layout.setOnClickListener(listener);
			layout.setTag(i);
			inner.addView(layout, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			View view = new View(this);
			int colorBackgroundAttr = Preferences.VALUES_THEME_COLORS[i][0];
			int colorPrimaryAttr = Preferences.VALUES_THEME_COLORS[i][1];
			int colorAccentAttr = Preferences.VALUES_THEME_COLORS[i][2];
			Resources.Theme theme = getResources().newTheme();
			theme.applyStyle(Preferences.VALUES_THEME_IDS[i], true);
			TypedArray typedArray = theme.obtainStyledAttributes(new int[] {colorBackgroundAttr,
					colorPrimaryAttr, colorAccentAttr});
			view.setBackground(new ThemeChoiceDrawable(typedArray.getColor(0, 0), typedArray.getColor(1, 0),
					typedArray.getColor(2, 0)));
			typedArray.recycle();
			if (C.API_LOLLIPOP) view.setElevation(6f * density);
			layout.addView(view, circleSize, circleSize);
			TextView textView = new TextView(this, null, android.R.attr.textAppearanceListItem);
			textView.setSingleLine(true);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			textView.setText(Preferences.ENTRIES_THEME[i]);
			if (C.API_LOLLIPOP)
			{
				textView.setAllCaps(true);
				textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
			}
			else textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
			textView.setGravity(Gravity.CENTER_HORIZONTAL);
			textView.setPadding(0, (int) (8f * density), 0, 0);
			layout.addView(textView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			if (i + 1 == Preferences.ENTRIES_THEME.length && Preferences.ENTRIES_THEME.length % 3 != 0)
			{
				if (Preferences.ENTRIES_THEME.length % 3 == 1)
				{
					inner.addView(new View(this), 0, new LinearLayout.LayoutParams(0,
							LinearLayout.LayoutParams.MATCH_PARENT, 1f));
				}
				inner.addView(new View(this), new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.MATCH_PARENT, 1f));
			}
		}
		dialog.show();
	}
	
	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		if (mPage != null) mPage.onListPulled(wrapper, side);
	}
	
	@Override
	public void onPullStateChanged(PullableWrapper wrapper, boolean busy)
	{
		setActionBarLocked(LOCKER_PULL, busy);
	}
	
	@Override
	public void onSortingStateChanged(SortableListView listView, boolean sorting)
	{
		if (!mWideMode)
		{
			mDrawerLayout.setDrawerLockMode(sorting ? DrawerLayout.LOCK_MODE_LOCKED_OPEN
					: DrawerLayout.LOCK_MODE_UNLOCKED);
		}
	}
	
	@Override
	public void onSelectChan(String chanName)
	{
		if (mPage != null)
		{
			if (!mPageManager.getCurrentPage().chanName.equals(chanName))
			{
				if (!Preferences.isMergeChans())
				{
					// Find chan page and open it. Open root page if nothing was found.
					PageHolder pageHolder = mPageManager.getLastPage(chanName);
					if (pageHolder != null) handleData(pageHolder, true); else
					{
						navigateBoardsOrThreads(chanName, Preferences.getDefaultBoardName(chanName), false, false);
					}
				}
				else
				{
					// Open root page. If page is already opened, load it from cache.
					boolean fromCache = false;
					String boardName = Preferences.getDefaultBoardName(chanName);
					for (PageHolder pageHolder : mPageManager.getPages())
					{
						if (pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS))
						{
							fromCache = true;
							break;
						}
					}
					navigateBoardsOrThreads(chanName, boardName, false, fromCache);
				}
				mDrawerManager.updateConfiguration(chanName);
				mDrawerManager.invalidateItems(true, false);
			}
		}
		if (!mWideMode) mDrawerLayout.closeDrawers();
	}
	
	@Override
	public void onSelectBoard(String chanName, String boardName, boolean fromCache)
	{
		if (mPage != null)
		{
			PageHolder pageHolder = mPageManager.getCurrentPage();
			if (mPageManager.isSingleBoardMode(chanName)) boardName = mPageManager.getSingleBoardName(chanName);
			if (!pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS))
			{
				navigateBoardsOrThreads(chanName, boardName, false, fromCache);
			}
		}
		if (!mWideMode) mDrawerLayout.closeDrawers();
	}
	
	@Override
	public boolean onSelectThread(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache)
	{
		if (mPage != null)
		{
			PageHolder pageHolder = mPageManager.getCurrentPage();
			if (mPageManager.isSingleBoardMode(chanName)) boardName = mPageManager.getSingleBoardName(chanName);
			else if (boardName == null)
			{
				switch (pageHolder.content)
				{
					case ALL_BOARDS:
					case USER_BOARDS:
					case HISTORY:
					{
						return false;
					}
					default: break;
				}
				boardName = pageHolder.boardName;
			}
			if (!pageHolder.is(chanName, boardName, threadNumber, PageHolder.Content.POSTS))
			{
				navigatePosts(chanName, boardName, threadNumber, postNumber, threadTitle, fromCache);
			}
		}
		if (!mWideMode) mDrawerLayout.closeDrawers();
		return true;
	}
	
	@Override
	public boolean onClosePage(String chanName, String boardName, String threadNumber)
	{
		if (mPage != null)
		{
			PageHolder pageHolder = mPageManager.getCurrentPage();
			if (pageHolder != null && isPageThreadsPosts(pageHolder, chanName, boardName, threadNumber))
			{
				PageHolder previousPageHolder = mPageManager.getTargetPreviousPage();
				mPageManager.removeCurrentPage();
				if (previousPageHolder != null) handleData(previousPageHolder, true); else
				{
					if (mPageManager.isSingleBoardMode(chanName))
					{
						performNavigation(PageHolder.Content.THREADS, chanName,
								mPageManager.getSingleBoardName(chanName), null, null, null, null, true, true);
					}
					else
					{
						performNavigation(PageHolder.Content.ALL_BOARDS, chanName, null, null, null, null, null,
								true, true);
					}
				}
				return true;
			}
			else
			{
				removePage(chanName, boardName, threadNumber);
				// Replace arrow with bars, if current threads page becomes root
				if (pageHolder.content == PageHolder.Content.THREADS && mPageManager.getStackSize() <= 1)
				{
					if (mPageManager.isSingleBoardMode() || pageHolder.boardName
							.equals(Preferences.getDefaultBoardName(pageHolder.chanName)))
					{
						invalidateHomeUpState();
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public void onCloseAllPages()
	{
		if (mPage != null)
		{
			String chanName = mPageManager.getCurrentPage().chanName;
			String boardName = Preferences.getDefaultBoardName(chanName);
			PageHolder targetPageHolder;
			if (!mPageManager.isSingleBoardMode() && boardName == null)
			{
				targetPageHolder = mPageManager.get(chanName, null, null, PageHolder.Content.ALL_BOARDS);
			}
			else if (mPageManager.isSingleBoardMode())
			{
				targetPageHolder = mPageManager.get(chanName, mPageManager.getSingleBoardName(chanName),
						null, PageHolder.Content.THREADS);
			}
			else targetPageHolder = mPageManager.get(chanName, boardName, null, PageHolder.Content.THREADS);
			mPageManager.closeAllExcept(targetPageHolder);
			if (mPageManager.getCurrentPage() == null) cleanupPage();
			mDrawerManager.invalidateItems(true, false);
			navigateBoardsOrThreads(chanName, boardName, false, targetPageHolder != null);
		}
	}
	
	@Override
	public int onEnterNumber(int number)
	{
		int result = 0;
		if (mPage != null) result = mPage.onDrawerNumberEntered(number);
		if (!mWideMode && FlagUtils.get(result, DrawerManager.RESULT_SUCCESS)) mDrawerLayout.closeDrawers();
		return result;
	}
	
	private final Runnable mPreferencesRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			startActivity(new Intent(MainActivity.this, PreferencesActivity.class));
		}
	};
	
	@Override
	public void onSelectDrawerMenuItem(int item)
	{
		PageHolder.Content content = null;
		switch (item)
		{
			case DrawerManager.MENU_ITEM_ALL_BOARDS:
			{
				content = PageHolder.Content.ALL_BOARDS;
				break;
			}
			case DrawerManager.MENU_ITEM_USER_BOARDS:
			{
				content = PageHolder.Content.USER_BOARDS;
				break;
			}
			case DrawerManager.MENU_ITEM_HISTORY:
			{
				content = PageHolder.Content.HISTORY;
				break;
			}
			case DrawerManager.MENU_ITEM_PREFERENCES:
			{
				if (mWideMode) mPreferencesRunnable.run(); else mListView.postDelayed(mPreferencesRunnable, 200);
				break;
			}
		}
		if (content != null)
		{
			if (mPage != null)
			{
				PageHolder pageHolder = mPageManager.getCurrentPage();
				if (pageHolder.content != content)
				{
					for (PageHolder itPageHolder : mPageManager.getPages())
					{
						// Reset list position
						if (itPageHolder.content == content) itPageHolder.position = null;
					}
					performNavigation(content, pageHolder.chanName, pageHolder.boardName, null, null, null, null,
							false, true);
				}
			}
		}
		if (!mWideMode) mDrawerLayout.closeDrawers();
	}
	
	@Override
	public ArrayList<PageHolder> getDrawerPageHolders()
	{
		return mPageManager.getPages();
	}
	
	private final BroadcastReceiver mNewPostReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (mPage != null) mPage.handleNewPostDatasNow();
		}
	};
	
	private void startUpdateTask()
	{
		if (!Preferences.isCheckUpdatesOnStart()) return;
		long lastUpdateCheck = Preferences.getLastUpdateCheck();
		if (System.currentTimeMillis() - lastUpdateCheck < 12 * 60 * 60 * 1000) return; // 12 hours
		mReadUpdateTask = new ReadUpdateTask(this, this);
		mReadUpdateTask.executeOnExecutor(ReadUpdateTask.THREAD_POOL_EXECUTOR);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap)
	{
		mReadUpdateTask = null;
		if (updateDataMap == null) return;
		Preferences.setLastUpdateCheck(System.currentTimeMillis());
		int count = PreferencesActivity.checkNewVersions(MainActivity.this, updateDataMap);
		if (count <= 0) return;
		Notification.Builder builder = new Notification.Builder(MainActivity.this);
		builder.setSmallIcon(R.drawable.ic_new_releases_white_24dp);
		String text = getString(R.string.text_updates_available_format, count);
		if (C.API_LOLLIPOP)
		{
			builder.setColor(ResourceUtils.getColor(MainActivity.this, android.R.attr.colorAccent));
			builder.setPriority(Notification.PRIORITY_HIGH);
			builder.setVibrate(new long[0]);
		}
		else builder.setTicker(text);
		builder.setContentTitle(getString(R.string.text_app_name_update,
				getString(R.string.const_app_name)));
		builder.setContentText(text);
		builder.setContentIntent(PendingIntent.getActivity(MainActivity.this, 0, PreferencesActivity.createUpdateIntent
				(this, updateDataMap).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT));
		builder.setAutoCancel(true);
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.notify(C.NOTIFICATION_TAG_UPDATE, 0, builder.build());
	}
	
	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action)
	{
		switch (action)
		{
			case FavoritesStorage.ACTION_ADD:
			case FavoritesStorage.ACTION_REMOVE:
			{
				mDrawerManager.invalidateItems(false, true);
				break;
			}
		}
	}

	@Override
	public void onWatcherUpdate(WatcherService.WatcherItem watcherItem, WatcherService.State state)
	{
		mDrawerManager.onWatcherUpdate(watcherItem, state);
	}
	
	@Override
	public void invalidateDrawerItems(boolean pages, boolean favorites)
	{
		mDrawerManager.invalidateItems(pages, favorites);
	}
	
	@Override
	public void updateOptionsMenu(boolean recreate)
	{
		if (recreate || mCurrentMenu == null)
		{
			mSearchViewController.mMenuUpdated = true;
			invalidateOptionsMenu();
		}
		else onPrepareOptionsMenu(mCurrentMenu);
	}
	
	@Override
	public void switchView(ListPage.ViewType viewType, String message)
	{
		mProgressView.setVisibility(viewType == ListPage.ViewType.PROGRESS ? View.VISIBLE : View.GONE);
		mErrorView.setVisibility(viewType == ListPage.ViewType.ERROR ? View.VISIBLE : View.GONE);
		if (viewType == ListPage.ViewType.ERROR)
		{
			mErrorText.setText(message != null ? message : getString(R.string.message_unknown_error));
		}
	}
	
	private final Runnable mShowScaleRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			showScaleAnimation(false);
		}
	};
	
	@Override
	public void showScaleAnimation()
	{
		showScaleAnimation(true);
	}
	
	private void showScaleAnimation(boolean post)
	{
		clearListAnimator();
		if (mAllowScaleAnimation)
		{
			if (post)
			{
				mListView.setVisibility(View.INVISIBLE);
				mHandler.post(mShowScaleRunnable);
			}
			else
			{
				final float fromScale = 0.925f;
				ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mListView, View.ALPHA, 0f, 1f);
				ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(mListView, View.SCALE_X, fromScale, 1f);
				ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(mListView, View.SCALE_Y, fromScale, 1f);
				AnimatorSet animatorSet = new AnimatorSet();
				animatorSet.playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator);
				animatorSet.setDuration(100);
				startListAnimator(animatorSet);
			}
		}
	}
	
	private Animator mCurrentListAnimator;
	
	private void clearListAnimator()
	{
		if (mCurrentListAnimator != null)
		{
			mCurrentListAnimator.cancel();
			mCurrentListAnimator = null;
		}
		mListView.setVisibility(View.VISIBLE);
	}
	
	private void startListAnimator(Animator animator)
	{
		clearListAnimator();
		mCurrentListAnimator = animator;
		animator.start();
	}
	
	@Override
	public void removePage(PageHolder pageHolder)
	{
		removePage(pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber);
	}
	
	private void removePage(String chanName, String boardName, String threadNumber)
	{
		ArrayList<PageHolder> pageHolders = mPageManager.getPages();
		for (int i = 0; i < pageHolders.size(); i++)
		{
			if (isPageThreadsPosts(pageHolders.get(i), chanName, boardName, threadNumber))
			{
				pageHolders.remove(i);
				break;
			}
		}
		mDrawerManager.invalidateItems(true, false);
	}
	
	private boolean isPageThreadsPosts(PageHolder pageHolder, String chanName, String boardName, String threadNumber)
	{
		if (threadNumber != null) return pageHolder.is(chanName, boardName, threadNumber, PageHolder.Content.POSTS);
		else return pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS);
	}
	
	private void setActionBarLocked(String locker, boolean locked)
	{
		if (locked) mExpandedScreen.addLocker(locker); else mExpandedScreen.removeLocker(locker);
	}
	
	@Override
	public void setActionBarLocked(boolean locked)
	{
		setActionBarLocked(LOCKER_CUSTOM, locked);
	}
	
	private class ExpandedScreenDrawerLocker implements DrawerLayout.DrawerListener
	{
		@Override
		public void onDrawerSlide(View drawerView, float slideOffset)
		{
			
		}
		
		@Override
		public void onDrawerOpened(View drawerView)
		{
			setActionBarLocked(LOCKER_DRAWER, true);
		}
		
		@Override
		public void onDrawerClosed(View drawerView)
		{
			setActionBarLocked(LOCKER_DRAWER, false);
		}
		
		@Override
		public void onDrawerStateChanged(int newState)
		{
			
		}
	}
	
	/*{
		dbg.Log.d("create", hashCode());
	}
	
	@Override
	protected void finalize() throws Throwable
	{
		dbg.Log.d("finalize", hashCode());
		super.finalize();
	}*/
}