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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.lang.ref.WeakReference;

public abstract class ListPage implements PullableWrapper.PullCallback {
	private enum State {INIT, LOCKED, RESUMED, PAUSED, DESTROYED}

	public enum ViewType {LIST, PROGRESS, ERROR}

	public interface ExtraFactory<T> {
		T newExtra();
	}

	public static final class InitRequest {
		private static final InitRequest EMPTY_REQUEST = new InitRequest(false, null, null);

		public final boolean shouldLoad;
		public final PostNumber postNumber;
		public final String threadTitle;

		public InitRequest(boolean shouldLoad, PostNumber postNumber, String threadTitle) {
			this.shouldLoad = shouldLoad;
			this.postNumber = postNumber;
			this.threadTitle = threadTitle;
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
	private PullableRecyclerView recyclerView;
	private ListPosition listPosition;
	private UiManager uiManager;
	private Object retainExtra;
	private Parcelable parcelableExtra;
	private InitRequest initRequest;
	private InitSearch initSearch;

	private State state = State.INIT;

	public final void init(Page page, Callback callback, Fragment fragment, PullableRecyclerView recyclerView,
			ListPosition listPosition, UiManager uiManager, Object retainExtra, Parcelable parcelableExtra,
			InitRequest initRequest, InitSearch initSearch) {
		if (state == State.INIT) {
			state = State.LOCKED;
			this.callback = callback;
			this.fragment = fragment;
			this.page = page;
			this.recyclerView = recyclerView;
			this.listPosition = listPosition;
			this.uiManager = uiManager;
			this.retainExtra = retainExtra;
			this.parcelableExtra = parcelableExtra;
			this.initRequest = initRequest;
			this.initSearch = initSearch;
			getViewModel(fragment).update(this);
			onCreate();
			this.initRequest = null;
			this.initSearch = null;
			state = State.PAUSED;
		}
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

	protected final String getQuantityString(int resId, int quantity, Object... formatArgs) {
		return getResources().getQuantityString(resId, quantity, formatArgs);
	}

	protected final Page getPage() {
		return page;
	}

	protected final FragmentManager getFragmentManager() {
		return fragment.getChildFragmentManager();
	}

	protected final UiManager getUiManager() {
		return uiManager;
	}

	protected final Chan getChan() {
		return Chan.get(page.chanName);
	}

	protected final PullableRecyclerView getRecyclerView() {
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
		if (state == State.RESUMED || state == State.PAUSED) {
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

	protected final void switchView(ViewType viewType, String message) {
		callback.switchView(viewType, message);
	}

	protected final void switchView(ViewType viewType, int message) {
		callback.switchView(viewType, message != 0 ? getString(message) : null);
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
	protected final <T> T getRetainExtra(ExtraFactory<T> factory) {
		if (retainExtra == null && factory != null) {
			retainExtra = factory.newExtra();
		}
		return (T) retainExtra;
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
		return state == State.RESUMED || state == State.PAUSED;
	}

	public final boolean isDestroyed() {
		return state == State.DESTROYED;
	}

	private void performResume() {
		onResume();
		onHandleNewPostDataList();
	}

	public final void resume() {
		if (state == State.PAUSED) {
			state = State.RESUMED;
			performResume();
		}
	}

	public final void pause() {
		if (state == State.RESUMED) {
			state = State.PAUSED;
			onPause();
		}
	}

	public final void destroy() {
		if (state == State.RESUMED || state == State.PAUSED) {
			if (state == State.RESUMED) {
				onPause();
			}
			state = State.DESTROYED;
			onDestroy();
		}
	}

	public final void handleNewPostDataListNow() {
		if (state == State.RESUMED) {
			onHandleNewPostDataList();
		}
	}

	public final void handleScrollToPost(PostNumber postNumber) {
		if (state == State.RESUMED) {
			onScrollToPost(postNumber);
		}
	}

	public final ListPosition getListPosition() {
		return listPosition != null ? listPosition : ListPosition.obtain(recyclerView, null);
	}

	public final Pair<Object, Parcelable> getExtraToStore(boolean saveToStack) {
		onRequestStoreExtra(saveToStack);
		return new Pair<>(retainExtra, parcelableExtra);
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
		void switchView(ViewType viewType, String message);
		void showScaleAnimation();
		void handleRedirect(String chanName, String boardName, String threadNumber, PostNumber postNumber);
		void closePage();
	}
}
