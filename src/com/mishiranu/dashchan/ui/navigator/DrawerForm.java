/*
 * Copyright 2014-2017 Fukurou Mishiranu
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.page.PageHolder;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.EdgeEffectHandler;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import com.mishiranu.dashchan.widget.SortableListView;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

public class DrawerForm extends BaseAdapter implements EdgeEffectHandler.Shift, SortableListView.OnFinishedListener,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, DrawerLayout.DrawerListener,
		EditText.OnEditorActionListener, View.OnClickListener, View.OnTouchListener, WatcherService.Client.Callback {
	private final Context context;
	private final Context unstyledContext;
	private final Callback callback;

	private final InputMethodManager inputMethodManager;

	private final EditText searchEdit;
	private final View headerView;
	private final View restartView;
	private final TextView chanNameView;
	private final ImageView chanSelectorIcon;

	private final HashMap<String, Drawable> chanIcons = new HashMap<>();
	private final HashSet<String> watcherSupportSet = new HashSet<>();

	private final ArrayList<ListItem> chans = new ArrayList<>();
	private final ArrayList<ListItem> pages = new ArrayList<>();
	private final ArrayList<ListItem> favorites = new ArrayList<>();
	private final ArrayList<ArrayList<ListItem>> categories = new ArrayList<>();
	private final ArrayList<ListItem> menu = new ArrayList<>();

	private final WatcherService.Client watcherServiceClient;
	private SortableListView listView;

	private boolean mergeChans = false;
	private boolean showHistory = false;
	private boolean chanSelectMode = false;
	private String chanName;

	public static final int RESULT_REMOVE_ERROR_MESSAGE = 0x00000001;
	public static final int RESULT_SUCCESS = 0x00000002;

	public static final int MENU_ITEM_ALL_BOARDS = 1;
	public static final int MENU_ITEM_USER_BOARDS = 2;
	public static final int MENU_ITEM_HISTORY = 3;
	public static final int MENU_ITEM_PREFERENCES = 4;

	public interface Callback {
		public void onSelectChan(String chanName);
		public void onSelectBoard(String chanName, String boardName, boolean fromCache);
		public boolean onSelectThread(String chanName, String boardName, String threadNumber, String postNumber,
				String threadTitle, boolean fromCache);
		public boolean onClosePage(String chanName, String boardName, String threadNumber);
		public void onCloseAllPages();
		public int onEnterNumber(int number);
		public void onSelectDrawerMenuItem(int item);
		public ArrayList<PageHolder> getDrawerPageHolders();
		public void restartApplication();
	}

	public DrawerForm(Context context, Context unstyledContext, Callback callback,
			WatcherService.Client watcherServiceClient) {
		this.context = context;
		this.unstyledContext = unstyledContext;
		this.callback = callback;
		this.watcherServiceClient = watcherServiceClient;

		float density = ResourceUtils.obtainDensity(context);

		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setLayoutParams(new SortableListView.LayoutParams(SortableListView.LayoutParams.MATCH_PARENT,
				SortableListView.LayoutParams.WRAP_CONTENT));
		headerView = linearLayout;

		LinearLayout editTextContainer = new LinearLayout(context);
		editTextContainer.setGravity(Gravity.CENTER_VERTICAL);
		linearLayout.addView(editTextContainer);

		searchEdit = new SafePasteEditText(context);
		searchEdit.setOnKeyListener((v, keyCode, event) -> {
			if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
				v.clearFocus();
			}
			return false;
		});
		searchEdit.setHint(context.getString(R.string.text_code_number_address));
		searchEdit.setOnEditorActionListener(this);
		searchEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		searchEdit.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);

		ImageView searchIcon = new ImageView(context, null, android.R.attr.buttonBarButtonStyle);
		searchIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonForward, 0));
		searchIcon.setScaleType(ImageView.ScaleType.CENTER);
		searchIcon.setOnClickListener(this);
		editTextContainer.addView(searchEdit, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		editTextContainer.addView(searchIcon, (int) (40f * density), (int) (40f * density));
		if (C.API_LOLLIPOP) {
			editTextContainer.setPadding((int) (12f * density), (int) (8f * density), (int) (8f * density), 0);
		} else {
			editTextContainer.setPadding(0, (int) (2f * density), (int) (4f * density), (int) (2f * density));
		}

		LinearLayout selectorContainer = new LinearLayout(context);
		selectorContainer.setBackgroundResource(ResourceUtils.getResourceId(context,
				android.R.attr.selectableItemBackground, 0));
		selectorContainer.setOrientation(LinearLayout.HORIZONTAL);
		selectorContainer.setGravity(Gravity.CENTER_VERTICAL);
		selectorContainer.setOnClickListener(v -> {
			hideKeyboard();
			setChanSelectMode(!chanSelectMode);
		});
		linearLayout.addView(selectorContainer);
		selectorContainer.setMinimumHeight((int) (40f * density));
		if (C.API_LOLLIPOP) {
			selectorContainer.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
			((LinearLayout.LayoutParams) selectorContainer.getLayoutParams()).topMargin = (int) (4f * density);
		} else {
			selectorContainer.setPadding((int) (8f * density), 0, (int) (12f * density), 0);
		}

		chanNameView = new TextView(context, null, android.R.attr.textAppearanceListItem);
		chanNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, C.API_LOLLIPOP ? 14f : 16f);
		if (C.API_LOLLIPOP) {
			chanNameView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
		} else {
			chanNameView.setFilters(new InputFilter[] {new InputFilter.AllCaps()});
		}
		selectorContainer.addView(chanNameView, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1));

		chanSelectorIcon = new ImageView(context);
		chanSelectorIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonDropDown, 0));
		selectorContainer.addView(chanSelectorIcon, (int) (24f * density), (int) (24f * density));
		((LinearLayout.LayoutParams) chanSelectorIcon.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL
				| Gravity.END;

		LinearLayout restartLayout = new LinearLayout(context);
		restartLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.addView(restartLayout, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		restartLayout.addView(makeSimpleDivider(), LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		restartView = restartLayout;

		TextView restartTextView = new TextView(context, null, android.R.attr.textAppearanceSmall);
		restartTextView.setText(R.string.message_packages_installed);
		restartTextView.setTextColor(ResourceUtils.getColor(context, android.R.attr.textColorPrimary));
		if (C.API_LOLLIPOP) {
			restartTextView.setPadding((int) (16f * density), (int) (8f * density),
					(int) (16f * density), (int) (8f * density));
		} else {
			restartTextView.setPadding((int) (8f * density), (int) (8f * density),
					(int) (8f * density), (int) (8f * density));
		}
		restartLayout.addView(restartTextView, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);

		View restartButton = makeView(false, false, false, density);
		restartButton.setBackground(ResourceUtils.getDrawable(context,
				android.R.attr.listChoiceBackgroundIndicator, 0));
		ViewHolder restartButtonViewHolder = (ViewHolder) restartButton.getTag();
		restartButtonViewHolder.text.setText(R.string.action_restart);
		restartButton.setOnClickListener(v -> callback.restartApplication());
		restartLayout.addView(restartButton, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);

		inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		chans.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
		int color = ResourceUtils.getColor(context, R.attr.drawerIconColor);
		ChanManager manager = ChanManager.getInstance();
		Collection<String> availableChans = manager.getAvailableChanNames();
		for (String chanName : availableChans) {
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			if (configuration.getOption(ChanConfiguration.OPTION_READ_POSTS_COUNT)) {
				watcherSupportSet.add(chanName);
			}
			Drawable drawable = manager.getIcon(chanName, color);
			chanIcons.put(chanName, drawable);
			chans.add(new ListItem(ListItem.ITEM_CHAN, chanName, null, null, configuration.getTitle(), 0, drawable));
		}
		if (availableChans.size() == 1) {
			selectorContainer.setVisibility(View.GONE);
		}
	}

	public void bind(SortableListView listView) {
		this.listView = listView;
		listView.setClipToPadding(false);
		listView.setEdgeEffectShift(this);
		listView.setScrollBarStyle(SortableListView.SCROLLBARS_OUTSIDE_OVERLAY);
		listView.setAdapter(this);
		listView.setOnItemClickListener(this);
		listView.setOnItemLongClickListener(this);
		ScrollListenerComposite.obtain(listView).add(new ListViewScrollFixListener());
		listView.setOnSortingFinishedListener(this);
		listView.setOnTouchListener(this);
		float density = ResourceUtils.obtainDensity(context);
		if (C.API_LOLLIPOP) {
			listView.setDivider(null);
		} else {
			listView.setPadding((int) (12f * density), 0, (int) (12f * density), 0);
		}
	}

	private void updateConfiguration(String chanName, boolean force) {
		if (chanName != null && (!StringUtils.equals(chanName, this.chanName) || force)) {
			this.chanName = chanName;
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			chanNameView.setText(configuration.getTitle());
			menu.clear();
			Context context = this.context;
			menu.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
			TypedArray typedArray = context.obtainStyledAttributes(new int[] {R.attr.drawerMenuBoards,
					R.attr.drawerMenuUserBoards, R.attr.drawerMenuHistory, R.attr.drawerMenuPreferences});
			boolean addDivider = false;
			boolean hasUserBoards = configuration.getOption(ChanConfiguration.OPTION_READ_USER_BOARDS);
			if (!configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)) {
				menu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_ALL_BOARDS, typedArray.getResourceId(0, 0),
						context.getString(hasUserBoards ? R.string.action_general_boards : R.string.action_boards)));
				addDivider = true;
			}
			if (hasUserBoards) {
				menu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_USER_BOARDS, typedArray.getResourceId(1, 0),
						context.getString(R.string.action_user_boards)));
				addDivider = true;
			}
			if (addDivider) {
				menu.add(new ListItem(ListItem.ITEM_DIVIDER, 0, 0, null));
			}
			if (Preferences.isRememberHistory()) {
				menu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_HISTORY, typedArray.getResourceId(2, 0),
						context.getString(R.string.action_history)));
			}
			menu.add(new ListItem(ListItem.ITEM_MENU, MENU_ITEM_PREFERENCES, typedArray.getResourceId(3, 0),
					context.getString(R.string.action_preferences)));
			typedArray.recycle();
			invalidateItems(force, true);
		}
	}

	public void updateConfiguration(String chanName) {
		updateConfiguration(chanName, false);
	}

	public View getHeaderView() {
		return headerView;
	}

	public void setChanSelectMode(boolean enabled) {
		if (chans.size() > 2 && chanSelectMode != enabled) {
			chanSelectMode = enabled;
			chanSelectorIcon.setRotation(enabled ? 180f : 0f);
			notifyDataSetChanged();
			listView.setSelection(0);
			updateRestartViewVisibility();
		}
	}

	public void updateRestartViewVisibility() {
		restartView.setVisibility(!chanSelectMode && ChanManager.getInstance().hasNewExtensionsInstalled()
				? View.VISIBLE : View.GONE);
	}

	@Override
	public void onSortingFinished(SortableListView listView, int oldPosition, int newPosition) {
		if (chanSelectMode) {
			chans.add(newPosition - 1, chans.remove(oldPosition - 1));
			ArrayList<String> chanNames = new ArrayList<>();
			for (ListItem listItem : chans) {
				if (listItem.type == ListItem.ITEM_CHAN) {
					chanNames.add(listItem.chanName);
				}
			}
			ChanManager.getInstance().setChansOrder(chanNames);
			// Regroup favorite threads
			if (mergeChans) {
				invalidateItems(false, true);
			}
		} else {
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

	private boolean multipleFingersCountingTime = false;
	private long multipleFingersTime;
	private long multipleFingersStartTime;

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				multipleFingersCountingTime = false;
				multipleFingersStartTime = 0L;
				multipleFingersTime = 0L;
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN: {
				if (!multipleFingersCountingTime) {
					multipleFingersCountingTime = true;
					multipleFingersStartTime = System.currentTimeMillis();
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_UP: {
				if (event.getPointerCount() <= 2) {
					if (multipleFingersCountingTime) {
						multipleFingersCountingTime = false;
						multipleFingersTime += System.currentTimeMillis() - multipleFingersStartTime;
					}
				}
				break;
			}
		}
		return false;
	}

	public void performResume() {
		boolean mergeChans = Preferences.isMergeChans();
		boolean showHistory = Preferences.isRememberHistory();
		if (this.mergeChans != mergeChans || this.showHistory != showHistory) {
			this.mergeChans = mergeChans;
			this.showHistory = showHistory;
			updateConfiguration(chanName, true);
		}
		updateRestartViewVisibility();
	}

	public void invalidateItems(boolean pages, boolean favorites) {
		updateList(pages, favorites);
		notifyDataSetChanged();
	}

	@Override
	public void notifyDataSetChanged() {
		boolean focused = searchEdit.isFocused();
		if (focused) {
			focused = inputMethodManager != null && inputMethodManager.isActive(searchEdit);
		}
		super.notifyDataSetChanged();
		if (focused) {
			searchEdit.removeCallbacks(restoreSearchFocusRunnable);
			searchEdit.post(restoreSearchFocusRunnable);
		}
	}

	private final Runnable restoreSearchFocusRunnable = new Runnable() {
		@Override
		public void run() {
			searchEdit.requestFocus();
		}
	};

	@Override
	public int getEdgeEffectShift(boolean top) {
		int shift = listView.obtainEdgeEffectShift(top);
		return top ? shift + headerView.getPaddingTop() : shift;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		ListItem listItem = getItem(position);
		if (listItem != null) {
			switch (listItem.type) {
				case ListItem.ITEM_PAGE:
				case ListItem.ITEM_FAVORITE: {
					boolean fromCache = listItem.type == ListItem.ITEM_PAGE;
					if (!listItem.isThreadItem()) {
						callback.onSelectBoard(listItem.chanName, listItem.boardName, fromCache);
					} else {
						callback.onSelectThread(listItem.chanName, listItem.boardName, listItem.threadNumber, null,
								listItem.title, fromCache);
					}
					break;
				}
				case ListItem.ITEM_MENU: {
					callback.onSelectDrawerMenuItem(listItem.data);
					break;
				}
				case ListItem.ITEM_CHAN: {
					callback.onSelectChan(listItem.chanName);
					setChanSelectMode(false);
					if (C.API_LOLLIPOP) {
						listView.getSelector().jumpToCurrentState();
					}
					break;
				}
			}
		}
	}

	private static final int MENU_COPY_LINK = 0;
	private static final int MENU_SHARE_LINK = 1;
	private static final int MENU_ADD_TO_FAVORITES = 2;
	private static final int MENU_REMOVE_FROM_FAVORITES = 3;
	private static final int MENU_RENAME = 4;

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (chanSelectMode) {
			return listView.startSorting(2, chans.size(), position);
		}
		final ListItem listItem = getItem(position);
		if (listItem != null) {
			if (listItem.type == ListItem.ITEM_FAVORITE && listItem.threadNumber != null &&
					FavoritesStorage.getInstance().canSortManually()) {
				long time = multipleFingersTime + (multipleFingersCountingTime
						? System.currentTimeMillis() - multipleFingersStartTime : 0L);
				if (time >= ViewConfiguration.getLongPressTimeout() / 10) {
					int start = position;
					int end = position;
					for (int i = position - 1;; i--) {
						ListItem checkingListItem = getItem(i);
						if (checkingListItem == null) {
							break;
						}
						if (checkingListItem.type != ListItem.ITEM_FAVORITE ||
								!listItem.chanName.equals(checkingListItem.chanName)) {
							start = i + 1;
							break;
						}
					}
					for (int i = position + 1;; i++) {
						ListItem checkingListItem = getItem(i);
						if (checkingListItem == null) {
							break;
						}
						if (checkingListItem.type != ListItem.ITEM_FAVORITE ||
								!listItem.chanName.equals(checkingListItem.chanName)) {
							end = i - 1;
							break;
						}
					}
					listView.startSorting(start, end, position);
					return true;
				}
			}
			switch (listItem.type) {
				case ListItem.ITEM_PAGE:
				case ListItem.ITEM_FAVORITE: {
					DialogMenu dialogMenu = new DialogMenu(context, new DialogMenu.Callback() {
						@Override
						public void onItemClick(Context context, int id, Map<String, Object> extra) {
							switch (id) {
								case MENU_COPY_LINK:
								case MENU_SHARE_LINK: {
									ChanLocator locator = ChanLocator.get(listItem.chanName);
									Uri uri = listItem.isThreadItem() ? locator.safe(true).createThreadUri
											(listItem.boardName, listItem.threadNumber)
											: locator.safe(true).createBoardUri(listItem.boardName, 0);
									if (uri != null) {
										switch (id) {
											case MENU_COPY_LINK: {
												StringUtils.copyToClipboard(context, uri.toString());
												break;
											}
											case MENU_SHARE_LINK: {
												String subject = listItem.title;
												if (StringUtils.isEmptyOrWhitespace(subject)) {
													subject = uri.toString();
												}
												NavigationUtils.shareLink(context, subject, uri);
												break;
											}
										}
									}
									break;
								}
								case MENU_ADD_TO_FAVORITES: {
									if (listItem.isThreadItem()) {
										FavoritesStorage.getInstance().add(listItem.chanName, listItem.boardName,
												listItem.threadNumber, listItem.title, 0);
									} else {
										FavoritesStorage.getInstance().add(listItem.chanName, listItem.boardName);
									}
									break;
								}
								case MENU_REMOVE_FROM_FAVORITES: {
									FavoritesStorage.getInstance().remove(listItem.chanName, listItem.boardName,
											listItem.threadNumber);
									break;
								}
								case MENU_RENAME: {
									final EditText editText = new SafePasteEditText(context);
									editText.setSingleLine(true);
									editText.setText(listItem.title);
									editText.setSelection(editText.length());
									LinearLayout linearLayout = new LinearLayout(context);
									linearLayout.setOrientation(LinearLayout.HORIZONTAL);
									linearLayout.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT,
											LinearLayout.LayoutParams.WRAP_CONTENT);
									int padding = context.getResources().getDimensionPixelSize(R.dimen
											.dialog_padding_view);
									linearLayout.setPadding(padding, padding, padding, padding);
									AlertDialog dialog = new AlertDialog.Builder(context)
											.setView(linearLayout).setTitle(R.string.action_rename)
											.setNegativeButton(android.R.string.cancel, null)
											.setPositiveButton(android.R.string.ok, (d, which) -> {
										String newTitle = editText.getText().toString();
										FavoritesStorage.getInstance().modifyTitle(listItem.chanName,
												listItem.boardName, listItem.threadNumber, newTitle, true);
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
					if (listItem.isThreadItem()) {
						dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
					}
					if (listItem.type != ListItem.ITEM_FAVORITE && !FavoritesStorage.getInstance()
							.hasFavorite(listItem.chanName, listItem.boardName, listItem.threadNumber)) {
						dialogMenu.addItem(MENU_ADD_TO_FAVORITES, R.string.action_add_to_favorites);
					}
					if (listItem.type == ListItem.ITEM_FAVORITE) {
						dialogMenu.addItem(MENU_REMOVE_FROM_FAVORITES, R.string.action_remove_from_favorites);
						if (listItem.threadNumber != null) {
							dialogMenu.addItem(MENU_RENAME, R.string.action_rename);
						}
					}
					dialogMenu.show();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onDrawerSlide(View drawerView, float slideOffset) {}

	@Override
	public void onDrawerOpened(View drawerView) {}

	@Override
	public void onDrawerClosed(View drawerView) {
		hideKeyboard();
		setChanSelectMode(false);
	}

	@Override
	public void onDrawerStateChanged(int newState) {}

	private void clearTextAndHideKeyboard() {
		searchEdit.setText(null);
		hideKeyboard();
	}

	private void hideKeyboard() {
		searchEdit.clearFocus();
		if (inputMethodManager != null) {
			inputMethodManager.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
		}
	}

	private static final Pattern PATTERN_NAVIGATION_BOARD_THREAD = Pattern.compile("([\\w_-]+) (\\d+)");
	private static final Pattern PATTERN_NAVIGATION_BOARD = Pattern.compile("/?([\\w_-]+)");
	private static final Pattern PATTERN_NAVIGATION_THREAD = Pattern.compile("#(\\d+)");

	@Override
	public void onClick(View v) {
		String text = searchEdit.getText().toString().trim();
		int number = -1;
		try {
			number = Integer.parseInt(text);
		} catch (NumberFormatException e) {
			// Not a number, ignore exception
		}
		if (number >= 0) {
			int result = callback.onEnterNumber(number);
			if (FlagUtils.get(result, RESULT_SUCCESS)) {
				clearTextAndHideKeyboard();
				return;
			}
			if (FlagUtils.get(result, RESULT_REMOVE_ERROR_MESSAGE)) {
				return;
			}
		} else {
			{
				String boardName = null;
				String threadNumber = null;
				Matcher matcher = PATTERN_NAVIGATION_BOARD_THREAD.matcher(text);
				if (matcher.matches()) {
					boardName = matcher.group(1);
					threadNumber = matcher.group(2);
				} else {
					matcher = PATTERN_NAVIGATION_BOARD.matcher(text);
					if (matcher.matches()) {
						boardName = matcher.group(1);
					} else {
						matcher = PATTERN_NAVIGATION_THREAD.matcher(text);
						if (matcher.matches()) {
							threadNumber = matcher.group(1);
						}
					}
				}
				if (boardName != null || threadNumber != null) {
					boolean success;
					if (threadNumber == null) {
						callback.onSelectBoard(chanName, boardName, false);
						success = true;
					} else {
						success = callback.onSelectThread(chanName, boardName, threadNumber, null, null, false);
					}
					if (success) {
						clearTextAndHideKeyboard();
						return;
					}
				}
			}
			Uri uri = Uri.parse(text);
			String chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
			if (chanName != null) {
				boolean success = false;
				String boardName = null;
				String threadNumber = null;
				String postNumber = null;
				ChanLocator locator = ChanLocator.get(chanName);
				if (locator.safe(false).isThreadUri(uri)) {
					boardName = locator.safe(false).getBoardName(uri);
					threadNumber = locator.safe(false).getThreadNumber(uri);
					postNumber = locator.safe(false).getPostNumber(uri);
					success = true;
				} else if (locator.safe(false).isBoardUri(uri)) {
					boardName = locator.safe(false).getBoardName(uri);
					threadNumber = null;
					postNumber = null;
					success = true;
				}
				if (success) {
					if (threadNumber == null) {
						callback.onSelectBoard(chanName, boardName, false);
					} else {
						callback.onSelectThread(chanName, boardName, threadNumber, postNumber, null, false);
					}
					clearTextAndHideKeyboard();
					return;
				}
			}
		}
		ToastUtils.show(context, R.string.message_enter_valid_data);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		onClick(v);
		return true;
	}

	private void updateList(boolean pages, boolean favorites) {
		int mode = Preferences.getPagesListMode();
		if (pages) {
			this.pages.clear();
			if (mode != Preferences.PAGES_LIST_MODE_HIDE_PAGES) {
				updateListPages();
			}
		}
		if (favorites) {
			this.favorites.clear();
			updateListFavorites();
		}
		categories.clear();
		switch (mode) {
			case Preferences.PAGES_LIST_MODE_PAGES_FIRST: {
				categories.add(this.pages);
				categories.add(this.favorites);
				break;
			}
			case Preferences.PAGES_LIST_MODE_FAVORITES_FIRST: {
				categories.add(this.favorites);
				categories.add(this.pages);
				break;
			}
			case Preferences.PAGES_LIST_MODE_HIDE_PAGES: {
				categories.add(this.favorites);
				break;
			}
		}
		notifyDataSetChanged();
	}

	private void updateListPages() {
		boolean mergeChans = this.mergeChans;
		ArrayList<PageHolder> allPages = callback.getDrawerPageHolders();
		ArrayList<PageHolder> pages = new ArrayList<>();
		for (PageHolder pageHolder : allPages) {
			boolean isThreads = pageHolder.content == PageHolder.Content.THREADS;
			boolean isPosts = pageHolder.content == PageHolder.Content.POSTS;
			if (isThreads || isPosts) {
				if (mergeChans || pageHolder.chanName.equals(chanName)) {
					if (pageHolder.threadNumber != null || !ChanConfiguration.get(pageHolder.chanName)
							.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)) {
						pages.add(pageHolder);
					}
				}
			}
		}
		if (pages.size() > 0) {
			Collections.sort(pages, DATE_COMPARATOR);
			this.pages.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
					context.getString(R.string.text_open_pages), HEADER_ACTION_CLOSE_ALL,
					ResourceUtils.getResourceId(context, R.attr.buttonCancel, 0)));
			for (PageHolder pageHolder : pages) {
				Drawable drawable = chanIcons.get(pageHolder.chanName);
				if (pageHolder.threadNumber != null) {
					this.pages.add(new ListItem(ListItem.ITEM_PAGE, pageHolder.chanName, pageHolder.boardName,
							pageHolder.threadNumber, pageHolder.threadTitle, 0, drawable));
				} else {
					this.pages.add(new ListItem(ListItem.ITEM_PAGE, pageHolder.chanName, pageHolder.boardName,
							null, ChanConfiguration.get(pageHolder.chanName).getBoardTitle(pageHolder.boardName),
							0, drawable));
				}
			}
		}
	}

	private static final Comparator<PageHolder> DATE_COMPARATOR =
			(lhs, rhs) -> (int) (rhs.creationTime - lhs.creationTime);

	private void updateListFavorites() {
		boolean mergeChans = this.mergeChans;
		FavoritesStorage favoritesStorage = FavoritesStorage.getInstance();
		ArrayList<FavoritesStorage.FavoriteItem> favoriteBoards = favoritesStorage.getBoards(mergeChans
				? null : chanName);
		ArrayList<FavoritesStorage.FavoriteItem> favoriteThreads = favoritesStorage.getThreads(mergeChans
				? null : chanName);
		boolean addHeader = true;
		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		for (int i = 0; i < favoriteThreads.size(); i++) {
			FavoritesStorage.FavoriteItem favoriteItem = favoriteThreads.get(i);
			if (!chanNames.contains(favoriteItem.chanName)) {
				continue;
			}
			if (mergeChans || favoriteItem.chanName.equals(chanName)) {
				if (addHeader) {
					if (watcherSupportSet.contains(favoriteItem.chanName)
							|| mergeChans && !watcherSupportSet.isEmpty()) {
						favorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
								context.getString(R.string.text_favorite_threads), HEADER_ACTION_FAVORITES_MENU,
								ResourceUtils.getResourceId(context, R.attr.buttonMore, 0)));
					} else {
						favorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
								context.getString(R.string.text_favorite_threads)));
					}
					addHeader = false;
				}
				ListItem listItem = new ListItem(ListItem.ITEM_FAVORITE, favoriteItem.chanName, favoriteItem.boardName,
						favoriteItem.threadNumber, favoriteItem.title, 0, chanIcons.get(favoriteItem.chanName));
				if (watcherSupportSet.contains(favoriteItem.chanName)) {
					WatcherService.TemporalCountData temporalCountData = watcherServiceClient
							.countNewPosts(favoriteItem);
					listItem.watcherPostsCountDifference = temporalCountData.postsCountDifference;
					listItem.watcherHasNewPosts = temporalCountData.hasNewPosts;
					listItem.watcherIsError = temporalCountData.isError;
				}
				favorites.add(listItem);
			}
		}
		addHeader = true;
		for (int i = 0; i < favoriteBoards.size(); i++) {
			FavoritesStorage.FavoriteItem favoriteItem = favoriteBoards.get(i);
			if (!chanNames.contains(favoriteItem.chanName)) {
				continue;
			}
			if (mergeChans || favoriteItem.chanName.equals(chanName)) {
				if (addHeader) {
					favorites.add(new ListItem(ListItem.ITEM_HEADER, null, null, null,
							context.getString(R.string.text_favorite_boards)));
					addHeader = false;
				}
				favorites.add(new ListItem(ListItem.ITEM_FAVORITE, favoriteItem.chanName, favoriteItem.boardName, null,
						ChanConfiguration.get(favoriteItem.chanName).getBoardTitle(favoriteItem.boardName), 0,
						chanIcons.get(favoriteItem.chanName)));
			}
		}
	}

	private String formatBoardThreadTitle(boolean threadItem, String boardName, String threadNumber, String title) {
		if (threadItem) {
			if (!StringUtils.isEmptyOrWhitespace(title)) {
				return title;
			} else {
				return StringUtils.formatThreadTitle(chanName, boardName, threadNumber);
			}
		} else {
			return StringUtils.formatBoardTitle(chanName, boardName, title);
		}
	}

	private static class ListItem {
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

		public ListItem(int type, String chanName, String boardName, String threadNumber, String title) {
			this(type, chanName, boardName, threadNumber, title, 0, 0);
		}

		public ListItem(int type, String chanName, String boardName, String threadNumber, String title, int data,
				int resourceId) {
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
				Drawable resource) {
			this.type = type;
			this.data = data;
			this.resourceId = 0;
			this.resource = resource;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.title = title;
		}

		public ListItem(int type, int data, int resourceId, String title) {
			this.type = type;
			this.data = data;
			this.resourceId = resourceId;
			this.resource = null;
			this.chanName = null;
			this.boardName = null;
			this.threadNumber = null;
			this.title = title;
		}

		public boolean isThreadItem() {
			return threadNumber != null;
		}

		public boolean compare(String chanName, String boardName, String threadNumber) {
			return StringUtils.equals(this.chanName, chanName) && StringUtils.equals(this.boardName, boardName)
					&& StringUtils.equals(this.threadNumber, threadNumber);
		}
	}

	private final View.OnClickListener closeButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			ListItem listItem = getItem(v);
			if (listItem != null && listItem.type == ListItem.ITEM_PAGE) {
				boolean result = callback.onClosePage(listItem.chanName, listItem.boardName, listItem.threadNumber);
				if (result) {
					// size == 2 means only one item in list + header
					if (pages.size() == 2) {
						pages.clear();
					} else {
						pages.remove(listItem);
					}
					notifyDataSetChanged();
				}
			}
		}
	};

	private static final int HEADER_ACTION_CLOSE_ALL = 0;
	private static final int HEADER_ACTION_FAVORITES_MENU = 1;

	private static final int FAVORITES_MENU_REFRESH = 1;
	private static final int FAVORITES_MENU_CLEAR_DELETED = 2;

	private final View.OnClickListener headerButtonListener = new View.OnClickListener() {
		@SuppressLint("NewApi")
		@Override
		public void onClick(View v) {
			ListItem listItem = getItem(v);
			if (listItem != null && listItem.type == ListItem.ITEM_HEADER) {
				switch (listItem.data) {
					case HEADER_ACTION_CLOSE_ALL: {
						callback.onCloseAllPages();
						break;
					}
					case HEADER_ACTION_FAVORITES_MENU: {
						boolean hasEnabled = false;
						ArrayList<FavoritesStorage.FavoriteItem> deleteFavoriteItems = new ArrayList<>();
						FavoritesStorage favoritesStorage = FavoritesStorage.getInstance();
						for (ListItem itListItem : favorites) {
							if (itListItem.isThreadItem()) {
								FavoritesStorage.FavoriteItem favoriteItem = favoritesStorage.getFavorite
										(itListItem.chanName, itListItem.boardName, itListItem.threadNumber);
								if (favoriteItem != null) {
									hasEnabled |= favoriteItem.watcherEnabled;
									WatcherService.TemporalCountData temporalCountData =
											watcherServiceClient.countNewPosts(favoriteItem);
									if (temporalCountData.postsCountDifference ==
											WatcherService.POSTS_COUNT_DIFFERENCE_DELETED) {
										deleteFavoriteItems.add(favoriteItem);
									}
								}
							}
						}
						PopupMenu popupMenu = C.API_LOLLIPOP_MR1
								? new PopupMenu(v.getContext(), v, Gravity.END, 0, R.style.Widget_OverlapPopupMenu)
								: C.API_KITKAT ? new PopupMenu(v.getContext(), v, Gravity.END)
								: new PopupMenu(context, v);
						popupMenu.getMenu().add(0, FAVORITES_MENU_REFRESH, 0, R.string.action_refresh)
								.setEnabled(hasEnabled);
						popupMenu.getMenu().add(0, FAVORITES_MENU_CLEAR_DELETED, 0, R.string.action_clear_deleted)
								.setEnabled(!deleteFavoriteItems.isEmpty());
						popupMenu.setOnMenuItemClickListener(item -> {
							switch (item.getItemId()) {
								case FAVORITES_MENU_REFRESH: {
									watcherServiceClient.update();
									return true;
								}
								case FAVORITES_MENU_CLEAR_DELETED: {
									StringBuilder builder = new StringBuilder(context
											.getString(R.string.message_clear_deleted_threads_warning));
									builder.append("\n");
									for (FavoritesStorage.FavoriteItem favoriteItem : deleteFavoriteItems) {
										builder.append("\n\u2022 ").append(formatBoardThreadTitle(true,
												favoriteItem.boardName, favoriteItem.threadNumber, favoriteItem.title));
									}
									new AlertDialog.Builder(context).setMessage(builder)
											.setNegativeButton(android.R.string.cancel, null)
											.setPositiveButton(android.R.string.ok, (dialog, which) -> {
										for (FavoritesStorage.FavoriteItem favoriteItem : deleteFavoriteItems) {
											favoritesStorage.remove(favoriteItem.chanName,
													favoriteItem.boardName, favoriteItem.threadNumber);
										}
									}).show();
									return true;
								}
							}
							return false;
						});
						popupMenu.show();
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
	public int getViewTypeCount() {
		return 9;
	}

	@Override
	public int getItemViewType(int position) {
		ListItem listItem = getItem(position);
		if (listItem != null) {
			switch (listItem.type) {
				case ListItem.ITEM_HEADER: {
					return listItem.resourceId != 0 || listItem.resource != null ? TYPE_HEADER_BUTTON : TYPE_HEADER;
				}
				case ListItem.ITEM_PAGE: {
					return mergeChans && C.API_LOLLIPOP ? TYPE_VIEW_CLOSEABLE_ICON : TYPE_VIEW_CLOSEABLE;
				}
				case ListItem.ITEM_FAVORITE: {
					if (listItem.threadNumber != null) {
						boolean watcherSupported = watcherSupportSet.contains(listItem.chanName);
						if (mergeChans && C.API_LOLLIPOP) {
							return watcherSupported ? TYPE_VIEW_WATCHER_ICON : TYPE_VIEW_ICON;
						} else {
							return watcherSupported ? TYPE_VIEW_WATCHER : TYPE_VIEW;
						}
					} else {
						return mergeChans && C.API_LOLLIPOP ? TYPE_VIEW_ICON : TYPE_VIEW;
					}
				}
				case ListItem.ITEM_MENU: {
					return TYPE_VIEW_ICON;
				}
				case ListItem.ITEM_CHAN: {
					return C.API_LOLLIPOP ? TYPE_VIEW_ICON : TYPE_VIEW;
				}
				case ListItem.ITEM_DIVIDER: {
					return TYPE_DIVIDER;
				}
			}
		}
		return IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public int getCount() {
		int count = 1;
		if (chanSelectMode) {
			count += chans.size();
		} else {
			for (int i = 0; i < categories.size(); i++) {
				count += categories.get(i).size();
			}
			count += menu.size();
		}
		return count;
	}

	@Override
	public ListItem getItem(int position) {
		position--;
		if (position >= 0) {
			if (chanSelectMode) {
				if (position < chans.size()) {
					return chans.get(position);
				}
			} else {
				for (int i = 0; i < categories.size(); i++) {
					ArrayList<ListItem> listItems = categories.get(i);
					if (position < listItems.size()) {
						return listItems.get(position);
					}
					position -= listItems.size();
					if (position < 0) {
						return null;
					}
				}
				if (position < menu.size()) {
					return menu.get(position);
				}
			}
		}
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public boolean isEnabled(int position) {
		ListItem listItem = getItem(position);
		if (listItem != null) {
			int type = getItem(position).type;
			return type == ListItem.ITEM_PAGE || type == ListItem.ITEM_FAVORITE || type == ListItem.ITEM_MENU
					|| type == ListItem.ITEM_CHAN;
		}
		return false;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	private View makeSimpleDivider() {
		float density = ResourceUtils.obtainDensity(context);
		if (C.API_LOLLIPOP) {
			FrameLayout frameLayout = new FrameLayout(context);
			View view = new View(context);
			view.setBackgroundResource(ResourceUtils.getResourceId(context, android.R.attr.listDivider, 0));
			frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
			frameLayout.setPadding(0, (int) (8f * density), 0, (int) (8f * density));
			return frameLayout;
		} else {
			View view = new View(context);
			int[] attrs = {android.R.attr.listSeparatorTextViewStyle};
			TypedArray typedArray = context.obtainStyledAttributes(attrs);
			int style = typedArray.getResourceId(0, 0);
			typedArray.recycle();
			if (style != 0) {
				typedArray = context.obtainStyledAttributes(style, new int[] {android.R.attr.background});
				Drawable drawable = typedArray.getDrawable(0);
				typedArray.recycle();
				if (drawable != null) {
					view.setBackgroundColor(GraphicsUtils.getDrawableColor(context, drawable, Gravity.BOTTOM));
				}
			}
			view.setMinimumHeight((int) (2f * density));
			return view;
		}
	}

	private TextView makeCommonTextView(boolean header) {
		TextView textView = new TextView(context, null, C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem
				: android.R.attr.textAppearance);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, C.API_LOLLIPOP ? 14f : 16f);
		textView.setGravity(Gravity.CENTER_VERTICAL);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		textView.setSingleLine(true);
		if (C.API_LOLLIPOP) {
			textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			int color = textView.getTextColors().getDefaultColor();
			if (header) {
				color &= 0x5effffff;
			} else {
				color &= 0xddffffff;
			}
			textView.setTextColor(color);
		}
		return textView;
	}

	private View makeView(boolean icon, boolean watcher, boolean closeable, float density) {
		int size = (int) (48f * density);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.setGravity(Gravity.CENTER_VERTICAL);
		ImageView iconView = null;
		if (icon) {
			iconView = new ImageView(context);
			iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			linearLayout.addView(iconView, (int) (24f * density), size);
		}
		TextView textView = makeCommonTextView(false);
		linearLayout.addView(textView, new LinearLayout.LayoutParams(0, size, 1));
		WatcherView watcherView = null;
		if (watcher) {
			watcherView = new WatcherView(context);
			linearLayout.addView(watcherView, size, size);
		}
		ImageView closeView = null;
		if (!watcher && closeable) {
			closeView = new ImageView(context);
			closeView.setScaleType(ImageView.ScaleType.CENTER);
			closeView.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonCancel, 0));
			closeView.setBackgroundResource(ResourceUtils.getResourceId(context,
					android.R.attr.borderlessButtonStyle, android.R.attr.background, 0));
			linearLayout.addView(closeView, size, size);
			closeView.setOnClickListener(closeButtonListener);
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
		if (C.API_LOLLIPOP) {
			textLeftDp = 16;
			textRightDp = 16;
			if (icon) {
				layoutLeftDp = 16;
				textLeftDp = 32;
			}
			if (watcher || closeable) {
				layoutRightDp = 4;
				textRightDp = 8;
			}
		} else {
			textLeftDp = 8;
			textRightDp = 8;
			if (icon) {
				layoutLeftDp = 8;
				textLeftDp = 6;
				textView.setAllCaps(true);
			}
			if (watcher || closeable) {
				layoutRightDp = 0;
				textRightDp = 0;
			}
		}
		linearLayout.setPadding((int) (layoutLeftDp * density), 0, (int) (layoutRightDp * density), 0);
		textView.setPadding((int) (textLeftDp * density), 0, (int) (textRightDp * density), 0);
		return linearLayout;
	}

	private View makeHeader(ViewGroup parent, boolean button, float density) {
		if (C.API_LOLLIPOP) {
			LinearLayout linearLayout = new LinearLayout(context);
			linearLayout.setOrientation(LinearLayout.VERTICAL);
			View divider = makeSimpleDivider();
			int paddingTop = divider.getPaddingBottom();
			divider.setPadding(divider.getPaddingLeft(), divider.getPaddingTop(),
					divider.getPaddingRight(), 0);
			linearLayout.addView(divider, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			LinearLayout linearLayout2 = new LinearLayout(context);
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
			if (button) {
				ImageView imageView = new ImageView(context);
				imageView.setScaleType(ImageView.ScaleType.CENTER);
				imageView.setBackgroundResource(ResourceUtils.getResourceId(context,
						android.R.attr.borderlessButtonStyle, android.R.attr.background, 0));
				imageView.setOnClickListener(headerButtonListener);
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
		} else {
			View view = LayoutInflater.from(context).inflate(ResourceUtils.getResourceId(context,
					android.R.attr.preferenceCategoryStyle, android.R.attr.layout,
					android.R.layout.preference_category), parent, false);
			ViewHolder holder = new ViewHolder();
			holder.text = view.findViewById(android.R.id.title);
			if (button) {
				int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
				view.measure(measureSpec, measureSpec);
				int size = view.getMeasuredHeight();
				if (size == 0) {
					size = (int) (32f * density);
				}
				FrameLayout frameLayout = new FrameLayout(context);
				frameLayout.addView(view);
				view = frameLayout;
				ImageView imageView = new ImageView(context);
				imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
				int padding = (int) (4f * density);
				imageView.setPadding(padding, padding, padding, padding);
				frameLayout.addView(imageView, new FrameLayout.LayoutParams
						((int) (48f * density), size, Gravity.END));
				View buttonView = new View(context);
				buttonView.setBackgroundResource(ResourceUtils.getResourceId(context,
						android.R.attr.selectableItemBackground, 0));
				buttonView.setOnClickListener(headerButtonListener);
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
	public View getView(int position, View convertView, ViewGroup parent) {
		ListItem listItem = getItem(position);
		if (listItem == null) {
			switch (position) {
				case 0: {
					return headerView;
				}
			}
			throw new NullPointerException();
		}
		if (convertView == null) {
			float density = ResourceUtils.obtainDensity(context);
			int viewType = getItemViewType(position);
			switch (viewType) {
				case TYPE_VIEW:
				case TYPE_VIEW_ICON:
				case TYPE_VIEW_WATCHER:
				case TYPE_VIEW_WATCHER_ICON:
				case TYPE_VIEW_CLOSEABLE:
				case TYPE_VIEW_CLOSEABLE_ICON: {
					boolean icon = viewType == TYPE_VIEW_ICON || viewType == TYPE_VIEW_WATCHER_ICON
							|| viewType == TYPE_VIEW_CLOSEABLE_ICON;
					boolean watcher = viewType == TYPE_VIEW_WATCHER || viewType == TYPE_VIEW_WATCHER_ICON;
					boolean closeable = viewType == TYPE_VIEW_CLOSEABLE || viewType == TYPE_VIEW_CLOSEABLE_ICON;
					convertView = makeView(icon, watcher, closeable, density);
					break;
				}
				case TYPE_HEADER:
				case TYPE_HEADER_BUTTON: {
					convertView = makeHeader(parent, viewType == TYPE_HEADER_BUTTON, density);
					break;
				}
				case TYPE_DIVIDER: {
					convertView = makeSimpleDivider();
					break;
				}
			}
		}
		ViewHolder holder = (ViewHolder) convertView.getTag();
		if (holder != null) {
			holder.listItem = listItem;
		}
		switch (listItem.type) {
			case ListItem.ITEM_PAGE:
			case ListItem.ITEM_FAVORITE: {
				holder.text.setText(formatBoardThreadTitle(listItem.isThreadItem(),
						listItem.boardName, listItem.threadNumber, listItem.title));
				if (listItem.type == ListItem.ITEM_FAVORITE && listItem.isThreadItem() &&
						watcherSupportSet.contains(listItem.chanName)) {
					WatcherService.WatcherItem watcherItem = watcherServiceClient.getItem(listItem.chanName,
							listItem.boardName, listItem.threadNumber);
					updateWatcherItem(holder, watcherItem != null ? watcherItem.getLastState()
							: WatcherService.State.DISABLED);
				}
				break;
			}
			case ListItem.ITEM_HEADER:
			case ListItem.ITEM_MENU:
			case ListItem.ITEM_CHAN: {
				holder.text.setText(listItem.title);
				break;
			}
		}
		if (holder != null && holder.icon != null) {
			if (listItem.resourceId != 0) {
				holder.icon.setImageResource(listItem.resourceId);
			} else if (listItem.resource != null) {
				holder.icon.setImageDrawable(listItem.resource);
			} else {
				holder.icon.setImageDrawable(null);
			}
		}
		return convertView;
	}

	private class ViewHolder {
		public ListItem listItem;
		public TextView text;
		public ImageView icon;
		public View extra;
	}

	private ListItem getItem(View view) {
		view = ListViewUtils.getRootViewInList(view);
		ViewHolder holder = (ViewHolder) view.getTag();
		return holder.listItem;
	}

	private void updateWatcherItem(ViewHolder holder, WatcherService.State state) {
		WatcherView watcherView = (WatcherView) holder.extra;
		watcherView.setPostsCountDifference(holder.listItem.watcherPostsCountDifference,
				holder.listItem.watcherHasNewPosts, holder.listItem.watcherIsError);
		// Null state means state not changed
		if (state != null) {
			watcherView.setWatcherState(state);
		}
	}

	private WatcherDrawableColorSet watcherDrawableColorSet;

	private class WatcherDrawableColorSet {
		public final int enabledColor;
		public final int unavailableColor;
		public final int disabledColor;

		public WatcherDrawableColorSet(int enabledColor, int unavailableColor, int disabledColor) {
			this.enabledColor = enabledColor;
			this.unavailableColor = unavailableColor;
			this.disabledColor = disabledColor;
		}
	}

	private class WatcherView extends FrameLayout implements View.OnClickListener {
		private final ProgressBar progressBar;

		private String text = "";
		private boolean hasNew = false;
		private int color;

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public WatcherView(Context context) {
			super(context);
			setBackgroundResource(ResourceUtils.getResourceId(context, android.R.attr.selectableItemBackground, 0));
			progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
			if (C.API_LOLLIPOP) {
				progressBar.getIndeterminateDrawable().setTint(Color.WHITE);
			}
			addView(progressBar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
					FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
			setOnClickListener(this);
			setWatcherState(WatcherService.State.DISABLED);
			setPostsCountDifference(0, false, false);
		}

		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
		private final RectF rectF = new RectF();
		private final Rect rect = new Rect();

		@Override
		public void draw(Canvas canvas) {
			float density = ResourceUtils.obtainDensity(context);
			if (C.API_LOLLIPOP) {
				int paddingHorizontal = (int) (8f * density);
				int paddingVertical = (int) (12f * density);
				rectF.set(paddingHorizontal, paddingVertical, getWidth() - paddingHorizontal,
						getHeight() - paddingVertical);
			} else {
				int padding = (int) (8f * density);
				rectF.set(padding, padding, getWidth() - padding, getHeight() - padding);
			}
			int cornerRadius = C.API_LOLLIPOP ? (int) density : (int) (4f * density);
			paint.setColor(color);
			canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
			canvas.save();
			canvas.clipRect(rectF);
			super.draw(canvas);
			if (progressBar.getVisibility() != View.VISIBLE) {
				float fontSize = C.API_LOLLIPOP ? 12f : 16f;
				paint.setColor(Color.WHITE);
				if (!hasNew) {
					paint.setAlpha(0x99);
				}
				paint.setTextSize(fontSize * getResources().getDisplayMetrics().scaledDensity);
				paint.setTextAlign(Paint.Align.CENTER);
				paint.getTextBounds(text, 0, text.length(), rect);
				canvas.drawText(text, getWidth() / 2f, (getHeight() + rect.height()) / 2f, paint);
			} else if (hasNew) {
				paint.setColor(Color.WHITE);
				canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (int) (4f * density), paint);
			}
			canvas.restore();
		}

		public void setPostsCountDifference(int postsCountDifference, boolean hasNew, boolean error) {
			String text;
			if (postsCountDifference == WatcherService.POSTS_COUNT_DIFFERENCE_DELETED) {
				text = "X";
			} else {
				if (Math.abs(postsCountDifference) >= 1000) {
					text = Integer.toString(postsCountDifference / 1000) + "K+";
				} else {
					text = Integer.toString(postsCountDifference);
				}
				if (error) {
					text += "?";
				}
			}
			this.text = text;
			this.hasNew = hasNew;
			invalidate();
		}

		public void setWatcherState(WatcherService.State state) {
			if (watcherDrawableColorSet == null) {
				int enabledColor = ResourceUtils.getColor(unstyledContext, R.attr.colorAccentSupport);
				int disabledColor = 0xff666666;
				int unavailableColor = GraphicsUtils.mixColors(disabledColor, enabledColor & 0x7fffffff);
				watcherDrawableColorSet = new WatcherDrawableColorSet(enabledColor, unavailableColor, disabledColor);
			}
			switch (state) {
				case DISABLED:
				case ENABLED:
				case UNAVAILABLE: {
					progressBar.setVisibility(View.GONE);
					break;
				}
				case BUSY: {
					progressBar.setVisibility(View.VISIBLE);
					break;
				}
			}
			switch (state) {
				case DISABLED: {
					color = watcherDrawableColorSet.disabledColor;
					break;
				}
				case ENABLED:
				case BUSY: {
					color = watcherDrawableColorSet.enabledColor;
					break;
				}
				case UNAVAILABLE: {
					color = watcherDrawableColorSet.unavailableColor;
					break;
				}
			}
			invalidate();
		}

		@Override
		public void onClick(View v) {
			ListItem listItem = getItem(this);
			FavoritesStorage.getInstance().toggleWatcher(listItem.chanName, listItem.boardName, listItem.threadNumber);
		}
	}

	@Override
	public void onWatcherUpdate(WatcherService.WatcherItem watcherItem, WatcherService.State state) {
		if ((mergeChans || watcherItem.chanName.equals(chanName))
				&& watcherSupportSet.contains(watcherItem.chanName)) {
			ListItem targetItem = null;
			for (ListItem listItem : favorites) {
				if (listItem.compare(watcherItem.chanName, watcherItem.boardName, watcherItem.threadNumber)) {
					targetItem = listItem;
					break;
				}
			}
			if (targetItem != null) {
				targetItem.watcherPostsCountDifference = watcherItem.getPostsCountDifference();
				targetItem.watcherHasNewPosts = watcherItem.hasNewPosts();
				targetItem.watcherIsError = watcherItem.isError();
				if (!listView.isSorting()) {
					for (int i = 0, count = listView.getChildCount(); i < count; i++) {
						View view = listView.getChildAt(i);
						ViewHolder holder = (ViewHolder) view.getTag();
						if (holder != null && targetItem == holder.listItem) {
							updateWatcherItem(holder, state);
							break;
						}
					}
				}
			}
		}
	}

	private class ListViewScrollFixListener implements ListView.OnScrollListener {
		@Override
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
				View focusView = listView.getFocusedChild();
				if (focusView != null) {
					focusView.clearFocus();
					hideKeyboard();
				}
			}
		}
	}
}
