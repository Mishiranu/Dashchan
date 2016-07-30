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

package com.mishiranu.dashchan.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.v4.widget.DrawerLayout;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.WatcherService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.page.PageHolder;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.EdgeEffectHandler;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import com.mishiranu.dashchan.widget.SortableListView;

public class DrawerManager extends BaseAdapter implements EdgeEffectHandler.Shift, SortableListView.OnFinishedListener,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, DrawerLayout.DrawerListener,
		EditText.OnEditorActionListener, View.OnClickListener, View.OnTouchListener, WatcherService.Client.Callback
{
	private final Context mContext;
	private final Context mUnstyledContext;
	private final Callback mCallback;
	
	private final InputMethodManager mInputMethodManager;

	private final EditText mSearchEdit;
	private final View mHeaderView;
	private final TextView mChanNameView;
	private final ImageView mChanSelectorIcon;
	
	private final HashMap<String, Drawable> mChanIcons = new HashMap<>();
	private final HashSet<String> mWatcherSupportSet = new HashSet<>();
	
	private final ArrayList<ListItem> mChans = new ArrayList<>();
	private final ArrayList<ListItem> mPages = new ArrayList<>();
	private final ArrayList<ListItem> mFavorites = new ArrayList<>();
	private final ArrayList<ArrayList<ListItem>> mCategories = new ArrayList<>();
	private final ArrayList<ListItem> mMenu = new ArrayList<>();

	private final WatcherService.Client mWatcherServiceClient;
	private SortableListView mListView;
	
	private boolean mMergeChans = false;
	private boolean mChanSelectMode = false;
	private String mChanName;

	public static final int RESULT_REMOVE_ERROR_MESSAGE = 0x00000001;
	public static final int RESULT_SUCCESS = 0x00000002;
	
	public static final int MENU_ITEM_ALL_BOARDS = 1;
	public static final int MENU_ITEM_USER_BOARDS = 2;
	public static final int MENU_ITEM_HISTORY = 3;
	public static final int MENU_ITEM_PREFERENCES = 4;
	
	public interface Callback
	{
		public void onSelectChan(String chanName);
		public void onSelectBoard(String chanName, String boardName, boolean fromCache);
		public boolean onSelectThread(String chanName, String boardName, String threadNumber, String postNumber,
				String threadTitle, boolean fromCache);
		public boolean onClosePage(String chanName, String boardName, String threadNumber);
		public void onCloseAllPages();
		public int onEnterNumber(int number);
		public void onSelectDrawerMenuItem(int item);
		public ArrayList<PageHolder> getDrawerPageHolders();
	}
	
	public DrawerManager(Context context, Context unstyledContext, Callback callback,
			WatcherService.Client watcherServiceClient)
	{
		mContext = context;
		mUnstyledContext = unstyledContext;
		mCallback = callback;
		mWatcherServiceClient = watcherServiceClient;
		float density = ResourceUtils.obtainDensity(context);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setLayoutParams(new SortableListView.LayoutParams(SortableListView.LayoutParams.MATCH_PARENT,
				SortableListView.LayoutParams.WRAP_CONTENT));
		LinearLayout editTextContainer = new LinearLayout(context);
		editTextContainer.setGravity(Gravity.CENTER_VERTICAL);
		linearLayout.addView(editTextContainer);
		mSearchEdit = new SafePasteEditText(context);
		mSearchEdit.setOnKeyListener((v, keyCode, event) ->
		{
			if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) v.clearFocus();
			return false;
		});
		mSearchEdit.setHint(context.getString(R.string.text_code_number_address));
		mSearchEdit.setOnEditorActionListener(this);
		mSearchEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		mSearchEdit.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		ImageView searchIcon = new ImageView(context, null, android.R.attr.buttonBarButtonStyle);
		searchIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonForward, 0));
		searchIcon.setScaleType(ImageView.ScaleType.CENTER);
		searchIcon.setOnClickListener(this);
		editTextContainer.addView(mSearchEdit, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		editTextContainer.addView(searchIcon, (int) (40f * density), (int) (40f * density));
		if (C.API_LOLLIPOP)
		{
			editTextContainer.setPadding((int) (12f * density), (int) (8f * density), (int) (8f * density), 0);
		}
		else
		{
			editTextContainer.setPadding(0, (int) (2f * density), (int) (4f * density), (int) (2f * density));
		}
		LinearLayout selectorContainer = new LinearLayout(context);
		selectorContainer.setBackgroundResource(ResourceUtils.getResourceId(context,
				android.R.attr.selectableItemBackground, 0));
		selectorContainer.setOrientation(LinearLayout.HORIZONTAL);
		selectorContainer.setGravity(Gravity.CENTER_VERTICAL);
		selectorContainer.setOnClickListener(v ->
		{
			hideKeyboard();
			setChanSelectMode(!mChanSelectMode);
		});
		linearLayout.addView(selectorContainer);
		selectorContainer.setMinimumHeight((int) (40f * density));
		if (C.API_LOLLIPOP)
		{
			selectorContainer.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
			((LinearLayout.LayoutParams) selectorContainer.getLayoutParams()).topMargin = (int) (4f * density);
		}
		else
		{
			selectorContainer.setPadding((int) (8f * density), 0, (int) (12f * density), 0);
		}
		mChanNameView = new TextView(context, null, android.R.attr.textAppearanceListItem);
		mChanNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, C.API_LOLLIPOP ? 14f : 16f);
		if (C.API_LOLLIPOP) mChanNameView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
		else mChanNameView.setFilters(new InputFilter[] {new InputFilter.AllCaps()});
		selectorContainer.addView(mChanNameView, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		mChanSelectorIcon = new ImageView(context);
		mChanSelectorIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonDropDownDrawer, 0));
		selectorContainer.addView(mChanSelectorIcon, (int) (24f * density), (int) (24f * density));
		((LinearLayout.LayoutParams) mChanSelectorIcon.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL
				| Gravity.END;
		mHeaderView = linearLayout;
		mInputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		mChans.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
		int color = ResourceUtils.getColor(mContext, R.attr.drawerIconColor);
		ChanManager manager = ChanManager.getInstance();
		Collection<String> availableChans = manager.getAvailableChanNames();
		for (String chanName : availableChans)
		{
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			if (configuration.getOption(ChanConfiguration.OPTION_READ_POSTS_COUNT)) mWatcherSupportSet.add(chanName);
			Drawable drawable = manager.getIcon(chanName, color);
			mChanIcons.put(chanName, drawable);
			mChans.add(new ListItem(ListItem.ITEM_CHAN, chanName, null, null, configuration.getTitle(), 0, drawable));
		}
		if (availableChans.size() == 1) selectorContainer.setVisibility(View.GONE);
	}
	
	public void bind(SortableListView listView)
	{
		mListView = listView;
		listView.setClipToPadding(false);
		listView.setEdgeEffectShift(this);
		listView.setScrollBarStyle(SortableListView.SCROLLBARS_OUTSIDE_OVERLAY);
		listView.setAdapter(this);
		listView.setOnItemClickListener(this);
		listView.setOnItemLongClickListener(this);
		listView.setOnScrollListener(new ListViewScrollFixListener());
		listView.setOnSortingFinishedListener(this);
		listView.setOnTouchListener(this);
		float density = ResourceUtils.obtainDensity(mContext);
		if (C.API_LOLLIPOP) listView.setDivider(null);
		else listView.setPadding((int) (12f * density), 0, (int) (12f * density), 0);
	}
	
	public void updateConfiguration(String chanName)
	{
		if (!StringUtils.equals(chanName, mChanName))
		{
			mChanName = chanName;
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			mChanNameView.setText(configuration.getTitle());
			mMenu.clear();
			Context context = mContext;
			mMenu.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
			TypedArray typedArray = context.obtainStyledAttributes(new int[] {R.attr.drawerMenuBoards,
					R.attr.drawerMenuUserBoards, R.attr.drawerMenuHistory, R.attr.drawerMenuPreferences});
			boolean addDivider = false;
			boolean hasUserBoards = configuration.getOption(ChanConfiguration.OPTION_READ_USER_BOARDS);
			if (!configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE))
			{
				mMenu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_ALL_BOARDS, typedArray.getResourceId(0, 0),
						context.getString(hasUserBoards ? R.string.action_general_boards : R.string.action_boards)));
				addDivider = true;
			}
			if (hasUserBoards)
			{
				mMenu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_USER_BOARDS, typedArray.getResourceId(1, 0),
						context.getString(R.string.action_user_boards)));
				addDivider = true;
			}
			if (addDivider) mMenu.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
			mMenu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_HISTORY, typedArray.getResourceId(2, 0),
					context.getString(R.string.action_history)));
			mMenu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_PREFERENCES, typedArray.getResourceId(3, 0),
					context.getString(R.string.action_preferences)));
			typedArray.recycle();
			invalidateItems(false, true);
		}
	}
	
	public View getHeaderView()
	{
		return mHeaderView;
	}
	
	private void setChanSelectMode(boolean enabled)
	{
		if (mChanSelectMode != enabled)
		{
			mChanSelectMode = enabled;
			mChanSelectorIcon.setRotation(enabled ? 180f : 0f);
			notifyDataSetChanged();
			mListView.setSelection(0);
		}
	}
	
	@Override
	public void onSortingFinished(SortableListView listView, int oldPosition, int newPosition)
	{
		if (mChanSelectMode)
		{
			mChans.add(newPosition - 1, mChans.remove(oldPosition - 1));
			ArrayList<String> chanNames = new ArrayList<>();
			for (ListItem listItem : mChans)
			{
				if (listItem.type == ListItem.ITEM_CHAN) chanNames.add(listItem.chanName);
			}
			ChanManager.getInstance().setChansOrder(chanNames);
			// Regroup favorite threads
			if (mMergeChans) invalidateItems(false, true);
		}
		else
		{
			ListItem listItem = getItem(oldPosition);
			ListItem afterListItem = newPosition > oldPosition ? getItem(newPosition) : getItem(newPosition - 1);
			FavoritesStorage favoritesStorage = FavoritesStorage.getInstance();
			FavoritesStorage.FavoriteItem favoriteItem = favoritesStorage.getFavorite(listItem.chanName,
					listItem.boardName, listItem.threadNumber);
			FavoritesStorage.FavoriteItem afterFavoriteItem = afterListItem.type == ListItem.ITEM_FAVORITE
					&& afterListItem.chanName.equals(favoriteItem.chanName) ? favoritesStorage
					.getFavorite(afterListItem.chanName, afterListItem.boardName, afterListItem.threadNumber) : null;
			favoritesStorage.moveAfter(favoriteItem, afterFavoriteItem);
			invalidateItems(false, true);
		}
	}
	
	private boolean mMultipleFingersCountingTime = false;
	private long mMultipleFingersTime;
	private long mMultipleFingersStartTime;
	
	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		switch (event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
			{
				mMultipleFingersCountingTime = false;
				mMultipleFingersStartTime = 0L;
				mMultipleFingersTime = 0L;
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				if (!mMultipleFingersCountingTime)
				{
					mMultipleFingersCountingTime = true;
					mMultipleFingersStartTime = System.currentTimeMillis();
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_UP:
			{
				if (event.getPointerCount() <= 2)
				{
					if (mMultipleFingersCountingTime)
					{
						mMultipleFingersCountingTime = false;
						mMultipleFingersTime += System.currentTimeMillis() - mMultipleFingersStartTime;
					}
				}
				break;
			}
		}
		return false;
	}
	
	public void performResume()
	{
		boolean mergeChans = Preferences.isMergeChans();
		if (mMergeChans != mergeChans)
		{
			mMergeChans = mergeChans;
			invalidateItems(true, true);
		}
	}
	
	public void invalidateItems(boolean pages, boolean favorites)
	{
		updateList(pages, favorites);
		notifyDataSetChanged();
	}
	
	@Override
	public void notifyDataSetChanged()
	{
		boolean focused = mSearchEdit.isFocused();
		if (focused) focused = mInputMethodManager != null && mInputMethodManager.isActive(mSearchEdit);
		super.notifyDataSetChanged();
		if (focused)
		{
			mSearchEdit.removeCallbacks(mRestoreSearchFocusRunnable);
			mSearchEdit.post(mRestoreSearchFocusRunnable);
		}
	}
	
	private final Runnable mRestoreSearchFocusRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			mSearchEdit.requestFocus();
		}
	};
	
	@Override
	public int getEdgeEffectShift(boolean top)
	{
		int shift = mListView.obtainEdgeEffectShift(top);
		return top ? shift + mHeaderView.getPaddingTop() : shift;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		ListItem listItem = getItem(position);
		if (listItem != null)
		{
			switch (listItem.type)
			{
				case ListItem.ITEM_PAGE:
				case ListItem.ITEM_FAVORITE:
				{
					boolean fromCache = listItem.type == ListItem.ITEM_PAGE;
					if (!listItem.isThreadItem())
					{
						mCallback.onSelectBoard(listItem.chanName, listItem.boardName, fromCache);
					}
					else
					{
						mCallback.onSelectThread(listItem.chanName, listItem.boardName, listItem.threadNumber, null,
								listItem.title, fromCache);
					}
					break;
				}
				case ListItem.ITEM_MENU:
				{
					mCallback.onSelectDrawerMenuItem(listItem.data);
					break;
				}
				case ListItem.ITEM_CHAN:
				{
					mCallback.onSelectChan(listItem.chanName);
					setChanSelectMode(false);
					if (C.API_LOLLIPOP) mListView.getSelector().jumpToCurrentState();
					break;
				}
			}
		}
	}
	
	private static final int MENU_COPY_LINK = 0;
	private static final int MENU_ADD_TO_FAVORITES = 1;
	private static final int MENU_REMOVE_FROM_FAVORITES = 2;
	private static final int MENU_RENAME = 3;
	
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		if (mChanSelectMode) return mListView.startSorting(2, mChans.size(), position);
		final ListItem listItem = getItem(position);
		if (listItem != null)
		{
			if (listItem.type == ListItem.ITEM_FAVORITE && listItem.threadNumber != null &&
					FavoritesStorage.getInstance().canSortManually())
			{
				long time = mMultipleFingersTime + (mMultipleFingersCountingTime
						? System.currentTimeMillis() - mMultipleFingersStartTime : 0L);
				if (time >= ViewConfiguration.getLongPressTimeout() / 10)
				{
					int start = position;
					int end = position;
					for (int i = position - 1;; i--)
					{
						ListItem checkingListItem = getItem(i);
						if (checkingListItem == null) break;
						if (checkingListItem.type != ListItem.ITEM_FAVORITE ||
								!listItem.chanName.equals(checkingListItem.chanName))
						{
							start = i + 1;
							break;
						}
					}
					for (int i = position + 1;; i++)
					{
						ListItem checkingListItem = getItem(i);
						if (checkingListItem == null) break;
						if (checkingListItem.type != ListItem.ITEM_FAVORITE ||
								!listItem.chanName.equals(checkingListItem.chanName))
						{
							end = i - 1;
							break;
						}
					}
					mListView.startSorting(start, end, position);
					return true;
				}
			}
			switch (listItem.type)
			{
				case ListItem.ITEM_PAGE:
				case ListItem.ITEM_FAVORITE:
				{
					DialogMenu dialogMenu = new DialogMenu(mContext, new DialogMenu.Callback()
					{
						@Override
						public void onItemClick(Context context, int id, Map<String, Object> extra)
						{
							switch (id)
							{
								case MENU_COPY_LINK:
								{
									ChanLocator locator = ChanLocator.get(listItem.chanName);
									Uri uri = listItem.isThreadItem() ? locator.safe(true).createThreadUri
											(listItem.boardName, listItem.threadNumber)
											: locator.safe(true).createBoardUri(listItem.boardName, 0);
									if (uri != null) StringUtils.copyToClipboard(mContext, uri.toString());
									break;
								}
								case MENU_ADD_TO_FAVORITES:
								{
									if (listItem.isThreadItem())
									{
										FavoritesStorage.getInstance().add(listItem.chanName, listItem.boardName,
												listItem.threadNumber, listItem.title, 0);
									}
									else
									{
										FavoritesStorage.getInstance().add(listItem.chanName, listItem.boardName);
									}
									break;
								}
								case MENU_REMOVE_FROM_FAVORITES:
								{
									FavoritesStorage.getInstance().remove(listItem.chanName, listItem.boardName,
											listItem.threadNumber);
									break;
								}
								case MENU_RENAME:
								{
									final EditText editText = new SafePasteEditText(mContext);
									editText.setSingleLine(true);
									editText.setText(listItem.title);
									editText.setSelection(editText.length());
									LinearLayout linearLayout = new LinearLayout(mContext);
									linearLayout.setOrientation(LinearLayout.HORIZONTAL);
									linearLayout.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT,
											LinearLayout.LayoutParams.WRAP_CONTENT);
									int padding = mContext.getResources().getDimensionPixelSize(R.dimen
											.dialog_padding_view);
									linearLayout.setPadding(padding, padding, padding, padding);
									AlertDialog dialog = new AlertDialog.Builder(mContext).setView(linearLayout)
											.setTitle(R.string.action_rename).setNegativeButton(android.R.string.cancel, null)
											.setPositiveButton(android.R.string.ok, (d, which) ->
									{
										String newTitle = editText.getText().toString();
										FavoritesStorage.getInstance().modifyTitle(listItem.chanName,
												listItem.boardName, listItem.threadNumber, newTitle, true);
										invalidateItems(false, true);
										
									}).create();
									dialog.getWindow().setSoftInputMode(WindowManager
											.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
									dialog.show();
									break;
								}
							}
						}
					});
					dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
					if (listItem.type != ListItem.ITEM_FAVORITE && !FavoritesStorage.getInstance()
							.hasFavorite(listItem.chanName, listItem.boardName, listItem.threadNumber))
					{
						dialogMenu.addItem(MENU_ADD_TO_FAVORITES, R.string.action_add_to_favorites);
					}
					if (listItem.type == ListItem.ITEM_FAVORITE)
					{
						dialogMenu.addItem(MENU_REMOVE_FROM_FAVORITES, R.string.action_remove_from_favorites);
						if (listItem.threadNumber != null) dialogMenu.addItem(MENU_RENAME, R.string.action_rename);
					}
					dialogMenu.show();
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void onDrawerSlide(View drawerView, float slideOffset)
	{
		
	}
	
	@Override
	public void onDrawerOpened(View drawerView)
	{
		
	}
	
	@Override
	public void onDrawerClosed(View drawerView)
	{
		hideKeyboard();
		setChanSelectMode(false);
	}
	
	@Override
	public void onDrawerStateChanged(int newState)
	{
		
	}
	
	private void clearTextAndHideKeyboard()
	{
		mSearchEdit.setText(null);
		hideKeyboard();
	}
	
	private void hideKeyboard()
	{
		mSearchEdit.clearFocus();
		if (mInputMethodManager != null) mInputMethodManager.hideSoftInputFromWindow(mSearchEdit.getWindowToken(), 0);
	}
	
	private static final Pattern PATTERN_NAVIGATION_BOARD_THREAD = Pattern.compile("([\\w_-]+) (\\d+)");
	private static final Pattern PATTERN_NAVIGATION_BOARD = Pattern.compile("/?([\\w_-]+)");
	private static final Pattern PATTERN_NAVIGATION_THREAD = Pattern.compile("#(\\d+)");
	
	@Override
	public void onClick(View v)
	{
		String text = mSearchEdit.getText().toString().trim();
		int number = -1;
		try
		{
			number = Integer.parseInt(text);
		}
		catch (NumberFormatException e)
		{
			
		}
		if (number >= 0)
		{
			int result = mCallback.onEnterNumber(number);
			if (FlagUtils.get(result, RESULT_SUCCESS))
			{
				clearTextAndHideKeyboard();
				return;
			}
			if (FlagUtils.get(result, RESULT_REMOVE_ERROR_MESSAGE)) return;
		}
		else
		{
			{
				String boardName = null;
				String threadNumber = null;
				Matcher matcher = PATTERN_NAVIGATION_BOARD_THREAD.matcher(text);
				if (matcher.matches())
				{
					boardName = matcher.group(1);
					threadNumber = matcher.group(2);
				}
				else
				{
					matcher = PATTERN_NAVIGATION_BOARD.matcher(text);
					if (matcher.matches())
					{
						boardName = matcher.group(1);
					}
					else
					{
						matcher = PATTERN_NAVIGATION_THREAD.matcher(text);
						if (matcher.matches())
						{
							threadNumber = matcher.group(1);
						}
					}
				}
				if (boardName != null || threadNumber != null)
				{
					boolean success;
					if (threadNumber == null)
					{
						mCallback.onSelectBoard(mChanName, boardName, false);
						success = true;
					}
					else
					{
						success = mCallback.onSelectThread(mChanName, boardName, threadNumber, null, null, false);
					}
					if (success)
					{
						clearTextAndHideKeyboard();
						return;
					}
				}
			}
			Uri uri = Uri.parse(text);
			String chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
			if (chanName != null)
			{
				boolean success = false;
				String boardName = null;
				String threadNumber = null;
				String postNumber = null;
				ChanLocator locator = ChanLocator.get(chanName);
				if (locator.safe(false).isThreadUri(uri))
				{
					boardName = locator.safe(false).getBoardName(uri);
					threadNumber = locator.safe(false).getThreadNumber(uri);
					postNumber = locator.safe(false).getPostNumber(uri);
					success = true;
				}
				else if (locator.safe(false).isBoardUri(uri))
				{
					boardName = locator.safe(false).getBoardName(uri);
					threadNumber = null;
					postNumber = null;
					success = true;
				}
				if (success)
				{
					if (threadNumber == null) mCallback.onSelectBoard(chanName, boardName, false);
					else mCallback.onSelectThread(chanName, boardName, threadNumber, postNumber, null, false);
					clearTextAndHideKeyboard();
					return;
				}
			}
		}
		ToastUtils.show(mContext, R.string.message_enter_valid_data);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
	{
		onClick(v);
		return true;
	}
	
	private void updateList(boolean pages, boolean favorites)
	{
		int mode = Preferences.getPagesListMode();
		if (pages)
		{
			mPages.clear();
			if (mode != Preferences.PAGES_LIST_MODE_HIDE_PAGES) updateListPages();
		}
		if (favorites)
		{
			mFavorites.clear();
			updateListFavorites();
		}
		mCategories.clear();
		switch (mode)
		{
			case Preferences.PAGES_LIST_MODE_PAGES_FIRST:
			{
				mCategories.add(mPages);
				mCategories.add(mFavorites);
				break;
			}
			case Preferences.PAGES_LIST_MODE_FAVORITES_FIRST:
			{
				mCategories.add(mFavorites);
				mCategories.add(mPages);
				break;
			}
			case Preferences.PAGES_LIST_MODE_HIDE_PAGES:
			{
				mCategories.add(mFavorites);
				break;
			}
		}
		notifyDataSetChanged();
	}
	
	private void updateListPages()
	{
		boolean mergeChans = mMergeChans;
		ArrayList<PageHolder> allPages = mCallback.getDrawerPageHolders();
		ArrayList<PageHolder> pages = new ArrayList<>();
		for (PageHolder pageHolder : allPages)
		{
			boolean isThreads = pageHolder.content == PageHolder.Content.THREADS;
			boolean isPosts = pageHolder.content == PageHolder.Content.POSTS;
			if (isThreads || isPosts)
			{
				if (mergeChans || pageHolder.chanName.equals(mChanName))
				{
					if (pageHolder.threadNumber != null || !ChanConfiguration.get(pageHolder.chanName)
							.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE))
					{
						pages.add(pageHolder);
					}
				}
			}
		}
		if (pages.size() > 0)
		{
			Collections.sort(pages, DATE_COMPARATOR);
			mPages.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
					mContext.getString(R.string.text_open_pages), HEADER_ACTION_CLOSE_ALL,
					ResourceUtils.getResourceId(mContext, R.attr.buttonCancel, 0)));
			for (PageHolder pageHolder : pages)
			{
				Drawable drawable = mChanIcons.get(pageHolder.chanName);
				if (pageHolder.threadNumber != null)
				{
					mPages.add(new ListItem(ListItem.ITEM_PAGE, pageHolder.chanName, pageHolder.boardName,
							pageHolder.threadNumber, pageHolder.threadTitle, 0, drawable));
				}
				else
				{
					mPages.add(new ListItem(ListItem.ITEM_PAGE, pageHolder.chanName, pageHolder.boardName,
							null, ChanConfiguration.get(pageHolder.chanName).getBoardTitle(pageHolder.boardName),
							0, drawable));
				}
			}
		}
	}
	
	private static final Comparator<PageHolder> DATE_COMPARATOR = (lhs, rhs) ->
	{
		return (int) (rhs.creationTime - lhs.creationTime);
	};
	
	private void updateListFavorites()
	{
		boolean mergeChans = mMergeChans;
		FavoritesStorage favoritesStorage = FavoritesStorage.getInstance();
		ArrayList<FavoritesStorage.FavoriteItem> favoriteBoards = favoritesStorage.getBoards(mergeChans
				? null : mChanName);
		ArrayList<FavoritesStorage.FavoriteItem> favoriteThreads = favoritesStorage.getThreads(mergeChans
				? null : mChanName);
		boolean addHeader = true;
		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		for (int i = 0; i < favoriteThreads.size(); i++)
		{
			FavoritesStorage.FavoriteItem favoriteItem = favoriteThreads.get(i);
			if (!chanNames.contains(favoriteItem.chanName)) continue;
			if (mergeChans || favoriteItem.chanName.equals(mChanName))
			{
				if (addHeader)
				{
					if (mWatcherSupportSet.contains(favoriteItem.chanName)
							|| mergeChans && !mWatcherSupportSet.isEmpty())
					{
						mFavorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
								mContext.getString(R.string.text_favorite_threads), HEADER_ACTION_REFRESH_WATCHER,
								ResourceUtils.getResourceId(mContext, R.attr.buttonRefresh, 0)));
					}
					else
					{
						mFavorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
								mContext.getString(R.string.text_favorite_threads)));
					}
					addHeader = false;
				}
				ListItem listItem = new ListItem(ListItem.ITEM_FAVORITE, favoriteItem.chanName, favoriteItem.boardName,
						favoriteItem.threadNumber, favoriteItem.title, 0, mChanIcons.get(favoriteItem.chanName));
				if (mWatcherSupportSet.contains(favoriteItem.chanName))
				{
					WatcherService.TemporalCountData temporalCountData = mWatcherServiceClient
							.countNewPosts(favoriteItem);
					listItem.watcherPostsCountDifference = temporalCountData.postsCountDifference;
					listItem.watcherHasNewPosts = temporalCountData.hasNewPosts;
					listItem.watcherIsError = temporalCountData.isError;
				}
				mFavorites.add(listItem);
			}
		}
		addHeader = true;
		for (int i = 0; i < favoriteBoards.size(); i++)
		{
			FavoritesStorage.FavoriteItem favoriteItem = favoriteBoards.get(i);
			if (!chanNames.contains(favoriteItem.chanName)) continue;
			if (mergeChans || favoriteItem.chanName.equals(mChanName))
			{
				if (addHeader)
				{
					mFavorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
							mContext.getString(R.string.text_favorite_boards)));
					addHeader = false;
				}
				mFavorites.add(new ListItem(ListItem.ITEM_FAVORITE, favoriteItem.chanName, favoriteItem.boardName, null,
						ChanConfiguration.get(favoriteItem.chanName).getBoardTitle(favoriteItem.boardName), 0,
						mChanIcons.get(favoriteItem.chanName)));
			}
		}
	}
	
	private static class ListItem
	{
		public static final int ITEM_HEADER = 1;
		public static final int ITEM_PAGE = 2;
		public static final int ITEM_FAVORITE = 3;
		public static final int ITEM_MENU = 4;
		public static final int ITEM_CHAN = 5;
		public static final int ITEM_DIVIDER = 6;
		
		public final int type, data, resourceId;
		public final String chanName, boardName, threadNumber, title;
		public final Drawable resource;
		
		private int watcherPostsCountDifference = 0;
		private boolean watcherHasNewPosts = false;
		private boolean watcherIsError = false;
		
		public ListItem(int type, String chanName, String boardName, String threadNumber, String title)
		{
			this(type, chanName, boardName, threadNumber, title, 0, 0);
		}
		
		public ListItem(int type, String chanName, String boardName, String threadNumber, String title, int data,
				int resourceId)
		{
			this.type = type;
			this.data = data;
			this.resourceId = resourceId;
			this.resource = null;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.title = title;
		}
		
		public ListItem(int type, String chanName, String boardName, String threadNumber, String title, int data,
				Drawable resource)
		{
			this.type = type;
			this.data = data;
			this.resourceId = 0;
			this.resource = resource;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.title = title;
		}
		
		public ListItem(int type, int data, int resourceId, String title)
		{
			this.type = type;
			this.data = data;
			this.resourceId = resourceId;
			this.resource = null;
			this.chanName = null;
			this.boardName = null;
			this.threadNumber = null;
			this.title = title;
		}
		
		public boolean isThreadItem()
		{
			return threadNumber != null;
		}

		public boolean compare(String chanName, String boardName, String threadNumber)
		{
			return StringUtils.equals(this.chanName, chanName) && StringUtils.equals(this.boardName, boardName)
					&& StringUtils.equals(this.threadNumber, threadNumber);
		}
	}
	
	private final View.OnClickListener mCloseButtonListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			ListItem listItem = getItem(v);
			if (listItem != null && listItem.type == ListItem.ITEM_PAGE)
			{
				boolean result = mCallback.onClosePage(listItem.chanName, listItem.boardName, listItem.threadNumber);
				if (result)
				{
					// size == 2 means only one item in list + header
					if (mPages.size() == 2) mPages.clear(); else mPages.remove(listItem);
					notifyDataSetChanged();
				}
			}
		}
	};
	
	private static final int HEADER_ACTION_CLOSE_ALL = 0;
	private static final int HEADER_ACTION_REFRESH_WATCHER = 1;
	
	private final View.OnClickListener mHeaderButtonListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			ListItem listItem = getItem(v);
			if (listItem != null && listItem.type == ListItem.ITEM_HEADER)
			{
				switch (listItem.data)
				{
					case HEADER_ACTION_CLOSE_ALL:
					{
						mCallback.onCloseAllPages();
						break;
					}
					case HEADER_ACTION_REFRESH_WATCHER:
					{
						mWatcherServiceClient.update();
						break;
					}
				}
			}
		}
	};
	
	private static final int TYPE_VIEW = 0;
	private static final int TYPE_VIEW_ICON = 1;
	private static final int TYPE_VIEW_WATCHER = 2;
	private static final int TYPE_VIEW_WATCHER_ICON = 3;
	private static final int TYPE_VIEW_CLOSEABLE = 4;
	private static final int TYPE_VIEW_CLOSEABLE_ICON = 5;
	private static final int TYPE_HEADER = 6;
	private static final int TYPE_HEADER_BUTTON = 7;
	private static final int TYPE_DIVIDER = 8;
	
	@Override
	public int getViewTypeCount()
	{
		return 9;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		ListItem listItem = getItem(position);
		if (listItem != null)
		{
			switch (listItem.type)
			{
				case ListItem.ITEM_HEADER:
				{
					return listItem.resourceId != 0 || listItem.resource != null ? TYPE_HEADER_BUTTON : TYPE_HEADER;
				}
				case ListItem.ITEM_PAGE:
				{
					return mMergeChans && C.API_LOLLIPOP ? TYPE_VIEW_CLOSEABLE_ICON : TYPE_VIEW_CLOSEABLE;
				}
				case ListItem.ITEM_FAVORITE:
				{
					if (listItem.threadNumber != null)
					{
						boolean watcherSupported = mWatcherSupportSet.contains(listItem.chanName);
						if (mMergeChans && C.API_LOLLIPOP)
						{
							return watcherSupported ? TYPE_VIEW_WATCHER_ICON : TYPE_VIEW_ICON;
						}
						else
						{
							return watcherSupported ? TYPE_VIEW_WATCHER : TYPE_VIEW;
						}
					}
					else return mMergeChans && C.API_LOLLIPOP ? TYPE_VIEW_ICON : TYPE_VIEW;
				}
				case ListItem.ITEM_MENU:
				{
					return TYPE_VIEW_ICON;
				}
				case ListItem.ITEM_CHAN:
				{
					return C.API_LOLLIPOP ? TYPE_VIEW_ICON : TYPE_VIEW;
				}
				case ListItem.ITEM_DIVIDER:
				{
					return TYPE_DIVIDER;
				}
			}
		}
		return IGNORE_ITEM_VIEW_TYPE;
	}
	
	@Override
	public int getCount()
	{
		int count = 1;
		if (mChanSelectMode)
		{
			count += mChans.size();
		}
		else
		{
			for (int i = 0; i < mCategories.size(); i++) count += mCategories.get(i).size();
			count += mMenu.size();
		}
		return count;
	}
	
	@Override
	public ListItem getItem(int position)
	{
		position--;
		if (position >= 0)
		{
			if (mChanSelectMode)
			{
				if (position < mChans.size()) return mChans.get(position);
			}
			else
			{
				for (int i = 0; i < mCategories.size(); i++)
				{
					ArrayList<ListItem> listItems = mCategories.get(i);
					if (position < listItems.size()) return listItems.get(position);
					position -= listItems.size();
					if (position < 0) return null;
				}
				if (position < mMenu.size()) return mMenu.get(position);
			}
		}
		return null;
	}
	
	@Override
	public long getItemId(int position)
	{
		return 0;
	}
	
	@Override
	public boolean isEnabled(int position)
	{
		ListItem listItem = getItem(position);
		if (listItem != null)
		{
			int type = getItem(position).type;
			return type == ListItem.ITEM_PAGE || type == ListItem.ITEM_FAVORITE || type == ListItem.ITEM_MENU
					|| type == ListItem.ITEM_CHAN;
		}
		return false;
	}
	
	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}
	
	private View makeSimpleDivider()
	{
		float density = ResourceUtils.obtainDensity(mContext);
		if (C.API_LOLLIPOP)
		{
			FrameLayout frameLayout = new FrameLayout(mContext);
			View view = new View(mContext);
			view.setBackgroundResource(ResourceUtils.getResourceId(mContext, android.R.attr.listDivider, 0));
			frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
			frameLayout.setPadding(0, (int) (8f * density), 0, (int) (8f * density));
			return frameLayout;
		}
		else
		{
			View view = new View(mContext);
			TypedArray typedArray = mContext.obtainStyledAttributes(new int[]
					{android.R.attr.listSeparatorTextViewStyle});
			int style = typedArray.getResourceId(0, 0);
			typedArray.recycle();
			if (style != 0)
			{
				typedArray = mContext.obtainStyledAttributes(style, new int[] {android.R.attr.background});
				Drawable drawable = typedArray.getDrawable(0);
				typedArray.recycle();
				if (drawable != null)
				{
					view.setBackgroundColor(GraphicsUtils.getDrawableColor(mContext, drawable, Gravity.BOTTOM));
				}
			}
			view.setMinimumHeight((int) (2f * density));
			return view;
		}
	}
	
	private TextView makeCommonTextView(boolean header)
	{
		TextView textView = new TextView(mContext, null, C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem
				: android.R.attr.textAppearance);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, C.API_LOLLIPOP ? 14f : 16f);
		textView.setGravity(Gravity.CENTER_VERTICAL);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		textView.setSingleLine(true);
		if (C.API_LOLLIPOP)
		{
			textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			int color = textView.getTextColors().getDefaultColor();
			if (header) color &= 0x5effffff; else color &= 0xddffffff;
			textView.setTextColor(color);
		}
		return textView;
	}
	
	private View makeView(boolean icon, boolean watcher, boolean closeable, float density)
	{
		int size = (int) (48f * density);
		LinearLayout linearLayout = new LinearLayout(mContext);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setGravity(Gravity.CENTER_VERTICAL);
		ImageView iconView = null;
		if (icon)
		{
			iconView = new ImageView(mContext);
			iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			linearLayout.addView(iconView, (int) (24f * density), size);
		}
		TextView textView = makeCommonTextView(false);
		linearLayout.addView(textView, new LinearLayout.LayoutParams(0, size, 1));
		WatcherView watcherView = null;
		if (watcher)
		{
			watcherView = new WatcherView(mContext);
			linearLayout.addView(watcherView, size, size);
		}
		ImageView closeView = null;
		if (!watcher && closeable)
		{
			closeView = new ImageView(mContext);
			closeView.setScaleType(ImageView.ScaleType.CENTER);
			closeView.setImageResource(ResourceUtils.getResourceId(mContext, R.attr.buttonCancel, 0));
			closeView.setBackgroundResource(ResourceUtils.getResourceId(mContext,
					android.R.attr.borderlessButtonStyle, android.R.attr.background, 0));
			linearLayout.addView(closeView, size, size);
			closeView.setOnClickListener(mCloseButtonListener);
		}
		ViewHolder holder = new ViewHolder();
		holder.icon = iconView;
		holder.text = textView;
		holder.extra = watcherView != null ? watcherView : closeView;
		linearLayout.setTag(holder);
		int layoutLeftDp = 0;
		int layoutRightDp = 0;
		int textLeftDp;
		int textRightDp;
		if (C.API_LOLLIPOP)
		{
			textLeftDp = 16;
			textRightDp = 16;
			if (icon)
			{
				layoutLeftDp = 16;
				textLeftDp = 32;
			}
			if (watcher || closeable)
			{
				layoutRightDp = 4;
				textRightDp = 8;
			}
		}
		else
		{
			textLeftDp = 8;
			textRightDp = 8;
			if (icon)
			{
				layoutLeftDp = 8;
				textLeftDp = 6;
				textView.setAllCaps(true);
			}
			if (watcher || closeable)
			{
				layoutRightDp = 0;
				textRightDp = 0;
			}
		}
		linearLayout.setPadding((int) (layoutLeftDp * density), 0, (int) (layoutRightDp * density), 0);
		textView.setPadding((int) (textLeftDp * density), 0, (int) (textRightDp * density), 0);
		return linearLayout;
	}
	
	private View makeHeader(ViewGroup parent, boolean button, float density)
	{
		if (C.API_LOLLIPOP)
		{
			LinearLayout linearLayout = new LinearLayout(mContext);
			linearLayout.setOrientation(LinearLayout.VERTICAL);
			View divider = makeSimpleDivider();
			int paddingTop = divider.getPaddingBottom();
			divider.setPadding(divider.getPaddingLeft(), divider.getPaddingTop(),
					divider.getPaddingRight(), 0);
			linearLayout.addView(divider, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			LinearLayout linearLayout2 = new LinearLayout(mContext);
			linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
			linearLayout.addView(linearLayout2, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			TextView textView = makeCommonTextView(true);
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams
					(0, (int) (32f * density), 1);
			layoutParams.setMargins((int) (16f * density), paddingTop, (int) (16f * density),
					(int) (8f * density));
			linearLayout2.addView(textView, layoutParams);
			ViewHolder holder = new ViewHolder();
			holder.text = textView;
			if (button)
			{
				ImageView imageView = new ImageView(mContext);
				imageView.setScaleType(ImageView.ScaleType.CENTER);
				imageView.setBackgroundResource(ResourceUtils.getResourceId(mContext,
						android.R.attr.borderlessButtonStyle, android.R.attr.background, 0));
				imageView.setOnClickListener(mHeaderButtonListener);
				imageView.setImageAlpha(0x5e);
				int size = (int) (48f * density);
				layoutParams = new LinearLayout.LayoutParams(size, size);
				layoutParams.rightMargin = (int) (4f * density);
				linearLayout2.addView(imageView, layoutParams);
				holder.extra = imageView;
				holder.icon = imageView;
			}
			linearLayout.setTag(holder);
			return linearLayout;
		}
		else
		{
			View view = LayoutInflater.from(mContext).inflate(ResourceUtils.getResourceId(mContext,
					android.R.attr.preferenceCategoryStyle, android.R.attr.layout,
					android.R.layout.preference_category), parent, false);
			ViewHolder holder = new ViewHolder();
			holder.text = (TextView) view.findViewById(android.R.id.title);
			if (button)
			{
				int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
				view.measure(measureSpec, measureSpec);
				int size = view.getMeasuredHeight();
				if (size == 0) size = (int) (32f * density);
				FrameLayout frameLayout = new FrameLayout(mContext);
				frameLayout.addView(view);
				view = frameLayout;
				ImageView imageView = new ImageView(mContext);
				imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
				int padding = (int) (4f * density);
				imageView.setPadding(padding, padding, padding, padding);
				frameLayout.addView(imageView, new FrameLayout.LayoutParams
						((int) (48f * density), size, Gravity.END));
				View buttonView = new View(mContext);
				buttonView.setBackgroundResource(ResourceUtils.getResourceId(mContext,
						android.R.attr.selectableItemBackground, 0));
				buttonView.setOnClickListener(mHeaderButtonListener);
				frameLayout.addView(buttonView, FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT);
				holder.extra = buttonView;
				holder.icon = imageView;
			}
			view.setTag(holder);
			return view;
		}
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ListItem listItem = getItem(position);
		if (listItem == null)
		{
			switch (position)
			{
				case 0: return mHeaderView;
			}
			throw new NullPointerException();
		}
		if (convertView == null)
		{
			float density = ResourceUtils.obtainDensity(mContext);
			int viewType = getItemViewType(position);
			switch (viewType)
			{
				case TYPE_VIEW:
				case TYPE_VIEW_ICON:
				case TYPE_VIEW_WATCHER:
				case TYPE_VIEW_WATCHER_ICON:
				case TYPE_VIEW_CLOSEABLE:
				case TYPE_VIEW_CLOSEABLE_ICON:
				{
					boolean icon = viewType == TYPE_VIEW_ICON || viewType == TYPE_VIEW_WATCHER_ICON
							|| viewType == TYPE_VIEW_CLOSEABLE_ICON;
					boolean watcher = viewType == TYPE_VIEW_WATCHER || viewType == TYPE_VIEW_WATCHER_ICON;
					boolean closeable = viewType == TYPE_VIEW_CLOSEABLE || viewType == TYPE_VIEW_CLOSEABLE_ICON;
					convertView = makeView(icon, watcher, closeable, density);
					break;
				}
				case TYPE_HEADER:
				case TYPE_HEADER_BUTTON:
				{
					convertView = makeHeader(parent, viewType == TYPE_HEADER_BUTTON, density);
					break;
				}
				case TYPE_DIVIDER:
				{
					convertView = makeSimpleDivider();
					break;
				}
			}
		}
		ViewHolder holder = (ViewHolder) convertView.getTag();
		if (holder != null) holder.listItem = listItem;
		switch (listItem.type)
		{
			case ListItem.ITEM_PAGE:
			case ListItem.ITEM_FAVORITE:
			{
				String text;
				if (listItem.isThreadItem())
				{
					if (!StringUtils.isEmptyOrWhitespace(listItem.title)) text = listItem.title; else
					{
						text = StringUtils.formatThreadTitle(listItem.chanName, listItem.boardName,
								listItem.threadNumber);
					}
				}
				else text = StringUtils.formatBoardTitle(listItem.chanName, listItem.boardName, listItem.title);
				holder.text.setText(text);
				if (listItem.type == ListItem.ITEM_FAVORITE && listItem.isThreadItem() &&
						mWatcherSupportSet.contains(listItem.chanName))
				{
					WatcherService.WatcherItem watcherItem = mWatcherServiceClient.getItem(listItem.chanName,
							listItem.boardName, listItem.threadNumber);
					updateWatcherItem(holder, watcherItem != null ? watcherItem.getLastState()
							: WatcherService.State.DISABLED);
				}
				break;
			}
			case ListItem.ITEM_HEADER:
			case ListItem.ITEM_MENU:
			case ListItem.ITEM_CHAN:
			{
				holder.text.setText(listItem.title);
				break;
			}
		}
		if (holder != null && holder.icon != null)
		{
			if (listItem.resourceId != 0) holder.icon.setImageResource(listItem.resourceId);
			else if (listItem.resource != null) holder.icon.setImageDrawable(listItem.resource);
			else holder.icon.setImageDrawable(null);
		}
		return convertView;
	}
	
	private class ViewHolder
	{
		public ListItem listItem;
		public TextView text;
		public ImageView icon;
		public View extra;
	}
	
	private ListItem getItem(View view)
	{
		view = ListViewUtils.getRootViewInList(view);
		ViewHolder holder = (ViewHolder) view.getTag();
		return holder.listItem;
	}
	
	private void updateWatcherItem(ViewHolder holder, WatcherService.State state)
	{
		WatcherView watcherView = (WatcherView) holder.extra;
		watcherView.setPostsCountDifference(holder.listItem.watcherPostsCountDifference,
				holder.listItem.watcherHasNewPosts, holder.listItem.watcherIsError);
		watcherView.setWatcherState(state);
	}
	
	private WatcherDrawableColorSet mWatcherDrawableColorSet;
	
	private class WatcherDrawableColorSet
	{
		public final int enabledColor;
		public final int unavailableColor;
		public final int disabledColor;
		
		public WatcherDrawableColorSet(int enabledColor, int unavailableColor, int disabledColor)
		{
			this.enabledColor = enabledColor;
			this.unavailableColor = unavailableColor;
			this.disabledColor = disabledColor;
		}
	}
	
	private class WatcherView extends FrameLayout implements View.OnClickListener
	{
		private final ProgressBar mProgressBar;
		
		private String mText = "";
		private boolean mHasNew = false;
		private int mColor;
		
		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public WatcherView(Context context)
		{
			super(context);
			setBackgroundResource(ResourceUtils.getResourceId(context, android.R.attr.selectableItemBackground, 0));
			mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
			if (C.API_LOLLIPOP) mProgressBar.getIndeterminateDrawable().setTint(Color.WHITE);
			addView(mProgressBar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
			setOnClickListener(this);
			setWatcherState(WatcherService.State.DISABLED);
			setPostsCountDifference(0, false, false);
		}
		
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
		private final RectF mRectF = new RectF();
		private final Rect mRect = new Rect();
		
		@Override
		public void draw(Canvas canvas)
		{
			float density = ResourceUtils.obtainDensity(mContext);
			if (C.API_LOLLIPOP)
			{
				int paddingHorizontal = (int) (8f * density);
				int paddingVertical = (int) (12f * density);
				mRectF.set(paddingHorizontal, paddingVertical, getWidth() - paddingHorizontal,
						getHeight() - paddingVertical);
			}
			else
			{
				int padding = (int) (8f * density);
				mRectF.set(padding, padding, getWidth() - padding, getHeight() - padding);
			}
			int cornerRadius = C.API_LOLLIPOP ? (int) density : (int) (4f * density);
			mPaint.setColor(mColor);
			canvas.drawRoundRect(mRectF, cornerRadius, cornerRadius, mPaint);
			canvas.save();
			canvas.clipRect(mRectF);
			super.draw(canvas);
			if (mProgressBar.getVisibility() != View.VISIBLE)
			{
				float fontSize = C.API_LOLLIPOP ? 12f : 16f;
				mPaint.setColor(Color.WHITE);
				if (!mHasNew) mPaint.setAlpha(0x99);
				mPaint.setTextSize(fontSize * getResources().getDisplayMetrics().scaledDensity);
				mPaint.setTextAlign(Paint.Align.CENTER);
				mPaint.getTextBounds(mText, 0, mText.length(), mRect);
				canvas.drawText(mText, getWidth() / 2f, (getHeight() + mRect.height()) / 2f, mPaint);
			}
			else if (mHasNew)
			{
				mPaint.setColor(Color.WHITE);
				canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (int) (4f * density), mPaint);
			}
			canvas.restore();
		}
		
		public void setPostsCountDifference(int postsCountDifference, boolean hasNew, boolean error)
		{
			String text;
			if (postsCountDifference == WatcherService.POSTS_COUNT_DIFFERENCE_DELETED) text = "X"; else
			{
				if (Math.abs(postsCountDifference) >= 1000) text = Integer.toString(postsCountDifference / 1000) + "K+";
				else text = Integer.toString(postsCountDifference);
				if (error) text += "?";
			}
			mText = text;
			mHasNew = hasNew;
			invalidate();
		}
		
		public void setWatcherState(WatcherService.State state)
		{
			if (mWatcherDrawableColorSet == null)
			{
				int enabledColor = ResourceUtils.getColor(mUnstyledContext, R.attr.colorAccentSupport);
				int disabledColor = 0xff666666;
				int unavailableColor = GraphicsUtils.mixColors(disabledColor, enabledColor & 0x7fffffff);
				mWatcherDrawableColorSet = new WatcherDrawableColorSet(enabledColor, unavailableColor, disabledColor);
			}
			switch (state)
			{
				case DISABLED:
				case ENABLED:
				case UNAVAILABLE:
				{
					mProgressBar.setVisibility(View.GONE);
					break;
				}
				case BUSY:
				{
					mProgressBar.setVisibility(View.VISIBLE);
					break;
				}
			}
			switch (state)
			{
				case DISABLED:
				{
					mColor = mWatcherDrawableColorSet.disabledColor;
					break;
				}
				case ENABLED:
				case BUSY:
				{
					mColor = mWatcherDrawableColorSet.enabledColor;
					break;
				}
				case UNAVAILABLE:
				{
					mColor = mWatcherDrawableColorSet.unavailableColor;
					break;
				}
			}
			invalidate();
		}
		
		@Override
		public void onClick(View v)
		{
			ListItem listItem = getItem(this);
			FavoritesStorage.getInstance().toggleWatcher(listItem.chanName, listItem.boardName, listItem.threadNumber);
		}
	}
	
	@Override
	public void onWatcherUpdate(WatcherService.WatcherItem watcherItem, WatcherService.State state)
	{
		if ((mMergeChans || watcherItem.chanName.equals(mChanName))
				&& mWatcherSupportSet.contains(watcherItem.chanName))
		{
			ListItem targetItem = null;
			for (ListItem listItem : mFavorites)
			{
				if (listItem.compare(watcherItem.chanName, watcherItem.boardName, watcherItem.threadNumber))
				{
					targetItem = listItem;
					break;
				}
			}
			if (targetItem != null)
			{
				targetItem.watcherPostsCountDifference = watcherItem.getPostsCountDifference();
				targetItem.watcherHasNewPosts = watcherItem.hasNewPosts();
				targetItem.watcherIsError = watcherItem.isError();
				if (!mListView.isSorting())
				{
					for (int i = 0, count = mListView.getChildCount(); i < count; i++)
					{
						View view = mListView.getChildAt(i);
						ViewHolder holder = (ViewHolder) view.getTag();
						if (holder != null && targetItem == holder.listItem)
						{
							updateWatcherItem(holder, state);
							break;
						}
					}
				}
			}
		}
	}
	
	private class ListViewScrollFixListener implements ListView.OnScrollListener
	{
		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
		{
			
		}
		
		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState)
		{
			if (scrollState == SCROLL_STATE_TOUCH_SCROLL)
			{
				View focusView = mListView.getFocusedChild();
				if (focusView != null)
				{
					focusView.clearFocus();
					hideKeyboard();
				}
			}
		}
	}
}