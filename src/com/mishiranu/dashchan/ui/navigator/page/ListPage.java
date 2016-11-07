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

package com.mishiranu.dashchan.ui.navigator.page;

import android.app.Activity;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;

import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public abstract class ListPage<Adapter extends BaseAdapter> implements PullableWrapper.PullCallback,
		BusyScrollListener.Callback
{
	public static final int OPTIONS_MENU_APPEARANCE = -1;
	public static final int OPTIONS_MENU_SEARCH = -2;
	public static final int OPTIONS_MENU_SEARCH_VIEW = -3;

	public static final int APPEARANCE_MENU_CHANGE_THEME = 100;
	public static final int APPEARANCE_MENU_EXPANDED_SCREEN = 101;
	public static final int APPEARANCE_MENU_SPOILERS = 102;
	public static final int APPEARANCE_MENU_MY_POSTS = 103;
	public static final int APPEARANCE_MENU_DRAWER = 104;
	public static final int APPEARANCE_MENU_THREADS_GRID = 105;
	public static final int APPEARANCE_MENU_SFW_MODE = 106;

	private enum State {INIT, LOCKED, RESUMED, PAUSED, CONSUMED}

	public enum ViewType {LIST, PROGRESS, ERROR}

	private Activity mActivity;
	private Callback mCallback;
	private PageHolder mPageHolder;
	private PullableListView mListView;
	private UiManager mUiManager;
	private ActionIconSet mActionIconSet;

	private Adapter mAdapter;
	private BusyScrollListener.Callback mBusyScrollListenerCallback;
	private State mState = State.INIT;

	public final void init(Activity activity, Callback callback, PageHolder pageHolder, PullableListView listView,
			UiManager uiManager, ActionIconSet actionIconSet)
	{
		if (mState == State.INIT)
		{
			mState = State.LOCKED;
			mActivity = activity;
			mCallback = callback;
			mPageHolder = pageHolder;
			mListView = listView;
			mUiManager = uiManager;
			mActionIconSet = actionIconSet;
			listView.setDivider(ResourceUtils.getDrawable(activity, android.R.attr.listDivider, 0));
			ListScroller.cancel(listView);
			onCreate();
			if (mAdapter == null) throw new IllegalStateException("Adapter wasn't initialized");
			mState = State.RESUMED;
			performResume();
		}
	}

	protected final Activity getActivity()
	{
		return mActivity;
	}

	protected final Resources getResources()
	{
		return mActivity.getResources();
	}

	protected final String getString(int resId)
	{
		return mActivity.getString(resId);
	}

	protected final String getString(int resId, Object... formatArgs)
	{
		return mActivity.getString(resId, formatArgs);
	}

	protected final String getQuantityString(int resId, int quantity, Object... formatArgs)
	{
		return getResources().getQuantityString(resId, quantity, formatArgs);
	}

	protected final PageHolder getPageHolder()
	{
		return mPageHolder;
	}

	protected final UiManager getUiManager()
	{
		return mUiManager;
	}

	protected final Adapter getAdapter()
	{
		return mAdapter;
	}

	protected final ChanLocator getChanLocator()
	{
		return ChanLocator.get(mPageHolder.chanName);
	}

	protected final ChanConfiguration getChanConfiguration()
	{
		return ChanConfiguration.get(mPageHolder.chanName);
	}

	protected final PullableListView getListView()
	{
		return mListView;
	}

	protected final void notifyAllAdaptersChanged()
	{
		mAdapter.notifyDataSetChanged();
		mUiManager.dialog().notifyDataSetChangedToAll();
	}

	protected final int obtainIcon(int attr)
	{
		if (mActionIconSet != null) return mActionIconSet.getId(attr); else return 0;
	}

	protected final void initAdapter(Adapter adapter, BusyScrollListener.Callback callback)
	{
		if (mState == State.LOCKED)
		{
			mAdapter = adapter;
			mBusyScrollListenerCallback = callback;
			mUiManager.view().notifyUnbindListView(mListView);
			mListView.setAdapter(adapter);
		}
		else throw new IllegalStateException("Adapter can be initialized only in onCreate method");
	}

	protected final void setCustomSearchView(View view)
	{
		mCallback.setCustomSearchView(view);
	}

	protected final void notifyTitleChanged()
	{
		mCallback.notifyTitleChanged();
	}

	protected final void updateOptionsMenu(boolean recreate)
	{
		if (mState == State.RESUMED || mState == State.PAUSED) mCallback.updateOptionsMenu(recreate);
	}

	protected final void switchView(ViewType viewType, String message)
	{
		mCallback.switchView(viewType, message);
	}

	protected final void switchView(ViewType viewType, int message)
	{
		mCallback.switchView(viewType, message != 0 ? getString(message) : null);
	}

	protected final void showScaleAnimation()
	{
		mCallback.showScaleAnimation();
	}

	protected final void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber)
	{
		mCallback.handleRedirect(chanName, boardName, threadNumber, postNumber);
	}

	protected void onCreate()
	{

	}

	protected void onResume()
	{

	}

	protected void onPause()
	{

	}

	protected void onDestroy()
	{

	}

	protected void onHandleNewPostDatas()
	{

	}

	public String obtainTitle()
	{
		return null;
	}

	public void onItemClick(View view, int position, long id)
	{

	}

	public boolean onItemLongClick(View view, int position, long id)
	{
		return false;
	}

	public void onCreateOptionsMenu(Menu menu)
	{

	}

	public void onPrepareOptionsMenu(Menu menu)
	{

	}

	public boolean onOptionsItemSelected(MenuItem item)
	{
		return false;
	}

	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView)
	{

	}

	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		return false;
	}

	public void onAppearanceOptionChanged(int what)
	{

	}

	public void onSearchQueryChange(String query)
	{

	}

	public boolean onSearchSubmit(String query)
	{
		return false;
	}

	public void onSearchCancel()
	{

	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{

	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView)
	{
		if (mBusyScrollListenerCallback != null) mBusyScrollListenerCallback.setListViewBusy(isBusy, listView);
	}

	public int onDrawerNumberEntered(int number)
	{
		return 0;
	}

	public void onRequestStoreExtra()
	{

	}

	public void updatePageConfiguration(String postNumber, String threadTitle)
	{

	}

	public final boolean isDestroyed()
	{
		return mState == State.CONSUMED;
	}

	private void performResume()
	{
		onResume();
		onHandleNewPostDatas();
	}

	public final void resume()
	{
		if (mState == State.PAUSED)
		{
			mState = State.RESUMED;
			setListViewBusy(false, mListView); // Refresh list view contents
			performResume();
		}
	}

	public final void pause()
	{
		if (mState == State.RESUMED)
		{
			mState = State.PAUSED;
			onPause();
		}
	}

	public final void cleanup()
	{
		if (mState == State.RESUMED || mState == State.PAUSED)
		{
			if (mState == State.RESUMED) onPause();
			mState = State.CONSUMED;
			onDestroy();
		}
	}

	public final void handleNewPostDatasNow()
	{
		if (mState == State.RESUMED) onHandleNewPostDatas();
	}

	public interface Callback
	{
		public void notifyTitleChanged();
		public void updateOptionsMenu(boolean recreate);
		public void setCustomSearchView(View view);
		public void switchView(ViewType viewType, String message);
		public void showScaleAnimation();
		public void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber);
	}
}