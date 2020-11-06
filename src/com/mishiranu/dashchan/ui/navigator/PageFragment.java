package com.mishiranu.dashchan.ui.navigator;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.widget.CustomSearchView;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.MenuExpandListener;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import java.util.Collection;
import java.util.UUID;

public final class PageFragment extends Fragment implements ActivityHandler, ListPage.Callback {
	private static final String EXTRA_PAGE = "page";
	private static final String EXTRA_RETAIN_ID = "retainId";

	private static final String EXTRA_LIST_POSITION = "listPosition";
	private static final String EXTRA_PARCELABLE_EXTRA = "parcelableExtra";
	private static final String EXTRA_INIT_ERROR_ITEM = "initErrorItem";
	private static final String EXTRA_SEARCH_CURRENT_QUERY = "searchCurrentQuery";
	private static final String EXTRA_SEARCH_SUBMIT_QUERY = "searchSubmitQuery";
	private static final String EXTRA_SEARCH_FOCUSED = "searchFocused";

	public interface Callback {
		UiManager getUiManager();
		ListPage.Retainable getRetainableExtra(String retainId);
		void storeRetainableExtra(String retainId, ListPage.Retainable extra);
		ActionBar getActionBar();
		void setPageTitle(String title, String subtitle);
		void invalidateHomeUpState();
		void handleRedirect(String chanName, String boardName, String threadNumber, PostNumber postNumber);
		void closeCurrentPage();
	}

	public PageFragment() {}

