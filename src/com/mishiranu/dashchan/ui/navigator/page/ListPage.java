package com.mishiranu.dashchan.ui.navigator.page;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.lang.ref.WeakReference;
import java.util.Objects;

public abstract class ListPage implements LifecycleOwner, PullableWrapper.PullCallback {
	public interface ExtraFactory<T> {
		T newExtra();
	}

	public interface Retainable {
		default void clear() {}
	}

	public static final class InitRequest {
		private static final InitRequest EMPTY_REQUEST = new InitRequest(false, null, null);

		public final boolean shouldLoad;
		public final PostNumber postNumber;
		public final String threadTitle;
		public final ErrorItem errorItem;

		public InitRequest(boolean shouldLoad, PostNumber postNumber, String threadTitle) {
			this.shouldLoad = shouldLoad;
			this.postNumber = postNumber;
			this.threadTitle = threadTitle;
			errorItem = null;
		}

		public InitRequest(ErrorItem errorItem) {
			shouldLoad = false;
			postNumber = null;
			threadTitle = null;
			this.errorItem = errorItem;
		}
	}

	public static final class InitSearch {
		private static final InitSearch EMPTY_SEARCH = new InitSearch(null, null);

		public final String currentQuery;
		public final String submitQuery;

		public InitSearch(String currentQuery, String submitQuery) {
			this.currentQuery = currentQuery;
			this.submitQuery = submitQuery;
		}
	}

	private Page page;
	private Callback callback;
	private Fragment fragment;
	private LifecycleRegistry lifecycle;
	private PaddedRecyclerView recyclerView;
	private ListPosition listPosition;
	private UiManager uiManager;
	private Retainable retainableExtra;
	private Parcelable parcelableExtra;
	private InitRequest initRequest;
	private InitSearch initSearch;

	public final void init(Page page, Callback callback, Fragment fragment, PaddedRecyclerView recyclerView,
			ListPosition listPosition, UiManager uiManager, Retainable retainableExtra, Parcelable parcelableExtra,
			InitRequest initRequest, InitSearch initSearch) {
		if (lifecycle == null) {
			lifecycle = new LifecycleRegistry(this);
			this.callback = callback;
			this.fragment = fragment;
			this.page = page;
			this.recyclerView = recyclerView;
			this.listPosition = listPosition;
			this.uiManager = uiManager;
			this.retainableExtra = retainableExtra;
			this.parcelableExtra = parcelableExtra;
			this.initRequest = initRequest;
			this.initSearch = initSearch;
			getViewModel(fragment).update(this);
			lifecycle.setCurrentState(Lifecycle.State.INITIALIZED);
			onCreate();
			lifecycle.setCurrentState(Lifecycle.State.STARTED);
			this.initRequest = null;
			this.initSearch = null;
		}
	}

	private Lifecycle.State getState() {
		return lifecycle != null ? lifecycle.getCurrentState() : null;
	}

	protected final Context getContext() {
		return recyclerView.getContext();
	}

	protected final Context getToolbarContext() {
		return callback.getToolbarContext();
	}

	protected final Resources getResources() {
		return getContext().getResources();
	}

	protected final String getString(int resId) {
		return getContext().getString(resId);
	}

	protected final String getString(int resId, Object... formatArgs) {
		return getContext().getString(resId, formatArgs);
	}

	protected final Page getPage() {
		return page;
	}

	protected final FragmentManager getFragmentManager() {
		return fragment.getChildFragmentManager();
	}

	protected final <T extends ViewModel> T getViewModel(Class<T> modelClass) {
		return new ViewModelProvider(fragment).get(modelClass);
	}

	protected final UiManager getUiManager() {
		return uiManager;
	}

	protected final Chan getChan() {
		return Chan.get(page.chanName);
	}

	protected final PaddedRecyclerView getRecyclerView() {
		return recyclerView;
	}

	protected final ListPosition takeListPosition() {
		ListPosition listPosition = this.listPosition;
		this.listPosition = null;
		return listPosition;
	}

	protected final void restoreListPosition() {
		ListPosition listPosition = takeListPosition();
		if (listPosition != null) {
			listPosition.apply(recyclerView);
		}
	}

	protected final InitRequest getInitRequest() {
		return initRequest != null ? initRequest : InitRequest.EMPTY_REQUEST;
	}

	protected final InitSearch getInitSearch() {
		return initSearch != null ? initSearch : InitSearch.EMPTY_SEARCH;
	}

	protected final void notifyAllAdaptersChanged() {
		recyclerView.getAdapter().notifyDataSetChanged();
		onNotifyAllAdaptersChanged();
	}

	protected final Drawable getActionBarIcon(int attr) {
		return ResourceUtils.getActionBarIcon(getToolbarContext(), attr);
	}

	protected final void notifyTitleChanged() {
		callback.notifyTitleChanged();
	}

	protected final void updateOptionsMenu() {
		if (isRunning()) {
			callback.updateOptionsMenu();
		}
	}

	protected final void setCustomSearchView(View view) {
		callback.setCustomSearchView(view);
	}

	protected final void clearSearchFocus() {
		callback.clearSearchFocus();
	}

	protected final ActionMode startActionMode(ActionMode.Callback callback) {
		return this.callback.startActionMode(callback);
	}

	protected final void switchList() {
		callback.switchList();
	}

