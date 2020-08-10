package com.mishiranu.dashchan.ui.navigator;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.ThemeChoiceDrawable;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.ui.navigator.entity.Page;
import com.mishiranu.dashchan.ui.navigator.entity.PageItem;
import com.mishiranu.dashchan.ui.navigator.entity.SavedPageItem;
import com.mishiranu.dashchan.ui.navigator.entity.StackItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.ui.posting.PostingActivity;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcatIterable;
import com.mishiranu.dashchan.util.DrawerToggle;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ExpandedScreen;
import com.mishiranu.dashchan.widget.SortableListView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;

public class NavigatorActivity extends StateActivity implements DrawerForm.Callback,
		SortableListView.OnStateChangedListener, FavoritesStorage.Observer, WatcherService.Client.Callback,
		UiManager.LocalNavigator, PageFragment.Callback, ReadUpdateTask.Callback {
	private static final String EXTRA_STACK_PAGE_ITEMS = "stackPageItems";
	private static final String EXTRA_PRESERVED_PAGE_ITEMS = "preservedPageItems";
	private static final String EXTRA_CURRENT_FRAGMENT = "currentFragment";
	private static final String EXTRA_CURRENT_PAGE_ITEM = "currentPageItem";

	private static final PageFragment REFERENCE_FRAGMENT = new PageFragment();

	private final ArrayList<SavedPageItem> stackPageItems = new ArrayList<>();
	private final ArrayList<SavedPageItem> preservedPageItems = new ArrayList<>();
	private PageItem currentPageItem;

	private UiManager uiManager;
	private RetainFragment retainFragment;
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
	private boolean wideMode;

	private ReadUpdateTask readUpdateTask;
	private Intent navigateIntentOnResume;

	private static final String LOCKER_DRAWER = "drawer";

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
		ExpandedScreen.Init expandedScreenInit = new ExpandedScreen.Init(this, Preferences.isExpandedScreen());
		super.onCreate(savedInstanceState);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		float density = ResourceUtils.obtainDensity(this);
		setContentView(R.layout.activity_main);
		ClickableToast.register(clickableToastHolder);
		FavoritesStorage.getInstance().getObservable().register(this);
		watcherServiceClient.bind(this);
		actionIconSet = new ActionIconSet(this);
		drawerCommon = findViewById(R.id.drawer_common);
		drawerWide = findViewById(R.id.drawer_wide);
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
		drawerLayout = findViewById(R.id.drawer_layout);
		FrameLayout drawerInterlayer = findViewById(R.id.drawer_interlayer);
		if (C.API_LOLLIPOP) {
			getLayoutInflater().inflate(R.layout.widget_toolbar, drawerInterlayer);
			Toolbar toolbar = drawerInterlayer.findViewById(R.id.toolbar);
			setActionBar(toolbar);
			toolbarView = toolbar;
		} else {
			// Show white logo on search
			getActionBar().setIcon(R.drawable.ic_logo);
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

		updateWideConfiguration(true);
		expandedScreen = new ExpandedScreen(expandedScreenInit, drawerLayout, toolbarView, drawerInterlayer,
				drawerParent, drawerListView, drawerForm.getHeaderView());
		expandedScreen.setDrawerOverToolbarEnabled(!wideMode);
		uiManager = new UiManager(this, this);
		ViewGroup contentFragment = findViewById(R.id.content_fragment);
		contentFragment.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
			@Override
			public void onChildViewAdded(View parent, View child) {
				expandedScreen.addContentView(child);
			}

			@Override
			public void onChildViewRemoved(View parent, View child) {
				expandedScreen.removeContentView(child);
			}
		});
		LocalBroadcastManager.getInstance(this).registerReceiver(newPostReceiver,
				new IntentFilter(C.ACTION_POST_SENT));
		if (savedInstanceState == null) {
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

		FragmentManager fragmentManager = getSupportFragmentManager();
		retainFragment = (RetainFragment) fragmentManager.findFragmentByTag(RetainFragment.class.getName());
		if (retainFragment == null) {
			retainFragment = new RetainFragment();
			fragmentManager.beginTransaction()
					.add(retainFragment, RetainFragment.class.getName())
					.commit();
		}

		Fragment currentFragmentFromSaved = null;
		if (savedInstanceState == null) {
			File file = getSavedPagesFile();
			if (file != null && file.exists()) {
				Parcel parcel = Parcel.obtain();
				FileInputStream input = null;
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				try {
					input = new FileInputStream(file);
					IOUtils.copyStream(input, output);
					byte[] data = output.toByteArray();
					parcel.unmarshall(data, 0, data.length);
					parcel.setDataPosition(0);
					Bundle bundle = new Bundle();
					bundle.setClassLoader(getClass().getClassLoader());
					bundle.readFromParcel(parcel);
					savedInstanceState = bundle;
				} catch (IOException e) {
					// Ignore exception
				} finally {
					IOUtils.close(input);
					parcel.recycle();
					file.delete();
				}
			}
			if (savedInstanceState != null) {
				currentFragmentFromSaved = savedInstanceState
						.<StackItem>getParcelable(EXTRA_CURRENT_FRAGMENT).create(null);
				if (currentFragmentFromSaved == null) {
					savedInstanceState = null;
				}
			}
		}

		if (savedInstanceState != null) {
			stackPageItems.addAll(savedInstanceState.getParcelableArrayList(EXTRA_STACK_PAGE_ITEMS));
			preservedPageItems.addAll(savedInstanceState.getParcelableArrayList(EXTRA_PRESERVED_PAGE_ITEMS));
			currentPageItem = savedInstanceState.getParcelable(EXTRA_CURRENT_PAGE_ITEM);
		}
		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
		while (iterator.hasNext()) {
			if (!chanNames.contains(getSavedPage(iterator.next()).chanName)) {
				iterator.remove();
			}
		}
		if (currentFragmentFromSaved != null) {
			if (currentFragmentFromSaved instanceof PageFragment &&
					!chanNames.contains(((PageFragment) currentFragmentFromSaved).getPage().chanName)) {
				currentFragmentFromSaved = null;
				currentPageItem = null;
			}
			if (currentFragmentFromSaved == null && !stackPageItems.isEmpty()) {
				Pair<PageFragment, PageItem> pair = stackPageItems.remove(stackPageItems.size() - 1).create();
				currentFragmentFromSaved = pair.first;
				currentPageItem = pair.second;
			}
			if (currentFragmentFromSaved != null) {
				fragmentManager.beginTransaction()
						.replace(R.id.content_fragment, currentFragmentFromSaved)
						.commit();
				updatePostFragmentConfiguration();
			} else {
				navigateIntent(getIntent(), false);
			}
		} else {
			Fragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment &&
					!chanNames.contains(((PageFragment) currentFragment).getPage().chanName)) {
				currentFragment = null;
				currentPageItem = null;
			}
			if (currentFragment == null) {
				navigateIntent(getIntent(), false);
				startUpdateTask();
			} else {
				updatePostFragmentConfiguration();
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		navigateIntent(intent, true);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		writePagesState(outState);
	}

	private void writePagesState(Bundle outState) {
		outState.putParcelableArrayList(EXTRA_STACK_PAGE_ITEMS, stackPageItems);
		outState.putParcelableArrayList(EXTRA_PRESERVED_PAGE_ITEMS, preservedPageItems);
		outState.putParcelable(EXTRA_CURRENT_PAGE_ITEM, currentPageItem);
	}

	private File getSavedPagesFile() {
		return CacheManager.getInstance().getInternalCacheFile("saved-pages");
	}

	private Fragment getCurrentFragment() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		try {
			fragmentManager.executePendingTransactions();
		} catch (IllegalStateException e) {
			// Ignore exception
		}
		return fragmentManager.findFragmentById(R.id.content_fragment);
	}

	@Override
	public void navigateBoardsOrThreads(String chanName, String boardName, int flags) {
		flags = flags & (NavigationUtils.FLAG_FROM_CACHE | NavigationUtils.FLAG_RETURNABLE);
		navigateIntentData(chanName, boardName, null, null, null, null, flags);
	}

	@Override
	public void navigatePosts(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, int flags) {
		flags = flags & (NavigationUtils.FLAG_FROM_CACHE | NavigationUtils.FLAG_RETURNABLE);
		navigateIntentData(chanName, boardName, threadNumber, postNumber, threadTitle, null, flags);
	}

	@Override
	public void navigateSearch(String chanName, String boardName, String searchQuery, int flags) {
		flags = flags & NavigationUtils.FLAG_RETURNABLE;
		navigateIntentData(chanName, boardName, null, null, null, searchQuery, flags);
	}

	@Override
	public void navigateArchive(String chanName, String boardName, int flags) {
		boolean returnable = FlagUtils.get(flags, NavigationUtils.FLAG_RETURNABLE);
		navigatePage(Page.Content.ARCHIVE, chanName, boardName, null, null, null, null, false, returnable, false);
	}

	@Override
	public void navigateTarget(String chanName, ChanLocator.NavigationData data, int flags) {
		switch (data.target) {
			case ChanLocator.NavigationData.TARGET_THREADS: {
				navigateBoardsOrThreads(chanName, data.boardName, flags);
				break;
			}
			case ChanLocator.NavigationData.TARGET_POSTS: {
				navigatePosts(chanName, data.boardName, data.threadNumber, data.postNumber, null, flags);
				break;
			}
			case ChanLocator.NavigationData.TARGET_SEARCH: {
				navigateSearch(chanName, data.boardName, data.searchQuery, flags);
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

	private void navigateIntent(Intent intent, boolean replaceIntent) {
		if (replaceIntent) {
			navigateIntentOnResume = intent;
		} else {
			navigateIntentUnchecked(intent, false);
		}
	}

	private void navigateIntentUnchecked(Intent intent, boolean replaceIntent) {
		String chanName = intent.getStringExtra(C.EXTRA_CHAN_NAME);
		String boardName = intent.getStringExtra(C.EXTRA_BOARD_NAME);
		String threadNumber = intent.getStringExtra(C.EXTRA_THREAD_NUMBER);
		String postNumber = intent.getStringExtra(C.EXTRA_POST_NUMBER);
		String threadTitle = intent.getStringExtra(C.EXTRA_THREAD_TITLE);
		String searchQuery = intent.getStringExtra(C.EXTRA_SEARCH_QUERY);
		int flags = intent.getIntExtra(C.EXTRA_NAVIGATION_FLAGS, 0);
		if (replaceIntent && FlagUtils.get(flags, NavigationUtils.FLAG_LAUNCHER)) {
			return;
		}
		navigateIntentData(chanName, boardName, threadNumber, postNumber, threadTitle, searchQuery, flags);
	}

	private static boolean isSingleBoardMode(String chanName) {
		return ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
	}

	private static String getSingleBoardName(String chanName) {
		return ChanConfiguration.get(chanName).getSingleBoardName();
	}

	private Page getSavedPage(SavedPageItem savedPageItem) {
		REFERENCE_FRAGMENT.setArguments(savedPageItem.stackItem.arguments);
		return REFERENCE_FRAGMENT.getPage();
	}

	private int getPagesStackSize(String chanName) {
		boolean mergeChans = Preferences.isMergeChans();
		int size = 0;
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment && currentPageItem != null &&
				(mergeChans || (((PageFragment) currentFragment).getPage().chanName.equals(chanName)))) {
			size++;
		}
		for (SavedPageItem savedPageItem : stackPageItems) {
			if (mergeChans || getSavedPage(savedPageItem).chanName.equals(chanName)) {
				size++;
			}
		}
		return size;
	}

	private SavedPageItem prepareTargetPreviousPage(boolean allowForeignChan) {
		Fragment currentFragment = getCurrentFragment();
		String chanName = ((PageFragment) currentFragment).getPage().chanName;
		boolean mergeChans = Preferences.isMergeChans();
		for (int i = stackPageItems.size() - 1; i >= 0; i--) {
			SavedPageItem savedPageItem = stackPageItems.get(i);
			if (mergeChans || getSavedPage(savedPageItem).chanName.equals(chanName)) {
				stackPageItems.remove(i);
				return savedPageItem;
			}
		}
		if (allowForeignChan && currentPageItem.returnable && !stackPageItems.isEmpty()) {
			return stackPageItems.remove(stackPageItems.size() - 1);
		}
		return null;
	}

	private void clearStackAndCurrent() {
		Fragment currentFragment = getCurrentFragment();
		boolean mergeChans = Preferences.isMergeChans();
		boolean closeOnBack = Preferences.isCloseOnBack();
		String chanName = ((PageFragment) currentFragment).getPage().chanName;
		Iterator<SavedPageItem> iterator = stackPageItems.iterator();
		while (iterator.hasNext()) {
			SavedPageItem savedPageItem = iterator.next();
			Page page = getSavedPage(savedPageItem);
			if (mergeChans || page.chanName.equals(chanName)) {
				iterator.remove();
				if (!(page.canDestroyIfNotInStack() || closeOnBack && page.isThreadsOrPosts())) {
					preservedPageItems.add(savedPageItem);
				}
			}
		}
		Page page = ((PageFragment) currentFragment).getPage();
		if (mergeChans || page.chanName.equals(chanName)) {
			if (!(page.canDestroyIfNotInStack() || closeOnBack && page.isThreadsOrPosts())) {
				preservedPageItems.add(currentPageItem.toSaved(getSupportFragmentManager(),
						(PageFragment) currentFragment));
			}
			currentPageItem = null;
		}
	}

	private void navigateIntentData(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, String searchQuery, int flags) {
		if (chanName == null) {
			return;
		}
		boolean fromCache = FlagUtils.get(flags, NavigationUtils.FLAG_FROM_CACHE);
		boolean returnable = FlagUtils.get(flags, NavigationUtils.FLAG_RETURNABLE);
		boolean forceBoardPage = false;
		if (isSingleBoardMode(chanName)) {
			boardName = getSingleBoardName(chanName);
			forceBoardPage = true;
		}
		if (boardName != null || threadNumber != null || forceBoardPage) {
			Page.Content content = searchQuery != null ? Page.Content.SEARCH
					: threadNumber == null ? Page.Content.THREADS : Page.Content.POSTS;
			navigatePage(content, chanName, boardName, threadNumber, postNumber, threadTitle,
					searchQuery, fromCache, returnable, false);
		} else {
			String currentChanName = null;
			Fragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment) {
				currentChanName = ((PageFragment) currentFragment).getPage().chanName;
			}
			if (getPagesStackSize(chanName) == 0 || !chanName.equals(currentChanName)) {
				navigatePage(Page.Content.BOARDS, chanName, null, null, null, null, null, false, returnable, false);
			}
		}
	}

	private Pair<PageFragment, PageItem> prepareAddPage(Page.Content content,
			String chanName, String boardName, String threadNumber, String searchQuery,
			ListPage.InitRequest initRequest) {
		SavedPageItem targetSavedPageItem = null;
		Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
		while (iterator.hasNext()) {
			SavedPageItem savedPageItem = iterator.next();
			if (getSavedPage(savedPageItem).is(content, chanName, boardName, threadNumber)) {
				targetSavedPageItem = savedPageItem;
				iterator.remove();
				break;
			}
		}

		Page page = new Page(content, chanName, boardName, threadNumber, searchQuery);
		Pair<PageFragment, PageItem> pair;
		if (targetSavedPageItem != null) {
			Page savedPage = getSavedPage(targetSavedPageItem);
			if (savedPage.equals(page)) {
				pair = targetSavedPageItem.create();
			} else {
				pair = targetSavedPageItem.createWithNewPage(page);
			}
		} else {
			PageFragment pageFragment = new PageFragment(page, UUID.randomUUID().toString());
			PageItem pageItem = new PageItem();
			pair = new Pair<>(pageFragment, pageItem);
		}
		if (initRequest != null) {
			pair.first.setInitRequest(initRequest);
		}

		boolean mergeChans = Preferences.isMergeChans();
		int depth = 0;
		// Remove deep search, boards, etc pages if they are deep in stack
		for (int i = stackPageItems.size() - 1; i >= 0; i--) {
			SavedPageItem savedPageItem = stackPageItems.get(i);
			Page savedPage = getSavedPage(savedPageItem);
			if (mergeChans || savedPage.chanName.equals(chanName)) {
				if (depth++ >= 2 && savedPage.canRemoveFromStackIfDeep()) {
					stackPageItems.remove(i);
					if (!savedPage.canDestroyIfNotInStack()) {
						preservedPageItems.add(savedPageItem);
					}
				}
			}
		}
		return pair;
	}

	private void navigatePage(Page.Content content, String chanName, String boardName,
			String threadNumber, String postNumber, String threadTitle, String searchQuery,
			boolean fromCache, boolean returnable, boolean resetScroll) {
		Fragment currentFragment = getCurrentFragment();
		Page currentPage = currentFragment instanceof PageFragment
				? ((PageFragment) currentFragment).getPage() : null;
		if (currentPage != null && currentPage.is(content, chanName, boardName, threadNumber) && searchQuery == null) {
			if (currentPageItem == null && (content == Page.Content.BOARDS || content == Page.Content.THREADS)) {
				// Was removed from stack during clearStackAndCurrent
				Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
				while (iterator.hasNext()) {
					if (getSavedPage(iterator.next()).is(content, chanName, boardName, null)) {
						iterator.remove();
						break;
					}
				}
				currentPageItem = new PageItem();
				currentPageItem.createdRealtime = SystemClock.elapsedRealtime();
			}
			if (currentPageItem != null) {
				currentPageItem.returnable &= returnable;
				((PageFragment) currentFragment).updatePageConfiguration(postNumber);
				invalidateHomeUpState();
				return;
			}
		}
		Pair<PageFragment, PageItem> pair;
		switch (content) {
			case THREADS: {
				pair = prepareAddPage(content, chanName, boardName, null, null,
						new ListPage.InitRequest(!fromCache, null, null));
				break;
			}
			case POSTS: {
				pair = prepareAddPage(content, chanName, boardName, threadNumber, null,
						new ListPage.InitRequest(!fromCache, postNumber, threadTitle));
				break;
			}
			case SEARCH: {
				pair = prepareAddPage(content, chanName, boardName, null, searchQuery,
						new ListPage.InitRequest(!fromCache, null, null));
				break;
			}
			case ARCHIVE:
			case BOARDS:
			case USER_BOARDS:
			case HISTORY: {
				pair = prepareAddPage(content, chanName, boardName, null, null, null);
				break;
			}
			default: {
				throw new RuntimeException();
			}
		}
		pair.second.returnable = returnable;
		if (resetScroll) {
			pair.first.requestResetScroll();
		}
		navigateFragment(pair.first, pair.second);
	}

	private void navigateSavedPage(SavedPageItem savedPageItem) {
		Pair<PageFragment, PageItem> pair = savedPageItem.create();
		navigateFragment(pair.first, pair.second);
	}

	private void navigateFragment(Fragment fragment, PageItem pageItem) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			// TODO Clear non-page fragments
			// currentPageItem == null means page was deleted
			if (currentPageItem != null) {
				stackPageItems.add(currentPageItem.toSaved(fragmentManager,
						(PageFragment) currentFragment));
			}
			if (fragment instanceof PageFragment) {
				PostingService.clearNewThreadData();
			}
		} else if (currentFragment != null) {
			// TODO Handle non-page fragments
		}

		if (currentFragment instanceof ActivityHandler) {
			((ActivityHandler) currentFragment).onTerminate();
		}
		ClickableToast.cancel(this);
		InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		if (inputMethodManager != null) {
			View view = getCurrentFocus();
			inputMethodManager.hideSoftInputFromWindow((view != null
					? view : getWindow().getDecorView()).getWindowToken(), 0);
		}
		uiManager.view().resetPages();
		if (pageItem != null) {
			pageItem.createdRealtime = SystemClock.elapsedRealtime();
		}
		currentPageItem = pageItem;
		fragmentManager.beginTransaction()
				.setCustomAnimations(R.animator.fragment_in, R.animator.fragment_out)
				.replace(R.id.content_fragment, fragment)
				.commit();
		updatePostFragmentConfiguration();

		if (currentFragment instanceof PageFragment || fragment instanceof PageFragment) {
			HashSet<String> retainIds = new HashSet<>(1 + stackPageItems.size() + preservedPageItems.size());
			if (fragment instanceof PageFragment) {
				retainIds.add(((PageFragment) fragment).getRetainId());
			}
			for (SavedPageItem savedPageItem : new ConcatIterable<>(preservedPageItems, stackPageItems)) {
				REFERENCE_FRAGMENT.setArguments(savedPageItem.stackItem.arguments);
				String retainId = REFERENCE_FRAGMENT.getRetainId();
				retainIds.add(retainId);
			}
			retainFragment.extras.keySet().retainAll(retainIds);
		}
	}

	private void updatePostFragmentConfiguration() {
		Fragment currentFragment = getCurrentFragment();
		String chanName;
		if (currentFragment instanceof PageFragment) {
			chanName = ((PageFragment) currentFragment).getPage().chanName;
		} else if (!stackPageItems.isEmpty()) {
			chanName = getSavedPage(stackPageItems.get(stackPageItems.size() - 1)).chanName;
		} else {
			chanName = ChanManager.getInstance().getDefaultChanName();
		}
		watcherServiceClient.updateConfiguration(chanName);
		drawerForm.updateConfiguration(chanName);
		invalidateHomeUpState();
		if (!wideMode && !drawerLayout.isDrawerOpen(Gravity.START)) {
			drawerListView.setSelection(0);
		}
	}

	@Override
	public UiManager getUiManager() {
		return uiManager;
	}

	@Override
	public ActionIconSet getActionIconSet() {
		return actionIconSet;
	}

	@Override
	public Object getRetainExtra(String retainId) {
		return retainFragment.extras.get(retainId);
	}

	@Override
	public void storeRetainExtra(String retainId, Object extra) {
		if (extra != null) {
			retainFragment.extras.put(retainId, extra);
		} else {
			retainFragment.extras.remove(retainId);
		}
	}

	@Override
	public void invalidateHomeUpState() {
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof ActivityHandler && ((ActivityHandler) currentFragment).isSearchMode()) {
			drawerToggle.setDrawerIndicatorMode(DrawerToggle.MODE_UP);
		} else if (currentFragment instanceof PageFragment) {
			boolean displayUp = false;
			Page page = ((PageFragment) currentFragment).getPage();
			switch (page.content) {
				case THREADS: {
					displayUp = getPagesStackSize(page.chanName) > 1;
					break;
				}
				case POSTS:
				case SEARCH:
				case ARCHIVE: {
					displayUp = true;
					break;
				}
				case BOARDS:
				case USER_BOARDS:
				case HISTORY: {
					displayUp = page.boardName != null || getPagesStackSize(page.chanName) > 1;
					break;
				}
			}
			drawerToggle.setDrawerIndicatorMode(displayUp ? DrawerToggle.MODE_UP : wideMode
					? DrawerToggle.MODE_DISABLED : DrawerToggle.MODE_DRAWER);
		} else {
			// TODO Handle non-page fragments
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
	protected void onStart() {
		super.onStart();

		Preferences.Holder newPreferences = Preferences.getCurrent();
		if (currentPreferences.isNeedRestartActivity(newPreferences)) {
			// Recreate after onResume
			postRecreate();
			return;
		} else if (currentPreferences.isNeedRefreshList(newPreferences)) {
			Fragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment) {
				((PageFragment) currentFragment).notifyAdapterChanged();
			}
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
		clickableToastHolder.onResume();
		ChanManager.getInstance().getInstallationObservable().register(installationCallback);
		ForegroundManager.register(this);

		Intent navigateIntentOnResume = this.navigateIntentOnResume;
		this.navigateIntentOnResume = null;
		if (navigateIntentOnResume != null) {
			navigateIntentUnchecked(navigateIntentOnResume, true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		watcherServiceClient.stop();
		clickableToastHolder.onPause();
		ChanManager.getInstance().getInstallationObservable().unregister(installationCallback);
		ForegroundManager.unregister(this);
	}

	@Override
	protected void onStop() {
		super.onStop();

		// Intent is valid only for onNewIntent -> onResume behavior
		navigateIntentOnResume = null;
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
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ViewUtils.applyToolbarStyle(this, toolbarView);
		drawerToggle.onConfigurationChanged();
		updateWideConfiguration(false);
		expandedScreen.onConfigurationChanged(newConfig);
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			((PageFragment) currentFragment).updateOptionsMenu();
		}
	}

	@Override
	public boolean onSearchRequested() {
		Fragment currentFragment = getCurrentFragment();
		return currentFragment instanceof ActivityHandler &&
				((ActivityHandler) currentFragment).onSearchRequested();
	}

	private long backPressed = 0;

	@Override
	public void onBackPressed() {
		if (!wideMode && drawerLayout.isDrawerOpen(Gravity.START)) {
			drawerLayout.closeDrawers();
		} else {
			Fragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof ActivityHandler &&
					((ActivityHandler) currentFragment).onBackPressed()) {
				return;
			}
			boolean handled = false;
			if (currentFragment instanceof PageFragment) {
				SavedPageItem savedPageItem = prepareTargetPreviousPage(true);
				if (savedPageItem != null) {
					if (currentFragment instanceof PageFragment) {
						Page page = ((PageFragment) currentFragment).getPage();
						if (!(page.isThreadsOrPosts() && Preferences.isCloseOnBack())) {
							preservedPageItems.add(currentPageItem.toSaved(getSupportFragmentManager(),
									(PageFragment) currentFragment));
						}
						currentPageItem = null;
					}
					navigateSavedPage(savedPageItem);
					handled = true;
				}
			} else {
				// TODO Check non-page fragments in stack
			}
			if (!handled) {
				if (System.currentTimeMillis() - backPressed > 2000) {
					ClickableToast.show(this, R.string.message_press_again_to_exit);
					backPressed = System.currentTimeMillis();
				} else {
					super.onBackPressed();
				}
			}
		}
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

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		MenuItem appearanceOptionsItem = menu.findItem(ListPage.OPTIONS_MENU_APPEARANCE);
		if (appearanceOptionsItem != null) {
			Menu appearanceOptionsMenu = appearanceOptionsItem.getSubMenu();
			if (appearanceOptionsMenu.size() == 0) {
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
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_EXPANDED_SCREEN)
					.setChecked(Preferences.isExpandedScreen());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_SPOILERS)
					.setChecked(Preferences.isShowSpoilers());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_MY_POSTS)
					.setChecked(Preferences.isShowMyPosts());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_DRAWER)
					.setVisible(ViewUtils.isDrawerLockable(getResources().getConfiguration()))
					.setChecked(Preferences.isDrawerLocked());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_THREADS_GRID)
					.setChecked(Preferences.isThreadsGridMode());
			appearanceOptionsMenu.findItem(ListPage.APPEARANCE_MENU_SFW_MODE)
					.setChecked(Preferences.isSfwMode());
		}
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		Fragment currentFragment = getCurrentFragment();
		switch (item.getItemId()) {
			case android.R.id.home: {
				if (currentFragment instanceof ActivityHandler &&
						((ActivityHandler) currentFragment).onBackPressed()) {
					return true;
				}
				drawerLayout.closeDrawers();
				if (currentFragment instanceof PageFragment) {
					Page page = ((PageFragment) currentFragment).getPage();
					String newChanName = page.chanName;
					String newBoardName = page.boardName;
					if (page.content == Page.Content.THREADS) {
						// Up button must navigate to main page in threads list
						newBoardName = Preferences.getDefaultBoardName(page.chanName);
						if (Preferences.isMergeChans() && StringUtils.equals(page.boardName, newBoardName)) {
							newChanName = ChanManager.getInstance().getDefaultChanName();
							newBoardName = Preferences.getDefaultBoardName(newChanName);
						}
					}
					clearStackAndCurrent();
					boolean fromCache = false;
					for (SavedPageItem savedPageItem : new ConcatIterable<>(preservedPageItems, stackPageItems)) {
						if (getSavedPage(savedPageItem).is(Page.Content.THREADS, newChanName, newBoardName, null)) {
							fromCache = true;
							break;
						}
					}
					navigateIntentData(newChanName, newBoardName, null, null, null, null,
							fromCache ? NavigationUtils.FLAG_FROM_CACHE : 0);
				} else {
					// TODO Check non-page fragments
				}
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
					if (currentFragment instanceof PageFragment) {
						((PageFragment) currentFragment).onAppearanceOptionChanged(item.getItemId());
					}
				}
			}
		}
		return super.onOptionsItemSelected(item);
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
	public void onSortingStateChanged(SortableListView listView, boolean sorting) {
		if (!wideMode) {
			drawerLayout.setDrawerLockMode(sorting ? DrawerLayout.LOCK_MODE_LOCKED_OPEN
					: DrawerLayout.LOCK_MODE_UNLOCKED);
		}
	}

	@Override
	public void onSelectChan(String chanName) {
		Fragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (page == null || !page.chanName.equals(chanName)) {
			if (!Preferences.isMergeChans()) {
				// Find chan page and open it. Open root page if nothing was found.
				SavedPageItem lastSavedPageItem = null;
				for (int i = stackPageItems.size() - 1; i >= 0; i--) {
					SavedPageItem savedPageItem = stackPageItems.get(i);
					if (getSavedPage(savedPageItem).chanName.equals(chanName)) {
						stackPageItems.remove(savedPageItem);
						lastSavedPageItem = savedPageItem;
						break;
					}
				}
				if (lastSavedPageItem != null) {
					if (page != null) {
						stackPageItems.add(currentPageItem.toSaved(getSupportFragmentManager(),
								(PageFragment) currentFragment));
						currentPageItem = null;
					}
					navigateSavedPage(lastSavedPageItem);
				} else {
					navigateBoardsOrThreads(chanName, Preferences.getDefaultBoardName(chanName), 0);
				}
			} else {
				// Open root page. If page is already opened, load it from cache.
				boolean fromCache = false;
				String boardName = Preferences.getDefaultBoardName(chanName);
				for (SavedPageItem savedPageItem : new ConcatIterable<>(preservedPageItems, stackPageItems)) {
					if (getSavedPage(savedPageItem).is(Page.Content.THREADS, chanName, boardName, null)) {
						fromCache = true;
						break;
					}
				}
				navigateBoardsOrThreads(chanName, boardName, fromCache ? NavigationUtils.FLAG_FROM_CACHE : 0);
			}
			drawerForm.updateConfiguration(chanName);
			drawerForm.invalidateItems(true, false);
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public void onSelectBoard(String chanName, String boardName, boolean fromCache) {
		Fragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (isSingleBoardMode(chanName)) {
			boardName = getSingleBoardName(chanName);
		}
		if (page == null || !page.is(Page.Content.THREADS, chanName, boardName, null)) {
			navigateBoardsOrThreads(chanName, boardName, fromCache ? NavigationUtils.FLAG_FROM_CACHE : 0);
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public boolean onSelectThread(String chanName, String boardName, String threadNumber, String postNumber,
			String threadTitle, boolean fromCache) {
		Fragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (isSingleBoardMode(chanName)) {
			boardName = getSingleBoardName(chanName);
		} else if (boardName == null) {
			if (page == null) {
				return false;
			} else {
				switch (page.content) {
					case BOARDS:
					case USER_BOARDS:
					case HISTORY: {
						return false;
					}
					default: {
						break;
					}
				}
				boardName = page.boardName;
			}
		}
		if (page == null || !page.is(Page.Content.POSTS, chanName, boardName, threadNumber)) {
			navigatePosts(chanName, boardName, threadNumber, postNumber, threadTitle,
					fromCache ? NavigationUtils.FLAG_FROM_CACHE : 0);
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
		return true;
	}

	@Override
	public void onClosePage(String chanName, String boardName, String threadNumber) {
		Fragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (page != null && page.isThreadsOrPosts(chanName, boardName, threadNumber)) {
			SavedPageItem savedPageItem = prepareTargetPreviousPage(false);
			currentPageItem = null;
			if (savedPageItem != null) {
				navigateSavedPage(savedPageItem);
			} else {
				if (isSingleBoardMode(chanName)) {
					navigatePage(Page.Content.THREADS, chanName,
							getSingleBoardName(chanName), null, null, null, null, true, false, false);
				} else {
					navigatePage(Page.Content.BOARDS, chanName,
							null, null, null, null, null, true, false, false);
				}
			}
		} else {
			Iterator<SavedPageItem> iterator = stackPageItems.iterator();
			while (iterator.hasNext()) {
				if (getSavedPage(iterator.next()).isThreadsOrPosts(chanName, boardName, threadNumber)) {
					iterator.remove();
					break;
				}
			}
			iterator = preservedPageItems.iterator();
			while (iterator.hasNext()) {
				if (getSavedPage(iterator.next()).isThreadsOrPosts(chanName, boardName, threadNumber)) {
					iterator.remove();
					break;
				}
			}
			drawerForm.invalidateItems(true, false);
			invalidateHomeUpState();
		}
	}

	private boolean isCloseAllTarget(Page page, String chanName, String boardName,
			boolean singleBoardMode, String singleBoardName) {
		if (!singleBoardMode && boardName == null) {
			return page.is(Page.Content.BOARDS, chanName, null, null);
		} else if (singleBoardMode) {
			return page.is(Page.Content.THREADS, chanName, singleBoardName, null);
		} else {
			return page.is(Page.Content.THREADS, chanName, boardName, null);
		}
	}

	@Override
	public void onCloseAllPages() {
		Fragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		String chanName = page != null ? page.chanName : null;
		if (chanName == null && !stackPageItems.isEmpty()) {
			chanName = getSavedPage(stackPageItems.get(stackPageItems.size() - 1)).chanName;
		}
		if (chanName != null) {
			String boardName = Preferences.getDefaultBoardName(chanName);
			boolean singleBoardMode = isSingleBoardMode(chanName);
			String singleBoardName = getSingleBoardName(chanName);
			boolean cached = page != null && isCloseAllTarget(page,
					chanName, boardName, singleBoardMode, singleBoardName);
			boolean mergeChans = Preferences.isMergeChans();
			ArrayList<SavedPageItem> addPreserved = new ArrayList<>();
			Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
			while (iterator.hasNext()) {
				SavedPageItem savedPageItem = iterator.next();
				Page savedPage = getSavedPage(savedPageItem);
				if (mergeChans || savedPage.chanName.equals(chanName)) {
					cached |= isCloseAllTarget(savedPage, chanName, boardName, singleBoardMode, singleBoardName);
					iterator.remove();
					if (!(savedPage.isThreadsOrPosts() || savedPage.canDestroyIfNotInStack())) {
						addPreserved.add(savedPageItem);
					}
				}
			}
			preservedPageItems.addAll(addPreserved);
			if (page != null) {
				if (!(page.isThreadsOrPosts() || page.canDestroyIfNotInStack())) {
					preservedPageItems.add(currentPageItem.toSaved(getSupportFragmentManager(),
							(PageFragment) currentFragment));
				}
				currentPageItem = null;
				navigateBoardsOrThreads(chanName, boardName, cached ? NavigationUtils.FLAG_FROM_CACHE : 0);
			}
		} else {
			ArrayList<SavedPageItem> addPreserved = new ArrayList<>();
			Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
			while (iterator.hasNext()) {
				SavedPageItem savedPageItem = iterator.next();
				Page savedPage = getSavedPage(savedPageItem);
				iterator.remove();
				if (!(savedPage.isThreadsOrPosts() || savedPage.canDestroyIfNotInStack())) {
					addPreserved.add(savedPageItem);
				}
			}
			preservedPageItems.addAll(addPreserved);
		}
		drawerForm.invalidateItems(true, false);
		invalidateHomeUpState();
	}

	@Override
	public int onEnterNumber(int number) {
		int result = 0;
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			result = ((PageFragment) currentFragment).onDrawerNumberEntered(number);
		}
		if (!wideMode && FlagUtils.get(result, DrawerForm.RESULT_SUCCESS)) {
			drawerLayout.closeDrawers();
		}
		return result;
	}

	private final Runnable preferencesRunnable = () -> startActivity(new Intent(this, PreferencesActivity.class));

	@Override
	public void onSelectDrawerMenuItem(int item) {
		Page.Content content = null;
		switch (item) {
			case DrawerForm.MENU_ITEM_BOARDS: {
				content = Page.Content.BOARDS;
				break;
			}
			case DrawerForm.MENU_ITEM_USER_BOARDS: {
				content = Page.Content.USER_BOARDS;
				break;
			}
			case DrawerForm.MENU_ITEM_HISTORY: {
				content = Page.Content.HISTORY;
				break;
			}
			case DrawerForm.MENU_ITEM_PREFERENCES: {
				if (wideMode) {
					preferencesRunnable.run();
				} else {
					drawerListView.postDelayed(preferencesRunnable, 200);
				}
				break;
			}
		}
		if (content != null) {
			Fragment currentFragment = getCurrentFragment();
			Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
			if (page == null || page.content != content) {
				if (page == null && !stackPageItems.isEmpty()) {
					page = getSavedPage(stackPageItems.get(stackPageItems.size() - 1));
				}
				String chanName = page != null ? page.chanName : null;
				String boardName = page != null ? page.boardName : null;
				if (chanName == null) {
					chanName = ChanManager.getInstance().getDefaultChanName();
					boardName = Preferences.getDefaultBoardName(boardName);
				}
				if (chanName != null) {
					navigatePage(content, chanName, boardName, null, null, null, null, false, false, true);
				}
			}
		}
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public Collection<DrawerForm.Page> obtainDrawerPages() {
		ArrayList<DrawerForm.Page> drawerPages = new ArrayList<>(1 +
				stackPageItems.size() + preservedPageItems.size());
		for (SavedPageItem savedPageItem : new ConcatIterable<>(preservedPageItems, stackPageItems)) {
			Page page = getSavedPage(savedPageItem);
			if (page.isThreadsOrPosts()) {
				drawerPages.add(new DrawerForm.Page(page.chanName, page.boardName, page.threadNumber,
						savedPageItem.threadTitle, savedPageItem.createdRealtime));
			}
		}
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			Page page = ((PageFragment) currentFragment).getPage();
			if (page.isThreadsOrPosts()) {
				drawerPages.add(new DrawerForm.Page(page.chanName, page.boardName, page.threadNumber,
						currentPageItem.threadTitle, currentPageItem.createdRealtime));
			}
		}
		return drawerPages;
	}

	private final Runnable installationCallback = () -> drawerForm.updateRestartViewVisibility();

	@Override
	public void restartApplication() {
		Bundle outState = new Bundle();
		writePagesState(outState);
		outState.putParcelable(EXTRA_CURRENT_FRAGMENT,
				new StackItem(getSupportFragmentManager(), getCurrentFragment()));
		File file = getSavedPagesFile();
		if (file != null) {
			Parcel parcel = Parcel.obtain();
			FileOutputStream output = null;
			try {
				outState.writeToParcel(parcel, 0);
				byte[] data = parcel.marshall();
				output = new FileOutputStream(file);
				IOUtils.copyStream(new ByteArrayInputStream(data), output);
			} catch (IOException e) {
				file.delete();
			} finally {
				IOUtils.close(output);
				parcel.recycle();
			}
		}
		if (file != null && file.exists()) {
			NavigationUtils.restartApplication(this);
		}
	}

	private final BroadcastReceiver newPostReceiver = AndroidUtils.createReceiver((r, c, i) -> {
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			// TODO Replace receiver with service binding
			((PageFragment) currentFragment).handleNewPostDataListNow();
		}
	});

	private void startUpdateTask() {
		if (!Preferences.isCheckUpdatesOnStart() || System.currentTimeMillis()
				- Preferences.getLastUpdateCheck() < 12 * 60 * 60 * 1000) {
			// Check for updates ones per 12 hours
			return;
		}
		readUpdateTask = new ReadUpdateTask(this, this);
		readUpdateTask.executeOnExecutor(ReadUpdateTask.THREAD_POOL_EXECUTOR);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap, ErrorItem errorItem) {
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
		// TODO Handle deprecation
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
	public void setPageTitle(String title) {
		setTitle(title);
		if (((PageFragment) getCurrentFragment()).getPage().content == Page.Content.POSTS) {
			currentPageItem.threadTitle = title;
		}
		drawerForm.invalidateItems(true, false);
	}

	@Override
	public void handleRedirect(Page page, String chanName, String boardName, String threadNumber, String postNumber) {
		if (page.isThreadsOrPosts()) {
			if (page.content == Page.Content.POSTS) {
				FavoritesStorage.getInstance().move(page.chanName,
						page.boardName, page.threadNumber, boardName, threadNumber);
				CacheManager.getInstance().movePostsPage(page.chanName,
						page.boardName, page.threadNumber, boardName, threadNumber);
				DraftsStorage.getInstance().movePostDraft(page.chanName,
						page.boardName, page.threadNumber, boardName, threadNumber);
				drawerForm.invalidateItems(true, false);
			}
			currentPageItem = null;
			if (threadNumber == null) {
				navigateBoardsOrThreads(chanName, boardName, 0);
			} else {
				navigatePosts(chanName, boardName, threadNumber, postNumber, null, 0);
			}
		}
	}

	@Override
	public void setActionBarLocked(String locker, boolean locked) {
		if (locked) {
			expandedScreen.addLocker(locker);
		} else {
			expandedScreen.removeLocker(locker);
		}
	}

	private class ExpandedScreenDrawerLocker implements DrawerLayout.DrawerListener {
		@Override
		public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

		@Override
		public void onDrawerOpened(@NonNull View drawerView) {
			setActionBarLocked(LOCKER_DRAWER, true);
		}

		@Override
		public void onDrawerClosed(@NonNull View drawerView) {
			setActionBarLocked(LOCKER_DRAWER, false);
		}

		@Override
		public void onDrawerStateChanged(int newState) {}
	}

	public static class RetainFragment extends Fragment {
		private final HashMap<String, Object> extras = new HashMap<>();

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}
	}
}
