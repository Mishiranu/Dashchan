package com.mishiranu.dashchan.ui.navigator.adapter;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ThreadsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
		implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	public interface Callback extends ListViewUtils.SimpleCallback<PostItem> {}

	private enum ViewType {THREAD, THREAD_CELL, THREAD_HIDDEN, HEADER, DIVIDER}

	private static final int HEADER_PADDING_HORIZONTAL = 20;
	private static final int CARD_MIN_WIDTH_DP = 120;
	private static final int CARD_PADDING_OUT_DP = 8;
	private static final int CARD_PADDING_IN_DP = 4;
	private static final int CARD_PADDING_IN_EXTRA_DP = 1;

	private static class ListItem {
		public final String title;
		public final Integer pageNumber;
		public final PostItem postItem;

		public ListItem(String title, Integer pageNumber, PostItem postItem) {
			this.title = title;
			this.pageNumber = pageNumber;
			this.postItem = postItem;
		}
	}

	private static class GridMode {
		public final int columns;
		public final int gridItemContentHeight;

		private GridMode(int columns, int gridItemContentHeight) {
			this.columns = columns;
			this.gridItemContentHeight = gridItemContentHeight;
		}
	}

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private ArrayList<ListItem> filteredListItems;
	private ArrayList<ListItem> catalogSortedListItems;

	private final Context context;
	private final Callback callback;
	private final String chanName;
	private final String boardName;
	private final UiManager uiManager;
	private final UiManager.ConfigurationSet configurationSet;

	private final View headerView;
	private final View headerAdditional;
	private final View headerClickableView;
	private final ImageView headerExpandIcon;
	private final TextView[] headerData = new TextView[6];
	private final RadioButton[] sortingData = new RadioButton[3];

	private String filterText;
	private GridMode gridMode;
	private boolean headerExpanded;

	public ThreadsAdapter(Context context, Callback callback, String chanName, String boardName, UiManager uiManager,
			UiManager.PostStateProvider postStateProvider, boolean headerExpanded, int catalogSortIndex) {
		this.context = context;
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(null, null, postStateProvider,
				new GalleryItem.Set(false), uiManager.dialog().createStackInstance(), null,
				false, true, false, false, false, null);
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		int additionalPaddingTop = 2;
		frameLayout.setPadding((int) ((HEADER_PADDING_HORIZONTAL / 2) * density),
				(int) ((additionalPaddingTop + CARD_PADDING_OUT_DP - CARD_PADDING_IN_DP) * density),
				(int) ((HEADER_PADDING_HORIZONTAL / 2) * density), 0);
		headerClickableView = new View(context);
		headerClickableView.setOnClickListener(this);
		ViewUtils.setSelectableItemBackground(headerClickableView);
		frameLayout.addView(headerClickableView, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		frameLayout.addView(linearLayout, FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		linearLayout.setPadding((int) ((HEADER_PADDING_HORIZONTAL / 2) * density),
				(int) (CARD_PADDING_IN_DP * density), (int) ((HEADER_PADDING_HORIZONTAL / 2) * density),
				(int) (CARD_PADDING_IN_DP * density));
		LinearLayout innerLayout = new LinearLayout(context);
		innerLayout.setOrientation(LinearLayout.HORIZONTAL);
		linearLayout.addView(innerLayout, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		LinearLayout topLayout = new LinearLayout(context);
		topLayout.setOrientation(LinearLayout.VERTICAL);
		innerLayout.addView(topLayout, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		headerData[0] = new TextView(context, null, android.R.attr.textAppearanceLarge);
		ViewUtils.setTextSizeScaled(headerData[0], 18);
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
		headerExpandIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.iconButtonDropDown, 0));
		if (C.API_LOLLIPOP) {
			headerExpandIcon.setImageTintList(ResourceUtils.getColorStateList(context,
					android.R.attr.textColorPrimary));
		}
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
			ViewUtils.setTextSizeScaled(headerData[i], 16);
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
		headerData[4].setText(context.getString(R.string.configuration));
		LinearLayout radioButtonsContainer = new LinearLayout(context);
		radioButtonsContainer.setOrientation(LinearLayout.VERTICAL);
		radioButtonsContainer.setClickable(true);
		headerAdditional.addView(radioButtonsContainer, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		for (int i = 0; i < sortingData.length; i++) {
			sortingData[i] = new RadioButton(context);
			ThemeEngine.applyStyle(sortingData[i]);
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
		sortingData[0].setText(context.getString(R.string.unsorted));
		sortingData[1].setText(context.getString(R.string.sort_by_date_created));
		sortingData[2].setText(context.getString(R.string.sort_by_replies));
		sortingData[0].setChecked(true);
		headerView = frameLayout;
		this.headerExpanded = headerExpanded;
		if (catalogSortIndex >= 0) {
			sortingData[catalogSortIndex].setChecked(true);
		}
	}

	private RecyclerView.ViewHolder configureCard(RecyclerView.ViewHolder viewHolder) {
		View view = ((ViewGroup) viewHolder.itemView).getChildAt(0);
		return ListViewUtils.bind(viewHolder, view, true, this::getPostItem, callback);
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case THREAD: {
				return configureCard(uiManager.view().createThreadViewHolder(parent, configurationSet, false));
			}
			case THREAD_CELL: {
				return configureCard(uiManager.view().createThreadViewHolder(parent, configurationSet, true));
			}
			case THREAD_HIDDEN: {
				return configureCard(uiManager.view().createThreadHiddenView(parent, configurationSet));
			}
			case HEADER: {
				return new SimpleViewHolder(headerView);
			}
			case DIVIDER: {
				float density = ResourceUtils.obtainDensity(parent);
				TextView textView = new TextView(parent.getContext(), null, android.R.attr.textAppearanceLarge);
				ViewUtils.setTextSizeScaled(textView, 18);
				if (C.API_LOLLIPOP) {
					textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
				}
				textView.setPadding((int) (HEADER_PADDING_HORIZONTAL * density), 0,
						(int) (HEADER_PADDING_HORIZONTAL * density), 0);
				return new SimpleViewHolder(textView);
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ListItem listItem = getListItem(position);
		switch (ViewType.values()[holder.getItemViewType()]) {
			case THREAD: {
				uiManager.view().bindThreadView(holder, listItem.postItem);
				break;
			}
			case THREAD_CELL: {
				uiManager.view().bindThreadCellView(holder, listItem.postItem,
						gridMode.gridItemContentHeight);
				break;
			}
			case THREAD_HIDDEN: {
				uiManager.view().bindThreadHiddenView(holder, listItem.postItem);
				break;
			}
			case DIVIDER: {
				((TextView) holder.itemView).setText(listItem.title);
				break;
			}
		}
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
		if (sortingData[0].getVisibility() == View.VISIBLE) {
			for (int i = 0; i < sortingData.length; i++) {
				if (sortingData[i].isChecked()) {
					return i;
				}
			}
		}
		return -1;
	}

	public boolean isRealEmpty() {
		return listItems.size() == 0;
	}

	@Override
	public int getItemViewType(int position) {
		ListItem listItem = getListItem(position);
		ViewType viewType;
		if (listItem.pageNumber != null) {
			viewType = listItem.pageNumber > 0 ? ViewType.DIVIDER : ViewType.HEADER;
		} else if (listItem.postItem != null) {
			viewType = gridMode != null ? ViewType.THREAD_CELL
					: configurationSet.postStateProvider.isHiddenResolve(listItem.postItem)
					? ViewType.THREAD_HIDDEN : ViewType.THREAD;
		} else {
			throw new IllegalStateException();
		}
		return viewType.ordinal();
	}

	private List<ListItem> getListItems() {
		return filteredListItems != null ? filteredListItems : catalogSortedListItems != null
				? catalogSortedListItems : listItems;
	}

	private ListItem getListItem(int position) {
		return getListItems().get(position);
	}

	private PostItem getPostItem(int position) {
		return getListItem(position).postItem;
	}

	@Override
	public int getItemCount() {
		return getListItems().size();
	}

	public int getSpanSize(int position) {
		ListItem listItem = getListItem(position);
		return listItem.pageNumber != null && gridMode != null ? gridMode.columns : 1;
	}

	public void applyItemPadding(View view, int position, int column, Rect rect) {
		ListItem listItem = getListItem(position);
		float density = ResourceUtils.obtainDensity(view);
		int paddingOut = (int) (CARD_PADDING_OUT_DP * density);
		int paddingIn = (int) (CARD_PADDING_IN_DP * density);
		if (listItem.postItem != null) {
			int columns = gridMode != null ? gridMode.columns : 1;
			int left;
			int right;
			if (columns >= 2) {
				int paddingInExtra = (int) ((CARD_PADDING_IN_DP + CARD_PADDING_IN_EXTRA_DP) * density);
				int total = 2 * paddingOut + (columns - 1) * paddingInExtra;
				float average = (float) total / columns;
				left = (int) AnimationUtils.lerp(paddingOut, average - paddingOut, (float) column / (columns - 1));
				right = (int) average - left;
			} else {
				left = paddingOut;
				right = paddingOut;
			}
			boolean firstRow = position - column == 0;
			boolean lastRow = position + columns - column >= getItemCount();
			rect.set(left, firstRow ? paddingOut : paddingIn, right, lastRow ? paddingOut : 0);
		} else if (view == headerView) {
			rect.set(0, 0, 0, 0);
		} else {
			rect.set(0, paddingOut, 0, position + 1 == getItemCount() ? paddingOut : paddingOut - paddingIn);
		}
	}

	public void setItems(Collection<ArrayList<PostItem>> postItems, int pageNumber, int boardSpeed) {
		listItems.clear();
		filteredListItems = null;
		catalogSortedListItems = null;
		for (ArrayList<PostItem> pagePostItems : postItems) {
			appendItemsInternal(pagePostItems, pageNumber, boardSpeed);
			pageNumber++;
		}
		applySorting(getCatalogSortIndex());
		applyCurrentFilter();
		notifyDataSetChanged();
	}

	public void appendItems(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed) {
		filteredListItems = null;
		catalogSortedListItems = null;
		appendItemsInternal(postItems, pageNumber, boardSpeed);
		applyCurrentFilter();
		notifyDataSetChanged();
	}

	public void notifyNotModified() {
		for (ListItem listItem : listItems) {
			if (listItem.postItem != null) {
				listItem.postItem.setHidden(PostItem.HideState.UNDEFINED, null);
			}
		}
		notifyDataSetChanged();
	}

	private void appendItemsInternal(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed) {
		if (pageNumber > 0) {
			listItems.add(new ListItem(context.getString(R.string.number_page__format, pageNumber), pageNumber, null));
		} else if (pageNumber == 0 || pageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG) {
			listItems.add(new ListItem(null, pageNumber, null));
			updateHeaderView(pageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG, boardSpeed);
		} else {
			throw new IllegalStateException();
		}
		if (postItems != null) {
			for (PostItem postItem : postItems) {
				if (Preferences.isDisplayHiddenThreads() ||
						!configurationSet.postStateProvider.isHiddenResolve(postItem)) {
					listItems.add(new ListItem(null, null, postItem));
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
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		String title = configuration.getBoardTitle(boardName);
		if (StringUtils.isEmpty(title)) {
			title = StringUtils.formatBoardTitle(chanName, boardName, null);
		}
		boolean mayExpand = false;
		if (boardSpeed > 0) {
			headerData[1].setVisibility(View.VISIBLE);
			headerData[1].setText(ResourceUtils.getColonString(context.getResources(),
					R.string.speed, context.getResources().getQuantityString
							(R.plurals.number_posts_per_hour__format, boardSpeed, boardSpeed)));
		} else {
			headerData[1].setVisibility(View.GONE);
		}
		if (catalog) {
			boolean fromCatalogOrNewAdapter = sortingData[0].getVisibility() == View.VISIBLE;
			for (RadioButton button : sortingData) {
				button.setVisibility(View.VISIBLE);
			}
			headerData[0].setText(context.getString(R.string.catalog) + ": " + title);
			headerData[2].setVisibility(View.VISIBLE);
			headerData[3].setVisibility(View.GONE);
			headerData[4].setVisibility(View.GONE);
			headerData[5].setVisibility(View.GONE);
			headerData[2].setText(context.getString(R.string.sorting));
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
				appendNewLine(builder, ResourceUtils.getColonString(context.getResources(),
						R.string.pages_count, pagesCount));
			}
			ChanConfiguration.Board board = configuration.safe().obtainBoard(boardName);
			ChanConfiguration.Posting posting = board.allowPosting
					? configuration.safe().obtainPosting(boardName, true) : null;
			if (posting != null) {
				int bumpLimit = configuration.getBumpLimit(boardName);
				if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
					appendNewLine(builder, context.getString(R.string.bump_limit_posts__colon_format, bumpLimit));
				}
				if (!posting.allowSubject) {
					appendNewLine(builder, context.getString(R.string.subjects_are_disabled));
				}
				if (!posting.allowName) {
					appendNewLine(builder, context.getString(R.string.names_are_disabled));
				} else if (!posting.allowTripcode) {
					appendNewLine(builder, context.getString(R.string.tripcodes_are_disabled));
				}
				if (posting.attachmentCount <= 0) {
					appendNewLine(builder, context.getString(R.string.images_are_disabled));
				}
				if (!posting.optionSage) {
					appendNewLine(builder, context.getString(R.string.sage_is_disabled));
				}
				if (posting.hasCountryFlags) {
					appendNewLine(builder, context.getString(R.string.flags_are_enabled));
				}
				if (posting.userIcons.size() > 0) {
					appendNewLine(builder, context.getString(R.string.icons_are_enabled));
				}
			} else {
				appendNewLine(builder, context.getString(R.string.read_only));
			}
			if (info != null) {
				headerData[3].setText(info);
				headerData[2].setText(context.getString(R.string.description));
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

	private static final Comparator<ListItem> SORT_BY_DATE_COMPARATOR =
			(lhs, rhs) -> lhs.postItem == null ? rhs.postItem == null ? 0 : -1 : rhs.postItem == null ? 1
					: Long.compare(rhs.postItem.getTimestamp(), lhs.postItem.getTimestamp());

	private static final Comparator<ListItem> SORT_BY_REPLIES_COMPARATOR =
			(lhs, rhs) -> lhs.postItem == null ? rhs.postItem == null ? 0 : -1 : rhs.postItem == null ? 1
					: Integer.compare(rhs.postItem.getThreadPostsCount(), lhs.postItem.getThreadPostsCount());

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
			notifyDataSetChanged();
		}
	}

	private void applySorting(int index) {
		if (index <= 0) {
			catalogSortedListItems = null;
		} else {
			if (catalogSortedListItems == null) {
				catalogSortedListItems = new ArrayList<>(listItems);
			} else {
				catalogSortedListItems.clear();
				catalogSortedListItems.addAll(listItems);
			}
			switch (index) {
				case 1: {
					Collections.sort(catalogSortedListItems, SORT_BY_DATE_COMPARATOR);
					break;
				}
				case 2: {
					Collections.sort(catalogSortedListItems, SORT_BY_REPLIES_COMPARATOR);
					break;
				}
			}
		}
	}

	public void applyFilter(String text) {
		if (!StringUtils.emptyIfNull(filterText).equals(StringUtils.emptyIfNull(text))) {
			filterText = text;
			applyCurrentFilter();
			notifyDataSetChanged();
		}
	}

	private void applyCurrentFilter() {
		String text = filterText;
		if (!StringUtils.isEmpty(text)) {
			if (filteredListItems == null) {
				filteredListItems = new ArrayList<>();
			} else {
				filteredListItems.clear();
			}
			text = text.toLowerCase(Locale.getDefault());
			for (ListItem listItem : listItems) {
				if (listItem.postItem != null) {
					boolean add = listItem.postItem.getSubject().toLowerCase(Locale.getDefault()).contains(text) ||
							listItem.postItem.getComment().toString().toLowerCase(Locale.getDefault()).contains(text);
					if (add) {
						filteredListItems.add(listItem);
					}
				}
			}
		} else {
			filteredListItems = null;
		}
	}

	public int setGridMode(boolean gridMode) {
		if (gridMode) {
			float density = ResourceUtils.obtainDensity(context);
			int totalWidth = (int) (context.getResources().getConfiguration().screenWidthDp * density);
			int minWidth = (int) (CARD_MIN_WIDTH_DP * density);
			int paddingOut = (int) (CARD_PADDING_OUT_DP * density);
			int paddingInExtra = (int) ((CARD_PADDING_IN_DP + CARD_PADDING_IN_EXTRA_DP) * density);
			int columns = Math.min(Math.max(1, (totalWidth - 2 * paddingOut + paddingInExtra)
					/ (minWidth + paddingInExtra)), 6);
			int contentWidth = (totalWidth - 2 * paddingOut - (columns - 1) * paddingInExtra) / columns;
			int contentHeight = (int) (contentWidth * 1.5f);
			this.gridMode = new GridMode(columns, contentHeight);
			return columns;
		} else {
			this.gridMode = null;
			return 1;
		}
	}
}
