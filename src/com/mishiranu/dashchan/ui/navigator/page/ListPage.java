package com.mishiranu.dashchan.ui.navigator.page;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcelable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public abstract class ListPage implements PullableWrapper.PullCallback {
	public static final int OPTIONS_MENU_APPEARANCE = -1;
	public static final int OPTIONS_MENU_SEARCH = -2;

	public static final int APPEARANCE_MENU_CHANGE_THEME = 100;
	public static final int APPEARANCE_MENU_EXPANDED_SCREEN = 101;
	public static final int APPEARANCE_MENU_SPOILERS = 102;
	public static final int APPEARANCE_MENU_MY_POSTS = 103;
	public static final int APPEARANCE_MENU_DRAWER = 104;
	public static final int APPEARANCE_MENU_THREADS_GRID = 105;
	public static final int APPEARANCE_MENU_SFW_MODE = 106;

	private enum State {INIT, LOCKED, RESUMED, PAUSED, DESTROYED}

	public enum ViewType {LIST, PROGRESS, ERROR}

	public interface ExtraFactory<T> {
		T newExtra();
	}

	public static final class InitRequest {
		private static final InitRequest EMPTY_REQUEST = new InitRequest(false, null, null);

		public final boolean shouldLoad;
		public final String postNumber;
		public final String threadTitle;

		public InitRequest(boolean shouldLoad, String postNumber, String threadTitle) {
			this.shouldLoad = shouldLoad;
			this.postNumber = postNumber;
			this.threadTitle = threadTitle;
		}
	}

	private Callback callback;
	private Page page;
	private PullableListView listView;
	private ListPosition listPosition;
	private UiManager uiManager;
	private ActionIconSet actionIconSet;
	private Object retainExtra;
	private Parcelable parcelableExtra;
	private InitRequest initRequest;

	private State state = State.INIT;

	public final void init(Callback callback, Page page, PullableListView listView,
			ListPosition listPosition, UiManager uiManager, ActionIconSet actionIconSet,
			Object retainExtra, Parcelable parcelableExtra, InitRequest initRequest) {
		if (state == State.INIT) {
			state = State.LOCKED;
			this.callback = callback;
			this.page = page;
			this.listView = listView;
			this.listPosition = listPosition;
			this.uiManager = uiManager;
			this.actionIconSet = actionIconSet;
			this.retainExtra = retainExtra;
			this.parcelableExtra = parcelableExtra;
			this.initRequest = initRequest;
			onCreate();
			this.initRequest = null;
			state = State.PAUSED;
		}
	}

	protected final Context getContext() {
		return listView.getContext();
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

	protected final UiManager getUiManager() {
		return uiManager;
	}

	protected final ChanLocator getChanLocator() {
		return ChanLocator.get(page.chanName);
	}

	protected final ChanConfiguration getChanConfiguration() {
		return ChanConfiguration.get(page.chanName);
	}

	protected final PullableListView getListView() {
		return listView;
	}

	protected interface RestorePositionCallback {
		void restore(ListPosition listPosition);
	}

	protected final void restoreListPosition(RestorePositionCallback callback) {
		if (listPosition != null) {
			if (callback != null) {
				callback.restore(listPosition);
			} else {
				listPosition.apply(listView);
			}
			listPosition = null;
		}
	}

	protected final InitRequest getInitRequest() {
		return initRequest != null ? initRequest : InitRequest.EMPTY_REQUEST;
	}

	protected final void notifyAllAdaptersChanged() {
		((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
		onNotifyAllAdaptersChanged();
	}

	protected final int obtainIcon(int attr) {
		if (actionIconSet != null) {
			return actionIconSet.getId(attr);
		} else {
			return 0;
		}
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

	protected final void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber) {
		callback.handleRedirect(chanName, boardName, threadNumber, postNumber);
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

	protected void onScrollToPost(String postNumber) {}

	protected void onRequestStoreExtra(boolean saveToStack) {}

	public String obtainTitle() {
		return null;
	}

	public void onItemClick(View view, int position) {}

	public boolean onItemLongClick(View view, int position) {
		return false;
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

	public void updatePageConfiguration(String postNumber) {}

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

	public final void cleanup() {
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

	public final void handleScrollToPost(String postNumber) {
		if (state == State.RESUMED) {
			onScrollToPost(postNumber);
		}
	}

	public final ListPosition getListPosition() {
		return listPosition != null ? listPosition : ListPosition.obtain(listView);
	}

	public final Pair<Object, Parcelable> getExtraToStore(boolean saveToStack) {
		onRequestStoreExtra(saveToStack);
		return new Pair<>(retainExtra, parcelableExtra);
	}

	public interface Callback {
		void notifyTitleChanged();
		void updateOptionsMenu();
		void setCustomSearchView(View view);
		ActionMode startActionMode(ActionMode.Callback callback);
		void switchView(ViewType viewType, String message);
		void showScaleAnimation();
		void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber);
	}
}
