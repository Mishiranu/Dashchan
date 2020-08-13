package com.mishiranu.dashchan.ui.navigator.adapter;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.HidePerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ThreadsAdapter extends BaseAdapter implements BusyScrollListener.Callback, View.OnClickListener,
		CompoundButton.OnCheckedChangeListener {
	private static final int ITEM_VIEW_TYPE_THREAD = 0;
	private static final int ITEM_VIEW_TYPE_THREAD_HIDDEN = 1;
	private static final int ITEM_VIEW_TYPE_THREAD_GRID = 2;
	private static final int ITEM_VIEW_TYPE_PAGE_DIVIDER = 3;

	private final ArrayList<Object> items = new ArrayList<>();
	private ArrayList<PostItem> catalogPostItems;
	private ArrayList<PostItem> filteredPostItems;
	private ArrayList<Object> gridItems;

	private final Context context;
	private final String chanName;
	private final String boardName;
	private final UiManager uiManager;
	private final UiManager.ConfigurationSet configurationSet;

	private final View headerView;
	private final View headerAdditional;
	private final ClickableView headerClickableView;
	private final ImageView headerExpandIcon;
	private final TextView[] headerData = new TextView[6];
	private final RadioButton[] sortingData = new RadioButton[3];

	private boolean mayShowHeader = false;
	private boolean headerExpanded = false;
	private boolean busy = false;

	private String filterText;
	private boolean gridMode = false;
	private int gridRowCount = 1;
	private int gridItemContentHeight;

	public ThreadsAdapter(Context context, String chanName, String boardName, UiManager uiManager) {
		this.context = context;
		this.chanName = chanName;
		this.boardName = boardName;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(null, null, new HidePerformer(),
				new GalleryItem.GallerySet(false), uiManager.dialog().createStackInstance(), null, null,
				false, true, false, false, false, null);
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		frameLayout.setPadding((int) (10f * density), (int) (6f * density), (int) (10f * density), 0);
		headerClickableView = new ClickableView(context);
		headerClickableView.setOnClickListener(this);
		frameLayout.addView(headerClickableView, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		frameLayout.addView(linearLayout, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		linearLayout.setPadding((int) (10f * density), (int) (4f * density),
				(int) (10f * density), (int) (4f * density));
		LinearLayout innerLayout = new LinearLayout(context);
		innerLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.addView(innerLayout, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		LinearLayout topLayout = new LinearLayout(context);
		topLayout.setOrientation(LinearLayout.VERTICAL);
		innerLayout.addView(topLayout, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		headerData[0] = new TextView(context, null, android.R.attr.textAppearanceLarge);
		headerData[0].setTextSize(18f);
		if (C.API_LOLLIPOP) {
			headerData[0].setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
		}
		topLayout.addView(headerData[0], LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		headerData[1] = new TextView(context, null, android.R.attr.textAppearanceSmall);
		topLayout.addView(headerData[1], LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		headerData[1].setPadding(0, (int) (2f * density), 0, (int) (2f * density));
		headerExpandIcon = new ImageView(context);
		headerExpandIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonDropDown, 0));
		innerLayout.addView(headerExpandIcon, (int) (24f * density), (int) (24f * density));
		LinearLayout.LayoutParams iconLayoutParams = (LinearLayout.LayoutParams) headerExpandIcon.getLayoutParams();
		iconLayoutParams.gravity = Gravity.BOTTOM;
		iconLayoutParams.setMargins((int) (4f * density), 0, (int) (-6f * density), 0);
		LinearLayout headerAdditional = new LinearLayout(context);
		headerAdditional.setOrientation(LinearLayout.VERTICAL);
		this.headerAdditional = headerAdditional;
		linearLayout.addView(headerAdditional, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		for (int i = 2; i < 6; i += 2) {
			headerData[i] = new TextView(context, null, android.R.attr.textAppearanceLarge);
			headerData[i].setTextSize(16f);
			if (C.API_LOLLIPOP) {
				headerData[i].setTypeface(headerData[0].getTypeface());
			}
			headerData[i].setPadding(0, (int) (8f * density), 0, (int) (4f * density));
			headerAdditional.addView(headerData[i], LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			headerData[i + 1] = new TextView(context, null, android.R.attr.textAppearanceSmall);
			headerData[i + 1].setLineSpacing((int) (2f * density), 1f);
			headerAdditional.addView(headerData[i + 1], LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			headerData[i + 1].setPadding(0, 0, 0, (int) (2f * density));
		}
		headerData[4].setText(context.getString(R.string.text_configuration));
		LinearLayout radioButtonsContainer = new LinearLayout(context);
		radioButtonsContainer.setOrientation(LinearLayout.VERTICAL);
		radioButtonsContainer.setClickable(true);
		headerAdditional.addView(radioButtonsContainer, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		for (int i = 0; i < sortingData.length; i++) {
			sortingData[i] = new RadioButton(context);
			sortingData[i].setOnCheckedChangeListener(this);
			radioButtonsContainer.addView(sortingData[i], LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) sortingData[i]
					.getLayoutParams();
			if (i == 0) {
				layoutParams.topMargin = (int) (4f * density);
			}
			if (C.API_LOLLIPOP) {
				layoutParams.leftMargin = (int) (-4f * density);
			}
		}
		sortingData[0].setText(context.getString(R.string.text_sort_by_unsorted));
		sortingData[1].setText(context.getString(R.string.text_sort_by_date));
		sortingData[2].setText(context.getString(R.string.text_sort_by_replies));
		sortingData[0].setChecked(true);
		headerView = frameLayout;
	}

	public void applyAttributesBeforeFill(boolean headerExpanded, int catalogSortIndex, boolean gridMode) {
		this.headerExpanded = headerExpanded;
		if (catalogSortIndex >= 0) {
			sortingData[catalogSortIndex].setChecked(true);
		}
		catalogPostItems = null;
		setGridMode(gridMode);
	}

	private void prepareGridItems() {
		if (gridItems != null && gridItems.size() > 0) {
			return;
		}
		if (gridItems == null) {
			gridItems = new ArrayList<>();
		}
		int rowCount = gridRowCount;
		int currentIndex = 0;
		PostItem[] postItems = null;
		ArrayList<?> items = filteredPostItems != null ? filteredPostItems : catalogPostItems != null
				? catalogPostItems : this.items;
		for (Object item : items) {
			if (item instanceof PostItem) {
				if (postItems == null) {
					postItems = new PostItem[rowCount];
					currentIndex = 0;
				}
				postItems[currentIndex++] = (PostItem) item;
				if (currentIndex == postItems.length) {
					gridItems.add(postItems);
					postItems = null;
				}
			} else {
				if (postItems != null) {
					gridItems.add(postItems);
					postItems = null;
				}
				gridItems.add(item);
			}
		}
		if (postItems != null) {
			gridItems.add(postItems);
		}
	}

	private boolean isShowHeader() {
		return mayShowHeader && filteredPostItems == null;
	}

	public boolean isRealEmpty() {
		return items.size() == 0;
	}

	@Override
	public void notifyDataSetChanged() {
		if (gridItems != null) {
			gridItems.clear();
		}
		super.notifyDataSetChanged();
	}

	@Override
	public int getViewTypeCount() {
		return 4;
	}

	@Override
	public int getItemViewType(int position) {
		Object item = getItemInternal(position);
		return item instanceof PostItem ? ((PostItem) item).isHidden(configurationSet.hidePerformer)
				? ITEM_VIEW_TYPE_THREAD_HIDDEN : ITEM_VIEW_TYPE_THREAD : item instanceof PostItem[]
				? ITEM_VIEW_TYPE_THREAD_GRID : item instanceof DividerItem
				? ITEM_VIEW_TYPE_PAGE_DIVIDER : IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public int getCount() {
		int count = 0;
		if (gridMode) {
			prepareGridItems();
			count += gridItems.size();
		} else {
			count += (filteredPostItems != null ? filteredPostItems : items).size();
		}
		count += isShowHeader() ? 1 : 0;
		return count;
	}

	@Override
	public Object getItem(int position) {
		Object item = getItemInternal(position);
		return item instanceof PostItem ? (PostItem) item : item instanceof PostItem[] ? (PostItem[]) item : null;
	}

	public Object getItemInternal(int position) {
		if (isShowHeader()) {
			if (position == 0) {
				return null;
			}
			position--;
		}
		if (gridMode) {
			prepareGridItems();
			return gridItems.get(position);
		} else {
			return (filteredPostItems != null ? filteredPostItems : catalogPostItems != null
					? catalogPostItems : items).get(position);
		}
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public boolean isEnabled(int position) {
		return getItem(position) != null;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Object item = getItemInternal(position);
		if (item == null) {
			return headerView;
		}
		if (item instanceof PostItem) {
			PostItem postItem = (PostItem) item;
			if (!postItem.isHidden(configurationSet.hidePerformer)) {
				convertView = uiManager.view().getThreadView(postItem, convertView, parent, busy, configurationSet);
			} else {
				convertView = uiManager.view().getThreadHiddenView(postItem, convertView, parent, configurationSet);
			}
			ViewUtils.applyCardHolderPadding(convertView, position == 0, position == getCount() - 1, false);
		} else if (item instanceof PostItem[]) {
			PostItem[] postItems = (PostItem[]) item;
			LinearLayout linearLayout = (LinearLayout) convertView;
			if (linearLayout == null) {
				linearLayout = new LinearLayout(context);
				linearLayout.setOrientation(LinearLayout.HORIZONTAL);
				linearLayout.setMotionEventSplittingEnabled(false);
				ViewUtils.applyMultipleCardHolderPadding(linearLayout);
				// Free space view
				linearLayout.addView(new View(context), 0, LinearLayout.LayoutParams.MATCH_PARENT);
				convertView = linearLayout;
			}
			while (linearLayout.getChildCount() - 1 > gridRowCount) {
				linearLayout.removeViewAt(gridRowCount - 1);
			}
			int count = getCount();
			int freeSpaceIndex = linearLayout.getChildCount() - 1;
			View freeSpaceView = linearLayout.getChildAt(freeSpaceIndex);
			freeSpaceView.setVisibility(View.GONE);
			for (int i = 0; i < postItems.length; i++) {
				PostItem postItem = postItems[i];
				if (postItem != null) {
					View convertViewChild = null;
					if (i < freeSpaceIndex) {
						View view = linearLayout.getChildAt(i);
						if (view.getClass() == View.class) {
							linearLayout.removeViewAt(i);
						} else {
							convertViewChild = view;
						}
					}
					boolean add = convertViewChild == null;
					convertViewChild = uiManager.view().getThreadViewForGrid(postItem, convertViewChild, parent,
							gridItemContentHeight, busy, configurationSet);
					if (add) {
						linearLayout.addView(convertViewChild, i, new LinearLayout.LayoutParams(0,
								LinearLayout.LayoutParams.WRAP_CONTENT, 1));
					} else {
						convertViewChild.setVisibility(View.VISIBLE);
					}
					ViewUtils.applyCardHolderPadding(convertViewChild, position == 0, position == count - 1, true);
				} else {
					for (int j = i; j < freeSpaceIndex; j++) {
						linearLayout.getChildAt(j).setVisibility(View.GONE);
					}
					freeSpaceView.setVisibility(View.VISIBLE);
					((LinearLayout.LayoutParams) freeSpaceView.getLayoutParams()).weight = postItems.length - i;
					break;
				}
			}
		} else {
			DividerItem dividerItem = (DividerItem) item;
			float density = ResourceUtils.obtainDensity(context);
			if (convertView == null) {
				TextView textView = new TextView(context, null, android.R.attr.textAppearanceLarge);
				textView.setTextSize(18f);
				if (C.API_LOLLIPOP) {
					textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
				}
				convertView = textView;
			}
			((TextView) convertView).setText(dividerItem.title);
			convertView.setPadding((int) (20f * density), position == 0 ? (int) (10f * density)
					: (int) (8f * density), (int) (20f * density), (int) (4f * density));
		}
		return convertView;
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	private Animator lastAnimator;

	@Override
	public void onClick(View v) {
		boolean visible = false;
		for (int i = 1; i < headerData.length; i++) {
			if (headerData[i].getVisibility() != View.GONE) {
				visible = true;
				break;
			}
		}
		if (!visible && !headerExpanded) {
			return;
		}
		headerExpanded = !headerExpanded;
		headerExpandIcon.setRotation(headerExpanded ? 180f : 0f);
		if (lastAnimator != null) {
			lastAnimator.cancel();
		}
		if (headerExpanded) {
			headerAdditional.setVisibility(View.VISIBLE);
			headerAdditional.setAlpha(0f);
			Animator heightAnimator = AnimationUtils.ofHeight(headerAdditional, 0,
					ViewGroup.LayoutParams.WRAP_CONTENT, true);
			heightAnimator.setDuration(200);
			ValueAnimator alphaAnimator = ObjectAnimator.ofFloat(headerAdditional, View.ALPHA, 0f, 1f);
			alphaAnimator.setStartDelay(100);
			alphaAnimator.setDuration(200);
			AnimatorSet animatorSet = new AnimatorSet();
			animatorSet.playTogether(heightAnimator, alphaAnimator);
			animatorSet.start();
			lastAnimator = animatorSet;
		} else {
			ValueAnimator alphaAnimator = ObjectAnimator.ofFloat(headerAdditional, View.ALPHA, 1f, 0f);
			alphaAnimator.setDuration(200);
			Animator heightAnimator = AnimationUtils.ofHeight(headerAdditional,
					ViewGroup.LayoutParams.WRAP_CONTENT, 0, false);
			heightAnimator.setStartDelay(100);
			heightAnimator.setDuration(200);
			AnimatorSet animatorSet = new AnimatorSet();
			animatorSet.playTogether(heightAnimator, alphaAnimator);
			animatorSet.addListener(new AnimationUtils.VisibilityListener(headerAdditional, View.GONE));
			animatorSet.start();
			lastAnimator = animatorSet;
		}
	}

	public boolean isHeaderExpanded() {
		return headerExpanded;
	}

	public int getCatalogSortIndex() {
		if (mayShowHeader && sortingData[0].getVisibility() == View.VISIBLE) {
			for (int i = 0; i < sortingData.length; i++) {
				if (sortingData[i].isChecked()) {
					return i;
				}
			}
		}
		return -1;
	}

	public void setItems(Collection<ArrayList<PostItem>> postItems, int pageNumber, int boardSpeed) {
		catalogPostItems = null;
		items.clear();
		mayShowHeader = false;
		for (ArrayList<PostItem> pagePostItems : postItems) {
			appendItemsInternal(pagePostItems, pageNumber, boardSpeed);
			pageNumber++;
		}
		int index = getCatalogSortIndex();
		if (index != -1) {
			applySorting(index);
		}
		applyFilterIfNecessary();
		notifyDataSetChanged();
	}

	public void appendItems(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed) {
		catalogPostItems = null;
		appendItemsInternal(postItems, pageNumber, boardSpeed);
		applyFilterIfNecessary();
		notifyDataSetChanged();
	}

	public void notifyNotModified() {
		for (Object item : items) {
			if (item instanceof PostItem) {
				PostItem postItem = (PostItem) item;
				postItem.invalidateHidden();
			}
		}
		notifyDataSetChanged();
	}

	private void appendItemsInternal(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed) {
		if (pageNumber > 0) {
			items.add(new DividerItem(context.getString(R.string.text_page_format, pageNumber), pageNumber));
		} else {
			updateHeaderView(pageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG, boardSpeed);
		}
		if (postItems != null) {
			for (PostItem postItem : postItems) {
				if (Preferences.isDisplayHiddenThreads() || !postItem.isHidden(configurationSet.hidePerformer)) {
					items.add(postItem);
				}
			}
		}
	}

	private static void appendNewLine(StringBuilder builder, String line) {
		if (builder.length() > 0) {
			builder.append('\n');
		}
		builder.append(line);
	}

	private void updateHeaderView(boolean catalog, int boardSpeed) {
		mayShowHeader = true;
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		String title = configuration.getBoardTitle(boardName);
		if (StringUtils.isEmpty(title)) {
			title = StringUtils.formatBoardTitle(chanName, boardName, null);
		}
		boolean mayExpand = false;
		if (boardSpeed > 0) {
			headerData[1].setVisibility(View.VISIBLE);
			headerData[1].setText(context.getString(R.string.text_board_speed_format, context.getResources()
					.getQuantityString(R.plurals.text_posts_per_hour_format, boardSpeed, boardSpeed)));
		} else {
			headerData[1].setVisibility(View.GONE);
		}
		if (catalog) {
			boolean fromCatalogOrNewAdapter = sortingData[0].getVisibility() == View.VISIBLE;
			for (RadioButton button : sortingData) {
				button.setVisibility(View.VISIBLE);
			}
			headerData[0].setText(context.getString(R.string.action_catalog) + ": " + title);
			headerData[2].setVisibility(View.VISIBLE);
			headerData[3].setVisibility(View.GONE);
			headerData[4].setVisibility(View.GONE);
			headerData[5].setVisibility(View.GONE);
			headerData[2].setText(context.getString(R.string.text_sorting));
			if (!fromCatalogOrNewAdapter) {
				sortingData[0].setChecked(true);
			}
			mayExpand = true;
		} else {
			for (RadioButton button : sortingData) {
				button.setVisibility(View.GONE);
			}
			headerData[0].setText(title);
			String info = StringUtils.nullIfEmpty(configuration.getBoardDescription(boardName));
			StringBuilder builder = new StringBuilder();
			int pagesCount = Math.max(configuration.getPagesCount(boardName), 1);
			if (pagesCount != ChanConfiguration.PAGES_COUNT_INVALID) {
				appendNewLine(builder, context.getString(R.string.text_pages_count_format, pagesCount));
			}
			ChanConfiguration.Board board = configuration.safe().obtainBoard(boardName);
			ChanConfiguration.Posting posting = board.allowPosting
					? configuration.safe().obtainPosting(boardName, true) : null;
			if (posting != null) {
				int bumpLimit = configuration.getBumpLimit(boardName);
				if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
					appendNewLine(builder, context.getString(R.string.text_bump_limit_format, bumpLimit));
				}
				if (!posting.allowSubject) {
					appendNewLine(builder, context.getString(R.string.text_disabled_subjects));
				}
				if (!posting.allowName) {
					appendNewLine(builder, context.getString(R.string.text_disabled_names));
				} else if (!posting.allowTripcode) {
					appendNewLine(builder, context.getString(R.string.text_disabled_tripcodes));
				}
				if (posting.attachmentCount <= 0) {
					appendNewLine(builder, context.getString(R.string.text_disabled_images));
				}
				if (!posting.optionSage) {
					appendNewLine(builder, context.getString(R.string.text_disabled_sage));
				}
				if (posting.hasCountryFlags) {
					appendNewLine(builder, context.getString(R.string.text_enabled_flags));
				}
				if (posting.userIcons.size() > 0) {
					appendNewLine(builder, context.getString(R.string.text_enabled_icons));
				}
			} else {
				appendNewLine(builder, context.getString(R.string.text_read_only));
			}
			if (info != null) {
				headerData[3].setText(info);
				headerData[2].setText(context.getString(R.string.text_description));
				headerData[2].setVisibility(View.VISIBLE);
				headerData[3].setVisibility(View.VISIBLE);
				mayExpand = true;
			} else {
				headerData[2].setVisibility(View.GONE);
				headerData[3].setVisibility(View.GONE);
			}
			if (builder.length() > 0) {
				headerData[5].setText(builder.toString());
				headerData[4].setVisibility(View.VISIBLE);
				headerData[5].setVisibility(View.VISIBLE);
				mayExpand = true;
			} else {
				headerData[4].setVisibility(View.GONE);
				headerData[5].setVisibility(View.GONE);
			}
		}
		if (!mayExpand) {
			headerExpanded = false;
			headerExpandIcon.setRotation(0f);
		}
		headerAdditional.setVisibility(headerExpanded ? View.VISIBLE : View.GONE);
		headerClickableView.setVisibility(mayExpand ? View.VISIBLE : View.GONE);
		headerExpandIcon.setVisibility(mayExpand ? View.VISIBLE : View.GONE);
	}

	private static final Comparator<PostItem> SORT_BY_DATE_COMPARATOR =
			(lhs, rhs) -> ((Long) rhs.getTimestamp()).compareTo(lhs.getTimestamp());

	private static final Comparator<PostItem> SORT_BY_REPLIES_COMPARATOR =
			(lhs, rhs) -> rhs.getThreadPostsCount() - lhs.getThreadPostsCount();

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isChecked) {
			int index = -1;
			for (int i = 0; i < sortingData.length; i++) {
				CompoundButton button = sortingData[i];
				if (button == buttonView) {
					index = i;
				} else {
					button.setChecked(false);
				}
			}
			applySorting(index);
			if (!catalogPostItems.isEmpty()) {
				notifyDataSetChanged();
			}
		}
	}

	private void applySorting(int index) {
		if (catalogPostItems == null) {
			catalogPostItems = new ArrayList<>(items.size());
		} else {
			catalogPostItems.clear();
		}
		for (Object object : items) {
			catalogPostItems.add((PostItem) object);
		}
		switch (index) {
			case 1: {
				Collections.sort(catalogPostItems, SORT_BY_DATE_COMPARATOR);
				break;
			}
			case 2: {
				Collections.sort(catalogPostItems, SORT_BY_REPLIES_COMPARATOR);
				break;
			}
		}
	}

	private void applyFilterIfNecessary() {
		if (filteredPostItems != null) {
			applyFilter(filterText);
		}
	}

	// Returns true, if adapter isn't empty.
	public boolean applyFilter(String text) {
		filterText = text;
		boolean filterMode = !StringUtils.isEmpty(text);
		if (filterMode) {
			if (filteredPostItems == null) {
				filteredPostItems = new ArrayList<>();
			} else {
				filteredPostItems.clear();
			}
			text = text.toLowerCase(Locale.getDefault());
			for (Object item : items) {
				if (item instanceof PostItem) {
					PostItem postItem = (PostItem) item;
					boolean add = false;
					if (postItem.getSubject().toLowerCase(Locale.getDefault()).contains(text)) {
						add = true;
					} else if (postItem.getComment().toString().toLowerCase(Locale.getDefault()).contains(text)) {
						add = true;
					}
					if (add) {
						filteredPostItems.add(postItem);
					}
				}
			}
		} else {
			filteredPostItems = null;
		}
		notifyDataSetChanged();
		return !filterMode || filteredPostItems.size() > 0;
	}

	public void updateConfiguration(int listViewWidth) {
		Pair<Integer, Integer> configuration = obtainGridConfiguration(context, listViewWidth,
				gridRowCount, gridItemContentHeight);
		if (configuration != null) {
			gridRowCount = configuration.first;
			gridItemContentHeight = configuration.second;
			if (gridMode && items.size() > 0) {
				notifyDataSetChanged();
			}
		}
	}

	static Pair<Integer, Integer> obtainGridConfiguration(Context context, int listViewWidth, int currentRowCount,
			int currentContentHeight) {
		if (listViewWidth > 0) {
			float density = ResourceUtils.obtainDensity(context);
			int size = (int) (120f * density); // Minimum card width
			int padding = (int) (8f * density); // Card Padding
			int rowCount = Math.max((listViewWidth - padding) / (size + padding), 1);
			int width = (listViewWidth - padding * (rowCount + 1)) / rowCount;
			int contentHeight = (int) (width * 1.5f);
			if (rowCount != currentRowCount || contentHeight != currentContentHeight) {
				return new Pair<>(rowCount, contentHeight);
			}
		}
		return null;
	}

	public void setGridMode(boolean gridMode) {
		if (this.gridMode != gridMode) {
			this.gridMode = gridMode;
			if (items.size() > 0) {
				notifyDataSetChanged();
			}
		}
	}

	public boolean isGridMode() {
		return gridMode;
	}

	private static String getPositionType(int type, Object item) {
		switch (type) {
			case ITEM_VIEW_TYPE_THREAD:
			case ITEM_VIEW_TYPE_THREAD_HIDDEN: {
				return "thread" + ((PostItem) item).getPostNumber();
			}
			case ITEM_VIEW_TYPE_THREAD_GRID: {
				return "thread" + ((PostItem[]) item)[0].getPostNumber();
			}
			case ITEM_VIEW_TYPE_PAGE_DIVIDER: {
				return "divider" + ((DividerItem) item).pageNumber;
			}
			case IGNORE_ITEM_VIEW_TYPE: {
				return "header";
			}
		}
		return null;
	}

	public String getPositionInfo(int position) {
		int count = getCount();
		if (position < 0 || position >= count) {
			return null;
		}
		Object item = getItemInternal(position);
		int type = getItemViewType(position);
		return getPositionType(type, item);
	}

	public int getPositionFromInfo(String positionInfo) {
		if (positionInfo != null) {
			int count = getCount();
			for (int i = 0; i < count; i++) {
				Object item = getItemInternal(i);
				int type = getItemViewType(i);
				if (type == ITEM_VIEW_TYPE_THREAD_GRID) {
					PostItem[] postItems = (PostItem[]) item;
					for (PostItem postItem : postItems) {
						if (postItem != null) {
							String positionType = getPositionType(ITEM_VIEW_TYPE_THREAD, postItem);
							if (positionInfo.equals(positionType)) {
								return i;
							}
						}
					}
				} else {
					String positionType = getPositionType(type, item);
					if (positionInfo.equals(positionType)) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView) {
		if (!isBusy) {
			int count = listView.getChildCount();
			for (int i = 0; i < count; i++) {
				View view = listView.getChildAt(i);
				int position = listView.getPositionForView(view);
				Object item = getItem(position);
				if (item instanceof PostItem) {
					PostItem postItem = (PostItem) item;
					uiManager.view().displayThumbnails(view, postItem.getAttachmentItems());
				} else if (item instanceof PostItem[]) {
					PostItem[] postItems = (PostItem[]) item;
					ViewGroup viewGroup = (ViewGroup) view;
					for (int j = 0; j < postItems.length; j++) {
						PostItem postItem = postItems[j];
						if (postItem != null && !postItem.isHiddenUnchecked()) {
							View child = viewGroup.getChildAt(j);
							uiManager.view().displayThumbnails(child, postItem.getAttachmentItems());
						}
					}
				}
			}
		}
		busy = isBusy;
	}

	private static class DividerItem {
		public final String title;
		public final int pageNumber;

		public DividerItem(String title, int pageNumber) {
			this.title = title;
			this.pageNumber = pageNumber;
		}
	}
}
