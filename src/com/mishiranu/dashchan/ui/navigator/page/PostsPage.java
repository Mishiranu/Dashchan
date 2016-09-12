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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcel;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.StatisticsManager;
import com.mishiranu.dashchan.content.async.DeserializePostsTask;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.SeekBarForm;
import com.mishiranu.dashchan.ui.navigator.DrawerForm;
import com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.ThreadshotPerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.SearchHelper;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class PostsPage extends ListPage<PostsAdapter> implements FavoritesStorage.Observer, UiManager.Observer,
		DeserializePostsTask.Callback, ReadPostsTask.Callback, ActionMode.Callback
{
	private DeserializePostsTask mDeserializeTask;
	private ReadPostsTask mReadTask;
	
	private Replyable mReplyable;
	private HidePerformer mHidePerformer;
	private Pair<String, Uri> mOriginalThreadData;
	
	private String mScrollToPostNumber;
	private ActionMode mSelectionMode;
	
	private LinearLayout mSearchController;
	private TextView mSearchTextResult;
	private final ArrayList<Integer> mSearchFoundPosts = new ArrayList<>();
	private boolean mSearching = false;
	private int mSearchLastPosition;
	
	private int mAutoRefreshInterval = 30;
	private boolean mAutoRefreshEnabled = false;
	
	private final ArrayList<String> mLastEditedPostNumbers = new ArrayList<>();
	
	private final BroadcastReceiver mGalleryPagerReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String chanName = intent.getStringExtra(C.EXTRA_CHAN_NAME);
			String boardName = intent.getStringExtra(C.EXTRA_BOARD_NAME);
			String threadNumber = intent.getStringExtra(C.EXTRA_THREAD_NUMBER);
			PageHolder pageHolder = getPageHolder();
			if (pageHolder.chanName.equals(chanName) && StringUtils.equals(pageHolder.boardName, boardName)
					&& pageHolder.threadNumber.equals(threadNumber))
			{
				String postNumber = intent.getStringExtra(C.EXTRA_POST_NUMBER);
				int position = getAdapter().findPositionByPostNumber(postNumber);
				if (position >= 0) ListScroller.scrollTo(getListView(), position);
			}
		}
	};
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UiManager uiManager = getUiManager();
		mHidePerformer = new HidePerformer();
		PostsExtra extra = getExtra();
		listView.setDivider(ResourceUtils.getDrawable(activity, R.attr.postsDivider, 0));
		ChanConfiguration.Board board = getChanConfiguration().safe().obtainBoard(pageHolder.boardName);
		if (board.allowPosting)
		{
			mReplyable = (data) ->
			{
				getUiManager().navigator().navigatePosting(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber, data);
			};
		}
		PostsAdapter adapter = new PostsAdapter(activity, pageHolder.chanName, pageHolder.boardName, uiManager,
				mReplyable, mHidePerformer, extra.userPostNumbers, listView);
		initAdapter(adapter, adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		uiManager.observable().register(this);
		
		Context darkStyledContext = new ContextThemeWrapper(activity, R.style.Theme_General_Main_Dark);
		mSearchController = new LinearLayout(darkStyledContext);
		mSearchController.setOrientation(LinearLayout.HORIZONTAL);
		mSearchController.setGravity(Gravity.CENTER_VERTICAL);
		float density = ResourceUtils.obtainDensity(getResources());
		int padding = (int) (10f * density);
		mSearchTextResult = new TextView(darkStyledContext, null, android.R.attr.textAppearanceLarge);
		mSearchTextResult.setTextSize(11f);
		mSearchTextResult.setTypeface(null, Typeface.BOLD);
		mSearchTextResult.setPadding((int) (4f * density), 0, (int) (4f * density), 0);
		mSearchController.addView(mSearchTextResult, LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		ImageView backButtonView = new ImageView(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		backButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		backButtonView.setImageResource(obtainIcon(R.attr.actionBack));
		backButtonView.setPadding(padding, padding, padding, padding);
		backButtonView.setOnClickListener(v -> findBack());
		mSearchController.addView(backButtonView, (int) (48f * density), (int) (48f * density));
		if (C.API_LOLLIPOP)
		{
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) backButtonView.getLayoutParams();
			layoutParams.leftMargin = (int) (2f * density);
			layoutParams.rightMargin = -(int) (8f * density);
		}
		ImageView forwardButtonView = new ImageView(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		forwardButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		forwardButtonView.setImageResource(obtainIcon(R.attr.actionForward));
		forwardButtonView.setPadding(padding, padding, padding, padding);
		forwardButtonView.setOnClickListener(v -> findForward());
		mSearchController.addView(forwardButtonView, (int) (48f * density), (int) (48f * density));
		
		mScrollToPostNumber = pageHolder.initialPostNumber;
		FavoritesStorage.getInstance().getObservable().register(this);
		LocalBroadcastManager.getInstance(activity).registerReceiver(mGalleryPagerReceiver,
				new IntentFilter(C.ACTION_GALLERY_GO_TO_POST));
		boolean hasNewPostDatas = handleNewPostDatas();
		extra.forceRefresh = hasNewPostDatas || !pageHolder.initialFromCache;
		if (extra.cachedPosts != null && extra.cachedPostItems.size() > 0)
		{
			onDeserializePostsCompleteInternal(true, extra.cachedPosts, new ArrayList<>(extra.cachedPostItems), true);
		}
		else
		{
			mDeserializeTask = new DeserializePostsTask(this, pageHolder.chanName, pageHolder.boardName,
					pageHolder.threadNumber, extra.cachedPosts);
			mDeserializeTask.executeOnExecutor(DeserializePostsTask.THREAD_POOL_EXECUTOR);
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
		pageHolder.setInitialPostsData(false, null);
	}
	
	@Override
	protected void onResume()
	{
		queueNextRefresh(true);
	}
	
	@Override
	protected void onPause()
	{
		stopRefresh();
	}
	
	@Override
	protected void onDestroy()
	{
		getAdapter().cleanup();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mGalleryPagerReceiver);
		getUiManager().observable().unregister(this);
		if (mDeserializeTask != null)
		{
			mDeserializeTask.cancel();
			mDeserializeTask = null;
		}
		if (mReadTask != null)
		{
			mReadTask.cancel();
			mReadTask = null;
		}
		ImageLoader.getInstance().clearTasks(getPageHolder().chanName);
		FavoritesStorage.getInstance().getObservable().unregister(this);
	}
	
	@Override
	protected void onHandleNewPostDatas()
	{
		boolean hasNewPostDatas = handleNewPostDatas();
		if (hasNewPostDatas) refreshPosts(true, false);
	}
	
	@Override
	public String obtainTitle()
	{
		PageHolder pageHolder = getPageHolder();
		if (!StringUtils.isEmptyOrWhitespace(pageHolder.threadTitle)) return pageHolder.threadTitle;
		else return StringUtils.formatThreadTitle(pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber);
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		if (mSelectionMode != null)
		{
			getAdapter().toggleItemSelected(getListView(), position);
			mSelectionMode.setTitle(getString(R.string.text_selected_format, getAdapter().getSelectedCount()));
			return;
		}
		PostsAdapter adapter = getAdapter();
		PostItem postItem = adapter.getItem(position);
		if (postItem != null) getUiManager().interaction().handlePostClick(view, postItem, adapter);
	}
	
	@Override
	public boolean onItemLongClick(View view, int position, long id)
	{
		if (mSelectionMode != null) return false;
		PostsAdapter adapter = getAdapter();
		PostItem postItem = adapter.getItem(position);
		return postItem != null && getUiManager().interaction().handlePostContextMenu(postItem, mReplyable, true, true);
	}

	private static final int OPTIONS_MENU_ADD_POST = 0;
	private static final int OPTIONS_MENU_GALLERY = 1;
	private static final int OPTIONS_MENU_SELECT = 2;
	private static final int OPTIONS_MENU_REFRESH = 3;
	private static final int OPTIONS_MENU_THREAD_OPTIONS = 4;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_TEXT = 5;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT = 6;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_ICON = 7;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON = 8;
	private static final int OPTIONS_MENU_OPEN_ORIGINAL_THREAD = 9;
	private static final int OPTIONS_MENU_ARCHIVE = 10;
	private static final int OPTIONS_MENU_SEARCH_CONTROLLER = 11;
	
	private static final int THREAD_OPTIONS_MENU_RELOAD = 200;
	private static final int THREAD_OPTIONS_MENU_AUTO_REFRESH = 201;
	private static final int THREAD_OPTIONS_MENU_HIDDEN_POSTS = 202;
	private static final int THREAD_OPTIONS_MENU_CLEAR_DELETED = 203;
	private static final int THREAD_OPTIONS_MENU_SUMMARY = 204;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, OPTIONS_MENU_ADD_POST, 0, R.string.action_add_post).setIcon(obtainIcon(R.attr.actionAddPost))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_GALLERY, 0, R.string.action_gallery);
		menu.add(0, OPTIONS_MENU_SELECT, 0, R.string.action_select);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
		SubMenu threadOptions = menu.addSubMenu(0, OPTIONS_MENU_THREAD_OPTIONS, 0, R.string.action_thread_options);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_TEXT, 0, R.string.action_add_to_favorites);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT, 0, R.string.action_remove_from_favorites);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_ICON, 0, R.string.action_add_to_favorites)
				.setIcon(obtainIcon(R.attr.actionAddToFavorites)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON, 0, R.string.action_remove_from_favorites)
				.setIcon(obtainIcon(R.attr.actionRemoveFromFavorites)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_OPEN_ORIGINAL_THREAD, 0, R.string.action_open_the_original);
		menu.add(0, OPTIONS_MENU_ARCHIVE, 0, R.string.action_archive_add);
		menu.add(0, OPTIONS_MENU_SEARCH_CONTROLLER, 0, null).setActionView(mSearchController)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		
		threadOptions.add(0, THREAD_OPTIONS_MENU_RELOAD, 0, R.string.action_reload);
		threadOptions.add(0, THREAD_OPTIONS_MENU_AUTO_REFRESH, 0, R.string.action_auto_refresh).setCheckable(true);
		threadOptions.add(0, THREAD_OPTIONS_MENU_HIDDEN_POSTS, 0, R.string.action_hidden_posts);
		threadOptions.add(0, THREAD_OPTIONS_MENU_CLEAR_DELETED, 0, R.string.action_clear_deleted);
		threadOptions.add(0, THREAD_OPTIONS_MENU_SUMMARY, 0, R.string.action_summary);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu)
	{
		if (mSearching)
		{
			for (int i = 0; i < menu.size(); i++) menu.getItem(i).setVisible(false);
			menu.findItem(OPTIONS_MENU_SEARCH).setVisible(true);
			menu.findItem(OPTIONS_MENU_SEARCH_CONTROLLER).setVisible(true);
		}
		else
		{
			for (int i = 0; i < menu.size(); i++) menu.getItem(i).setVisible(true);
			PageHolder pageHolder = getPageHolder();
			menu.findItem(OPTIONS_MENU_ADD_POST).setVisible(mReplyable != null);
			boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName, pageHolder.boardName,
					pageHolder.threadNumber);
			boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
			menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_TEXT).setVisible(!iconFavorite && !isFavorite);
			menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT).setVisible(!iconFavorite && isFavorite);
			menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_ICON).setVisible(iconFavorite && !isFavorite);
			menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON).setVisible(iconFavorite && isFavorite);
			menu.findItem(OPTIONS_MENU_OPEN_ORIGINAL_THREAD).setVisible(mOriginalThreadData != null);
			menu.findItem(OPTIONS_MENU_ARCHIVE).setVisible(ChanManager.getInstance()
					.canBeArchived(pageHolder.chanName));
			menu.findItem(OPTIONS_MENU_SEARCH_CONTROLLER).setVisible(false);
			menu.findItem(THREAD_OPTIONS_MENU_AUTO_REFRESH).setVisible(Preferences.getAutoRefreshMode()
					== Preferences.AUTO_REFRESH_MODE_SEPARATE).setEnabled(!getAdapter().isEmpty())
					.setChecked(mAutoRefreshEnabled);
			menu.findItem(THREAD_OPTIONS_MENU_HIDDEN_POSTS).setEnabled(mHidePerformer.hasLocalAutohide());
			menu.findItem(THREAD_OPTIONS_MENU_CLEAR_DELETED).setEnabled(getAdapter().hasDeletedPosts());
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		Activity activity = getActivity();
		PageHolder pageHolder = getPageHolder();
		PostsAdapter adapter = getAdapter();
		switch (item.getItemId())
		{
			case OPTIONS_MENU_ADD_POST:
			{
				getUiManager().navigator().navigatePosting(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber);
				return true;
			}
			case OPTIONS_MENU_GALLERY:
			{
				int imageIndex = -1;
				ListView listView = getListView();
				View child = listView.getChildAt(0);
				if (child != null)
				{
					UiManager uiManager = getUiManager();
					ArrayList<GalleryItem> galleryItems = getAdapter().getGallerySet().getItems();
					int position = listView.getPositionForView(child);
					OUTER: for (int v = 0; v <= 1; v++)
					{
						for (PostItem postItem : adapter.iterate(v == 0, position))
						{
							imageIndex = uiManager.view().findImageIndex(galleryItems, postItem);
							if (imageIndex != -1) break OUTER;
						}
					}
				}
				NavigationUtils.openGallery(getActivity(), null, pageHolder.chanName, imageIndex,
						adapter.getGallerySet(), true, true);
				return true;
			}
			case OPTIONS_MENU_SELECT:
			{
				mSelectionMode = getActivity().startActionMode(this);
				return true;
			}
			case OPTIONS_MENU_REFRESH:
			{
				refreshPosts(true, false);
				return true;
			}
			case OPTIONS_MENU_ADD_TO_FAVORITES_TEXT:
			case OPTIONS_MENU_ADD_TO_FAVORITES_ICON:
			{
				FavoritesStorage.getInstance().add(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber, pageHolder.threadTitle, adapter.getExistingPostsCount());
				updateOptionsMenu(false);
				return true;
			}
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT:
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON:
			{
				FavoritesStorage.getInstance().remove(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber);
				updateOptionsMenu(false);
				return true;
			}
			case OPTIONS_MENU_OPEN_ORIGINAL_THREAD:
			{
				String chanName = mOriginalThreadData.first;
				Uri uri = mOriginalThreadData.second;
				ChanLocator locator = ChanLocator.get(chanName);
				String boardName = locator.safe(true).getBoardName(uri);
				String threadNumber = locator.safe(true).getThreadNumber(uri);
				if (threadNumber != null)
				{
					String threadTitle = getAdapter().getItem(0).getSubjectOrComment();
					getUiManager().navigator().navigatePosts(chanName, boardName, threadNumber, null,
							threadTitle, false);
				}
				return true;
			}
			case OPTIONS_MENU_ARCHIVE:
			{
				String threadTitle = null;
				if (adapter.getCount() > 0) threadTitle = adapter.getItem(0).getSubjectOrComment();
				getUiManager().dialog().performSendArchiveThread(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber, threadTitle, getExtra().cachedPosts);
				return true;
			}
			case THREAD_OPTIONS_MENU_RELOAD:
			{
				refreshPosts(true, true);
				return true;
			}
			case THREAD_OPTIONS_MENU_AUTO_REFRESH:
			{
				SeekBarForm seekBarForm = new SeekBarForm(true);
				seekBarForm.setConfiguration(Preferences.MIN_AUTO_REFRESH_INTERVAL,
						Preferences.MAX_AUTO_REFRESH_INTERVAL, Preferences.STEP_AUTO_REFRESH_INTERVAL, 1f);
				seekBarForm.setValueFormat(getString(R.string.preference_auto_refresh_interval_summary_format));
				seekBarForm.setCurrentValue(mAutoRefreshInterval);
				seekBarForm.setSwitchValue(mAutoRefreshEnabled);
				new AlertDialog.Builder(activity).setTitle(R.string.action_auto_refresh).setView(seekBarForm.inflate
						(getActivity())).setPositiveButton(android.R.string.ok, (dialog, which) ->
				{
					mAutoRefreshEnabled = seekBarForm.getSwitchValue();
					mAutoRefreshInterval = seekBarForm.getCurrentValue();
					Posts posts = getExtra().cachedPosts;
					boolean changed = posts.setAutoRefreshData(mAutoRefreshEnabled, mAutoRefreshInterval);
					if (changed) serializePosts();
					queueNextRefresh(true);
					
				}).setNegativeButton(android.R.string.cancel, null).show();
				return true;
			}
			case THREAD_OPTIONS_MENU_HIDDEN_POSTS:
			{
				ArrayList<String> localAutohide = mHidePerformer.getReadableLocalAutohide();
				final boolean[] checked = new boolean[localAutohide.size()];
				new AlertDialog.Builder(activity).setMultiChoiceItems(CommonUtils.toArray(localAutohide, String.class),
						checked, (dialog, which, isChecked) -> checked[which] = isChecked)
						.setPositiveButton(android.R.string.ok, (dialog, which) ->
				{
					boolean hasDeleted = false;
					for (int i = 0, j = 0; i < checked.length; i++, j++)
					{
						if (checked[i])
						{
							mHidePerformer.removeLocalAutohide(j--);
							hasDeleted = true;
						}
					}
					if (hasDeleted)
					{
						adapter.invalidateHidden();
						notifyAllAdaptersChanged();
						mHidePerformer.encodeLocalAutohide(getExtra().cachedPosts);
						serializePosts();
						adapter.preloadPosts(getListView().getFirstVisiblePosition());
					}
					
				}).setNegativeButton(android.R.string.cancel, null).setTitle(R.string.text_remove_rules).show();
				return true;
			}
			case THREAD_OPTIONS_MENU_CLEAR_DELETED:
			{
				new AlertDialog.Builder(getActivity()).setMessage(R.string.message_clear_deleted_warning)
						.setPositiveButton(android.R.string.ok, (dialog, which) ->
				{
					PostsExtra extra = getExtra();
					Posts cachedPosts = extra.cachedPosts;
					cachedPosts.clearDeletedPosts();
					ArrayList<PostItem> deletedPostItems = adapter.clearDeletedPosts();
					if (deletedPostItems != null)
					{
						extra.cachedPostItems.removeAll(deletedPostItems);
						for (PostItem postItem : deletedPostItems)
						{
							extra.userPostNumbers.remove(postItem.getPostNumber());
						}
						notifyAllAdaptersChanged();
					}
					updateOptionsMenu(false);
					serializePosts();
					
				}).setNegativeButton(android.R.string.cancel, null).show();
				return true;
			}
			case THREAD_OPTIONS_MENU_SUMMARY:
			{
				PostsExtra extra = getExtra();
				int files = 0;
				int postsWithFiles = 0;
				int links = 0;
				for (PostItem postItem : getAdapter())
				{
					ArrayList<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
					if (attachmentItems != null)
					{
						int itFiles = 0;
						for (AttachmentItem attachmentItem : attachmentItems)
						{
							int generalType = attachmentItem.getGeneralType();
							switch (generalType)
							{
								case AttachmentItem.GENERAL_TYPE_FILE:
								case AttachmentItem.GENERAL_TYPE_EMBEDDED:
								{
									itFiles++;
									break;
								}
								case AttachmentItem.GENERAL_TYPE_LINK:
								{
									links++;
									break;
								}
							}
						}
						if (itFiles > 0)
						{
							postsWithFiles++;
							files += itFiles;
						}
					}
				}
				int uniquePosters = extra.cachedPosts!= null ? extra.cachedPosts.getUniquePosters() : -1;
				StringBuilder builder = new StringBuilder();
				String boardName = pageHolder.boardName;
				if (boardName != null)
				{
					builder.append(getString(R.string.text_board)).append(": ");
					String title = getChanConfiguration().getBoardTitle(boardName);
					builder.append(StringUtils.formatBoardTitle(pageHolder.chanName, boardName, title));
					builder.append('\n');
				}
				builder.append(getString(R.string.text_files_format, files));
				builder.append('\n').append(getString(R.string.text_posts_with_files_format, postsWithFiles));
				builder.append('\n').append(getString(R.string.text_links_attachments_format, links));
				if (uniquePosters > 0)
				{
					builder.append('\n').append(getString(R.string.text_unique_posters_format, uniquePosters));
				}
				new AlertDialog.Builder(getActivity()).setTitle(R.string.action_summary).setMessage(builder)
						.setPositiveButton(android.R.string.ok, null).show();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action)
	{
		switch (action)
		{
			case FavoritesStorage.ACTION_ADD:
			case FavoritesStorage.ACTION_REMOVE:
			{
				PageHolder pageHolder = getPageHolder();
				if (favoriteItem.equals(pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber))
				{
					updateOptionsMenu(false);
				}
				break;
			}
		}
	}
	
	@Override
	public void onAppearanceOptionChanged(int what)
	{
		switch (what)
		{
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_MY_POSTS:
			case APPEARANCE_MENU_SFW_MODE:
			{
				notifyAllAdaptersChanged();
				break;
			}
		}
	}
	
	private static final int ACTION_MENU_MAKE_THREADSHOT = 0;
	private static final int ACTION_MENU_REPLY = 1;
	private static final int ACTION_MENU_DELETE_POSTS = 2;
	private static final int ACTION_MENU_SEND_REPORT = 3;
	
	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu)
	{
		PageHolder pageHolder = getPageHolder();
		ChanConfiguration configuration = getChanConfiguration();
		getAdapter().setSelectionModeEnabled(true);
		mode.setTitle(getString(R.string.text_selected_format, 0));
		int pasteResId = ResourceUtils.getSystemSelectionIcon(getActivity(), "actionModePasteDrawable",
				"ic_menu_paste_holo_dark");
		int flags = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
		ChanConfiguration.Board board = configuration.safe().obtainBoard(pageHolder.boardName);
		menu.add(0, ACTION_MENU_MAKE_THREADSHOT, 0, R.string.action_make_threadshot)
				.setIcon(obtainIcon(R.attr.actionMakeThreadshot)).setShowAsAction(flags);
		if (mReplyable != null)
		{
			menu.add(0, ACTION_MENU_REPLY, 0, R.string.action_reply).setIcon(pasteResId).setShowAsAction(flags);
		}
		if (board.allowDeleting)
		{
			ChanConfiguration.Deleting deleting = configuration.safe().obtainDeleting(pageHolder.boardName);
			if (deleting != null && deleting.multiplePosts)
			{
				menu.add(0, ACTION_MENU_DELETE_POSTS, 0, R.string.action_delete)
						.setIcon(obtainIcon(R.attr.actionDelete)).setShowAsAction(flags);
			}
		}
		if (board.allowReporting)
		{
			ChanConfiguration.Reporting reporting = configuration.safe().obtainReporting(pageHolder.boardName);
			if (reporting != null && reporting.multiplePosts)
			{
				menu.add(0, ACTION_MENU_SEND_REPORT, 0, R.string.action_report)
						.setIcon(obtainIcon(R.attr.actionReport)).setShowAsAction(flags);
			}
		}
		return true;
	}
	
	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu)
	{
		return false;
	}
	
	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item)
	{
		switch (item.getItemId())
		{
			case ACTION_MENU_MAKE_THREADSHOT:
			{
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				if (postItems.size() > 0)
				{
					PageHolder pageHolder = getPageHolder();
					new ThreadshotPerformer(getListView(), getUiManager(), pageHolder.chanName, pageHolder.boardName,
							pageHolder.threadNumber, getAdapter().getGallerySet().getThreadTitle(), postItems);
				}
				mode.finish();
				return true;
			}
			case ACTION_MENU_REPLY:
			{
				ArrayList<Replyable.ReplyData> data = new ArrayList<>();
				for (PostItem postItem : getAdapter().getSelectedItems())
				{
					data.add(new Replyable.ReplyData(postItem.getPostNumber(), null));
				}
				if (data.size() > 0) mReplyable.onRequestReply(CommonUtils.toArray(data, Replyable.ReplyData.class));
				mode.finish();
				return true;
			}
			case ACTION_MENU_DELETE_POSTS:
			{
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<String> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems)
				{
					if (!postItem.isDeleted()) postNumbers.add(postItem.getPostNumber());
				}
				if (postNumbers.size() > 0)
				{
					PageHolder pageHolder = getPageHolder();
					getUiManager().dialog().performSendDeletePosts(pageHolder.chanName, pageHolder.boardName,
							pageHolder.threadNumber, postNumbers);
				}
				mode.finish();
				return true;
			}
			case ACTION_MENU_SEND_REPORT:
			{
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<String> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems)
				{
					if (!postItem.isDeleted()) postNumbers.add(postItem.getPostNumber());
				}
				if (postNumbers.size() > 0)
				{
					PageHolder pageHolder = getPageHolder();
					getUiManager().dialog().performSendReportPosts(pageHolder.chanName, pageHolder.boardName,
							pageHolder.threadNumber, postNumbers);
				}
				mode.finish();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onDestroyActionMode(ActionMode mode)
	{
		getAdapter().setSelectionModeEnabled(false);
		mSelectionMode = null;
	}
	
	@Override
	public boolean onStartSearch(String query)
	{
		PostsAdapter adapter = getAdapter();
		if (adapter.isEmpty()) return false;
		mSearchFoundPosts.clear();
		int listPosition = ListPosition.obtain(getListView()).position;
		mSearchLastPosition = 0;
		boolean positionDefined = false;
		Locale locale = Locale.getDefault();
		SearchHelper helper = new SearchHelper();
		helper.setFlags("m", "r", "a", "d", "e", "op");
		HashSet<String> queries = helper.handleQueries(locale, query);
		HashSet<String> fileNames = new HashSet<>();
		PostsExtra extra = getExtra();
		OUTER: for (int i = 0; i < adapter.getCount(); i++)
		{
			PostItem postItem = adapter.getItem(i);
			if (postItem != null && !postItem.isHidden(mHidePerformer))
			{
				String postNumber = postItem.getPostNumber();
				String comment = postItem.getComment().toString().toLowerCase(locale);
				boolean userPost = postItem.isUserPost();
				boolean reply = false;
				HashSet<String> referencesTo = postItem.getReferencesTo();
				if (referencesTo != null)
				{
					for (String referenceTo : referencesTo)
					{
						if (extra.userPostNumbers.contains(referenceTo))
						{
							reply = true;
							break;
						}
					}
				}
				boolean hasAttachments = postItem.hasAttachments();
				boolean deleted = postItem.isDeleted();
				boolean edited = mLastEditedPostNumbers.contains(postNumber);
				boolean originalPoster = postItem.isOriginalPoster();
				if (!helper.checkFlags("m", userPost, "r", reply, "a", hasAttachments, "d", deleted, "e", edited,
						"op", originalPoster))
				{
					continue;
				}
				for (String lowQuery : helper.getExcluded())
				{
					if (comment.contains(lowQuery)) continue OUTER;
				}
				String subject = postItem.getSubject().toLowerCase(locale);
				String name = postItem.getFullName().toString().toLowerCase(locale);
				fileNames.clear();
				ArrayList<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
				if (attachmentItems != null)
				{
					for (AttachmentItem attachmentItem : attachmentItems)
					{
						String fileName = attachmentItem.getFileName();
						if (fileName != null)
						{
							fileNames.add(fileName.toLowerCase(locale));
							String originalName = attachmentItem.getOriginalName();
							if (originalName != null) fileNames.add(originalName.toLowerCase(locale));
						}
					}
				}
				boolean found = false;
				if (helper.hasIncluded())
				{
					QUERIES: for (String lowQuery : helper.getIncluded())
					{
						if (comment.contains(lowQuery))
						{
							found = true;
							break;
						}
						else if (subject.contains(lowQuery))
						{
							found = true;
							break;
						}
						else if (name.contains(lowQuery))
						{
							found = true;
							break;
						}
						else
						{
							for (String fileName : fileNames)
							{
								if (fileName.contains(lowQuery))
								{
									found = true;
									break QUERIES;
								}
							}
						}
					}
				}
				else found = true;
				if (found)
				{
					if (!positionDefined && i > listPosition)
					{
						mSearchLastPosition = mSearchFoundPosts.size();
						positionDefined = true;
					}
					mSearchFoundPosts.add(i);
				}
			}
		}
		boolean found = mSearchFoundPosts.size() > 0;
		setActionBarLocked(true);
		getUiManager().view().setHighlightText(found ? queries : null);
		adapter.notifyDataSetChanged();
		mSearching = true;
		if (found)
		{
			updateOptionsMenu(true);
			mSearchLastPosition--;
			findForward();
		}
		else
		{
			ToastUtils.show(getActivity(), R.string.message_not_found);
			mSearchLastPosition = -1;
			updateSearchTitle();
		}
		return true;
	}
	
	@Override
	public void onStopSearch()
	{
		onStopSearchInternal();
	}
	
	private boolean onStopSearchInternal()
	{
		if (mSearching)
		{
			mSearching = false;
			updateOptionsMenu(true);
			getUiManager().view().setHighlightText(null);
			getAdapter().notifyDataSetChanged();
			setActionBarLocked(false);
			return true;
		}
		else return false;
	}
	
	private void findBack()
	{
		int count = mSearchFoundPosts.size();
		if (count > 0)
		{
			mSearchLastPosition--;
			if (mSearchLastPosition < 0) mSearchLastPosition += count;
			ListScroller.scrollTo(getListView(), mSearchFoundPosts.get(mSearchLastPosition));
			updateSearchTitle();
		}
	}
	
	private void findForward()
	{
		int count = mSearchFoundPosts.size();
		if (count > 0)
		{
			mSearchLastPosition++;
			if (mSearchLastPosition >= count) mSearchLastPosition -= count;
			ListScroller.scrollTo(getListView(), mSearchFoundPosts.get(mSearchLastPosition));
			updateSearchTitle();
		}
	}
	
	private void updateSearchTitle()
	{
		mSearchTextResult.setText((mSearchLastPosition + 1) + "/" + mSearchFoundPosts.size());
	}
	
	@Override
	public boolean onBackPressed()
	{
		return onStopSearchInternal() || super.onBackPressed();
	}
	
	private boolean handleNewPostDatas()
	{
		PageHolder pageHolder = getPageHolder();
		ArrayList<PostingService.NewPostData> newPostDatas = PostingService.getNewPostDatas(getActivity(),
				pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber);
		if (newPostDatas != null)
		{
			boolean hasNewPostDatas = false;
			PostsExtra extra = getExtra();
			OUTER: for (PostingService.NewPostData newPostData : newPostDatas)
			{
				ReadPostsTask.UserPostPending userPostPending;
				if (newPostData.newThread)
				{
					userPostPending = new ReadPostsTask.NewThreadUserPostPending();
				}
				else if (newPostData.postNumber != null)
				{
					userPostPending = new ReadPostsTask.PostNumberUserPostPending(newPostData.postNumber);
					// Check this post had loaded before this callback was called
					// This can be unequivocally checked only for this type of UserPostPending
					for (PostItem postItem : getAdapter())
					{
						if (userPostPending.isUserPost(postItem.getPost()))
						{
							postItem.setUserPost(true);
							extra.userPostNumbers.add(postItem.getPostNumber());
							getUiManager().sendPostItemMessage(postItem, UiManager.MESSAGE_INVALIDATE_VIEW);
							serializePosts();
							continue OUTER;
						}
					}
				}
				else
				{
					userPostPending = new ReadPostsTask.CommentUserPostPending(newPostData.comment);
				}
				extra.userPostPendings.add(userPostPending);
				hasNewPostDatas = true;
			}
			return hasNewPostDatas;
		}
		return false;
	}
	
	@Override
	public int onDrawerNumberEntered(int number)
	{
		PostsAdapter adapter = getAdapter();
		int count = adapter.getCount();
		boolean success = false;
		if (count > 0 && number > 0)
		{
			if (number <= count)
			{
				int position = adapter.findPositionByOrdinalIndex(number - 1);
				if (position >= 0)
				{
					ListScroller.scrollTo(getListView(), position);
					success = true;
				}
			}
			if (!success)
			{
				int position = adapter.findPositionByPostNumber(Integer.toString(number));
				if (position >= 0)
				{
					ListScroller.scrollTo(getListView(), position);
					success = true;
				}
				else ToastUtils.show(getActivity(), R.string.message_post_not_found);
			}
		}
		int result = DrawerForm.RESULT_REMOVE_ERROR_MESSAGE;
		if (success) result |= DrawerForm.RESULT_SUCCESS;
		return result;
	}
	
	@Override
	public void onRequestStoreExtra()
	{
		PostsExtra extra = getExtra();
		extra.expandedPosts.clear();
		for (PostItem postItem : getAdapter())
		{
			if (postItem.isExpanded()) extra.expandedPosts.add(postItem.getPostNumber());
		}
	}
	
	@Override
	public void updatePageConfiguration(String postNumber, String threadTitle)
	{
		mScrollToPostNumber = postNumber;
		if (mReadTask == null && mDeserializeTask == null)
		{
			if (!scrollToSpecifiedPost(false)) refreshPosts(true, false);
		}
	}
	
	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshPosts(true, false, true);
	}
	
	private boolean scrollToSpecifiedPost(boolean instantly)
	{
		if (mScrollToPostNumber != null)
		{
			int position = getAdapter().findPositionByPostNumber(mScrollToPostNumber);
			if (position >= 0)
			{
				if (instantly) getListView().setSelection(position);
				else ListScroller.scrollTo(getListView(), position);
				mScrollToPostNumber = null;
			}
		}
		return mScrollToPostNumber == null;
	}
	
	private void onFirstPostsLoad()
	{
		if (mScrollToPostNumber == null)
		{
			PageHolder pageHolder = getPageHolder();
			if (pageHolder.position != null) pageHolder.position.apply(getListView());
		}
	}
	
	private void onAfterPostsLoad(boolean fromCache)
	{
		PageHolder pageHolder = getPageHolder();
		PostsExtra extra = getExtra();
		if (!extra.isAddedToHistory)
		{
			extra.isAddedToHistory = true;
			HistoryDatabase.getInstance().addHistory(pageHolder.chanName, pageHolder.boardName,
					pageHolder.threadNumber, pageHolder.threadTitle);
		}
		if (extra.cachedPosts != null)
		{
			Pair<String, Uri> originalThreadData = null;
			Uri archivedThreadUri = extra.cachedPosts.getArchivedThreadUri();
			if (archivedThreadUri != null)
			{
				String chanName = ChanManager.getInstance().getChanNameByHost(archivedThreadUri.getAuthority());
				if (chanName != null) originalThreadData = new Pair<>(chanName, archivedThreadUri);
			}
			if ((mOriginalThreadData == null) != (originalThreadData == null))
			{
				mOriginalThreadData = originalThreadData;
				updateOptionsMenu(false);
			}
		}
		if (!fromCache)
		{
			FavoritesStorage.getInstance().modifyPostsCount(pageHolder.chanName, pageHolder.boardName,
					pageHolder.threadNumber, getAdapter().getExistingPostsCount());
		}
		Iterator<PostItem> iterator = getAdapter().iterator();
		if (iterator.hasNext())
		{
			String title = iterator.next().getSubjectOrComment();
			if (StringUtils.isEmptyOrWhitespace(title)) title = null;
			FavoritesStorage.getInstance().modifyTitle(pageHolder.chanName, pageHolder.boardName,
					pageHolder.threadNumber, title, false);
			if (!StringUtils.equals(StringUtils.nullIfEmpty(pageHolder.threadTitle), title))
			{
				HistoryDatabase.getInstance().refreshTitles(pageHolder.chanName, pageHolder.boardName,
						pageHolder.threadNumber, title);
				pageHolder.threadTitle = title;
				notifyTitleChanged();
			}
		}
	}
	
	private static final Handler HANDLER = new Handler();
	
	private final Runnable mRefreshRunnable = () ->
	{
		if (mDeserializeTask == null && mReadTask == null) refreshPosts(true, false);
		queueNextRefresh(false);
	};
	
	private void queueNextRefresh(boolean instant)
	{
		HANDLER.removeCallbacks(mRefreshRunnable);
		int mode = Preferences.getAutoRefreshMode();
		boolean enabled = mode == Preferences.AUTO_REFRESH_MODE_SEPARATE && mAutoRefreshEnabled ||
				mode == Preferences.AUTO_REFRESH_MODE_ENABLED;
		if (enabled)
		{
			int interval = mode == Preferences.AUTO_REFRESH_MODE_SEPARATE ? mAutoRefreshInterval
					: Preferences.getAutoRefreshInterval();
			if (instant) HANDLER.post(mRefreshRunnable);
			else HANDLER.postDelayed(mRefreshRunnable, interval * 1000);
		}
	}
	
	private void stopRefresh()
	{
		HANDLER.removeCallbacks(mRefreshRunnable);
	}
	
	private void refreshPosts(boolean checkModified, boolean reload)
	{
		refreshPosts(checkModified, reload, !getAdapter().isEmpty());
	}
	
	private void refreshPosts(boolean checkModified, boolean reload, boolean showPull)
	{
		PostsExtra extra = getExtra();
		if (mDeserializeTask != null)
		{
			if (!reload) extra.forceRefresh = true;
			return;
		}
		if (mReadTask != null) mReadTask.cancel();
		PageHolder pageHolder = getPageHolder();
		PostsAdapter adapter = getAdapter();
		boolean partialLoading = !adapter.isEmpty();
		boolean useValidator = checkModified && partialLoading && !reload;
		mReadTask = new ReadPostsTask(this, pageHolder.chanName, pageHolder.boardName, pageHolder.threadNumber,
				extra.cachedPosts, useValidator, reload, adapter.getLastPostNumber(), extra.userPostPendings);
		mReadTask.executeOnExecutor(ReadPostsTask.THREAD_POOL_EXECUTOR);
		if (showPull)
		{
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTTOM);
			switchView(ViewType.LIST, null);
		}
		else
		{
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}
	
	@Override
	public void onRequestPreloadPosts(PostItem[] postItems)
	{
		PostsAdapter adapter = getAdapter();
		int count = adapter.getCount();
		int threshold = ListScroller.getJumpThreshold(getActivity());
		int handleNewCount = Math.min(threshold / 4, postItems.length);
		int handleOldCount = Math.min(threshold, count);
		for (int i = 0; i < handleNewCount; i++)
		{
			PostItem postItem = postItems[i];
			postItem.getComment();
			postItem.isHidden(mHidePerformer);
		}
		for (int i = 0; i < handleOldCount; i++)
		{
			PostItem postItem = adapter.getItem(count - i - 1);
			if (postItem != null)
			{
				postItem.getComment();
				postItem.isHidden(mHidePerformer);
			}
		}
	}
	
	@Override
	public void onDeserializePostsComplete(boolean success, Posts posts, ArrayList<PostItem> postItems)
	{
		mDeserializeTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		if (success && postItems != null)
		{
			PostsExtra extra = getExtra();
			extra.userPostNumbers.clear();
			for (PostItem postItem : postItems)
			{
				if (postItem.isUserPost()) extra.userPostNumbers.add(postItem.getPostNumber());
			}
		}
		onDeserializePostsCompleteInternal(success, posts, postItems, false);
	}
	
	private void onDeserializePostsCompleteInternal(boolean success, Posts posts, ArrayList<PostItem> postItems,
			boolean isLoadedExplicitly)
	{
		PostsAdapter adapter = getAdapter();
		PostsExtra extra = getExtra();
		extra.cachedPosts = null;
		extra.cachedPostItems.clear();
		if (success)
		{
			mHidePerformer.decodeLocalAutohide(posts);
			extra.cachedPosts = posts;
			extra.cachedPostItems.addAll(postItems);
			ArrayList<ReadPostsTask.Patch> patches = new ArrayList<>();
			for (int i = 0; i < postItems.size(); i++) patches.add(new ReadPostsTask.Patch(postItems.get(i), i));
			adapter.setItems(patches, isLoadedExplicitly);
			for (PostItem postItem : adapter)
			{
				if (extra.expandedPosts.contains(postItem.getPostNumber())) postItem.setExpanded(true);
			}
			Pair<Boolean, Integer> autoRefreshData = posts.getAutoRefreshData();
			mAutoRefreshEnabled = autoRefreshData.first;
			mAutoRefreshInterval = Math.min(Math.max(autoRefreshData.second, Preferences.MIN_AUTO_REFRESH_INTERVAL),
					Preferences.MAX_AUTO_REFRESH_INTERVAL);
			onFirstPostsLoad();
			onAfterPostsLoad(true);
			showScaleAnimation();
			scrollToSpecifiedPost(true);
			if (extra.forceRefresh)
			{
				extra.forceRefresh = false;
				refreshPosts(true, false);
			}
			queueNextRefresh(false);
		}
		else refreshPosts(false, false);
		updateOptionsMenu(false);
	}
	
	@Override
	public void onReadPostsSuccess(ReadPostsTask.Result result, boolean fullThread,
			ArrayList<ReadPostsTask.UserPostPending> removedUserPostPendings)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		PostsAdapter adapter = getAdapter();
		PageHolder pageHolder = getPageHolder();
		if (adapter.isEmpty()) StatisticsManager.getInstance().incrementViews(pageHolder.chanName);
		PostsExtra extra = getExtra();
		boolean wasEmpty = adapter.isEmpty();
		final int newPostPosition = adapter.getCount();
		if (removedUserPostPendings != null)
		{
			for (ReadPostsTask.UserPostPending userPostPending : removedUserPostPendings)
			{
				extra.userPostPendings.remove(userPostPending);
			}
		}
		if (fullThread)
		{
			// Thread was opened for the first time
			extra.cachedPosts = result.posts;
			extra.cachedPostItems.clear();
			extra.userPostNumbers.clear();
			for (ReadPostsTask.Patch patch : result.patches)
			{
				extra.cachedPostItems.add(patch.postItem);
				if (patch.newPost.isUserPost()) extra.userPostNumbers.add(patch.newPost.getPostNumber());
			}
			adapter.setItems(result.patches, false);
			boolean allowCache = CacheManager.getInstance().allowPagesCache(pageHolder.chanName);
			if (allowCache)
			{
				for (PostItem postItem : extra.cachedPostItems) postItem.setUnread(true);
			}
			onFirstPostsLoad();
		}
		else
		{
			if (extra.cachedPosts != null)
			{
				// Copy data from old model to new model
				Pair<Boolean, Integer> autoRefreshData = extra.cachedPosts.getAutoRefreshData();
				result.posts.setAutoRefreshData(autoRefreshData.first, autoRefreshData.second);
				result.posts.setLocalAutohide(extra.cachedPosts.getLocalAutohide());
			}
			extra.cachedPosts = result.posts;
			int repliesCount = 0;
			if (!result.patches.isEmpty())
			{
				// Copy data from old model to new model
				for (ReadPostsTask.Patch patch : result.patches)
				{
					if (patch.oldPost != null)
					{
						if (patch.oldPost.isUserPost()) patch.newPost.setUserPost(true);
						if (patch.oldPost.isHidden()) patch.newPost.setHidden(true);
						if (patch.oldPost.isShown()) patch.newPost.setHidden(false);
					}
				}
				for (ReadPostsTask.Patch patch : result.patches)
				{
					if (patch.newPost.isUserPost()) extra.userPostNumbers.add(patch.newPost.getPostNumber());
					if (patch.newPostAddedToEnd)
					{
						HashSet<String> referencesTo = patch.postItem.getReferencesTo();
						if (referencesTo != null)
						{
							for (String postNumber : referencesTo)
							{
								if (extra.userPostNumbers.contains(postNumber))
								{
									repliesCount++;
									break;
								}
							}
						}
					}
				}
				adapter.mergeItems(result.patches);
				extra.cachedPostItems.clear();
				for (PostItem postItem : adapter) extra.cachedPostItems.add(postItem);
				// Mark changed posts as unread
				for (ReadPostsTask.Patch patch : result.patches) patch.postItem.setUnread(true);
			}
			if (result.newCount > 0 || repliesCount > 0 || result.deletedCount > 0 || result.hasEdited)
			{
				StringBuilder message = new StringBuilder();
				if (repliesCount > 0 || result.deletedCount > 0)
				{
					message.append(getQuantityString(R.plurals.text_new_posts_count_short_format,
							result.newCount, result.newCount));
					if (repliesCount > 0)
					{
						message.append(", ").append(getQuantityString(R.plurals.text_replies_count_format,
								repliesCount, repliesCount));
					}
					if (result.deletedCount > 0)
					{
						message.append(", ").append(getQuantityString(R.plurals.text_deleted_count_format,
								result.deletedCount, result.deletedCount));
					}
				}
				else if (result.newCount > 0)
				{
					message.append(getQuantityString(R.plurals.text_new_posts_count_format,
							result.newCount, result.newCount));
				}
				else
				{
					message.append(getString(R.string.message_edited_posts));
				}
				if (result.newCount > 0)
				{
					ClickableToast.show(getActivity(), message, getString(R.string.action_show), () ->
					{
						if (!isDestroyed()) ListScroller.scrollTo(getListView(), newPostPosition);
						
					}, true);
				}
				else ClickableToast.show(getActivity(), message);
			}
		}
		boolean updateAdapters = result.newCount > 0 || result.deletedCount > 0 || result.hasEdited;
		serializePosts();
		if (result.hasEdited)
		{
			mLastEditedPostNumbers.clear();
			for (ReadPostsTask.Patch patch : result.patches)
			{
				if (!patch.newPostAddedToEnd) mLastEditedPostNumbers.add(patch.newPost.getPostNumber());
			}
		}
		if (updateAdapters)
		{
			getUiManager().dialog().updateAdapters();
			notifyAllAdaptersChanged();
		}
		onAfterPostsLoad(false);
		if (wasEmpty && !adapter.isEmpty()) showScaleAnimation();
		scrollToSpecifiedPost(wasEmpty);
		mScrollToPostNumber = null;
		updateOptionsMenu(false);
	}
	
	@Override
	public void onReadPostsEmpty()
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		if (getAdapter().isEmpty()) displayDownloadError(true, getString(R.string.message_empty_response));
		else onAfterPostsLoad(false);
	}
	
	@Override
	public void onReadPostsRedirect(String boardName, String threadNumber, String postNumber)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		PageHolder pageHolder = getPageHolder();
		removeCurrentPage();
		getUiManager().navigator().navigatePosts(pageHolder.chanName, boardName, threadNumber, postNumber, null, false);
	}
	
	@Override
	public void onReadPostsFail(ErrorItem errorItem)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		displayDownloadError(true, errorItem.toString());
		mScrollToPostNumber = null;
	}
	
	private void displayDownloadError(boolean show, String message)
	{
		if (show && getAdapter().getCount() > 0)
		{
			ClickableToast.show(getActivity(), message);
			return;
		}
		switchView(ViewType.ERROR, message);
	}
	
	private void hidePostAndReplies(PostItem postItem, ArrayList<PostItem> postItemsToInvalidate)
	{
		if (!postItem.getPost().isHidden())
		{
			postItem.setHidden(true);
			postItemsToInvalidate.add(postItem);
		}
		LinkedHashSet<String> referencesFrom = postItem.getReferencesFrom();
		if (referencesFrom != null)
		{
			PostsAdapter adapter = getAdapter();
			for (String postNumber : referencesFrom)
			{
				PostItem foundPostItem = adapter.findPostItem(postNumber);
				if (foundPostItem != null) hidePostAndReplies(foundPostItem, postItemsToInvalidate);
			}
		}
	}
	
	@Override
	public void onPostItemMessage(PostItem postItem, int message)
	{
		int index = getAdapter().indexOf(postItem);
		if (index == -1) return;
		switch (message)
		{
			case UiManager.MESSAGE_INVALIDATE_VIEW:
			{
				getAdapter().postNotifyDataSetChanged();
				break;
			}
			case UiManager.MESSAGE_INVALIDATE_COMMENT_VIEW:
			{
				getUiManager().view().invalidateCommentView(getListView(), index);
				break;
			}
			case UiManager.MESSAGE_PERFORM_SWITCH_USER_MARK:
			{
				postItem.setUserPost(!postItem.isUserPost());
				PostsExtra extra = getExtra();
				if (postItem.isUserPost()) extra.userPostNumbers.add(postItem.getPostNumber());
				else extra.userPostNumbers.remove(postItem.getPostNumber());
				getUiManager().sendPostItemMessage(postItem, UiManager.MESSAGE_INVALIDATE_VIEW);
				serializePosts();
				break;
			}
			case UiManager.MESSAGE_PERFORM_SWITCH_HIDE:
			{
				postItem.setHidden(!postItem.isHidden(mHidePerformer));
				getUiManager().sendPostItemMessage(postItem, UiManager.MESSAGE_INVALIDATE_VIEW);
				serializePosts();
				break;
			}
			case UiManager.MESSAGE_PERFORM_HIDE_REPLIES:
			{
				ArrayList<PostItem> postItemsToInvalidate = new ArrayList<>();
				hidePostAndReplies(postItem, postItemsToInvalidate);
				UiManager uiManager = getUiManager();
				for (PostItem invalidatePostItem : postItemsToInvalidate)
				{
					uiManager.sendPostItemMessage(invalidatePostItem, UiManager.MESSAGE_INVALIDATE_VIEW);
				}
				serializePosts();
				break;
			}
			case UiManager.MESSAGE_PERFORM_HIDE_NAME:
			case UiManager.MESSAGE_PERFORM_HIDE_SIMILAR:
			{
				PostsAdapter adapter = getAdapter();
				adapter.cancelPreloading();
				boolean success;
				if (message == UiManager.MESSAGE_PERFORM_HIDE_NAME) success = mHidePerformer.addHideByName(postItem);
				else success = mHidePerformer.addHideSimilar(postItem);
				if (success)
				{
					postItem.resetHidden();
					adapter.invalidateHidden();
					notifyAllAdaptersChanged();
					mHidePerformer.encodeLocalAutohide(getExtra().cachedPosts);
					serializePosts();
				}
				adapter.preloadPosts(getListView().getFirstVisiblePosition());
				break;
			}
			case UiManager.MESSAGE_PERFORM_DISPLAY_THUMBNAILS:
			{
				getUiManager().view().displayThumbnails(getListView(), index, postItem.getAttachmentItems(), true);
				break;
			}
		}
	}
	
	private void serializePosts()
	{
		PageHolder pageHolder = getPageHolder();
		CacheManager.getInstance().serializePosts(pageHolder.chanName, pageHolder.boardName,
				pageHolder.threadNumber, getExtra().cachedPosts);
	}
	
	public static class PostsExtra implements PageHolder.ParcelableExtra
	{
		public Posts cachedPosts;
		public final ArrayList<PostItem> cachedPostItems = new ArrayList<>();
		public final HashSet<String> userPostNumbers = new HashSet<>();
		
		public final ArrayList<ReadPostsTask.UserPostPending> userPostPendings = new ArrayList<>();
		public final HashSet<String> expandedPosts = new HashSet<>();
		public boolean isAddedToHistory = false;
		public boolean forceRefresh = false;
		
		@Override
		public void writeToParcel(Parcel dest)
		{
			dest.writeList(userPostPendings);
			dest.writeStringArray(CommonUtils.toArray(expandedPosts, String.class));
			dest.writeInt(isAddedToHistory ? 1 : 0);
			dest.writeInt(forceRefresh ? 1 : 0);
		}
		
		@Override
		public void readFromParcel(Parcel source)
		{
			@SuppressWarnings("unchecked")
			ArrayList<ReadPostsTask.UserPostPending> userPostPendings = source
					.readArrayList(PostsExtra.class.getClassLoader());
			if (userPostPendings.size() > 0) this.userPostPendings.addAll(userPostPendings);
			String[] data = source.createStringArray();
			if (data != null) Collections.addAll(expandedPosts, data);
			isAddedToHistory = source.readInt() != 0;
			forceRefresh = source.readInt() != 0;
		}
	}
	
	private PostsExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof PostsExtra)) pageHolder.extra = new PostsExtra();
		return (PostsExtra) pageHolder.extra;
	}
}