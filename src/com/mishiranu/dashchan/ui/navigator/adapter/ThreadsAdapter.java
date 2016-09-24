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

package com.mishiranu.dashchan.ui.navigator.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

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
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class ThreadsAdapter extends BaseAdapter implements BusyScrollListener.Callback, View.OnClickListener,
		CompoundButton.OnCheckedChangeListener
{
	private static final int ITEM_VIEW_TYPE_THREAD = 0;
	private static final int ITEM_VIEW_TYPE_THREAD_HIDDEN = 1;
	private static final int ITEM_VIEW_TYPE_THREAD_GRID = 2;
	private static final int ITEM_VIEW_TYPE_PAGE_DIVIDER = 3;

	private final ArrayList<Object> mItems = new ArrayList<>();
	private ArrayList<PostItem> mCatalogPostItems;
	private ArrayList<PostItem> mFilteredPostItems;
	private ArrayList<Object> mGridItems;

	private final Context mContext;
	private final String mChanName;
	private final String mBoardName;
	private final UiManager mUiManager;
	private final HidePerformer mHidePerformer;

	private final View mHeaderView;
	private final View mHeaderAdditional;
	private final ClickableView mHeaderClickableView;
	private final ImageView mHeaderExpandIcon;
	private final TextView[] mHeaderData = new TextView[6];
	private final RadioButton[] mSortingData = new RadioButton[3];

	private boolean mMayShowHeader = false;
	private boolean mHeaderExpanded = false;
	private boolean mBusy = false;

	private String mFilterText;
	private boolean mGridMode = false;
	private int mGridRowCount = 1;
	private int mGridItemContentHeight;

	public ThreadsAdapter(Context context, String chanName, String boardName, UiManager uiManager)
	{
		mContext = context;
		mChanName = chanName;
		mBoardName = boardName;
		mUiManager = uiManager;
		mHidePerformer = new HidePerformer();
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		frameLayout.setPadding((int) (10f * density), (int) (6f * density), (int) (10f * density), 0);
		mHeaderClickableView = new ClickableView(context);
		mHeaderClickableView.setOnClickListener(this);
		frameLayout.addView(mHeaderClickableView, FrameLayout.LayoutParams.MATCH_PARENT,
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
		mHeaderData[0] = new TextView(context, null, android.R.attr.textAppearanceLarge);
		mHeaderData[0].setTextSize(18f);
		if (C.API_LOLLIPOP) mHeaderData[0].setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
		topLayout.addView(mHeaderData[0], LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		mHeaderData[1] = new TextView(context, null, android.R.attr.textAppearanceSmall);
		topLayout.addView(mHeaderData[1], LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		mHeaderData[1].setPadding(0, (int) (2f * density), 0, (int) (2f * density));
		mHeaderExpandIcon = new ImageView(context);
		mHeaderExpandIcon.setImageResource(ResourceUtils.getResourceId(context, R.attr.buttonDropDown, 0));
		innerLayout.addView(mHeaderExpandIcon, (int) (24f * density), (int) (24f * density));
		LinearLayout.LayoutParams iconLayoutParams = (LinearLayout.LayoutParams) mHeaderExpandIcon.getLayoutParams();
		iconLayoutParams.gravity = Gravity.BOTTOM;
		iconLayoutParams.setMargins((int) (4f * density), 0, (int) (-6f * density), 0);
		LinearLayout headerAdditional = new LinearLayout(context);
		headerAdditional.setOrientation(LinearLayout.VERTICAL);
		mHeaderAdditional = headerAdditional;
		linearLayout.addView(headerAdditional, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		for (int i = 2; i < 6; i += 2)
		{
			mHeaderData[i] = new TextView(context, null, android.R.attr.textAppearanceLarge);
			mHeaderData[i].setTextSize(16f);
			if (C.API_LOLLIPOP) mHeaderData[i].setTypeface(mHeaderData[0].getTypeface());
			mHeaderData[i].setPadding(0, (int) (8f * density), 0, (int) (4f * density));
			headerAdditional.addView(mHeaderData[i], LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			mHeaderData[i + 1] = new TextView(context, null, android.R.attr.textAppearanceSmall);
			mHeaderData[i + 1].setLineSpacing((int) (2f * density), 1f);
			headerAdditional.addView(mHeaderData[i + 1], LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			mHeaderData[i + 1].setPadding(0, 0, 0, (int) (2f * density));
		}
		mHeaderData[4].setText(context.getString(R.string.text_configuration));
		LinearLayout radioButtonsContainer = new LinearLayout(context);
		radioButtonsContainer.setOrientation(LinearLayout.VERTICAL);
		radioButtonsContainer.setClickable(true);
		headerAdditional.addView(radioButtonsContainer, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		for (int i = 0; i < mSortingData.length; i++)
		{
			mSortingData[i] = new RadioButton(context);
			mSortingData[i].setOnCheckedChangeListener(this);
			radioButtonsContainer.addView(mSortingData[i], LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) mSortingData[i]
					.getLayoutParams();
			if (i == 0) layoutParams.topMargin = (int) (4f * density);
			if (C.API_LOLLIPOP) layoutParams.leftMargin = (int) (-4f * density);
		}
		mSortingData[0].setText(context.getString(R.string.text_sort_by_unsorted));
		mSortingData[1].setText(context.getString(R.string.text_sort_by_date));
		mSortingData[2].setText(context.getString(R.string.text_sort_by_replies));
		mSortingData[0].setChecked(true);
		mHeaderView = frameLayout;
	}

	public void applyAttributesBeforeFill(boolean headerExpanded, int catalogSortIndex, boolean gridMode)
	{
		mHeaderExpanded = headerExpanded;
		if (catalogSortIndex >= 0) mSortingData[catalogSortIndex].setChecked(true);
		mCatalogPostItems = null;
		setGridMode(gridMode);
	}

	private void prepareGridItems()
	{
		if (mGridItems != null && mGridItems.size() > 0) return;
		if (mGridItems == null) mGridItems = new ArrayList<>();
		int rowCount = mGridRowCount;
		int currentIndex = 0;
		PostItem[] postItems = null;
		ArrayList<?> items = mFilteredPostItems != null ? mFilteredPostItems : mCatalogPostItems != null
				? mCatalogPostItems : mItems;
		for (Object item : items)
		{
			if (item instanceof PostItem)
			{
				if (postItems == null)
				{
					postItems = new PostItem[rowCount];
					currentIndex = 0;
				}
				postItems[currentIndex++] = (PostItem) item;
				if (currentIndex == postItems.length)
				{
					mGridItems.add(postItems);
					postItems = null;
				}
			}
			else
			{
				if (postItems != null)
				{
					mGridItems.add(postItems);
					postItems = null;
				}
				mGridItems.add(item);
			}
		}
		if (postItems != null) mGridItems.add(postItems);
	}

	private boolean isShowHeader()
	{
		return mMayShowHeader && mFilteredPostItems == null;
	}

	public boolean isRealEmpty()
	{
		return mItems.size() == 0;
	}

	@Override
	public void notifyDataSetChanged()
	{
		if (mGridItems != null) mGridItems.clear();
		super.notifyDataSetChanged();
	}

	@Override
	public int getViewTypeCount()
	{
		return 4;
	}

	@Override
	public int getItemViewType(int position)
	{
		Object item = getItemInternal(position);
		return item instanceof PostItem ? ((PostItem) item).isHidden(mHidePerformer) ? ITEM_VIEW_TYPE_THREAD_HIDDEN
				: ITEM_VIEW_TYPE_THREAD : item instanceof PostItem[] ? ITEM_VIEW_TYPE_THREAD_GRID
				: item instanceof DividerItem ? ITEM_VIEW_TYPE_PAGE_DIVIDER : IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public int getCount()
	{
		int count = 0;
		if (mGridMode)
		{
			prepareGridItems();
			count += mGridItems.size();
		}
		else count += (mFilteredPostItems != null ? mFilteredPostItems : mItems).size();
		count += isShowHeader() ? 1 : 0;
		return count;
	}

	@Override
	public Object getItem(int position)
	{
		Object item = getItemInternal(position);
		return item instanceof PostItem ? (PostItem) item : item instanceof PostItem[] ? (PostItem[]) item : null;
	}

	public Object getItemInternal(int position)
	{
		if (isShowHeader())
		{
			if (position == 0) return null;
			position--;
		}
		if (mGridMode)
		{
			prepareGridItems();
			return mGridItems.get(position);
		}
		else
		{
			return (mFilteredPostItems != null ? mFilteredPostItems : mCatalogPostItems != null
					? mCatalogPostItems : mItems).get(position);
		}
	}

	@Override
	public long getItemId(int position)
	{
		return 0;
	}

	@Override
	public boolean isEnabled(int position)
	{
		return getItem(position) != null;
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return true;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Object item = getItemInternal(position);
		if (item == null) return mHeaderView;
		if (item instanceof PostItem)
		{
			PostItem postItem = (PostItem) item;
			if (!postItem.isHidden(mHidePerformer))
			{
				convertView = mUiManager.view().getThreadView(postItem, convertView, parent, mBusy);
			}
			else convertView = mUiManager.view().getThreadHiddenView(postItem, convertView, parent);
			ViewUtils.applyCardHolderPadding(convertView, position == 0, position == getCount() - 1, false);
		}
		else if (item instanceof PostItem[])
		{
			PostItem[] postItems = (PostItem[]) item;
			LinearLayout linearLayout = (LinearLayout) convertView;
			if (linearLayout == null)
			{
				linearLayout = new LinearLayout(mContext);
				linearLayout.setOrientation(LinearLayout.HORIZONTAL);
				linearLayout.setMotionEventSplittingEnabled(false);
				ViewUtils.applyMultipleCardHolderPadding(linearLayout);
				// Free space view
				linearLayout.addView(new View(mContext), 0, LinearLayout.LayoutParams.MATCH_PARENT);
				convertView = linearLayout;
			}
			while (linearLayout.getChildCount() - 1 > mGridRowCount) linearLayout.removeViewAt(mGridRowCount - 1);
			int count = getCount();
			int freeSpaceIndex = linearLayout.getChildCount() - 1;
			View freeSpaceView = linearLayout.getChildAt(freeSpaceIndex);
			freeSpaceView.setVisibility(View.GONE);
			for (int i = 0; i < postItems.length; i++)
			{
				PostItem postItem = postItems[i];
				if (postItem != null)
				{
					View convertViewChild = null;
					if (i < freeSpaceIndex)
					{
						View view = linearLayout.getChildAt(i);
						if (view.getClass() == View.class) linearLayout.removeViewAt(i); else convertViewChild = view;
					}
					boolean add = convertViewChild == null;
					convertViewChild = mUiManager.view().getThreadViewForGrid(postItem, convertViewChild, parent,
							mHidePerformer, mGridItemContentHeight, mBusy);
					if (add)
					{
						linearLayout.addView(convertViewChild, i, new LinearLayout.LayoutParams(0,
								LinearLayout.LayoutParams.WRAP_CONTENT, 1));
					}
					else convertViewChild.setVisibility(View.VISIBLE);
					ViewUtils.applyCardHolderPadding(convertViewChild, position == 0, position == count - 1, true);
				}
				else
				{
					for (int j = i; j < freeSpaceIndex; j++)
					{
						linearLayout.getChildAt(j).setVisibility(View.GONE);
					}
					freeSpaceView.setVisibility(View.VISIBLE);
					((LinearLayout.LayoutParams) freeSpaceView.getLayoutParams()).weight = postItems.length - i;
					break;
				}
			}
		}
		else
		{
			DividerItem dividerItem = (DividerItem) item;
			float density = ResourceUtils.obtainDensity(mContext);
			if (convertView == null)
			{
				TextView textView = new TextView(mContext, null, android.R.attr.textAppearanceLarge);
				textView.setTextSize(18f);
				if (C.API_LOLLIPOP) textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
				convertView = textView;
			}
			((TextView) convertView).setText(dividerItem.title);
			convertView.setPadding((int) (20f * density), position == 0 ? (int) (10f * density)
					: (int) (8f * density), (int) (20f * density), (int) (4f * density));
		}
		return convertView;
	}

	private Animator mLastAnimator;

	@Override
	public void onClick(View v)
	{
		boolean visible = false;
		for (int i = 1; i < mHeaderData.length; i++)
		{
			if (mHeaderData[i].getVisibility() != View.GONE)
			{
				visible = true;
				break;
			}
		}
		if (!visible && !mHeaderExpanded) return;
		mHeaderExpanded = !mHeaderExpanded;
		mHeaderExpandIcon.setRotation(mHeaderExpanded ? 180f : 0f);
		if (mLastAnimator != null) mLastAnimator.cancel();
		if (mHeaderExpanded)
		{
			mHeaderAdditional.setVisibility(View.VISIBLE);
			mHeaderAdditional.setAlpha(0f);
			Animator heightAnimator = AnimationUtils.ofHeight(mHeaderAdditional, 0,
					ViewGroup.LayoutParams.WRAP_CONTENT, true);
			heightAnimator.setDuration(200);
			ValueAnimator alphaAnimator = ObjectAnimator.ofFloat(mHeaderAdditional, View.ALPHA, 0f, 1f);
			alphaAnimator.setStartDelay(100);
			alphaAnimator.setDuration(200);
			AnimatorSet animatorSet = new AnimatorSet();
			animatorSet.playTogether(heightAnimator, alphaAnimator);
			animatorSet.start();
			mLastAnimator = animatorSet;
		}
		else
		{
			ValueAnimator alphaAnimator = ObjectAnimator.ofFloat(mHeaderAdditional, View.ALPHA, 1f, 0f);
			alphaAnimator.setDuration(200);
			Animator heightAnimator = AnimationUtils.ofHeight(mHeaderAdditional,
					ViewGroup.LayoutParams.WRAP_CONTENT, 0, false);
			heightAnimator.setStartDelay(100);
			heightAnimator.setDuration(200);
			AnimatorSet animatorSet = new AnimatorSet();
			animatorSet.playTogether(heightAnimator, alphaAnimator);
			animatorSet.addListener(new AnimationUtils.VisibilityListener(mHeaderAdditional, View.GONE));
			animatorSet.start();
			mLastAnimator = animatorSet;
		}
	}

	public boolean isHeaderExpanded()
	{
		return mHeaderExpanded;
	}

	public int getCatalogSortIndex()
	{
		if (mMayShowHeader && mSortingData[0].getVisibility() == View.VISIBLE)
		{
			for (int i = 0; i < mSortingData.length; i++)
			{
				if (mSortingData[i].isChecked()) return i;
			}
		}
		return -1;
	}

	public void setItems(Collection<ArrayList<PostItem>> postItems, int pageNumber, int boardSpeed)
	{
		mCatalogPostItems = null;
		mItems.clear();
		mMayShowHeader = false;
		for (ArrayList<PostItem> pagePostItems : postItems)
		{
			appendItemsInternal(pagePostItems, pageNumber, boardSpeed);
			pageNumber++;
		}
		int index = getCatalogSortIndex();
		if (index != -1) applySorting(index);
		applyFilterIfNecessary();
		notifyDataSetChanged();
	}

	public void appendItems(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed)
	{
		mCatalogPostItems = null;
		appendItemsInternal(postItems, pageNumber, boardSpeed);
		applyFilterIfNecessary();
		notifyDataSetChanged();
	}

	public void notifyNotModified()
	{
		for (Object item : mItems)
		{
			if (item instanceof PostItem)
			{
				PostItem postItem = (PostItem) item;
				postItem.invalidateHidden();
			}
		}
		notifyDataSetChanged();
	}

	private void appendItemsInternal(ArrayList<PostItem> postItems, int pageNumber, int boardSpeed)
	{
		if (pageNumber > 0)
		{
			mItems.add(new DividerItem(mContext.getString(R.string.text_page_format, pageNumber), pageNumber));
		}
		else updateHeaderView(pageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG, boardSpeed);
		if (postItems != null)
		{
			for (PostItem postItem : postItems)
			{
				if (Preferences.isDisplayHiddenThreads() || !postItem.isHidden(mHidePerformer)) mItems.add(postItem);
			}
		}
	}

	private static void appendNewLine(StringBuilder builder, String line)
	{
		if (builder.length() > 0) builder.append('\n');
		builder.append(line);
	}

	private void updateHeaderView(boolean catalog, int boardSpeed)
	{
		mMayShowHeader = true;
		ChanConfiguration configuration = ChanConfiguration.get(mChanName);
		String title = configuration.getBoardTitle(mBoardName);
		if (StringUtils.isEmpty(title)) title = StringUtils.formatBoardTitle(mChanName, mBoardName, null);
		boolean mayExpand = false;
		if (boardSpeed > 0)
		{
			mHeaderData[1].setVisibility(View.VISIBLE);
			mHeaderData[1].setText(mContext.getString(R.string.text_board_speed_format, mContext.getResources()
					.getQuantityString(R.plurals.text_posts_per_hour_format, boardSpeed, boardSpeed)));
		}
		else mHeaderData[1].setVisibility(View.GONE);
		if (catalog)
		{
			boolean fromCatalogOrNewAdapter = mSortingData[0].getVisibility() == View.VISIBLE;
			for (RadioButton button : mSortingData) button.setVisibility(View.VISIBLE);
			mHeaderData[0].setText(mContext.getString(R.string.action_catalog) + ": " + title);
			mHeaderData[2].setVisibility(View.VISIBLE);
			mHeaderData[3].setVisibility(View.GONE);
			mHeaderData[4].setVisibility(View.GONE);
			mHeaderData[5].setVisibility(View.GONE);
			mHeaderData[2].setText(mContext.getString(R.string.text_sorting));
			if (!fromCatalogOrNewAdapter) mSortingData[0].setChecked(true);
			mayExpand = true;
		}
		else
		{
			for (RadioButton button : mSortingData) button.setVisibility(View.GONE);
			mHeaderData[0].setText(title);
			String info = StringUtils.nullIfEmpty(configuration.getBoardDescription(mBoardName));
			StringBuilder builder = new StringBuilder();
			int pagesCount = Math.max(configuration.getPagesCount(mBoardName), 1);
			if (pagesCount != ChanConfiguration.PAGES_COUNT_INVALID)
			{
				appendNewLine(builder, mContext.getString(R.string.text_pages_count_format, pagesCount));
			}
			ChanConfiguration.Board board = configuration.safe().obtainBoard(mBoardName);
			ChanConfiguration.Posting posting = board.allowPosting
					? configuration.safe().obtainPosting(mBoardName, true) : null;
			if (posting != null)
			{
				int bumpLimit = configuration.getBumpLimit(mBoardName);
				if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID)
				{
					appendNewLine(builder, mContext.getString(R.string.text_bump_limit_format, bumpLimit));
				}
				if (!posting.allowSubject)
				{
					appendNewLine(builder, mContext.getString(R.string.text_disabled_subjects));
				}
				if (!posting.allowName)
				{
					appendNewLine(builder, mContext.getString(R.string.text_disabled_names));
				}
				else if (!posting.allowTripcode)
				{
					appendNewLine(builder, mContext.getString(R.string.text_disabled_tripcodes));
				}
				if (posting.attachmentCount <= 0)
				{
					appendNewLine(builder, mContext.getString(R.string.text_disabled_images));
				}
				if (!posting.optionSage)
				{
					appendNewLine(builder, mContext.getString(R.string.text_disabled_sage));
				}
				if (posting.hasCountryFlags)
				{
					appendNewLine(builder, mContext.getString(R.string.text_enabled_flags));
				}
				if (posting.userIcons.size() > 0)
				{
					appendNewLine(builder, mContext.getString(R.string.text_enabled_icons));
				}
			}
			else appendNewLine(builder, mContext.getString(R.string.text_read_only));
			if (info != null)
			{
				mHeaderData[3].setText(info);
				mHeaderData[2].setText(mContext.getString(R.string.text_description));
				mHeaderData[2].setVisibility(View.VISIBLE);
				mHeaderData[3].setVisibility(View.VISIBLE);
				mayExpand = true;
			}
			else
			{
				mHeaderData[2].setVisibility(View.GONE);
				mHeaderData[3].setVisibility(View.GONE);
			}
			if (builder.length() > 0)
			{
				mHeaderData[5].setText(builder.toString());
				mHeaderData[4].setVisibility(View.VISIBLE);
				mHeaderData[5].setVisibility(View.VISIBLE);
				mayExpand = true;
			}
			else
			{
				mHeaderData[4].setVisibility(View.GONE);
				mHeaderData[5].setVisibility(View.GONE);
			}
		}
		if (!mayExpand)
		{
			mHeaderExpanded = false;
			mHeaderExpandIcon.setRotation(0f);
		}
		mHeaderAdditional.setVisibility(mHeaderExpanded ? View.VISIBLE : View.GONE);
		mHeaderClickableView.setVisibility(mayExpand ? View.VISIBLE : View.GONE);
		mHeaderExpandIcon.setVisibility(mayExpand ? View.VISIBLE : View.GONE);
	}

	private static final Comparator<PostItem> SORT_BY_DATE_COMPARATOR = (lhs, rhs) ->
	{
		return ((Long) rhs.getTimestamp()).compareTo(lhs.getTimestamp());
	};

	private static final Comparator<PostItem> SORT_BY_REPLIES_COMPARATOR = (lhs, rhs) ->
	{
		return rhs.getThreadPostsCount() - lhs.getThreadPostsCount();
	};

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
	{
		if (isChecked)
		{
			int index = -1;
			for (int i = 0; i < mSortingData.length; i++)
			{
				CompoundButton button = mSortingData[i];
				if (button == buttonView) index = i; else button.setChecked(false);
			}
			applySorting(index);
			if (!mCatalogPostItems.isEmpty()) notifyDataSetChanged();
		}
	}

	private void applySorting(int index)
	{
		if (mCatalogPostItems == null) mCatalogPostItems = new ArrayList<>(mItems.size());
		else mCatalogPostItems.clear();
		for (Object object : mItems) mCatalogPostItems.add((PostItem) object);
		switch (index)
		{
			case 1:
			{
				Collections.sort(mCatalogPostItems, SORT_BY_DATE_COMPARATOR);
				break;
			}
			case 2:
			{
				Collections.sort(mCatalogPostItems, SORT_BY_REPLIES_COMPARATOR);
				break;
			}
		}
	}

	private void applyFilterIfNecessary()
	{
		if (mFilteredPostItems != null) applyFilter(mFilterText);
	}

	/*
	 * Returns true, if adapter isn't empty.
	 */
	public boolean applyFilter(String text)
	{
		mFilterText = text;
		boolean filterMode = !StringUtils.isEmpty(text);
		if (filterMode)
		{
			if (mFilteredPostItems == null) mFilteredPostItems = new ArrayList<>(); else mFilteredPostItems.clear();
			text = text.toLowerCase(Locale.getDefault());
			for (Object item : mItems)
			{
				if (item instanceof PostItem)
				{
					PostItem postItem = (PostItem) item;
					boolean add = false;
					if (postItem.getSubject().toLowerCase(Locale.getDefault()).contains(text))
					{
						add = true;
					}
					else if (postItem.getComment().toString().toLowerCase(Locale.getDefault()).contains(text))
					{
						add = true;
					}
					if (add) mFilteredPostItems.add(postItem);
				}
			}
		}
		else mFilteredPostItems = null;
		notifyDataSetChanged();
		return !filterMode || mFilteredPostItems.size() > 0;
	}

	public void updateConfiguration(int listViewWidth)
	{
		Pair<Integer, Integer> configuration = obtainGridConfiguration(mContext, listViewWidth,
				mGridRowCount, mGridItemContentHeight);
		if (configuration != null)
		{
			mGridRowCount = configuration.first;
			mGridItemContentHeight = configuration.second;
			if (mGridMode && mItems.size() > 0) notifyDataSetChanged();
		}
	}

	static Pair<Integer, Integer> obtainGridConfiguration(Context context, int listViewWidth, int currentRowCount,
			int currentContentHeight)
	{
		if (listViewWidth > 0)
		{
			float density = ResourceUtils.obtainDensity(context);
			int size = (int) (120f * density); // Minimum card width
			int padding = (int) (8f * density); // Card Padding
			int rowCount = Math.max((listViewWidth - padding) / (size + padding), 1);
			int width = (listViewWidth - padding * (rowCount + 1)) / rowCount;
			int contentHeight = (int) (width * 1.5f);
			if (rowCount != currentRowCount || contentHeight != currentContentHeight)
			{
				return new Pair<>(rowCount, contentHeight);
			}
		}
		return null;
	}

	public void setGridMode(boolean gridMode)
	{
		if (mGridMode != gridMode)
		{
			mGridMode = gridMode;
			if (mItems.size() > 0) notifyDataSetChanged();
		}
	}

	public boolean isGridMode()
	{
		return mGridMode;
	}

	private static String getPositionType(int type, Object item)
	{
		switch (type)
		{
			case ITEM_VIEW_TYPE_THREAD:
			case ITEM_VIEW_TYPE_THREAD_HIDDEN: return "thread" + ((PostItem) item).getPostNumber();
			case ITEM_VIEW_TYPE_THREAD_GRID: return "thread" + ((PostItem[]) item)[0].getPostNumber();
			case ITEM_VIEW_TYPE_PAGE_DIVIDER: return "divider" + ((DividerItem) item).pageNumber;
			case IGNORE_ITEM_VIEW_TYPE: return "header";
		}
		return null;
	}

	public String getPositionInfo(int position)
	{
		int count = getCount();
		if (position < 0 || position >= count) return null;
		Object item = getItemInternal(position);
		int type = getItemViewType(position);
		return getPositionType(type, item);
	}

	public int getPositionFromInfo(String positionInfo)
	{
		if (positionInfo != null)
		{
			int count = getCount();
			for (int i = 0; i < count; i++)
			{
				Object item = getItemInternal(i);
				int type = getItemViewType(i);
				if (type == ITEM_VIEW_TYPE_THREAD_GRID)
				{
					PostItem[] postItems = (PostItem[]) item;
					for (PostItem postItem : postItems)
					{
						if (postItem != null)
						{
							String positionType = getPositionType(ITEM_VIEW_TYPE_THREAD, postItem);
							if (positionInfo.equals(positionType)) return i;
						}
					}
				}
				else
				{
					String positionType = getPositionType(type, item);
					if (positionInfo.equals(positionType)) return i;
				}
			}
		}
		return -1;
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView)
	{
		if (!isBusy)
		{
			int count = listView.getChildCount();
			for (int i = 0; i < count; i++)
			{
				View view = listView.getChildAt(i);
				int position = listView.getPositionForView(view);
				Object item = getItem(position);
				if (item instanceof PostItem)
				{
					PostItem postItem = (PostItem) item;
					mUiManager.view().displayThumbnails(view, postItem.getAttachmentItems(), false);
				}
				else if (item instanceof PostItem[])
				{
					PostItem[] postItems = (PostItem[]) item;
					ViewGroup viewGroup = (ViewGroup) view;
					for (int j = 0; j < postItems.length; j++)
					{
						PostItem postItem = postItems[j];
						if (postItem != null && !postItem.isHiddenUnchecked())
						{
							View child = viewGroup.getChildAt(j);
							mUiManager.view().displayThumbnails(child, postItem.getAttachmentItems(), false);
						}
					}
				}
			}
		}
		mBusy = isBusy;
	}

	private static class DividerItem
	{
		public final String title;
		public final int pageNumber;

		public DividerItem(String title, int pageNumber)
		{
			this.title = title;
			this.pageNumber = pageNumber;
		}
	}
}