	protected final void switchProgress() {
		callback.switchProgress();
	}

	protected final void switchError(ErrorItem errorItem) {
		callback.switchError(errorItem != null ? errorItem : new ErrorItem(ErrorItem.Type.UNKNOWN));
	}

	protected final void switchError(String message) {
		callback.switchError(message != null ? new ErrorItem(message) : new ErrorItem(ErrorItem.Type.UNKNOWN));
	}

	protected final void switchError(int message) {
		callback.switchError(message != 0 ? new ErrorItem(message) : new ErrorItem(ErrorItem.Type.UNKNOWN));
	}

	protected final void showScaleAnimation() {
		callback.showScaleAnimation();
	}

	protected final void handleRedirect(String chanName,
			String boardName, String threadNumber, PostNumber postNumber) {
		callback.handleRedirect(chanName, boardName, threadNumber, postNumber);
	}

	protected final void closePage() {
		callback.closePage();
	}

	@SuppressWarnings("unchecked")
	protected final <T extends Retainable> T getRetainableExtra(ExtraFactory<T> factory) {
		if (retainableExtra == null && factory != null) {
			retainableExtra = factory.newExtra();
		}
		return (T) retainableExtra;
	}

	@SuppressWarnings("unchecked")
	protected final <T extends Parcelable> T getParcelableExtra(ExtraFactory<T> factory) {
		if (parcelableExtra == null && factory != null) {
			parcelableExtra = factory.newExtra();
		}
		return (T) parcelableExtra;
	}

	protected void onCreate() {}

	protected void onResume() {}

	protected void onPause() {}

	protected void onDestroy() {}

	protected void onNotifyAllAdaptersChanged() {}

	protected void onHandleNewPostDataList() {}

	protected void onScrollToPost(PostNumber postNumber) {}

	protected void onRequestStoreExtra(boolean saveToStack) {}

	public String obtainTitle() {
		return null;
	}

	public Pair<String, String> obtainTitleSubtitle() {
		return new Pair<>(obtainTitle(), null);
	}

	public void onCreateOptionsMenu(Menu menu) {}

	public void onPrepareOptionsMenu(Menu menu) {}

	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	public void onAppearanceOptionChanged(int what) {}

	public void onSearchQueryChange(String query) {}

	public boolean onSearchSubmit(String query) {
		return false;
	}

	public void onSearchCancel() {}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {}

	public int onDrawerNumberEntered(int number) {
		return 0;
	}

	public void updatePageConfiguration(PostNumber postNumber) {}

	public final boolean isRunning() {
		Lifecycle.State state = getState();
		return state == Lifecycle.State.STARTED || state == Lifecycle.State.RESUMED;
	}

	private void performResume() {
		onResume();
		onHandleNewPostDataList();
	}

	public final void resume() {
		if (getState() == Lifecycle.State.STARTED) {
			lifecycle.setCurrentState(Lifecycle.State.RESUMED);
			performResume();
		}
	}

	public final void pause() {
		if (getState() == Lifecycle.State.RESUMED) {
			lifecycle.setCurrentState(Lifecycle.State.STARTED);
			onPause();
		}
	}

	public final void destroy() {
		if (isRunning()) {
			if (getState() == Lifecycle.State.RESUMED) {
				lifecycle.setCurrentState(Lifecycle.State.STARTED);
				onPause();
			}
			lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
			onDestroy();
		}
	}

	public final void handleNewPostDataListNow() {
		if (getState() == Lifecycle.State.RESUMED) {
			onHandleNewPostDataList();
		}
	}

	public final void handleScrollToPost(PostNumber postNumber) {
		if (getState() == Lifecycle.State.RESUMED) {
			onScrollToPost(postNumber);
		}
	}

	public final ListPosition getListPosition() {
		return listPosition != null ? listPosition : ListPosition.obtain(recyclerView, null);
	}

	public final Pair<Retainable, Parcelable> getExtraToStore(boolean saveToStack) {
		onRequestStoreExtra(saveToStack);
		return new Pair<>(retainableExtra, parcelableExtra);
	}

	@NonNull
	@Override
	public final Lifecycle getLifecycle() {
		return Objects.requireNonNull(lifecycle);
	}

	public static class PageViewModel extends ViewModel {
		private WeakReference<ListPage> listPage;

		public void update(ListPage listPage) {
			this.listPage = listPage != null ? new WeakReference<>(listPage) : null;
		}
	}

	private static PageViewModel getViewModel(Fragment fragment) {
		return new ViewModelProvider(fragment).get(PageViewModel.class);
	}

	@SuppressWarnings("unchecked")
	protected static <T extends ListPage> T extract(InstanceDialog.Provider provider) {
		PageViewModel viewModel = getViewModel(provider.getParentFragment());
		ListPage listPage = viewModel.listPage != null ? viewModel.listPage.get() : null;
		return (T) listPage;
	}

	public interface Callback {
		void notifyTitleChanged();
		void updateOptionsMenu();
		void setCustomSearchView(View view);
		void clearSearchFocus();
		Context getToolbarContext();
		ActionMode startActionMode(ActionMode.Callback callback);
		void switchList();
		void switchProgress();
		void switchError(ErrorItem errorItem);
		void showScaleAnimation();
		void handleRedirect(String chanName, String boardName, String threadNumber, PostNumber postNumber);
		void closePage();
	}
}