	public PageFragment(Page page, String retainId) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_PAGE, page);
		args.putString(EXTRA_RETAIN_ID, retainId);
		setArguments(args);
	}

	public Page getPage() {
		return requireArguments().getParcelable(EXTRA_PAGE);
	}

	public String getRetainId() {
		return requireArguments().getString(EXTRA_RETAIN_ID);
	}

	private Callback getCallback() {
		return (Callback) requireActivity();
	}

	private ListPage listPage;
	private View progressView;
	private View errorView;
	private TextView errorText;
	private PullableRecyclerView recyclerView;
	private CustomSearchView searchView;

	private String actionBarLockerPull;
	private String actionBarLockerSearch;

	private ListPosition listPosition;
	private Parcelable parcelableExtra;
	private ErrorItem initErrorItem;
	private String searchCurrentQuery;
	private String searchSubmitQuery;
	private boolean searchFocused;

	private ListPage.InitRequest initRequest;
	private boolean resetScroll = false;

	private boolean allowShowScale;
	private Runnable doOnResume;
	private Menu currentMenu;
	private boolean fillMenuOnResume;
	private boolean searchMode = false;
	private boolean saveToStack = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		listPosition = savedInstanceState != null && !resetScroll
				? savedInstanceState.getParcelable(EXTRA_LIST_POSITION) : null;
		parcelableExtra = savedInstanceState != null ? savedInstanceState
				.getParcelable(EXTRA_PARCELABLE_EXTRA) : null;
		initErrorItem = savedInstanceState != null ? savedInstanceState
				.getParcelable(EXTRA_INIT_ERROR_ITEM) : null;
		searchCurrentQuery = savedInstanceState != null ? savedInstanceState
				.getString(EXTRA_SEARCH_CURRENT_QUERY) : null;
		searchSubmitQuery = savedInstanceState != null ? savedInstanceState
				.getString(EXTRA_SEARCH_SUBMIT_QUERY) : null;
		searchFocused = savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_SEARCH_FOCUSED);
		resetScroll = false;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.activity_common, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		actionBarLockerPull = "pull-" + UUID.randomUUID();
		actionBarLockerSearch = "search-" + UUID.randomUUID();

		allowShowScale = true;
		listPage = getPage().content.newPage();
		progressView = view.findViewById(R.id.progress);
		errorView = view.findViewById(R.id.error);
		errorText = view.findViewById(R.id.error_text);
		recyclerView = view.findViewById(android.R.id.list);
		recyclerView.setSaveEnabled(false);
		recyclerView.setFastScrollerEnabled(Preferences.isActiveScrollbar());
		recyclerView.getWrapper().setOnPullListener(listPage);
		recyclerView.getWrapper().setPullStateListener((wrapper, busy) -> ((FragmentHandler) requireActivity())
				.setActionBarLocked(actionBarLockerPull, busy));
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		FragmentHandler fragmentHandler = (FragmentHandler) requireActivity();
		fragmentHandler.setActionBarLocked(actionBarLockerPull, false);
		fragmentHandler.setActionBarLocked(actionBarLockerSearch, false);

		if (listPage != null) {
			listPage.destroy();
			listPage = null;
		}
		progressView = null;
		errorView = null;
		errorText = null;
		recyclerView = null;
		searchView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		ErrorItem initErrorItem = this.initErrorItem;
		this.initErrorItem = null;
		ListPage.InitRequest initRequest = this.initRequest;
		this.initRequest = null;
		if (initRequest == null && initErrorItem != null) {
			initRequest = new ListPage.InitRequest(initErrorItem);
		}
		listPage.init(getPage(), this, this, recyclerView, listPosition, getCallback().getUiManager(),
				getCallback().getRetainableExtra(getRetainId()), parcelableExtra, initRequest,
				new ListPage.InitSearch(searchCurrentQuery, searchSubmitQuery));
		notifyTitleChanged();
	}

	@Override
	public void onResume() {
		super.onResume();

		listPage.resume();
		if (currentMenu != null && fillMenuOnResume) {
			fillMenuOnResume = false;
			// Menu can be requested too early on some Android 4.x
			currentMenu.clear();
			onCreateOptionsMenu(currentMenu);
			onPrepareOptionsMenu(currentMenu);
		}
		Runnable doOnResume = this.doOnResume;
		this.doOnResume = null;
		if (doOnResume != null) {
			doOnResume.run();
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		if (listPage != null) {
			listPage.pause();
		}
	}

	public void setSaveToStack(boolean saveToStack) {
		this.saveToStack = saveToStack;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (listPage != null) {
			listPosition = listPage.getListPosition();
			Pair<ListPage.Retainable, Parcelable> extraPair = listPage.getExtraToStore(saveToStack);
			getCallback().storeRetainableExtra(getRetainId(), extraPair.first);
			parcelableExtra = extraPair.second;
			CustomSearchView searchView = getSearchView(false);
			if (searchView != null) {
				searchFocused = searchView.isSearchFocused();
			}
		}
		outState.putParcelable(EXTRA_LIST_POSITION, listPosition);
		outState.putParcelable(EXTRA_PARCELABLE_EXTRA, parcelableExtra);
		if (!saveToStack && initErrorItem != null) {
			outState.putParcelable(EXTRA_INIT_ERROR_ITEM, initErrorItem);
		}
		outState.putString(EXTRA_SEARCH_CURRENT_QUERY, searchCurrentQuery);
		outState.putString(EXTRA_SEARCH_SUBMIT_QUERY, searchSubmitQuery);
		outState.putBoolean(EXTRA_SEARCH_FOCUSED, searchFocused && !saveToStack);
	}

	@Override
	public void onTerminate() {
		if (currentMenu != null) {
			MenuItem menuItem = currentMenu.findItem(R.id.menu_search);
			if (menuItem != null && menuItem.isActionViewExpanded()) {
				menuItem.setOnActionExpandListener(null);
				menuItem.collapseActionView();
			}
		}
		currentMenu = null;
		if (listPage != null) {
			listPage.destroy();
			listPage = null;
		}
	}

	@Override
	public void onChansChanged(Collection<String> changed, Collection<String> removed) {
		Page page = getPage();
		if (changed.contains(page.chanName)) {
			updateOptionsMenu();
		}
	}

	private CustomSearchView getSearchView(boolean required) {
		if (searchView == null && required) {
			searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(requireContext(),
					R.style.Theme_Special_White) : getCallback().getActionBar().getThemedContext());
			searchView.setOnSubmitListener(query -> {
				if (listPage.onSearchSubmit(query)) {
					searchSubmitQuery = null;
					setSearchMode(false);
					return true;
				} else {
					searchSubmitQuery = query;
					return false;
				}
			});
			searchView.setOnChangeListener(query -> {
				if (listPage != null) {
					listPage.onSearchQueryChange(query);
				}
				if (searchCurrentQuery != null) {
					searchCurrentQuery = query;
				}
			});
		}
		return searchView;
	}

	private boolean setSearchMode(MenuItem menuItem, boolean search, boolean toggle) {
		if (searchMode != search) {
			searchMode = search;
			if (search) {
				CustomSearchView searchView = getSearchView(true);
				searchView.setHint(menuItem.getTitle());
				listPage.onSearchQueryChange(searchView.getQuery());
			} else {
				listPage.onSearchQueryChange("");
				listPage.onSearchCancel();
			}
			updateOptionsMenu();
			((FragmentHandler) requireActivity()).setActionBarLocked(actionBarLockerSearch, search);
			getCallback().invalidateHomeUpState();
			if (toggle) {
				if (search) {
					menuItem.expandActionView();
				} else {
					menuItem.collapseActionView();
				}
			}
			return true;
		}
		return false;
	}

	private boolean setSearchMode(boolean search) {
		if (currentMenu != null) {
			MenuItem menuItem = currentMenu.findItem(R.id.menu_search);
			return setSearchMode(menuItem, search, true);
		}
		return false;
	}

	public void setInitRequest(ListPage.InitRequest initRequest) {
		this.initRequest = initRequest;
	}

	public void requestResetScroll() {
		resetScroll = true;
	}

	public void onAppearanceOptionChanged(int what) {
		listPage.onAppearanceOptionChanged(what);
	}

	public int onDrawerNumberEntered(int number) {
		return listPage.onDrawerNumberEntered(number);
	}

	public void updatePageConfiguration(PostNumber postNumber) {
		if (listPage != null) {
			listPage.updatePageConfiguration(postNumber);
		} else {
			ListPage.InitRequest last = this.initRequest;
			initRequest = new ListPage.InitRequest(last != null && last.shouldLoad,
					postNumber, last != null ? last.threadTitle : null);
		}
	}

	public void handleNewPostDataListNow() {
		listPage.handleNewPostDataListNow();
	}

	public void scrollToPost(PostNumber postNumber) {
		listPage.handleScrollToPost(postNumber);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		onCreateOptionsMenu(menu);
	}

	private void onCreateOptionsMenu(Menu menu) {
		currentMenu = menu;
		if (listPage != null && listPage.isRunning()) {
			listPage.onCreateOptionsMenu(menu);
			MenuItem searchMenuItem = menu.findItem(R.id.menu_search);
			if (searchMenuItem != null) {
				CustomSearchView searchView = getSearchView(true);
				searchMenuItem.setActionView(searchView);
				searchMenuItem.setOnActionExpandListener(new MenuExpandListener((menuItem, expand) -> {
					if (expand) {
						searchView.setFocusOnExpand(searchFocused);
						if (searchCurrentQuery != null) {
							searchView.setQuery(searchCurrentQuery);
						} else {
							searchCurrentQuery = "";
						}
					} else {
						searchCurrentQuery = null;
						searchSubmitQuery = null;
					}
					setSearchMode(menuItem, expand, false);
					return true;
				}));
				if (searchCurrentQuery != null) {
					searchMenuItem.expandActionView();
				}
			}
		} else {
			fillMenuOnResume = true;
		}
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		currentMenu = menu;
		if (listPage != null && listPage.isRunning()) {
			for (int i = 0; i < menu.size(); i++) {
				MenuItem menuItem = menu.getItem(i);
				menuItem.setVisible(!searchMode || menuItem.getItemId() == R.id.menu_search);
			}
			if (searchMode) {
				return;
			}
			listPage.onPrepareOptionsMenu(menu);
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == R.id.menu_search) {
			searchFocused = true;
			return false;
		}
		if (listPage.onOptionsItemSelected(item)) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onSearchRequested() {
		return setSearchMode(true) || searchMode;
	}

	@Override
	public boolean onBackPressed() {
		return setSearchMode(false);
	}

	@Override
	public void notifyTitleChanged() {
		Pair<String, String> titleSubtitle = listPage.obtainTitleSubtitle();
		getCallback().setPageTitle(titleSubtitle != null ? titleSubtitle.first : null,
				titleSubtitle != null ? titleSubtitle.second : null);
	}

	@Override
	public void updateOptionsMenu() {
		if (currentMenu != null) {
			onPrepareOptionsMenu(currentMenu);
		}
	}

	@Override
	public void setCustomSearchView(View view) {
		getSearchView(true).setCustomView(view);
	}

	@Override
	public void clearSearchFocus() {
		CustomSearchView searchView = getSearchView(false);
		if (searchView != null) {
			searchView.clearFocus();
		}
	}

	@Override
	public Context getToolbarContext() {
		return ((FragmentHandler) requireActivity()).getToolbarContext();
	}

	@Override
	public ActionMode startActionMode(ActionMode.Callback callback) {
		return requireActivity().startActionMode(callback);
	}

	@Override
	public void switchList() {
		initErrorItem = null;
		progressView.setVisibility(View.GONE);
		errorView.setVisibility(View.GONE);
	}

	@Override
	public void switchProgress() {
		initErrorItem = null;
		progressView.setVisibility(View.VISIBLE);
		errorView.setVisibility(View.GONE);
	}

	@Override
	public void switchError(ErrorItem errorItem) {
		if (errorItem == null) {
			errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
		}
		initErrorItem = errorItem;
		progressView.setVisibility(View.GONE);
		errorView.setVisibility(View.VISIBLE);
		errorText.setText(errorItem.toString());
	}

	@Override
	public void showScaleAnimation() {
		if (allowShowScale) {
			allowShowScale = false;
			Animator animator = AnimatorInflater.loadAnimator(requireContext(), R.animator.fragment_in);
			animator.setTarget(recyclerView);
			animator.start();
		}
	}

	@Override
	public void handleRedirect(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
		if (isStateSaved()) {
			doOnResume = () -> handleRedirect(chanName, boardName, threadNumber, postNumber);
		} else {
			getCallback().handleRedirect(chanName, boardName, threadNumber, postNumber);
		}
	}

	@Override
	public void closePage() {
		if (isStateSaved()) {
			doOnResume = () -> getCallback().closeCurrentPage();
		} else {
			getCallback().closeCurrentPage();
		}
	}
}
