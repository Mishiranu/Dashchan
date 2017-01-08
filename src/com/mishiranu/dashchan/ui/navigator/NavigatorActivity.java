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

package com.mishiranu.dashchan.ui.navigator;

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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.ThemeChoiceDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.ui.ActionMenuConfigurator;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.ui.navigator.page.PageHolder;
import com.mishiranu.dashchan.ui.navigator.page.PageManager;
import com.mishiranu.dashchan.ui.posting.PostingActivity;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.DrawerToggle;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CustomSearchView;
import com.mishiranu.dashchan.widget.ExpandedScreen;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.SortableListView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

public class NavigatorActivity extends StateActivity implements BusyScrollListener.Callback, DrawerForm.Callback,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, PullableWrapper.PullCallback,
		ListPage.Callback, PullableWrapper.PullStateListener, SortableListView.OnStateChangedListener,
		FavoritesStorage.Observer, WatcherService.Client.Callback, UiManager.LocalNavigator, ReadUpdateTask.Callback {
	private UiManager uiManager;
	private PageManager pageManager;
	private ListPage<?> page;

	private Preferences.Holder currentPreferences;
	private ActionIconSet actionIconSet;
	private final WatcherService.Client watcherServiceClient = new WatcherService.Client(this);

	private SortableListView drawerListView;
	private DrawerForm drawerForm;
	private FrameLayout drawerParent;
	private DrawerLayout drawerLayout;
	private DrawerToggle drawerToggle;

	private ExpandedScreen expandedScreen;
	private View toolbarView;

	private ViewGroup drawerCommon, drawerWide;

	private PullableListView listView;
	private View progressView;
	private View errorView;
	private TextView errorText;
	private boolean wideMode;

	private ReadUpdateTask readUpdateTask;

	private static final String LOCKER_HANDLE = "handle";
	private static final String LOCKER_DRAWER = "drawer";
	private static final String LOCKER_SEARCH = "search";
	private static final String LOCKER_PULL = "pull";

	private final ActionMenuConfigurator actionMenuConfigurator = new ActionMenuConfigurator();
	private final ClickableToast.Holder clickableToastHolder = new ClickableToast.Holder(this);

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		currentPreferences = Preferences.getCurrent();
		if (C.API_LOLLIPOP) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
		}
		ResourceUtils.applyPreferredTheme(this);
		expandedScreen = new ExpandedScreen(this, Preferences.isExpandedScreen());
		super.onCreate(savedInstanceState);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		float density = ResourceUtils.obtainDensity(this);
		setContentView(R.layout.activity_main);
		ClickableToast.register(clickableToastHolder);
		FavoritesStorage.getInstance().getObservable().register(this);
		watcherServiceClient.bind(this);
		pageManager = new PageManager();
		actionIconSet = new ActionIconSet(this);
		progressView = findViewById(R.id.progress);
		errorView = findViewById(R.id.error);
		errorText = (TextView) findViewById(R.id.error_text);
		listView = (PullableListView) findViewById(android.R.id.list);
		registerForContextMenu(listView);
		drawerCommon = (ViewGroup) findViewById(R.id.drawer_common);
		drawerWide = (ViewGroup) findViewById(R.id.drawer_wide);
		TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.styleDrawerSpecial});
		int drawerResId = typedArray.getResourceId(0, 0);
		typedArray.recycle();
		ContextThemeWrapper styledContext = drawerResId != 0 ? new ContextThemeWrapper(this, drawerResId) : this;
		int drawerBackground = ResourceUtils.getColor(styledContext, R.attr.backgroundDrawer);
		drawerCommon.setBackgroundColor(drawerBackground);
		drawerWide.setBackgroundColor(drawerBackground);
		drawerListView = new SortableListView(styledContext, this);
		drawerListView.setId(android.R.id.tabcontent);
		drawerListView.setOnSortingStateChangedListener(this);
		drawerForm = new DrawerForm(styledContext, this, this, watcherServiceClient);
		drawerForm.bind(drawerListView);
		drawerParent = new FrameLayout(this);
		drawerParent.addView(drawerListView);
		drawerCommon.addView(drawerParent);
		uiManager = new UiManager(this, this, expandedScreen);
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (C.API_LOLLIPOP) {
			FrameLayout foreground = new FrameLayout(this);
			drawerLayout.addView(foreground, drawerLayout.indexOfChild(drawerCommon), new DrawerLayout.LayoutParams
					(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT));
			getLayoutInflater().inflate(R.layout.widget_toolbar, foreground);
			Toolbar toolbar = (Toolbar) foreground.findViewById(R.id.toolbar);
			setActionBar(toolbar);
			toolbarView = toolbar;
			expandedScreen.setToolbar(toolbar, foreground);
		} else {
			getActionBar().setIcon(R.drawable.ic_logo); // Show white logo on search
		}
		drawerToggle = new DrawerToggle(this, drawerLayout);
		if (C.API_LOLLIPOP) {
			drawerCommon.setElevation(6f * density);
			drawerWide.setElevation(4f * density);
		} else {
			drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);
		}
		drawerLayout.addDrawerListener(drawerToggle);
		drawerLayout.addDrawerListener(drawerForm);
		if (toolbarView == null) {
			drawerLayout.addDrawerListener(new ExpandedScreenDrawerLocker());
		}
		ViewUtils.applyToolbarStyle(this, toolbarView);
		if (Preferences.isActiveScrollbar()) {
			listView.setFastScrollEnabled(true);
			if (!C.API_LOLLIPOP) {
				ListViewUtils.colorizeListThumb4(listView);
			}
		}
		listView.setOnItemClickListener(this);
		listView.setOnItemLongClickListener(this);
		listView.getWrapper().setOnPullListener(this);
		listView.getWrapper().setPullStateListener(this);
		listView.setClipToPadding(false);
		ScrollListenerComposite scrollListenerComposite = new ScrollListenerComposite();
		listView.setOnScrollListener(scrollListenerComposite);
		scrollListenerComposite.add(new BusyScrollListener(this));
		updateWideConfiguration(true);
		expandedScreen.setDrawerOverToolbarEnabled(!wideMode);
		expandedScreen.setContentListView(listView, scrollListenerComposite);
		expandedScreen.setDrawerListView(drawerParent, drawerListView, drawerForm.getHeaderView());
		expandedScreen.addAdditionalView(progressView, true);
		expandedScreen.addAdditionalView(errorView, true);
		expandedScreen.finishInitialization();
		LocalBroadcastManager.getInstance(this).registerReceiver(newPostReceiver,
				new IntentFilter(C.ACTION_POST_SENT));
		if (savedInstanceState == null) {
			savedInstanceState = pageManager.readFromStorage();
		}
		PageHolder savedCurrentPage = pageManager.restore(savedInstanceState);
		if (savedCurrentPage != null) {
			handleData(savedCurrentPage, false);
		} else {
			handleIntent(getIntent(), false);
		}
		if (savedInstanceState == null) {
			startUpdateTask();
			int drawerInitialPosition = Preferences.getDrawerInitialPosition();
			if (drawerInitialPosition != Preferences.DRAWER_INITIAL_POSITION_CLOSED) {
				if (!wideMode) {
					drawerLayout.post(() -> drawerLayout.openDrawer(Gravity.START));
				}
				if (drawerInitialPosition == Preferences.DRAWER_INITIAL_POSITION_FORUMS) {
					drawerForm.setChanSelectMode(true);
				}
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (intent.getBooleanExtra(C.EXTRA_LAUNCHER, false)) {
			return;
		}
		handleIntent(intent, intent.getBooleanExtra(C.EXTRA_ANIMATED_TRANSITION, false));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		writePagesState(outState);
	}

	private void writePagesState(Bundle outState) {
		requestStoreExtraAndPosition();
		pageManager.save(outState);
	}

	@Override
	public void navigateBoardsOrThreads(String chanName, String boardName, boolean navigateTop, boolean fromCache) {
		handleIntentData(chanName, boardName, null, null, null, null, navigateTop, fromCache, true);
	}

	@Override
	public void navigatePosts(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache) {
		handleIntentData(chanName, boardName, threadNumber, postNumber, threadTitle, null,
				false, fromCache, true);
	}

	@Override
	public void navigateSearch(String chanName, String boardName, String searchQuery) {
		handleIntentData(chanName, boardName, null, null, null, searchQuery, false, false, true);
	}

	@Override
	public void navigateArchive(String chanName, String boardName) {
		performNavigation(PageHolder.Content.ARCHIVE, chanName, boardName, null, null, null, null, false, true);
	}

	@Override
	public void navigateTarget(String chanName, ChanLocator.NavigationData data, boolean fromCache) {
		switch (data.target) {
			case ChanLocator.NavigationData.TARGET_THREADS: {
				navigateBoardsOrThreads(chanName, data.boardName, false, fromCache);
				break;
			}
			case ChanLocator.NavigationData.TARGET_POSTS: {
				navigatePosts(chanName, data.boardName, data.threadNumber, data.postNumber, null, fromCache);
				break;
			}
			case ChanLocator.NavigationData.TARGET_SEARCH: {
				navigateSearch(chanName, data.boardName, data.searchQuery);
				break;
			}
			default: {
				throw new UnsupportedOperationException();
			}
		}
	}

	@Override
	public void navigatePosting(String chanName, String boardName, String threadNumber, Replyable.ReplyData... data) {
		Intent intent = new Intent(getApplicationContext(), PostingActivity.class);
		intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
		intent.putExtra(C.EXTRA_BOARD_NAME, boardName);
		intent.putExtra(C.EXTRA_THREAD_NUMBER, threadNumber);
		intent.putExtra(C.EXTRA_REPLY_DATA, data);
		startActivity(intent);
	}

	private void handleIntent(Intent intent, boolean animated) {
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
			String threadTitle, String searchQuery, boolean navigateTop, boolean fromCache, boolean animated) {
		PageHolder pageHolder = pageManager.getCurrentPage();
		String oldChanName = pageHolder != null ? pageHolder.chanName : null;
		if (chanName == null) return; // Void intent
		if (navigateTop) {
			pageManager.clearStack();
		}
		boolean forceBoardPage = false;
		if (pageManager.isSingleBoardMode(chanName)) {
			boardName = pageManager.getSingleBoardName(chanName);
			forceBoardPage = true;
		}
		if (boardName != null || threadNumber != null || forceBoardPage) {
			if (navigateTop && threadNumber == null && searchQuery == null) {
				fromCache |= pageManager.get(chanName, boardName, threadNumber, PageHolder.Content.THREADS) != null;
			}
			PageHolder.Content content = searchQuery != null ? PageHolder.Content.SEARCH
					: threadNumber == null ? PageHolder.Content.THREADS : PageHolder.Content.POSTS;
			performNavigation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
					searchQuery, fromCache, animated);
		} else if (pageManager.getStackSize(chanName) == 0 || !chanName.equals(oldChanName)) {
			performNavigation(PageHolder.Content.ALL_BOARDS, chanName, null, null, null, null, null,
					false, animated);
		}
	}

	private Runnable queuedHandler;
	private final Handler handler = new Handler();
	private boolean allowScaleAnimation = false;

	private void handleData(PageHolder pageHolder, boolean animated) {
		performNavigation(pageHolder.content, pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber, null,
				pageHolder.threadTitle, pageHolder.searchQuery, true, animated);
	}

	private void performNavigation(final PageHolder.Content content, final String chanName, final String boardName,
			final String threadNumber, final String postNumber, final String threadTitle, final String searchQuery,
			final boolean fromCache, boolean animated) {
		PageHolder pageHolder = pageManager.getCurrentPage();
		if (pageHolder != null && pageHolder.is(chanName, boardName, threadNumber, content) && searchQuery == null) {
			// Page could be deleted from stack during clearStack (when home button pressed, for example)
			pageManager.moveCurrentPageTop();
			page.updatePageConfiguration(postNumber, threadTitle);
			drawerForm.invalidateItems(true, false);
			invalidateHomeUpState();
			return;
		}
		switchView(ListPage.ViewType.LIST, null);
		listView.getWrapper().cancelBusyState();
		listView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		ClickableToast.cancel(this);
		requestStoreExtraAndPosition();
		cleanupPage();
		handler.removeCallbacks(queuedHandler);
		setActionBarLocked(LOCKER_HANDLE, true);
		if (animated) {
			queuedHandler = () -> {
				queuedHandler = null;
				if (listView.getAnimation() != null) {
					listView.getAnimation().cancel();
				}
				listView.setAlpha(1f);
				handleDataAfterAnimation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
						searchQuery, fromCache, true);
			};
			handler.postDelayed(queuedHandler, 300);
			ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(listView, View.ALPHA, 1f, 0f);
			alphaAnimator.setupStartValues();
			alphaAnimator.setStartDelay(150);
			alphaAnimator.setDuration(150);
			startListAnimator(alphaAnimator);
		} else {
			handleDataAfterAnimation(content, chanName, boardName, threadNumber, postNumber, threadTitle,
					searchQuery, fromCache, false);
		}
	}

	private void requestStoreExtraAndPosition() {
		if (page != null) {
			if (listView.getChildCount() > 0){
				pageManager.getCurrentPage().position = ListPosition.obtain(listView);
			}
			page.onRequestStoreExtra();
		}
	}

	private void handleDataAfterAnimation(PageHolder.Content content, String chanName, String boardName,
			String threadNumber, String postNumber, String threadTitle, String searchQuery,
			boolean fromCache, boolean animated) {
		clearListAnimator();
		allowScaleAnimation = animated;
		setActionBarLocked(LOCKER_HANDLE, false);
		watcherServiceClient.updateConfiguration(chanName);
		drawerForm.updateConfiguration(chanName);
		page = pageManager.newPage(content);
		sendPrepareMenuToPage = false; // Will be changed in onCreateOptionsMenu
		PageHolder pageHolder = null;
		switch (content) {
			case THREADS: {
				pageHolder = pageManager.add(content, chanName, boardName, null, null, null)
						.setInitialThreadsData(fromCache);
				break;
			}
			case POSTS: {
				pageHolder = pageManager.add(content, chanName, boardName, threadNumber, threadTitle, null)
						.setInitialPostsData(fromCache, postNumber);
				break;
			}
			case SEARCH: {
				pageHolder = pageManager.add(content, chanName, boardName, null, null, searchQuery)
						.setInitialSearchData(fromCache);
				break;
			}
			case ARCHIVE:
			case ALL_BOARDS:
			case USER_BOARDS:
			case HISTORY: {
				pageHolder = pageManager.add(content, chanName, boardName, null, null, null);
				break;
			}
		}
		if (pageHolder == null) {
			throw new RuntimeException();
		}
		uiManager.view().resetPages();
		page.init(this, this, pageHolder, listView, uiManager, actionIconSet);
		if (!wideMode && !drawerLayout.isDrawerOpen(Gravity.START)) {
			drawerListView.setSelection(0);
		}
		setSearchMode(false);
		invalidateOptionsMenu();
		invalidateHomeUpState();
		notifyTitleChanged();
		allowScaleAnimation = true;
	}

	private void cleanupPage() {
		if (page != null) {
			PostingService.clearNewThreadData();
			page.cleanup();
			page = null;
		}
	}

	private void invalidateHomeUpState() {
		if (searchMode) {
			drawerToggle.setDrawerIndicatorMode(DrawerToggle.MODE_UP);
		} else if (page != null) {
			boolean displayUp = false;
			PageHolder pageHolder = pageManager.getCurrentPage();
			switch (pageHolder.content) {
				case THREADS: {
					displayUp = pageManager.getStackSize() > 1;
					break;
				}
				case POSTS:
				case SEARCH:
				case ARCHIVE: {
					displayUp = true;
					break;
				}
				case ALL_BOARDS:
				case USER_BOARDS:
				case HISTORY: {
					displayUp = pageHolder.boardName != null || pageManager.getStackSize() > 1;
					break;
				}
			}
			drawerToggle.setDrawerIndicatorMode(displayUp ? DrawerToggle.MODE_UP : wideMode
					? DrawerToggle.MODE_DISABLED : DrawerToggle.MODE_DRAWER);
		} else {
			drawerToggle.setDrawerIndicatorMode(DrawerToggle.MODE_DISABLED);
		}
	}

	private void updateWideConfiguration(boolean forced) {
		Configuration configuration = getResources().getConfiguration();
		boolean newWideMode = ViewUtils.isDrawerLockable(configuration) && Preferences.isDrawerLocked();
		if (wideMode != newWideMode || forced) {
			wideMode = newWideMode;
			if (!forced) {
				expandedScreen.setDrawerOverToolbarEnabled(!wideMode);
			}
			drawerLayout.setDrawerLockMode(wideMode ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
					: DrawerLayout.LOCK_MODE_UNLOCKED);
			drawerWide.setVisibility(wideMode ? View.VISIBLE : View.GONE);
			ViewUtils.removeFromParent(drawerParent);
			(wideMode ? drawerWide : drawerCommon).addView(drawerParent);
			invalidateHomeUpState();
		}
		float density = ResourceUtils.obtainDensity(this);
		int actionBarSize = getResources().getDimensionPixelSize(ResourceUtils.getResourceId(this,
				android.R.attr.actionBarSize, 0));
		int drawerWidth = Math.min((int) (configuration.screenWidthDp * density + 0.5f) - actionBarSize,
				(int) (320 * density + 0.5f));
		drawerCommon.getLayoutParams().width = drawerWide.getLayoutParams().width = drawerWidth;
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView) {
		if (page != null) {
			page.setListViewBusy(isBusy, listView);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		Preferences.Holder newPreferences = Preferences.getCurrent();
		if (currentPreferences.isNeedRestartActivity(newPreferences)) {
			// Recreate after onResume
			postRecreate();
			return;
		} else if (currentPreferences.isNeedRefreshList(newPreferences)) {
			((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
		}
		drawerForm.invalidateItems(true, true);
		currentPreferences = newPreferences;
		updateWideConfiguration(false);
	}

	@Override
	protected void onResume() {
		super.onResume();
		expandedScreen.onResume();
		drawerForm.performResume();
		watcherServiceClient.start();
		if (page != null) {
			page.resume();
		}
		clickableToastHolder.onResume();
		if (!isRecreateCalled()) {
			showRestartDialogIfNeeded();
		}
		ChanManager.getInstance().getInstallationObservable().register(installationCallback);
		ForegroundManager.register(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		watcherServiceClient.stop();
		if (page != null) {
			page.pause();
		}
		clickableToastHolder.onPause();
		ForegroundManager.unregister(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		ChanManager.getInstance().getInstallationObservable().unregister(installationCallback);
	}

	@Override
	protected void onFinish() {
		super.onFinish();
		if (readUpdateTask != null) {
			readUpdateTask.cancel();
			readUpdateTask = null;
		}
		uiManager.onFinish();
		watcherServiceClient.unbind(this);
		ClickableToast.unregister(clickableToastHolder);
		FavoritesStorage.getInstance().getObservable().unregister(this);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(newPostReceiver);
		cleanupPage();
		for (String chanName : ChanManager.getInstance().getAvailableChanNames()) {
			ChanConfiguration.get(chanName).commit();
		}
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(C.NOTIFICATION_TAG_UPDATE, 0);
		FavoritesStorage.getInstance().await(true);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		ViewUtils.applyToolbarStyle(this, toolbarView);
		drawerToggle.onConfigurationChanged();
		updateWideConfiguration(false);
		expandedScreen.onConfigurationChanged(newConfig);
		updateOptionsMenu(false);
	}

	@Override
	public boolean onSearchRequested() {
		return setSearchMode(true) || searchMode;
	}

	private long backPressed = 0;

	@Override
	public void onBackPressed() {
		if (!wideMode && drawerLayout.isDrawerOpen(Gravity.START)) {
			drawerLayout.closeDrawers();
		} else if (page != null) {
			if (setSearchMode(false)) {
				return;
			}
			PageHolder previousPageHolder = pageManager.getTargetPreviousPage(true);
			if (previousPageHolder != null) {
				pageManager.removeCurrentPageFromStack();
				handleData(previousPageHolder, true);
			} else {
				if (System.currentTimeMillis() - backPressed > 2000) {
					ClickableToast.show(this, R.string.message_press_again_to_exit);
					backPressed = System.currentTimeMillis();
				} else {
					super.onBackPressed();
				}
			}
		}
	}

	private final Runnable installationCallback = () -> showRestartDialogIfNeeded();
	private boolean mayShowRestartDialog = true;

	private void showRestartDialogIfNeeded() {
		if (ChanManager.getInstance().checkNewExtensionsInstalled() && mayShowRestartDialog) {
			mayShowRestartDialog = false;
			AlertDialog dialog = new AlertDialog.Builder(this).setMessage(R.string.message_packages_installed)
					.setPositiveButton(R.string.action_restart, (d, which) -> {
				Bundle outState = new Bundle();
				writePagesState(outState);
				pageManager.writeToStorage(outState);
				NavigationUtils.restartApplication(this);
			}).setNegativeButton(android.R.string.cancel, null).create();
			dialog.setOnDismissListener(d -> mayShowRestartDialog = true);
			dialog.setCanceledOnTouchOutside(false);
			dialog.show();
			uiManager.dialog().notifySwitchBackground();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (page != null) {
			page.onItemClick(view, position, id);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (page != null) {
			return page.onItemLongClick(view, position, id);
		}
		return false;
	}

	@Override
	public void onActionModeStarted(ActionMode mode) {
		super.onActionModeStarted(mode);
		expandedScreen.setActionModeState(true);
	}

	@Override
	public void onActionModeFinished(ActionMode mode) {
		super.onActionModeFinished(mode);
		expandedScreen.setActionModeState(false);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		clickableToastHolder.onWindowFocusChanged(hasFocus);
	}

	private boolean searchMode = false;

	private boolean setSearchMode(boolean search) {
		if (searchMode != search) {
			searchMode = search;
			if (search) {
				if (currentMenu != null) {
					MenuItem menuItem = currentMenu.findItem(ListPage.OPTIONS_MENU_SEARCH);
					if (menuItem != null) {
						getSearchView(true).setQueryHint(menuItem.getTitle());
					}
				}
			}
			if (page != null) {
				if (search) {
					page.onSearchQueryChange(getSearchView(true).getQuery().toString());
				} else {
					page.onSearchQueryChange("");
					page.onSearchCancel();
				}
			}
			setActionBarLocked(LOCKER_SEARCH, search);
			invalidateOptionsMenu();
			invalidateHomeUpState();
			return true;
		}
		return false;
	}

	private CustomSearchView searchView;

	public CustomSearchView getSearchView(boolean createIfNull) {
		if (searchView == null && createIfNull) {
			searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(this,
					R.style.Theme_Special_White) : getActionBar().getThemedContext());
			searchView.setOnQueryTextListener(new CustomSearchView.OnQueryTextListener() {
				@Override
				public boolean onQueryTextSubmit(String query) {
					return page != null && page.onSearchSubmit(query);
				}

				@Override
				public boolean onQueryTextChange(String newText) {
					if (page != null) {
						page.onSearchQueryChange(newText);
					}
					return true;
				}
			});
		}
		return searchView;
	}

	private Menu currentMenu;
	private boolean sendPrepareMenuToPage = false;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (searchMode) {
			currentMenu = null;
			menu.add(0, ListPage.OPTIONS_MENU_SEARCH_VIEW, 0, "").setActionView(getSearchView(true))
					.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		} else {
			currentMenu = menu;
			if (page != null) {
				page.onCreateOptionsMenu(menu);
				sendPrepareMenuToPage = true;
			}
			MenuItem appearanceOptionsItem = menu.findItem(ListPage.OPTIONS_MENU_APPEARANCE);
			if (appearanceOptionsItem != null) {
				Menu appearanceOptionsMenu = appearanceOptionsItem.getSubMenu();
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_CHANGE_THEME, 0,
						R.string.action_change_theme);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_EXPANDED_SCREEN, 0,
						R.string.action_expanded_screen).setCheckable(true);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_SPOILERS, 0,
						R.string.action_spoilers).setCheckable(true);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_MY_POSTS, 0,
						R.string.action_my_posts).setCheckable(true);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_DRAWER, 0,
						R.string.action_lock_drawer).setCheckable(true);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_THREADS_GRID, 0,
						R.string.action_threads_grid).setCheckable(true);
				appearanceOptionsMenu.add(0, ListPage.APPEARANCE_MENU_SFW_MODE, 0,
						R.string.action_sfw_mode).setCheckable(true);
			}
			actionMenuConfigurator.onAfterCreateOptionsMenu(menu);
		}
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (searchMode) {
			return true;
		}
		if (page != null && sendPrepareMenuToPage) {
			page.onPrepareOptionsMenu(menu);
		}
		MenuItem appearanceOptionsItem = menu.findItem(ListPage.OPTIONS_MENU_APPEARANCE);
		if (appearanceOptionsItem != null) {
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
		actionMenuConfigurator.onAfterPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		if (page != null) {
			if (item.getItemId() == ListPage.OPTIONS_MENU_SEARCH) {
				setSearchMode(true);
				return true;
			}
			if (page.onOptionsItemSelected(item)) {
				return true;
			}
			switch (item.getItemId()) {
				case android.R.id.home: {
					if (setSearchMode(false)) {
						return true;
					}
					drawerLayout.closeDrawers();
					PageHolder pageHolder = pageManager.getCurrentPage();
					String newChanName = pageHolder.chanName;
					String newBoardName = pageHolder.boardName;
					if (pageHolder.content == PageHolder.Content.THREADS) {
						// Up button must navigate to main page in threads list
						newBoardName = Preferences.getDefaultBoardName(pageHolder.chanName);
						if (Preferences.isMergeChans() && StringUtils.equals(pageHolder.boardName, newBoardName)) {
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
				case ListPage.APPEARANCE_MENU_SFW_MODE: {
					try {
						switch (item.getItemId()) {
							case ListPage.APPEARANCE_MENU_CHANGE_THEME: {
								showThemeDialog();
								return true;
							}
							case ListPage.APPEARANCE_MENU_EXPANDED_SCREEN: {
								Preferences.setExpandedScreen(!item.isChecked());
								recreate();
								return true;
							}
							case ListPage.APPEARANCE_MENU_SPOILERS: {
								Preferences.setShowSpoilers(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_MY_POSTS: {
								Preferences.setShowMyPosts(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_DRAWER: {
								Preferences.setDrawerLocked(!item.isChecked());
								updateWideConfiguration(false);
								return true;
							}
							case ListPage.APPEARANCE_MENU_THREADS_GRID: {
								Preferences.setThreadsGridMode(!item.isChecked());
								return true;
							}
							case ListPage.APPEARANCE_MENU_SFW_MODE: {
								Preferences.setSfwMode(!item.isChecked());
								return true;
							}
						}
					} finally {
						if (page != null) {
							page.onAppearanceOptionChanged(item.getItemId());
						}
					}
				}
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if (page != null) {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			page.onCreateContextMenu(menu, v, info.position, info.targetView);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (page != null) {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
			return page.onContextItemSelected(item, info.position, info.targetView);
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showThemeDialog() {
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
		View.OnClickListener listener = v -> {
			int index = (int) v.getTag();
			if (index != checkedItem) {
				Preferences.setTheme(Preferences.VALUES_THEME[index]);
				recreate();
			}
			dialog.dismiss();
		};
		int circleSize = (int) (56f * density);
		int itemPadding = (int) (12f * density);
		LinearLayout inner = null;
		for (int i = 0; i < Preferences.ENTRIES_THEME.length; i++) {
			if (i % 3 == 0) {
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
			if (C.API_LOLLIPOP) {
				view.setElevation(6f * density);
			}
			layout.addView(view, circleSize, circleSize);
			TextView textView = new TextView(this, null, android.R.attr.textAppearanceListItem);
			textView.setSingleLine(true);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			textView.setText(Preferences.ENTRIES_THEME[i]);
			if (C.API_LOLLIPOP) {
				textView.setAllCaps(true);
				textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
			} else {
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
			}
			textView.setGravity(Gravity.CENTER_HORIZONTAL);
			textView.setPadding(0, (int) (8f * density), 0, 0);
			layout.addView(textView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			if (i + 1 == Preferences.ENTRIES_THEME.length && Preferences.ENTRIES_THEME.length % 3 != 0) {
				if (Preferences.ENTRIES_THEME.length % 3 == 1) {
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
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		if (page != null) {
			page.onListPulled(wrapper, side);
		}
	}

	@Override
	public void onPullStateChanged(PullableWrapper wrapper, boolean busy) {
		setActionBarLocked(LOCKER_PULL, busy);
	}

	@Override
	public void onSortingStateChanged(SortableListView listView, boolean sorting) {
		if (!wideMode) {
			drawerLayout.setDrawerLockMode(sorting ? DrawerLayout.LOCK_MODE_LOCKED_OPEN
					: DrawerLayout.LOCK_MODE_UNLOCKED);
		}
	}

	@Override
	public void onSelectChan(String chanName) {
		if (page != null) {
			if (!pageManager.getCurrentPage().chanName.equals(chanName)) {
				if (!Preferences.isMergeChans()) {
					// Find chan page and open it. Open root page if nothing was found.
					PageHolder pageHolder = pageManager.getLastPage(chanName);
					if (pageHolder != null) {
						handleData(pageHolder, true);
					} else {
						navigateBoardsOrThreads(chanName, Preferences.getDefaultBoardName(chanName), false, false);
					}
				} else {
					// Open root page. If page is already opened, load it from cache.
					boolean fromCache = false;
					String boardName = Preferences.getDefaultBoardName(chanName);
					for (PageHolder pageHolder : pageManager.getPages()) {
						if (pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS)) {
							fromCache = true;
							break;
						}
					}
					navigateBoardsOrThreads(chanName, boardName, false, fromCache);
				}
				drawerForm.updateConfiguration(chanName);
				drawerForm.invalidateItems(true, false);
			}
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public void onSelectBoard(String chanName, String boardName, boolean fromCache) {
		if (page != null) {
			PageHolder pageHolder = pageManager.getCurrentPage();
			if (pageManager.isSingleBoardMode(chanName)) {
				boardName = pageManager.getSingleBoardName(chanName);
			}
			if (!pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS)) {
				navigateBoardsOrThreads(chanName, boardName, false, fromCache);
			}
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public boolean onSelectThread(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache) {
		if (page != null) {
			PageHolder pageHolder = pageManager.getCurrentPage();
			if (pageManager.isSingleBoardMode(chanName)) {
				boardName = pageManager.getSingleBoardName(chanName);
			} else if (boardName == null) {
				switch (pageHolder.content) {
					case ALL_BOARDS:
					case USER_BOARDS:
					case HISTORY: {
						return false;
					}
					default: {
						break;
					}
				}
				boardName = pageHolder.boardName;
			}
			if (!pageHolder.is(chanName, boardName, threadNumber, PageHolder.Content.POSTS)) {
				navigatePosts(chanName, boardName, threadNumber, postNumber, threadTitle, fromCache);
			}
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
		return true;
	}

	private boolean isPageThreadsPosts(PageHolder pageHolder, String chanName, String boardName, String threadNumber) {
		if (threadNumber != null) {
			return pageHolder.is(chanName, boardName, threadNumber, PageHolder.Content.POSTS);
		} else {
			return pageHolder.is(chanName, boardName, null, PageHolder.Content.THREADS);
		}
	}

	@Override
	public boolean onClosePage(String chanName, String boardName, String threadNumber) {
		if (page != null) {
			PageHolder pageHolder = pageManager.getCurrentPage();
			if (pageHolder != null && isPageThreadsPosts(pageHolder, chanName, boardName, threadNumber)) {
				PageHolder previousPageHolder = pageManager.getTargetPreviousPage(false);
				pageManager.removeCurrentPage();
				if (previousPageHolder != null) {
					handleData(previousPageHolder, true);
				} else {
					if (pageManager.isSingleBoardMode(chanName)) {
						performNavigation(PageHolder.Content.THREADS, chanName,
								pageManager.getSingleBoardName(chanName), null, null, null, null, true, true);
					} else {
						performNavigation(PageHolder.Content.ALL_BOARDS, chanName, null, null, null, null, null,
								true, true);
					}
				}
				return true;
			} else {
				ArrayList<PageHolder> pageHolders = pageManager.getPages();
				for (int i = 0; i < pageHolders.size(); i++) {
					if (isPageThreadsPosts(pageHolders.get(i), chanName, boardName, threadNumber)) {
						pageHolders.remove(i);
						break;
					}
				}
				drawerForm.invalidateItems(true, false);
				// Replace arrow with bars, if current threads page becomes root
				if (pageHolder.content == PageHolder.Content.THREADS && pageManager.getStackSize() <= 1) {
					if (pageManager.isSingleBoardMode() || pageHolder.boardName
							.equals(Preferences.getDefaultBoardName(pageHolder.chanName))) {
						invalidateHomeUpState();
					}
				}
			}
		}
		return false;
	}

	@Override
	public void onCloseAllPages() {
		if (page != null) {
			String chanName = pageManager.getCurrentPage().chanName;
			String boardName = Preferences.getDefaultBoardName(chanName);
			PageHolder targetPageHolder;
			if (!pageManager.isSingleBoardMode() && boardName == null) {
				targetPageHolder = pageManager.get(chanName, null, null, PageHolder.Content.ALL_BOARDS);
			} else if (pageManager.isSingleBoardMode()) {
				targetPageHolder = pageManager.get(chanName, pageManager.getSingleBoardName(chanName),
						null, PageHolder.Content.THREADS);
			} else {
				targetPageHolder = pageManager.get(chanName, boardName, null, PageHolder.Content.THREADS);
			}
			pageManager.closeAllExcept(targetPageHolder);
			if (pageManager.getCurrentPage() == null) {
				cleanupPage();
			}
			drawerForm.invalidateItems(true, false);
			navigateBoardsOrThreads(chanName, boardName, false, targetPageHolder != null);
		}
	}

	@Override
	public int onEnterNumber(int number) {
		int result = 0;
		if (page != null) {
			result = page.onDrawerNumberEntered(number);
		}
		if (!wideMode && FlagUtils.get(result, DrawerForm.RESULT_SUCCESS)) {
			drawerLayout.closeDrawers();
		}
		return result;
	}

	private final Runnable preferencesRunnable = () -> startActivity(new Intent(this, PreferencesActivity.class));

	@Override
	public void onSelectDrawerMenuItem(int item) {
		PageHolder.Content content = null;
		switch (item) {
			case DrawerForm.MENU_ITEM_ALL_BOARDS: {
				content = PageHolder.Content.ALL_BOARDS;
				break;
			}
			case DrawerForm.MENU_ITEM_USER_BOARDS: {
				content = PageHolder.Content.USER_BOARDS;
				break;
			}
			case DrawerForm.MENU_ITEM_HISTORY: {
				content = PageHolder.Content.HISTORY;
				break;
			}
			case DrawerForm.MENU_ITEM_PREFERENCES: {
				if (wideMode) {
					preferencesRunnable.run();
				} else {
					listView.postDelayed(preferencesRunnable, 200);
				}
				break;
			}
		}
		if (content != null) {
			if (page != null) {
				PageHolder pageHolder = pageManager.getCurrentPage();
				if (pageHolder.content != content) {
					for (PageHolder itPageHolder : pageManager.getPages()) {
						// Reset list position
						if (itPageHolder.content == content) {
							itPageHolder.position = null;
						}
					}
					performNavigation(content, pageHolder.chanName, pageHolder.boardName, null, null, null, null,
							false, true);
				}
			}
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public ArrayList<PageHolder> getDrawerPageHolders() {
		return pageManager.getPages();
	}

	private final BroadcastReceiver newPostReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (page != null) {
				page.handleNewPostDatasNow();
			}
		}
	};

	private void startUpdateTask() {
		if (!Preferences.isCheckUpdatesOnStart()) {
			return;
		}
		long lastUpdateCheck = Preferences.getLastUpdateCheck();
		if (System.currentTimeMillis() - lastUpdateCheck < 12 * 60 * 60 * 1000) return; // 12 hours
		readUpdateTask = new ReadUpdateTask(this, this);
		readUpdateTask.executeOnExecutor(ReadUpdateTask.THREAD_POOL_EXECUTOR);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap) {
		readUpdateTask = null;
		if (updateDataMap == null) {
			return;
		}
		Preferences.setLastUpdateCheck(System.currentTimeMillis());
		int count = PreferencesActivity.checkNewVersions(updateDataMap);
		if (count <= 0) {
			return;
		}
		Notification.Builder builder = new Notification.Builder(this);
		builder.setSmallIcon(R.drawable.ic_new_releases_white_24dp);
		String text = getString(R.string.text_updates_available_format, count);
		if (C.API_LOLLIPOP) {
			builder.setColor(ResourceUtils.getColor(this, android.R.attr.colorAccent));
			builder.setPriority(Notification.PRIORITY_HIGH);
			builder.setVibrate(new long[0]);
		} else {
			builder.setTicker(text);
		}
		builder.setContentTitle(getString(R.string.text_app_name_update, getString(R.string.const_app_name)));
		builder.setContentText(text);
		builder.setContentIntent(PendingIntent.getActivity(this, 0, PreferencesActivity.createUpdateIntent
				(this, updateDataMap).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT));
		builder.setAutoCancel(true);
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.notify(C.NOTIFICATION_TAG_UPDATE, 0, builder.build());
	}

	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action) {
		switch (action) {
			case FavoritesStorage.ACTION_ADD:
			case FavoritesStorage.ACTION_REMOVE:
			case FavoritesStorage.ACTION_MODIFY_TITLE: {
				drawerForm.invalidateItems(false, true);
				break;
			}
		}
	}

	@Override
	public void onWatcherUpdate(WatcherService.WatcherItem watcherItem, WatcherService.State state) {
		drawerForm.onWatcherUpdate(watcherItem, state);
	}

	@Override
	public void notifyTitleChanged() {
		drawerForm.invalidateItems(true, false);
		if (page != null) {
			setTitle(page.obtainTitle());
		}
	}

	@Override
	public void updateOptionsMenu(boolean recreate) {
		if (recreate || currentMenu == null) {
			invalidateOptionsMenu();
		} else {
			onPrepareOptionsMenu(currentMenu);
		}
	}

	@Override
	public void setCustomSearchView(View view) {
		CustomSearchView searchView = getSearchView(view != null);
		if (searchView != null) {
			searchView.setCustomView(view);
		}
	}

	@Override
	public void switchView(ListPage.ViewType viewType, String message) {
		progressView.setVisibility(viewType == ListPage.ViewType.PROGRESS ? View.VISIBLE : View.GONE);
		errorView.setVisibility(viewType == ListPage.ViewType.ERROR ? View.VISIBLE : View.GONE);
		if (viewType == ListPage.ViewType.ERROR) {
			errorText.setText(message != null ? message : getString(R.string.message_unknown_error));
		}
	}

	private final Runnable showScaleRunnable = () -> showScaleAnimation(false);

	@Override
	public void showScaleAnimation() {
		showScaleAnimation(true);
	}

	private void showScaleAnimation(boolean post) {
		clearListAnimator();
		if (allowScaleAnimation) {
			if (post) {
				listView.setVisibility(View.INVISIBLE);
				handler.post(showScaleRunnable);
			} else {
				final float fromScale = 0.925f;
				ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(listView, View.ALPHA, 0f, 1f);
				ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(listView, View.SCALE_X, fromScale, 1f);
				ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(listView, View.SCALE_Y, fromScale, 1f);
				AnimatorSet animatorSet = new AnimatorSet();
				animatorSet.playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator);
				animatorSet.setDuration(100);
				startListAnimator(animatorSet);
			}
		}
	}

	private Animator currentListAnimator;

	private void clearListAnimator() {
		if (currentListAnimator != null) {
			currentListAnimator.cancel();
			currentListAnimator = null;
		}
		listView.setVisibility(View.VISIBLE);
	}

	private void startListAnimator(Animator animator) {
		clearListAnimator();
		currentListAnimator = animator;
		animator.start();
	}

	@Override
	public void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber) {
		PageHolder pageHolder = pageManager.getCurrentPage();
		if (pageHolder.isThreadsOrPosts()) {
			pageManager.removeCurrentPage();
			if (pageHolder.content == PageHolder.Content.POSTS) {
				FavoritesStorage.getInstance().move(pageHolder.chanName,
						pageHolder.boardName, pageHolder.threadNumber, boardName, threadNumber);
				CacheManager.getInstance().movePostsPage(pageHolder.chanName,
						pageHolder.boardName, pageHolder.threadNumber, boardName, threadNumber);
				DraftsStorage.getInstance().movePostDraft(pageHolder.chanName,
						pageHolder.boardName, pageHolder.threadNumber, boardName, threadNumber);
				drawerForm.invalidateItems(true, false);
			}
			if (threadNumber == null) {
				navigateBoardsOrThreads(chanName, boardName, false, false);
			} else {
				navigatePosts(chanName, boardName, threadNumber, postNumber, null, false);
			}
		}
	}

	private void setActionBarLocked(String locker, boolean locked) {
		if (locked) {
			expandedScreen.addLocker(locker);
		} else {
			expandedScreen.removeLocker(locker);
		}
	}

	private class ExpandedScreenDrawerLocker implements DrawerLayout.DrawerListener {
		@Override
		public void onDrawerSlide(View drawerView, float slideOffset) {}

		@Override
		public void onDrawerOpened(View drawerView) {
			setActionBarLocked(LOCKER_DRAWER, true);
		}

		@Override
		public void onDrawerClosed(View drawerView) {
			setActionBarLocked(LOCKER_DRAWER, false);
		}

		@Override
		public void onDrawerStateChanged(int newState) {}
	}
}