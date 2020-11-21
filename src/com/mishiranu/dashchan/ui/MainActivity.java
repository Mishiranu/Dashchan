package com.mishiranu.dashchan.ui;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.PageFragment;
import com.mishiranu.dashchan.ui.navigator.PageItem;
import com.mishiranu.dashchan.ui.navigator.SavedPageItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.ui.posting.PostingFragment;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.ui.preference.CategoriesFragment;
import com.mishiranu.dashchan.ui.preference.ThemesFragment;
import com.mishiranu.dashchan.ui.preference.UpdateFragment;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcatIterable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.DrawerToggle;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CustomDrawerLayout;
import com.mishiranu.dashchan.widget.ExpandedScreen;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class MainActivity extends StateActivity implements DrawerForm.Callback, ThemeDialog.Callback,
		FavoritesStorage.Observer, WatcherService.Client.Callback,
		UiManager.Callback, UiManager.LocalNavigator, FragmentHandler, PageFragment.Callback {
	private static final String EXTRA_FRAGMENTS = "fragments";
	private static final String EXTRA_STACK_PAGE_ITEMS = "stackPageItems";
	private static final String EXTRA_PRESERVED_PAGE_ITEMS = "preservedPageItems";
	private static final String EXTRA_CURRENT_FRAGMENT = "currentFragment";
	private static final String EXTRA_CURRENT_PAGE_ITEM = "currentPageItem";
	private static final String EXTRA_DRAWER_EXPANDED = "drawerExpanded";
	private static final String EXTRA_DRAWER_CHAN_SELECT_MODE = "drawerChanSelectMode";
	private static final String EXTRA_STORAGE_REQUEST_STATE = "storageRequestState";

	private static final PageFragment REFERENCE_FRAGMENT = new PageFragment();

	private enum StorageRequestState {NONE, INSTRUCTIONS, PICKER}

	private final ArrayList<StackItem> fragments = new ArrayList<>();
	private final ArrayList<SavedPageItem> stackPageItems = new ArrayList<>();
	private final ArrayList<SavedPageItem> preservedPageItems = new ArrayList<>();
	private PageItem currentPageItem;

	private UiManager uiManager;
	private InstanceViewModel instanceViewModel;
	private WatcherService.Client watcherServiceClient;
	private final ExtensionsTrustLoop.State extensionsTrustLoopState = new ExtensionsTrustLoop.State();
	private DownloadDialog downloadDialog;

	private DrawerForm drawerForm;
	private FrameLayout drawerParent;
	private CustomDrawerLayout drawerLayout;
	private DrawerToggle drawerToggle;
	private final HashSet<String> navigationAreaLockers = new HashSet<>();

	private ExpandedScreen expandedScreen;
	private ViewFactory.ToolbarHolder toolbarHolder;
	private FrameLayout toolbarExtra;

	private ViewGroup drawerCommon;
	private ViewGroup drawerWide;
	private boolean wideMode;

	private Intent navigateIntentOnResume;
	private StorageRequestState storageRequestState;

	private static final String LOCKER_DRAWER = "drawer";
	private static final String LOCKER_NON_PAGE = "nonPage";
	private static final String LOCKER_ACTION_MODE = "actionMode";

	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(ThemeEngine.attach(LocaleManager.getInstance().apply(newBase)));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (C.API_LOLLIPOP) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
		}
		ExpandedScreen.PreThemeInit expandedScreenPreThemeInit = new ExpandedScreen
				.PreThemeInit(this, Preferences.isExpandedScreen());
		ThemeEngine.applyTheme(this);
		ExpandedScreen.Init expandedScreenInit = expandedScreenPreThemeInit.initAfterTheme();
		super.onCreate(savedInstanceState);
		// ExpandedScreen should handle this for R+
		getWindow().setSoftInputMode(C.API_R ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
				: ViewUtils.SOFT_INPUT_ADJUST_RESIZE_COMPAT);
		float density = ResourceUtils.obtainDensity(this);
		setContentView(R.layout.activity_main);
		ClickableToast.register(this);
		ForegroundManager.getInstance().register(this);
		FavoritesStorage.getInstance().getObservable().register(this);
		Preferences.PREFERENCES.registerOnSharedPreferenceChangeListener(preferencesListener);
		ChanManager.getInstance().observable.register(chanManagerCallback);
		watcherServiceClient = WatcherService.getClient(this);
		watcherServiceClient.setCallback(this);
		drawerCommon = findViewById(R.id.drawer_common);
		drawerWide = findViewById(R.id.drawer_wide);
		ThemeEngine.Theme theme = ThemeEngine.getTheme(this);
		Context drawerContext;
		int drawerBackground;
		if (C.API_LOLLIPOP) {
			drawerContext = this;
			drawerBackground = theme.card;
		} else {
			boolean black = theme.isBlack4();
			drawerContext = new ContextThemeWrapper(this, R.style.Theme_Main_Dark);
			drawerBackground = black ? 0xff000000 : 0xff202020;
			if (black) {
				getActionBar().setLogo(android.R.color.transparent);
				getActionBar().setBackgroundDrawable(new ColorDrawable(0xff000000));
			}
		}
		drawerCommon.setBackgroundColor(drawerBackground);
		drawerWide.setBackgroundColor(drawerBackground);
		drawerForm = new DrawerForm(drawerContext, this, getSupportFragmentManager(), watcherServiceClient);
		drawerParent = new FrameLayout(this);
		drawerParent.addView(drawerForm.getContentView());
		drawerCommon.addView(drawerParent);
		drawerLayout = findViewById(R.id.drawer_layout);
		drawerLayout.setSaveEnabled(false);
		FrameLayout drawerInterlayer = findViewById(R.id.drawer_interlayer);
		if (C.API_LOLLIPOP) {
			getLayoutInflater().inflate(R.layout.widget_toolbar, drawerInterlayer);
			Toolbar toolbar = findViewById(R.id.toolbar);
			setActionBar(toolbar);
			setTitle(null);
			// Allow CustomSearchView to ignore content inset
			toolbar.setClipChildren(false);
			toolbarHolder = ViewFactory.addToolbarTitle(toolbar);
			toolbarExtra = findViewById(R.id.toolbar_extra);
			LayoutTransition layoutTransition = new LayoutTransition();
			layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0);
			layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
			layoutTransition.setDuration(100);
			toolbarExtra.setLayoutTransition(layoutTransition);
		} else {
			// Show white logo on search
			getActionBar().setIcon(R.mipmap.ic_logo);
		}
		View toolbarLayout = findViewById(R.id.toolbar_layout);

		drawerToggle = new DrawerToggle(this, toolbarHolder != null
				? toolbarHolder.toolbar.getContext() : null, drawerLayout);
		if (C.API_LOLLIPOP) {
			drawerCommon.setElevation(4f * density);
			drawerWide.setElevation(4f * density);
		} else {
			drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		}
		drawerLayout.addDrawerListener(drawerToggle);
		drawerLayout.addDrawerListener(drawerForm);
		if (toolbarHolder == null) {
			drawerLayout.addDrawerListener(new ExpandedScreenDrawerLocker());
		}

		downloadDialog = new DownloadDialog(this, new DownloadDialog.Callback() {
			@Override
			public void resolve(DownloadService.ChoiceRequest choiceRequest,
					DownloadService.DirectRequest directRequest) {
				if (downloadBinder != null) {
					downloadBinder.resolve(choiceRequest, directRequest);
				}
			}

			@Override
			public void resolve(DownloadService.ReplaceRequest replaceRequest,
					DownloadService.ReplaceRequest.Action action) {
				if (downloadBinder != null) {
					downloadBinder.resolve(replaceRequest, action);
				}
			}

			@Override
			public void cancel(DownloadService.PrepareRequest prepareRequest) {
				if (downloadBinder != null) {
					downloadBinder.cancel(prepareRequest);
				}
			}
		});

		updateWideConfiguration(true);
		expandedScreen = new ExpandedScreen(expandedScreenInit, drawerLayout, toolbarLayout, drawerInterlayer,
				drawerParent, drawerForm.getContentView(), drawerForm.getHeaderView());
		expandedScreen.setDrawerOverToolbarEnabled(!wideMode);
		uiManager = new UiManager(this, this, this);
		uiManager.attach(this);
		ContentFragment.prepare(this);
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
		bindService(new Intent(this, PostingService.class), postingConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, DownloadService.class), downloadConnection, BIND_AUTO_CREATE);
		boolean allowSelectChan = ChanManager.getInstance().hasMultipleAvailableChans();
		if (savedInstanceState == null) {
			Preferences.DrawerInitialPosition drawerInitialPosition = Preferences.getDrawerInitialPosition();
			if (drawerInitialPosition != Preferences.DrawerInitialPosition.CLOSED) {
				if (!wideMode) {
					drawerLayout.post(() -> drawerLayout.openDrawer(GravityCompat.START));
				}
				if (drawerInitialPosition == Preferences.DrawerInitialPosition.FORUMS) {
					drawerForm.setChanSelectMode(allowSelectChan);
				}
			}
		} else {
			if (!wideMode && savedInstanceState.getBoolean(EXTRA_DRAWER_EXPANDED)) {
				drawerLayout.openDrawer(GravityCompat.START);
			}
			drawerForm.setChanSelectMode(allowSelectChan &&
					savedInstanceState.getBoolean(EXTRA_DRAWER_CHAN_SELECT_MODE));
		}

		instanceViewModel = new ViewModelProvider(this).get(InstanceViewModel.class);
		storageRequestState = savedInstanceState != null ? StorageRequestState
				.valueOf(savedInstanceState.getString(EXTRA_STORAGE_REQUEST_STATE)) : StorageRequestState.NONE;

		ContentFragment currentFragmentFromSaved = null;
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
				currentFragmentFromSaved = (ContentFragment) savedInstanceState
						.<StackItem>getParcelable(EXTRA_CURRENT_FRAGMENT).create(null);
				if (currentFragmentFromSaved == null) {
					savedInstanceState = null;
				}
			}
		}

		if (savedInstanceState != null) {
			fragments.addAll(savedInstanceState.getParcelableArrayList(EXTRA_FRAGMENTS));
			stackPageItems.addAll(savedInstanceState.getParcelableArrayList(EXTRA_STACK_PAGE_ITEMS));
			preservedPageItems.addAll(savedInstanceState.getParcelableArrayList(EXTRA_PRESERVED_PAGE_ITEMS));
			currentPageItem = savedInstanceState.getParcelable(EXTRA_CURRENT_PAGE_ITEM);
		}
		Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
		while (iterator.hasNext()) {
			if (Chan.get(getSavedPage(iterator.next()).chanName).name == null) {
				iterator.remove();
			}
		}
		if (currentFragmentFromSaved != null) {
			if (currentFragmentFromSaved instanceof PageFragment &&
					Chan.get(((PageFragment) currentFragmentFromSaved).getPage().chanName).name == null) {
				currentFragmentFromSaved = null;
				currentPageItem = null;
			}
			if (currentFragmentFromSaved == null && !stackPageItems.isEmpty()) {
				Pair<PageFragment, PageItem> pair = stackPageItems.remove(stackPageItems.size() - 1).create();
				currentFragmentFromSaved = pair.first;
				currentPageItem = pair.second;
			}
			if (currentFragmentFromSaved != null) {
				getSupportFragmentManager().beginTransaction()
						.replace(R.id.content_fragment, currentFragmentFromSaved)
						.commit();
				updatePostFragmentConfiguration();
			}
		} else {
			ContentFragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment &&
					Chan.get(((PageFragment) currentFragment).getPage().chanName).name == null) {
				currentFragment = null;
				currentPageItem = null;
			}
			if (currentFragment != null) {
				updatePostFragmentConfiguration();
			}
		}

		if (!FlagUtils.get(getIntent().getFlags(), Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) &&
				savedInstanceState == null) {
			navigateIntent(getIntent(), false);
		}
		if (getCurrentFragment() == null) {
			if (!navigateInitial(false)) {
				ClickableToast.show(getString(R.string.no_extensions_installed), null,
						new ClickableToast.Button(R.string.install, false, () -> {
							if (getCurrentFragment() instanceof UpdateFragment) {
								navigateFragment(new UpdateFragment(), null, true);
							} else {
								pushFragment(new UpdateFragment());
							}
						}));
			}
		}

		startUpdateTask(savedInstanceState == null);
		ExtensionsTrustLoop.handleUntrustedExtensions(this, extensionsTrustLoopState);
		if (storageRequestState == StorageRequestState.INSTRUCTIONS) {
			showStorageInstructionsDialog();
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
		outState.putBoolean(EXTRA_DRAWER_EXPANDED, drawerLayout.isDrawerOpen(GravityCompat.START));
		outState.putBoolean(EXTRA_DRAWER_CHAN_SELECT_MODE, drawerForm.isChanSelectMode());
		outState.putString(EXTRA_STORAGE_REQUEST_STATE, storageRequestState.name());
	}

	private void writePagesState(Bundle outState) {
		outState.putParcelableArrayList(EXTRA_FRAGMENTS, fragments);
		outState.putParcelableArrayList(EXTRA_STACK_PAGE_ITEMS, stackPageItems);
		outState.putParcelableArrayList(EXTRA_PRESERVED_PAGE_ITEMS, preservedPageItems);
		outState.putParcelable(EXTRA_CURRENT_PAGE_ITEM, currentPageItem);
	}

	private File getSavedPagesFile() {
		return CacheManager.getInstance().getInternalCacheFile("saved-pages");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == C.REQUEST_CODE_OPEN_URI_TREE) {
			boolean cancel = resultCode != RESULT_OK;
			storageRequestState = StorageRequestState.NONE;
			if (!cancel && data != null) {
				Preferences.setDownloadUriTree(this, data.getData(), data.getFlags());
			}
			handleStorageRequestResult(cancel);
		}
	}

	private ContentFragment getCurrentFragment() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		try {
			fragmentManager.executePendingTransactions();
		} catch (IllegalStateException e) {
			// Ignore exception
		}
		return (ContentFragment) fragmentManager.findFragmentById(R.id.content_fragment);
	}

	@Override
	public void setTitleSubtitle(CharSequence title, CharSequence subtitle) {
		if (C.API_LOLLIPOP) {
			toolbarHolder.update(title, subtitle);
		} else {
			setTitle(title);
			getActionBar().setSubtitle(subtitle);
		}
	}

	@Override
	public ViewGroup getToolbarView() {
		if (toolbarHolder == null) {
			throw new IllegalStateException();
		}
		return toolbarHolder.toolbar;
	}

	@Override
	public FrameLayout getToolbarExtra() {
		if (toolbarExtra == null) {
			throw new IllegalStateException();
		}
		return toolbarExtra;
	}

	@Override
	public Context getToolbarContext() {
		return toolbarHolder != null ? toolbarHolder.toolbar.getContext() : this;
	}

	@Override
	public void navigateBoardsOrThreads(String chanName, String boardName) {
		navigateBoardsOrThreads(chanName, boardName, false, false);
	}

	private void navigateBoardsOrThreads(String chanName, String boardName, boolean fromCache, boolean allowReturn) {
		navigateData(chanName, boardName, null, null, null, null, FLAG_DATA_CLOSE_OVERLAYS |
				(fromCache ? FLAG_DATA_FROM_CACHE : 0) | (allowReturn ? FLAG_DATA_ALLOW_RETURN : 0));
	}

	@Override
	public void navigatePosts(String chanName, String boardName, String threadNumber,
			PostNumber postNumber, String threadTitle) {
		navigatePosts(chanName, boardName, threadNumber, postNumber, threadTitle, false, false);
	}

	private void navigatePosts(String chanName, String boardName, String threadNumber,
			PostNumber postNumber, String threadTitle, boolean fromCache, boolean allowReturn) {
		navigateData(chanName, boardName, threadNumber, postNumber, threadTitle, null, FLAG_DATA_CLOSE_OVERLAYS |
				(fromCache ? FLAG_DATA_FROM_CACHE : 0) | (allowReturn ? FLAG_DATA_ALLOW_RETURN : 0));
	}

	@Override
	public void navigateSearch(String chanName, String boardName, String searchQuery) {
		navigateSearch(chanName, boardName, searchQuery, false);
	}

	private void navigateSearch(String chanName, String boardName, String searchQuery, boolean allowReturn) {
		navigateData(chanName, boardName, null, null, null, searchQuery, FLAG_DATA_CLOSE_OVERLAYS |
				(allowReturn ? FLAG_DATA_ALLOW_RETURN : 0));
	}

	@Override
	public void navigateArchive(String chanName, String boardName) {
		navigatePage(Page.Content.ARCHIVE, chanName, boardName, null, null, null, null, FLAG_PAGE_CLOSE_OVERLAYS);
	}

	@Override
	public void navigateTargetAllowReturn(String chanName, ChanLocator.NavigationData data) {
		switch (data.target) {
			case THREADS: {
				navigateBoardsOrThreads(chanName, data.boardName, false, true);
				break;
			}
			case POSTS: {
				navigatePosts(chanName, data.boardName, data.threadNumber, data.postNumber, null, false, true);
				break;
			}
			case SEARCH: {
				navigateSearch(chanName, data.boardName, data.searchQuery, true);
				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}
	}

	@Override
	public void navigatePosting(String chanName, String boardName, String threadNumber, Replyable.ReplyData... data) {
		fragments.clear();
		navigateFragment(new PostingFragment(chanName, boardName, threadNumber, Arrays.asList(data)), null, true);
	}

	@Override
	public void navigateGallery(String chanName, GalleryItem.Set gallerySet, int imageIndex,
			View view, GalleryOverlay.NavigatePostMode navigatePostMode, boolean galleryMode) {
		List<GalleryItem> galleryItems = gallerySet.createList();
		navigatePostMode = gallerySet.isNavigatePostSupported() ? navigatePostMode
				: GalleryOverlay.NavigatePostMode.DISABLED;
		navigateOrCloseGallery(new GalleryOverlay(chanName, galleryItems, imageIndex, gallerySet.getThreadTitle(),
				view, navigatePostMode, galleryMode));
	}

	private void navigateGalleryUri(Uri uri) {
		navigateOrCloseGallery(new GalleryOverlay(uri));
	}

	private void navigateOrCloseGallery(GalleryOverlay galleryOverlay) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		String tag = GalleryOverlay.class.getName();
		GalleryOverlay currentGalleryOverlay = (GalleryOverlay) fragmentManager.findFragmentByTag(tag);
		if (currentGalleryOverlay != null) {
			currentGalleryOverlay.dismiss();
		}
		if (galleryOverlay != null) {
			galleryOverlay.show(fragmentManager, tag);
		}
	}

	@Override
	public void navigateSetTheme(ThemeEngine.Theme theme) {
		ConcurrentUtils.HANDLER.post(() -> {
			if (ThemeEngine.addTheme(theme)) {
				Preferences.setTheme(theme.name);
				recreate();
			}
		});
	}

	@Override
	public void scrollToPost(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
		ContentFragment fragment = getCurrentFragment();
		if (fragment instanceof PageFragment) {
			Page page = ((PageFragment) fragment).getPage();
			if (page.content == Page.Content.POSTS && page.chanName.equals(chanName) &&
					CommonUtils.equals(page.boardName, boardName) && page.threadNumber.equals(threadNumber)) {
				((PageFragment) fragment).scrollToPost(postNumber);
			}
		}
	}

	private void navigateIntent(Intent intent, boolean newIntent) {
		if (newIntent) {
			navigateIntentOnResume = intent;
		} else {
			navigateIntentUnchecked(intent);
		}
	}

	private void navigateIntentUnchecked(Intent intent) {
		ReadUpdateTask.UpdateDataMap updateDataMap = intent.getParcelableExtra(C.EXTRA_UPDATE_DATA_MAP);
		if (updateDataMap != null) {
			fragments.clear();
			navigateFragment(new UpdateFragment(updateDataMap), null, true);
		} else if (C.ACTION_POSTING.equals(intent.getAction())) {
			String chanName = intent.getStringExtra(C.EXTRA_CHAN_NAME);
			String boardName = intent.getStringExtra(C.EXTRA_BOARD_NAME);
			String threadNumber = intent.getStringExtra(C.EXTRA_THREAD_NUMBER);
			PostingService.FailResult failResult = intent.getParcelableExtra(C.EXTRA_FAIL_RESULT);
			ContentFragment currentFragment = getCurrentFragment();
			boolean replace = true;
			if (currentFragment instanceof PostingFragment &&
					((PostingFragment) currentFragment).check(chanName, boardName, threadNumber)) {
				replace = false;
			}
			if (replace) {
				fragments.clear();
				navigateFragment(new PostingFragment(chanName, boardName, threadNumber,
						Collections.emptyList()), null, true);
				currentFragment = getCurrentFragment();
			}
			if (failResult != null) {
				((PostingFragment) currentFragment).handleFailResult(failResult);
			}
		} else if (C.ACTION_GALLERY.equals(intent.getAction())) {
			navigateGalleryUri(intent.getData());
		} else if (C.ACTION_PLAYER.equals(intent.getAction())) {
			FragmentManager fragmentManager = getSupportFragmentManager();
			String tag = AudioPlayerDialog.class.getName();
			if (fragmentManager.findFragmentByTag(tag) == null) {
				new AudioPlayerDialog().show(fragmentManager, tag);
			}
		} else if (C.ACTION_BROWSER.equals(intent.getAction())) {
			BrowserFragment browserFragment = new BrowserFragment(intent.getData());
			if (getCurrentFragment() instanceof BrowserFragment) {
				navigateFragment(browserFragment, null, true);
			} else {
				pushFragment(browserFragment);
			}
		} else {
			Uri uri = intent.getData();
			if (uri != null) {
				if (intent.getBooleanExtra(C.EXTRA_FROM_CLIENT, false) || !navigateUri(uri)) {
					ClickableToast.show(R.string.unknown_address);
				}
			} else {
				String chanName = intent.getStringExtra(C.EXTRA_CHAN_NAME);
				String boardName = intent.getStringExtra(C.EXTRA_BOARD_NAME);
				String threadNumber = intent.getStringExtra(C.EXTRA_THREAD_NUMBER);
				PostNumber postNumber = PostNumber.parseNullable(intent.getStringExtra(C.EXTRA_POST_NUMBER));
				navigateData(chanName, boardName, threadNumber, postNumber, null, null, FLAG_DATA_CLOSE_OVERLAYS);
			}
		}
	}

	private boolean navigateUri(Uri uri) {
		Chan chan = Chan.getPreferred(null, uri);
		if (chan.name != null) {
			boolean boardUri = chan.locator.safe(false).isBoardUri(uri);
			boolean threadUri = chan.locator.safe(false).isThreadUri(uri);
			String boardName = boardUri || threadUri ? chan.locator.safe(false).getBoardName(uri) : null;
			String threadNumber = threadUri ? chan.locator.safe(false).getThreadNumber(uri) : null;
			PostNumber postNumber = threadUri ? chan.locator.safe(false).getPostNumber(uri) : null;
			if (boardUri) {
				navigateData(chan.name, boardName, null, null, null, null,
						FLAG_DATA_CLOSE_OVERLAYS | FLAG_DATA_ALLOW_RETURN);
				return true;
			} else if (threadUri) {
				navigateData(chan.name, boardName, threadNumber, postNumber, null, null,
						FLAG_DATA_CLOSE_OVERLAYS | FLAG_DATA_ALLOW_RETURN);
				return true;
			} else if (chan.locator.isImageUri(uri)) {
				navigateGalleryUri(uri);
				return true;
			} else if (chan.locator.isAudioUri(uri)) {
				AudioPlayerService.start(this, chan.name, uri, chan.locator.createAttachmentFileName(uri));
				return true;
			} else if (chan.locator.isVideoUri(uri)) {
				String fileName = chan.locator.createAttachmentFileName(uri);
				if (NavigationUtils.isOpenableVideoPath(fileName)) {
					navigateGalleryUri(chan.locator.convert(uri));
				} else {
					NavigationUtils.handleUri(this, chan.name, chan.locator.convert(uri),
							NavigationUtils.BrowserType.EXTERNAL);
				}
				return true;
			} else if (Preferences.isUseInternalBrowser()) {
				NavigationUtils.handleUri(this, chan.name, chan.locator.convert(uri),
						NavigationUtils.BrowserType.INTERNAL);
				return true;
			}
		}
		return false;
	}

	private static boolean isSingleBoardMode(Chan chan) {
		return chan.configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
	}

	private static String getSingleBoardName(Chan chan) {
		return chan.configuration.getSingleBoardName();
	}

	private Page getSavedPage(SavedPageItem savedPageItem) {
		REFERENCE_FRAGMENT.setArguments(savedPageItem.stackItem.arguments);
		return REFERENCE_FRAGMENT.getPage();
	}

	private int getPagesStackSize(String chanName) {
		boolean mergeChans = Preferences.isMergeChans();
		int size = 0;
		ContentFragment currentFragment = getCurrentFragment();
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
		if (allowForeignChan && currentPageItem.allowReturn && !stackPageItems.isEmpty()) {
			return stackPageItems.remove(stackPageItems.size() - 1);
		}
		ContentFragment currentFragment = getCurrentFragment();
		String chanName = ((PageFragment) currentFragment).getPage().chanName;
		boolean mergeChans = Preferences.isMergeChans();
		for (int i = stackPageItems.size() - 1; i >= 0; i--) {
			SavedPageItem savedPageItem = stackPageItems.get(i);
			if (mergeChans || getSavedPage(savedPageItem).chanName.equals(chanName)) {
				stackPageItems.remove(i);
				return savedPageItem;
			}
		}
		return null;
	}

	private void clearStackAndCurrent() {
		ContentFragment currentFragment = getCurrentFragment();
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

	private boolean navigateInitial(boolean closeOverlays) {
		currentPageItem = null;
		Chan chan = ChanManager.getInstance().getDefaultChan();
		if (chan != null) {
			navigateData(chan.name, Preferences.getDefaultBoardName(chan),
					null, null, null, null, closeOverlays ? FLAG_DATA_CLOSE_OVERLAYS : 0);
			return true;
		} else {
			navigateFragment(new CategoriesFragment(), null, closeOverlays);
			return false;
		}
	}

	private static final int FLAG_DATA_CLOSE_OVERLAYS = 0x00000001;
	private static final int FLAG_DATA_FROM_CACHE = 0x00000002;
	private static final int FLAG_DATA_ALLOW_RETURN = 0x00000004;

	private void navigateData(String chanName, String boardName, String threadNumber, PostNumber postNumber,
			String threadTitle, String searchQuery, int dataFlags) {
		Chan chan = Chan.get(chanName);
		if (chan.name == null) {
			return;
		}
		boolean forceBoardPage = false;
		if (isSingleBoardMode(chan)) {
			boardName = getSingleBoardName(chan);
			forceBoardPage = true;
		}
		int pageFlags = 0;
		pageFlags = FlagUtils.set(pageFlags, FLAG_PAGE_CLOSE_OVERLAYS,
				FlagUtils.get(dataFlags, FLAG_DATA_CLOSE_OVERLAYS));
		pageFlags = FlagUtils.set(pageFlags, FLAG_PAGE_ALLOW_RETURN,
				FlagUtils.get(dataFlags, FLAG_DATA_ALLOW_RETURN));
		if (boardName != null || threadNumber != null || forceBoardPage) {
			pageFlags = FlagUtils.set(pageFlags, FLAG_PAGE_FROM_CACHE,
					FlagUtils.get(dataFlags, FLAG_DATA_FROM_CACHE));
			Page.Content content = searchQuery != null ? Page.Content.SEARCH
					: threadNumber == null ? Page.Content.THREADS : Page.Content.POSTS;
			navigatePage(content, chan.name, boardName, threadNumber, postNumber, threadTitle, searchQuery, pageFlags);
		} else {
			String currentChanName = null;
			ContentFragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment) {
				currentChanName = ((PageFragment) currentFragment).getPage().chanName;
			}
			if (getPagesStackSize(chan.name) == 0 || !chan.name.equals(currentChanName)) {
				navigatePage(Page.Content.BOARDS, chan.name, null, null, null, null, null, pageFlags);
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

	private static final int FLAG_PAGE_CLOSE_OVERLAYS = 0x00000001;
	private static final int FLAG_PAGE_FROM_CACHE = 0x00000002;
	private static final int FLAG_PAGE_ALLOW_RETURN = 0x00000004;
	private static final int FLAG_PAGE_RESET_SCROLL = 0x00000008;

	private void navigatePage(Page.Content content, String chanName, String boardName,
			String threadNumber, PostNumber postNumber, String threadTitle, String searchQuery, int pageFlags) {
		ContentFragment currentFragment = getCurrentFragment();
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
				if (FlagUtils.get(pageFlags, FLAG_PAGE_CLOSE_OVERLAYS)) {
					closeOverlaysForNavigation();
				}
				currentPageItem.allowReturn &= FlagUtils.get(pageFlags, FLAG_PAGE_ALLOW_RETURN);
				((PageFragment) currentFragment).updatePageConfiguration(postNumber);
				invalidateHomeUpState();
				return;
			}
		}
		Pair<PageFragment, PageItem> pair;
		boolean fromCache = FlagUtils.get(pageFlags, FLAG_PAGE_FROM_CACHE);
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
		pair.second.allowReturn = FlagUtils.get(pageFlags, FLAG_PAGE_ALLOW_RETURN);
		if (FlagUtils.get(pageFlags, FLAG_PAGE_RESET_SCROLL)) {
			pair.first.requestResetScroll();
		}
		navigateFragment(pair.first, pair.second, FlagUtils.get(pageFlags, FLAG_PAGE_CLOSE_OVERLAYS));
	}

	private void navigateSavedPage(SavedPageItem savedPageItem, boolean closeOverlays) {
		Pair<PageFragment, PageItem> pair = savedPageItem.create();
		navigateFragment(pair.first, pair.second, closeOverlays);
	}

	@Override
	public void pushFragment(ContentFragment fragment) {
		ContentFragment currentFragment = getCurrentFragment();
		if (!(currentFragment instanceof PageFragment)) {
			StackItem stackItem = new StackItem(getSupportFragmentManager(), currentFragment, null);
			fragments.add(stackItem);
		}
		navigateFragment(fragment, null, true);
	}

	private void navigateFragment(ContentFragment fragment, PageItem pageItem, boolean closeOverlays) {
		if (closeOverlays) {
			closeOverlaysForNavigation();
		}
		FragmentManager fragmentManager = getSupportFragmentManager();
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			// currentPageItem == null means page was deleted
			if (currentPageItem != null) {
				stackPageItems.add(currentPageItem.toSaved(fragmentManager,
						(PageFragment) currentFragment));
			}
			if (fragment instanceof PageFragment) {
				PostingService.clearNewThreadData();
			}
		} else if (fragment instanceof PageFragment) {
			fragments.clear();
		}

		if (currentFragment != null) {
			currentFragment.onTerminate();
		}
		ClickableToast.cancel();
		InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		if (inputMethodManager != null) {
			View view = getCurrentFocus();
			inputMethodManager.hideSoftInputFromWindow((view != null
					? view : getWindow().getDecorView()).getWindowToken(), 0);
		}
		if (pageItem != null) {
			pageItem.createdRealtime = SystemClock.elapsedRealtime();
		}
		currentPageItem = pageItem;
		fragmentManager.beginTransaction()
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
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
			Iterator<HashMap.Entry<String, ListPage.Retainable>> iterator =
					instanceViewModel.extras.entrySet().iterator();
			while (iterator.hasNext()) {
				HashMap.Entry<String, ListPage.Retainable> entry = iterator.next();
				if (!retainIds.contains(entry.getKey())) {
					entry.getValue().clear();
					iterator.remove();
				}
			}
		}
	}

	private void closeOverlaysForNavigation() {
		navigateOrCloseGallery(null);
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	private void updatePostFragmentConfiguration() {
		ContentFragment currentFragment = getCurrentFragment();
		String chanName;
		if (currentFragment instanceof PageFragment) {
			chanName = ((PageFragment) currentFragment).getPage().chanName;
		} else if (!stackPageItems.isEmpty()) {
			chanName = getSavedPage(stackPageItems.get(stackPageItems.size() - 1)).chanName;
		} else {
			Chan chan = ChanManager.getInstance().getDefaultChan();
			chanName = chan != null ? chan.name : null;
		}
		if (currentFragment instanceof PageFragment) {
			expandedScreen.removeLocker(LOCKER_NON_PAGE);
		} else {
			expandedScreen.addLocker(LOCKER_NON_PAGE);
		}
		watcherServiceClient.updateConfiguration(chanName);
		drawerForm.updateConfiguration(chanName);
		invalidateHomeUpState();
	}

	@Override
	public void onDialogStackOpen() {
		if (!wideMode) {
			drawerLayout.closeDrawers();
		}
	}

	@Override
	public DownloadService.Binder getDownloadBinder() {
		return downloadBinder;
	}

	@Override
	public WatcherService.Client getWatcherClient() {
		return watcherServiceClient;
	}

	@Override
	public UiManager getUiManager() {
		return uiManager;
	}

	@Override
	public ListPage.Retainable getRetainableExtra(String retainId) {
		return instanceViewModel.extras.get(retainId);
	}

	@Override
	public void storeRetainableExtra(String retainId, ListPage.Retainable extra) {
		if (extra != null) {
			instanceViewModel.extras.put(retainId, extra);
		} else {
			instanceViewModel.extras.remove(retainId);
		}
	}

	@Override
	public void invalidateHomeUpState() {
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment != null && currentFragment.isSearchMode()) {
			drawerToggle.setDrawerIndicatorMode(DrawerToggle.Mode.UP);
		} else {
			boolean displayUp;
			if (currentFragment instanceof PageFragment) {
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
					default: {
						displayUp = false;
						break;
					}
				}
			} else {
				displayUp = !stackPageItems.isEmpty() || !fragments.isEmpty();
			}
			drawerToggle.setDrawerIndicatorMode(displayUp ? DrawerToggle.Mode.UP : wideMode
					? DrawerToggle.Mode.DISABLED : DrawerToggle.Mode.DRAWER);
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
			drawerLayout.setDrawerLockMode(wideMode ? CustomDrawerLayout.LOCK_MODE_LOCKED_CLOSED
					: CustomDrawerLayout.LOCK_MODE_UNLOCKED);
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
	public boolean requestStorage() {
		if (storageRequestState == StorageRequestState.NONE) {
			storageRequestState = StorageRequestState.INSTRUCTIONS;
			showStorageInstructionsDialog();
			return true;
		}
		return false;
	}

	@Override
	protected void onStart() {
		super.onStart();

		handleChansChangedDelayed();
		watcherServiceClient.notifyForeground();
	}

	@Override
	protected void onResume() {
		super.onResume();

		drawerForm.updateRestartViewVisibility();
		drawerForm.updateItems(true, true);
		updateWideConfiguration(false);
		handleChansChangedDelayed();
		ClickableToast.register(this);
		ForegroundManager.getInstance().register(this);

		Intent navigateIntentOnResume = this.navigateIntentOnResume;
		this.navigateIntentOnResume = null;
		if (navigateIntentOnResume != null) {
			navigateIntentUnchecked(navigateIntentOnResume);
		}

		if (downloadBinder != null) {
			downloadBinder.notifyReadyToHandleRequests();
		}
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

		if (postingBinder != null) {
			postingBinder.unregister(postingGlobalCallback);
			postingBinder = null;
		}
		if (downloadBinder != null) {
			downloadBinder.unregister(downloadCallback);
			downloadBinder = null;
		}
		unbindService(postingConnection);
		unbindService(downloadConnection);
		watcherServiceClient.setCallback(null);
		FavoritesStorage.getInstance().getObservable().unregister(this);
		Preferences.PREFERENCES.unregisterOnSharedPreferenceChangeListener(preferencesListener);
		ChanManager.getInstance().observable.unregister(chanManagerCallback);
		for (Chan chan : ChanManager.getInstance().getAvailableChans()) {
			chan.configuration.commit();
		}
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(C.NOTIFICATION_ID_UPDATES);
		FavoritesStorage.getInstance().await(true);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public boolean onSearchRequested() {
		ContentFragment currentFragment = getCurrentFragment();
		return currentFragment.onSearchRequested();
	}

	@Override
	public void removeFragment() {
		onBackPressed(true, false, () -> navigateInitial(true));
	}

	private long backPressed = 0;

	@Override
	public void onBackPressed() {
		onBackPressed(false, true, super::onBackPressed);
	}

	private void onBackPressed(boolean homeHandled, boolean allowTimeout, Runnable close) {
		if (!wideMode && drawerLayout.isDrawerOpen(GravityCompat.START)) {
			drawerLayout.closeDrawers();
		} else {
			ContentFragment currentFragment = getCurrentFragment();
			if (!homeHandled && currentFragment.onBackPressed()) {
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
					navigateSavedPage(savedPageItem, true);
					handled = true;
				}
			} else if (!fragments.isEmpty()) {
				ContentFragment fragment = (ContentFragment) fragments.remove(fragments.size() - 1).create(null);
				navigateFragment(fragment, null, true);
				handled = true;
			} else if (!stackPageItems.isEmpty()) {
				navigateSavedPage(stackPageItems.remove(stackPageItems.size() - 1), true);
				handled = true;
			}
			if (!handled) {
				if (allowTimeout && SystemClock.elapsedRealtime() - backPressed > 2000) {
					ClickableToast.show(R.string.press_again_to_exit);
					backPressed = SystemClock.elapsedRealtime();
				} else {
					close.run();
				}
			}
		}
	}

	private WeakReference<ActionMode> currentActionMode;

	@Override
	public void onActionModeStarted(ActionMode mode) {
		super.onActionModeStarted(mode);
		expandedScreen.setActionModeState(true);
		if (currentActionMode == null) {
			setNavigationAreaLocked(LOCKER_ACTION_MODE, true);
		}
		currentActionMode = new WeakReference<>(mode);
	}

	@Override
	public void onActionModeFinished(ActionMode mode) {
		if (currentActionMode != null) {
			if (currentActionMode.get() == mode) {
				currentActionMode = null;
				setNavigationAreaLocked(LOCKER_ACTION_MODE, false);
			}
		}
		super.onActionModeFinished(mode);
		expandedScreen.setActionModeState(false);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		MenuItem appearanceOptionsItem = menu.findItem(R.id.menu_appearance);
		if (appearanceOptionsItem != null) {
			Menu appearanceOptionsMenu = appearanceOptionsItem.getSubMenu();
			if (appearanceOptionsMenu.size() == 0) {
				appearanceOptionsMenu.add(0, R.id.menu_change_theme, 0,
						R.string.change_theme);
				appearanceOptionsMenu.add(0, R.id.menu_expanded_screen, 0,
						R.string.expanded_screen).setCheckable(true);
				appearanceOptionsMenu.add(0, R.id.menu_spoilers, 0,
						R.string.spoilers).setCheckable(true);
				appearanceOptionsMenu.add(0, R.id.menu_my_posts, 0,
						R.string.my_posts).setCheckable(true);
				appearanceOptionsMenu.add(0, R.id.menu_drawer, 0,
						R.string.lock_navigation).setCheckable(true);
				appearanceOptionsMenu.add(0, R.id.menu_sfw_mode, 0,
						R.string.sfw_mode).setCheckable(true);
			}
			appearanceOptionsMenu.findItem(R.id.menu_expanded_screen)
					.setChecked(Preferences.isExpandedScreen());
			appearanceOptionsMenu.findItem(R.id.menu_spoilers)
					.setChecked(Preferences.isShowSpoilers());
			appearanceOptionsMenu.findItem(R.id.menu_my_posts)
					.setChecked(Preferences.isShowMyPosts());
			appearanceOptionsMenu.findItem(R.id.menu_drawer)
					.setVisible(ViewUtils.isDrawerLockable(getResources().getConfiguration()))
					.setChecked(Preferences.isDrawerLocked());
			appearanceOptionsMenu.findItem(R.id.menu_sfw_mode)
					.setChecked(Preferences.isSfwMode());
		}
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		ContentFragment currentFragment = getCurrentFragment();
		switch (item.getItemId()) {
			case android.R.id.home: {
				if (currentFragment.onHomePressed()) {
					return true;
				}
				drawerLayout.closeDrawers();
				if (currentFragment instanceof PageFragment) {
					Page page = ((PageFragment) currentFragment).getPage();
					String newChanName = page.chanName;
					String newBoardName = page.boardName;
					if (page.content == Page.Content.THREADS) {
						// Up button must navigate to main page in threads list
						newBoardName = Preferences.getDefaultBoardName(Chan.get(page.chanName));
						if (Preferences.isMergeChans() && CommonUtils.equals(page.boardName, newBoardName)) {
							Chan chan = ChanManager.getInstance().getDefaultChan();
							newChanName = chan.name;
							newBoardName = Preferences.getDefaultBoardName(chan);
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
					navigateData(newChanName, newBoardName, null, null, null, null,
							FLAG_DATA_CLOSE_OVERLAYS | (fromCache ? FLAG_DATA_FROM_CACHE : 0));
				} else {
					onBackPressed(true, true, super::onBackPressed);
				}
				return true;
			}
			case R.id.menu_change_theme:
			case R.id.menu_expanded_screen:
			case R.id.menu_spoilers:
			case R.id.menu_my_posts:
			case R.id.menu_drawer:
			case R.id.menu_sfw_mode: {
				try {
					switch (item.getItemId()) {
						case R.id.menu_change_theme: {
							new ThemeDialog().show(getSupportFragmentManager(), ThemeDialog.class.getName());
							return true;
						}
						case R.id.menu_expanded_screen: {
							Preferences.setExpandedScreen(!item.isChecked());
							recreate();
							return true;
						}
						case R.id.menu_spoilers: {
							Preferences.setShowSpoilers(!item.isChecked());
							return true;
						}
						case R.id.menu_my_posts: {
							Preferences.setShowMyPosts(!item.isChecked());
							return true;
						}
						case R.id.menu_drawer: {
							Preferences.setDrawerLocked(!item.isChecked());
							updateWideConfiguration(false);
							return true;
						}
						case R.id.menu_sfw_mode: {
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

	@Override
	public void onThemeSelected(ThemeEngine.Theme theme) {
		if (theme != null) {
			Preferences.setTheme(theme.name);
			recreate();
		} else {
			fragments.clear();
			navigateFragment(new ThemesFragment(), null, true);
		}
	}

	private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener
			= (p, key) -> drawerForm.updatePreferences();

	@Override
	public void onSelectChan(String chanName) {
		ContentFragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (page == null || !page.chanName.equals(chanName)) {
			Chan chan = Chan.get(chanName);
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
					navigateSavedPage(lastSavedPageItem, true);
				} else {
					navigateBoardsOrThreads(chanName, Preferences.getDefaultBoardName(chan), false, false);
				}
			} else {
				// Open root page. If page is already opened, load it from cache.
				boolean fromCache = false;
				String boardName = Preferences.getDefaultBoardName(chan);
				for (SavedPageItem savedPageItem : new ConcatIterable<>(preservedPageItems, stackPageItems)) {
					if (getSavedPage(savedPageItem).is(Page.Content.THREADS, chanName, boardName, null)) {
						fromCache = true;
						break;
					}
				}
				navigateBoardsOrThreads(chanName, boardName, fromCache, false);
			}
			drawerForm.updateConfiguration(chanName);
		} else {
			closeOverlaysForNavigation();
		}
	}

	@Override
	public void onSelectBoard(String chanName, String boardName, boolean fromCache) {
		ContentFragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		Chan chan = Chan.get(chanName);
		if (isSingleBoardMode(chan)) {
			boardName = getSingleBoardName(chan);
		}
		if (page == null || !page.is(Page.Content.THREADS, chanName, boardName, null)) {
			navigateBoardsOrThreads(chanName, boardName, fromCache, false);
		} else {
			closeOverlaysForNavigation();
		}
	}

	@Override
	public boolean onSelectThread(String chanName, String boardName, String threadNumber, PostNumber postNumber,
			String threadTitle, boolean fromCache) {
		ContentFragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		Chan chan = Chan.get(chanName);
		if (isSingleBoardMode(chan)) {
			boardName = getSingleBoardName(chan);
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
			navigatePosts(chanName, boardName, threadNumber, postNumber, threadTitle, fromCache, false);
		} else {
			closeOverlaysForNavigation();
		}
		return true;
	}

	@Override
	public void onClosePage(String chanName, String boardName, String threadNumber) {
		ContentFragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		if (page != null && page.isThreadsOrPosts(chanName, boardName, threadNumber)) {
			SavedPageItem savedPageItem = prepareTargetPreviousPage(false);
			currentPageItem = null;
			if (savedPageItem != null) {
				navigateSavedPage(savedPageItem, false);
			} else {
				Chan chan = Chan.get(chanName);
				if (isSingleBoardMode(chan)) {
					navigatePage(Page.Content.THREADS, chanName,
							getSingleBoardName(chan), null, null, null, null, FLAG_PAGE_FROM_CACHE);
				} else {
					navigatePage(Page.Content.BOARDS, chanName,
							null, null, null, null, null, FLAG_PAGE_FROM_CACHE);
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
			drawerForm.updateItems(true, false);
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
		ContentFragment currentFragment = getCurrentFragment();
		Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
		String chanName = page != null ? page.chanName : null;
		if (chanName == null && !stackPageItems.isEmpty()) {
			chanName = getSavedPage(stackPageItems.get(stackPageItems.size() - 1)).chanName;
		}
		if (chanName != null) {
			Chan chan = Chan.get(chanName);
			String boardName = Preferences.getDefaultBoardName(chan);
			boolean singleBoardMode = isSingleBoardMode(chan);
			String singleBoardName = getSingleBoardName(chan);
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
				navigateData(chanName, boardName, null, null, null, null, cached ? FLAG_DATA_FROM_CACHE : 0);
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
		drawerForm.updateItems(true, false);
		invalidateHomeUpState();
	}

	@Override
	public int onEnterNumber(int number) {
		int result = 0;
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			result = ((PageFragment) currentFragment).onDrawerNumberEntered(number);
		}
		if (!wideMode && FlagUtils.get(result, DrawerForm.RESULT_SUCCESS)) {
			drawerLayout.closeDrawers();
		}
		return result;
	}

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
				if (!(getCurrentFragment() instanceof CategoriesFragment)) {
					fragments.clear();
					navigateFragment(new CategoriesFragment(), null, true);
				}
				break;
			}
		}
		boolean success = false;
		if (content != null) {
			ContentFragment currentFragment = getCurrentFragment();
			Page page = currentFragment instanceof PageFragment ? ((PageFragment) currentFragment).getPage() : null;
			if (page == null || page.content != content) {
				if (page == null && !stackPageItems.isEmpty()) {
					page = getSavedPage(stackPageItems.get(stackPageItems.size() - 1));
				}
				String chanName = page != null ? page.chanName : null;
				String boardName = page != null ? page.boardName : null;
				if (chanName == null) {
					Chan chan = ChanManager.getInstance().getDefaultChan();
					chanName = chan.name;
					boardName = Preferences.getDefaultBoardName(chan);
				}
				if (chanName != null) {
					navigatePage(content, chanName, boardName, null, null, null, null,
							FLAG_PAGE_CLOSE_OVERLAYS | FLAG_PAGE_RESET_SCROLL);
					success = true;
				}
			}
		}
		if (!success) {
			closeOverlaysForNavigation();
		}
	}

	@Override
	public void onDraggingStateChanged(boolean dragging) {
		if (!wideMode) {
			drawerLayout.setDrawerLockMode(dragging ? CustomDrawerLayout.LOCK_MODE_LOCKED_OPEN
					: CustomDrawerLayout.LOCK_MODE_UNLOCKED);
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
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			Page page = ((PageFragment) currentFragment).getPage();
			if (page.isThreadsOrPosts()) {
				drawerPages.add(new DrawerForm.Page(page.chanName, page.boardName, page.threadNumber,
						currentPageItem.threadTitle, currentPageItem.createdRealtime));
			}
		}
		return drawerPages;
	}

	private final HashSet<String> changedChanNames = new HashSet<>();
	private final HashSet<String> removedChanNames = new HashSet<>();

	private void handleChansChangedDelayed() {
		if (removedChanNames.isEmpty()) {
			if (!changedChanNames.isEmpty()) {
				ContentFragment currentFragment = getCurrentFragment();
				if (currentFragment instanceof FragmentHandler.Callback) {
					((FragmentHandler.Callback) currentFragment)
							.onChansChanged(Collections.unmodifiableSet(changedChanNames), Collections.emptySet());
				}
			}
		} else {
			Iterator<SavedPageItem> iterator = new ConcatIterable<>(preservedPageItems, stackPageItems).iterator();
			while (iterator.hasNext()) {
				if (removedChanNames.contains(getSavedPage(iterator.next()).chanName)) {
					iterator.remove();
				}
			}
			ContentFragment currentFragment = getCurrentFragment();
			if (currentFragment instanceof PageFragment &&
					removedChanNames.contains(((PageFragment) currentFragment).getPage().chanName)) {
				if (!stackPageItems.isEmpty()) {
					currentPageItem = null;
					navigateSavedPage(stackPageItems.remove(stackPageItems.size() - 1), true);
				} else {
					navigateInitial(true);
				}
			} else if (currentFragment instanceof FragmentHandler.Callback) {
				((FragmentHandler.Callback) currentFragment)
						.onChansChanged(Collections.unmodifiableSet(changedChanNames),
								Collections.unmodifiableSet(removedChanNames));
			}
			String galleryTag = GalleryOverlay.class.getName();
			GalleryOverlay currentGalleryOverlay = (GalleryOverlay) getSupportFragmentManager()
					.findFragmentByTag(galleryTag);
			if (currentGalleryOverlay != null && removedChanNames.contains(currentGalleryOverlay.getChanName())) {
				currentGalleryOverlay.dismiss();
			}
		}
		if (!changedChanNames.isEmpty() || !removedChanNames.isEmpty()) {
			changedChanNames.clear();
			removedChanNames.clear();
			drawerForm.updateChans();
			updatePostFragmentConfiguration();
		}
	}

	private final ChanManager.Callback chanManagerCallback = new ChanManager.Callback() {
		@Override
		public void onRestartRequiredChanged() {
			drawerForm.updateRestartViewVisibility();
		}

		@Override
		public void onUntrustedExtensionInstalled() {
			ExtensionsTrustLoop.handleUntrustedExtensions(MainActivity.this, extensionsTrustLoopState);
		}

		@Override
		public void onChanInstalled(Chan chan) {
			changedChanNames.add(chan.name);
			removedChanNames.remove(chan.name);
			if (!getSupportFragmentManager().isStateSaved()) {
				handleChansChangedDelayed();
			}
		}

		@Override
		public void onChanUninstalled(Chan chan) {
			changedChanNames.remove(chan.name);
			removedChanNames.add(chan.name);
			if (!getSupportFragmentManager().isStateSaved()) {
				handleChansChangedDelayed();
			}
		}
	};

	@Override
	public void restartApplication() {
		Bundle outState = new Bundle();
		writePagesState(outState);
		outState.putParcelable(EXTRA_CURRENT_FRAGMENT,
				new StackItem(getSupportFragmentManager(), getCurrentFragment(), null));
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

	private final PostingService.GlobalCallback postingGlobalCallback = () -> {
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof PageFragment) {
			((PageFragment) currentFragment).handleNewPostDataListNow();
		}
	};

	private PostingService.Binder postingBinder;
	private final ServiceConnection postingConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder binder) {
			postingBinder = (PostingService.Binder) binder;
			postingBinder.register(postingGlobalCallback);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			if (postingBinder != null) {
				postingBinder.unregister(postingGlobalCallback);
				postingBinder = null;
			}
		}
	};

	private void updateHandleDownloadRequests() {
		DownloadService.Binder binder = downloadBinder;
		downloadDialog.handleRequest(binder != null ? binder.getPrimaryRequest() : null);
	}

	private final DownloadService.Callback downloadCallback = new DownloadService.Callback() {
		@Override
		public void requestHandleRequest() {
			updateHandleDownloadRequests();
		}

		@Override
		public void requestPermission() {
			if (C.USE_SAF) {
				if (storageRequestState == StorageRequestState.NONE) {
					if (Preferences.getDownloadUriTree(MainActivity.this) != null) {
						downloadBinder.onPermissionResult(DownloadService.PermissionResult.SUCCESS);
					} else {
						storageRequestState = StorageRequestState.INSTRUCTIONS;
						showStorageInstructionsDialog();
					}
				}
			} else {
				downloadBinder.onPermissionResult(DownloadService.PermissionResult.SUCCESS);
			}
		}
	};

	private DownloadService.Binder downloadBinder;
	private Boolean lastStorageRequestResult;
	private final ServiceConnection downloadConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder binder) {
			downloadBinder = (DownloadService.Binder) binder;
			downloadBinder.register(downloadCallback);
			if (lastStorageRequestResult != null) {
				boolean cancel = lastStorageRequestResult;
				lastStorageRequestResult = null;
				notifyDownloadServiceStorageRequestResult(cancel);
			}
			downloadBinder.notifyReadyToHandleRequests();
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			if (downloadBinder != null) {
				downloadBinder.unregister(downloadCallback);
				downloadBinder = null;
			}
			updateHandleDownloadRequests();
		}
	};

	private void showStorageInstructionsDialog() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.download_directory)
				.setMessage(R.string.saf_instructions__sentence)
				.setPositiveButton(R.string.proceed, (d, w) -> {
					storageRequestState = StorageRequestState.PICKER;
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
							.putExtra("android.provider.extra.SHOW_ADVANCED", true)
							.putExtra("android.content.extra.SHOW_ADVANCED", true)
							.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
					if (C.API_OREO) {
						intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract
								.buildRootUri("com.android.externalstorage.documents", "primary"));
					}
					try {
						startActivityForResult(intent, C.REQUEST_CODE_OPEN_URI_TREE);
					} catch (ActivityNotFoundException e) {
						ClickableToast.show(R.string.unknown_address);
						storageRequestState = StorageRequestState.NONE;
						handleStorageRequestResult(true);
					}
				})
				.setNegativeButton(android.R.string.cancel, (d, w) -> {
					storageRequestState = StorageRequestState.NONE;
					handleStorageRequestResult(true);
				})
				.setOnCancelListener(d -> {
					storageRequestState = StorageRequestState.NONE;
					handleStorageRequestResult(true);
				})
				.show();
	}

	private void handleStorageRequestResult(boolean cancel) {
		notifyDownloadServiceStorageRequestResult(cancel);
		ContentFragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof FragmentHandler.Callback) {
			((FragmentHandler.Callback) currentFragment).onStorageRequestResult();
		}
	}

	private void notifyDownloadServiceStorageRequestResult(boolean cancel) {
		if (downloadBinder != null) {
			Uri uri = Preferences.getDownloadUriTree(this);
			downloadBinder.onPermissionResult(uri != null ? DownloadService.PermissionResult.SUCCESS
					: cancel ? DownloadService.PermissionResult.CANCEL : DownloadService.PermissionResult.FAIL);
		} else {
			lastStorageRequestResult = cancel;
		}
	}

	public static class UpdateViewModel extends TaskViewModel.Proxy<ReadUpdateTask, ReadUpdateTask.Callback> {}

	private void startUpdateTask(boolean allowStart) {
		// Check for updates once per 12 hours
		UpdateViewModel viewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
		if (allowStart && !viewModel.hasTaskOrValue() && Preferences.isCheckUpdatesOnStart() &&
				System.currentTimeMillis() - Preferences.getLastUpdateCheck() >= 12 * 60 * 60 * 1000) {
			ReadUpdateTask task = new ReadUpdateTask(this, viewModel.callback);
			task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			viewModel.attach(task);
		}
		viewModel.observe(this, (updateDataMap, errorItem) -> {
			Preferences.setLastUpdateCheck(System.currentTimeMillis());
			if (updateDataMap != null) {
				int count = UpdateFragment.checkNewVersions(updateDataMap);
				if (count > 0) {
					handleUpdateData(updateDataMap, count);
				}
			}
		});
	}

	private void handleUpdateData(ReadUpdateTask.UpdateDataMap updateDataMap, int count) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (C.API_OREO) {
			notificationManager.createNotificationChannel(AndroidUtils
					.createHeadsUpNotificationChannel(C.NOTIFICATION_CHANNEL_UPDATES,
							getString(R.string.updates)));
		}
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, C.NOTIFICATION_CHANNEL_UPDATES);
		builder.setSmallIcon(R.drawable.ic_new_releases_white_24dp);
		String text = ResourceUtils.getColonString(getResources(), R.string.updates_available__genitive, count);
		if (C.API_LOLLIPOP) {
			builder.setColor(ThemeEngine.getTheme(this).accent);
			builder.setPriority(NotificationCompat.PRIORITY_HIGH);
			builder.setVibrate(new long[0]);
		} else {
			builder.setTicker(text);
		}
		builder.setContentTitle(getString(R.string.application_name_update__format,
				AndroidUtils.getApplicationLabel(this)));
		builder.setContentText(text);
		// Set action to ensure unique pending intent
		Intent intent = new Intent(this, MainActivity.class).setAction("updates")
				.putExtra(C.EXTRA_UPDATE_DATA_MAP, updateDataMap)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
		builder.setAutoCancel(true);
		notificationManager.notify(C.NOTIFICATION_ID_UPDATES, builder.build());
	}

	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, FavoritesStorage.Action action) {
		switch (action) {
			case ADD:
			case REMOVE:
			case MODIFY_TITLE: {
				drawerForm.updateItems(false, true);
				break;
			}
		}
	}

	@Override
	public boolean isWatcherClientForeground() {
		return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
	}

	@Override
	public void onWatcherUpdate(String chanName, String boardName, String threadNumber,
			WatcherService.Counter counter) {
		drawerForm.onWatcherUpdate(chanName, boardName, threadNumber, counter);
	}

	@Override
	public void setPageTitle(String title, String subtitle) {
		setTitleSubtitle(title, subtitle);
		if (((PageFragment) getCurrentFragment()).getPage().content == Page.Content.POSTS) {
			currentPageItem.threadTitle = title;
		}
		drawerForm.updateItems(true, false);
	}

	@Override
	public void handleRedirect(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
		PageFragment currentFragment = (PageFragment) getCurrentFragment();
		Page page = currentFragment.getPage();
		if (page.isThreadsOrPosts()) {
			currentPageItem = null;
			if (threadNumber == null) {
				navigateBoardsOrThreads(chanName, boardName, false, false);
			} else {
				navigatePosts(chanName, boardName, threadNumber, postNumber, null, false, false);
			}
		}
	}

	@Override
	public void closeCurrentPage() {
		PageFragment currentFragment = (PageFragment) getCurrentFragment();
		Page page = currentFragment.getPage();
		SavedPageItem savedPageItem = prepareTargetPreviousPage(true);
		currentPageItem = null;
		if (savedPageItem != null) {
			navigateSavedPage(savedPageItem, false);
		} else {
			Chan chan = Chan.get(page.chanName);
			if (isSingleBoardMode(chan)) {
				navigatePage(Page.Content.THREADS, page.chanName,
						getSingleBoardName(chan), null, null, null, null, 0);
			} else {
				navigatePage(Page.Content.BOARDS, page.chanName,
						null, null, null, null, null, FLAG_PAGE_FROM_CACHE);
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

	@Override
	public void setNavigationAreaLocked(String locker, boolean locked) {
		if (locked) {
			navigationAreaLockers.add(locker);
		} else {
			navigationAreaLockers.remove(locker);
		}
		drawerLayout.setExpandableFromAnyPoint(navigationAreaLockers.isEmpty());
	}

	private class ExpandedScreenDrawerLocker implements CustomDrawerLayout.DrawerListener {
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

	public static class InstanceViewModel extends ViewModel {
		private final HashMap<String, ListPage.Retainable> extras = new HashMap<>();
		private final Runnable cookiesRequirement = ChanDatabase.getInstance().requireCookies();

		@Override
		protected void onCleared() {
			for (ListPage.Retainable retainable : extras.values()) {
				retainable.clear();
			}
			extras.clear();
			cookiesRequirement.run();
		}
	}
}
