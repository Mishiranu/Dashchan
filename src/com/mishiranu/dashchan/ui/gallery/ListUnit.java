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

package com.mishiranu.dashchan.ui.gallery;

import java.util.ArrayList;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.SelectorBorderDrawable;
import com.mishiranu.dashchan.graphics.SelectorCheckDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.EdgeEffectHandler;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class ListUnit implements AdapterView.OnItemClickListener, ActionMode.Callback
{
	private static final int GRID_SPACING_DP = 4;
	
	private final GalleryInstance mInstance;
	
	private final ScrollBarGridView mGridView;
	private final GridAdapter mGridAdapter;
	
	private int mGridRowCount;
	private ActionMode mSelectionMode;
	
	public ListUnit(GalleryInstance instance)
	{
		mInstance = instance;
		float density = ResourceUtils.obtainDensity(instance.context);
		int spacing = (int) (GRID_SPACING_DP * density);
		mGridView = new ScrollBarGridView(instance.context);
		mGridView.setClipToPadding(false);
		mGridView.setPadding(spacing, spacing, spacing, spacing);
		mGridView.setHorizontalSpacing(spacing);
		mGridView.setVerticalSpacing(spacing);
		mGridView.setId(android.R.id.list);
		mGridAdapter = new GridAdapter(instance.galleryItems);
		mGridView.setAdapter(mGridAdapter);
		mGridView.setOnScrollListener(new BusyScrollListener(mGridAdapter));
		mGridView.setOnItemClickListener(this);
		updateGridMetrics(instance.context.getResources().getConfiguration());
	}
	
	public AbsListView getListView()
	{
		return mGridView;
	}
	
	public void setListSelection(int position, boolean checkVisibility)
	{
		if (checkVisibility)
		{
			int delta = position - mGridView.getFirstVisiblePosition();
			if (delta >= 0 && delta <= mGridView.getChildCount()) return;
		}
		mGridView.setSelection(position);
	}
	
	public boolean areItemsSelectable()
	{
		return mGridAdapter.getCount() > 0;
	}
	
	public void startSelectionMode()
	{
		mSelectionMode = mGridView.startActionMode(this);
	}
	
	public boolean onApplyWindowPaddings(Rect rect)
	{
		if (C.API_LOLLIPOP)
		{
			float density = ResourceUtils.obtainDensity(mInstance.context);
			int spacing = (int) (GRID_SPACING_DP * density);
			// Add spacing to right to fix padding when navbar is right
			mGridView.setPadding(spacing, rect.top + spacing, rect.right + spacing, rect.bottom + spacing);
			mGridView.applyEdgeEffectShift(rect.top, rect.bottom);
			return true;
		}
		return false;
	}
	
	private static final float GRID_SCALE = 1.1f;
	
	public void switchMode(boolean galleryMode, int duration)
	{
		if (galleryMode)
		{
			mGridView.setVisibility(View.VISIBLE);
			mGridAdapter.activate();
			if (duration > 0)
			{
				mGridView.setAlpha(0f);
				mGridView.setScaleX(GRID_SCALE);
				mGridView.setScaleY(GRID_SCALE);
				mGridView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setListener(null).start();
			}
		}
		else
		{
			if (duration > 0)
			{
				mGridView.setAlpha(1f);
				mGridView.setScaleX(1f);
				mGridView.setScaleY(1f);
				mGridView.animate().alpha(0f).scaleX(GRID_SCALE).scaleY(GRID_SCALE).setDuration(duration)
						.setListener(new AnimationUtils.VisibilityListener(mGridView, View.GONE)).start();
			}
			else mGridView.setVisibility(View.GONE);
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		if (mSelectionMode != null)
		{
			int index = position - mGridView.getFirstVisiblePosition();
			updateGalleryItemChecked(mGridView.getChildAt(index), position);
			mSelectionMode.setTitle(mInstance.context.getString(R.string.text_selected_format,
					mGridView.getCheckedItemCount()));
		}
		else mInstance.callback.navigatePageFromList(position);
	}
	
	private static final int CONTEXT_MENU_DOWNLOAD_FILE = 0;
	private static final int CONTEXT_MENU_SEARCH_IMAGE = 1;
	private static final int CONTEXT_MENU_COPY_LINK = 2;
	private static final int CONTEXT_MENU_GO_TO_POST = 3;
	private static final int CONTEXT_MENU_SHARE_LINK = 4;
	
	public void onCreateContextMenu(ContextMenu menu, AbsListView.AdapterContextMenuInfo info)
	{
		if (mSelectionMode != null) return;
		GalleryItem galleryItem = mGridAdapter.getItem(info.position);
		menu.add(0, CONTEXT_MENU_DOWNLOAD_FILE, 0, R.string.action_download_file);
		if (galleryItem.getDisplayImageUri(mInstance.locator) != null)
		{
			menu.add(0, CONTEXT_MENU_SEARCH_IMAGE, 0, R.string.action_search_image);
		}
		menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
		if (galleryItem.postNumber != null) menu.add(0, CONTEXT_MENU_GO_TO_POST, 0, R.string.action_go_to_post);
		menu.add(0, CONTEXT_MENU_SHARE_LINK, 0, R.string.action_share_link);
	}
	
	public boolean onContextItemSelected(MenuItem item, AbsListView.AdapterContextMenuInfo info)
	{
		GalleryItem galleryItem = mGridAdapter.getItem(info.position);
		switch (item.getItemId())
		{
			case CONTEXT_MENU_DOWNLOAD_FILE:
			{
				mInstance.callback.downloadGalleryItem(galleryItem);
				return true;
			}
			case CONTEXT_MENU_SEARCH_IMAGE:
			{
				NavigationUtils.searchImage(mInstance.context, mInstance.chanName, galleryItem
						.getDisplayImageUri(mInstance.locator));
				return true;
			}
			case CONTEXT_MENU_COPY_LINK:
			{
				StringUtils.copyToClipboard(mInstance.context, galleryItem.getFileUri(mInstance.locator).toString());
				return true;
			}
			case CONTEXT_MENU_GO_TO_POST:
			{
				mInstance.callback.navigatePost(galleryItem, true);
				return true;
			}
			case CONTEXT_MENU_SHARE_LINK:
			{
				NavigationUtils.share(mInstance.context, galleryItem.getFileUri(mInstance.locator).toString());
				return true;
			}
		}
		return false;
	}
	
	public void onConfigurationChanged(Configuration newConfig)
	{
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) updateGridMetrics(newConfig);
	}
	
	private void updateGalleryItemChecked(View view, int position)
	{
		boolean checked = mGridView.isItemChecked(position);
		GridViewHolder holder = (GridViewHolder) view.getTag();
		if (C.API_LOLLIPOP) holder.selectorCheckDrawable.setSelected(checked, true);
		else holder.selectorBorderDrawable.setSelected(checked);
	}
	
	private void updateAllGalleryItemsChecked()
	{
		int startPosition = mGridView.getFirstVisiblePosition();
		int count = mGridView.getChildCount();
		for (int i = 0; i < count; i++) updateGalleryItemChecked(mGridView.getChildAt(i), startPosition + i);
	}
	
	private static final int ACTION_MENU_SELECT_ALL = 0;
	private static final int ACTION_MENU_DOWNLOAD_FILES = 1;

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu)
	{
		mGridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
		mGridView.clearChoices();
		mode.setTitle(mInstance.context.getString(R.string.text_selected_format, 0));
		int selectAllResId = ResourceUtils.getSystemSelectionIcon(mInstance.context, "actionModeSelectAllDrawable",
				"ic_menu_selectall_holo_dark");
		int flags = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
		ActionIconSet set = new ActionIconSet(mInstance.context);
		menu.add(0, ACTION_MENU_SELECT_ALL, 0, R.string.action_select_all)
				.setIcon(selectAllResId).setShowAsAction(flags);
		menu.add(0, ACTION_MENU_DOWNLOAD_FILES, 0, R.string.action_download_files)
				.setIcon(set.getId(R.attr.actionDownload)).setShowAsAction(flags);
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
			case ACTION_MENU_SELECT_ALL:
			{
				for (int i = 0; i < mGridView.getCount(); i++) mGridView.setItemChecked(i, true);
				mSelectionMode.setTitle(mInstance.context.getString(R.string.text_selected_format,
						mGridView.getCount()));
				updateAllGalleryItemsChecked();
				return true;
			}
			case ACTION_MENU_DOWNLOAD_FILES:
			{
				ArrayList<GalleryItem> galleryItems = new ArrayList<>();
				for (int i = 0; i < mGridView.getCount(); i++)
				{
					if (mGridView.isItemChecked(i)) galleryItems.add(mGridAdapter.getItem(i));
				}
				mInstance.callback.downloadGalleryItems(galleryItems);
				mode.finish();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onDestroyActionMode(ActionMode mode)
	{
		mSelectionMode = null;
		mGridView.setChoiceMode(GridView.CHOICE_MODE_NONE);
		updateAllGalleryItemsChecked();
	}
	
	private void updateGridMetrics(Configuration configuration)
	{
		if (mGridView != null)
		{
			if (!C.API_LOLLIPOP)
			{
				// Update top padding on old devices, on new devices paddings will be updated in onApplyWindowPaddings
				TypedArray typedArray = mInstance.context.obtainStyledAttributes
						(new int[] {android.R.attr.actionBarSize});
				int height = typedArray.getDimensionPixelSize(0, 0);
				typedArray.recycle();
				float density = ResourceUtils.obtainDensity(mInstance.context);
				int spacing = (int) (GRID_SPACING_DP * density);
				mGridView.setPadding(spacing, spacing + height, spacing, spacing);
				mGridView.applyEdgeEffectShift(height, 0);
			}
			// Items count in row must fit to this inequality: (widthDp - (i + 1) * GRID_SPACING_DP) / i >= SIZE
			// Where SIZE - size of item in grid, i - items count in row, unknown quantity
			// The solution is: i <= (widthDp + GRID_SPACING_DP) / (SIZE + GRID_SPACING_DP)
			int widthDp = configuration.screenWidthDp;
			int size = ResourceUtils.isTablet(configuration) ? 160 : 100;
			mGridRowCount = (widthDp - GRID_SPACING_DP) / (size + GRID_SPACING_DP);
			mGridView.setNumColumns(mGridRowCount);
			mGridView.post(() ->
			{
				float density = ResourceUtils.obtainDensity(mInstance.context);
				int spaceForRows = mGridView.getWidth() - mGridView.getPaddingLeft() - mGridView.getPaddingRight()
						- (int) ((mGridRowCount - 1) * GRID_SPACING_DP * density);
				int unusedSpace = (spaceForRows - spaceForRows / mGridRowCount * mGridRowCount) / 2;
				if (unusedSpace > 0)
				{
					mGridView.setPadding(mGridView.getPaddingLeft() + unusedSpace, mGridView.getPaddingTop(),
							mGridView.getPaddingRight() + unusedSpace, mGridView.getPaddingBottom());
				}
			});
		}
	}
	
	private static class GridViewHolder
	{
		public AttachmentView thumbnail;
		public TextView attachmentInfo;
		public SelectorBorderDrawable selectorBorderDrawable;
		public SelectorCheckDrawable selectorCheckDrawable;
	}
	
	private class GridAdapter extends BaseAdapter implements BusyScrollListener.Callback
	{
		private final ArrayList<GalleryItem> mGalleryItems;
		
		private boolean mEnabled = false;
		private boolean mBusy = false;
		
		public GridAdapter(ArrayList<GalleryItem> galleryItems)
		{
			mGalleryItems = galleryItems;
		}
		
		public void activate()
		{
			mEnabled = true;
		}
		
		@Override
		public int getCount()
		{
			return mEnabled ? mGalleryItems.size() : 0;
		}
		
		@Override
		public GalleryItem getItem(int position)
		{
			return mGalleryItems.get(position);
		}
		
		@Override
		public long getItemId(int position)
		{
			return 0;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			GridViewHolder holder;
			if (convertView == null)
			{
				holder = new GridViewHolder();
				convertView = LayoutInflater.from(mInstance.context).inflate(R.layout.list_item_attachment,
						parent, false);
				holder.thumbnail = (AttachmentView) convertView.findViewById(R.id.thumbnail);
				holder.thumbnail.setBackgroundColor(0xff333333);
				holder.thumbnail.setCropEnabled(true);
				holder.attachmentInfo = (TextView) convertView.findViewById(R.id.attachment_info);
				holder.attachmentInfo.setBackgroundColor(0xaa111111);
				holder.attachmentInfo.setGravity(Gravity.CENTER);
				holder.attachmentInfo.setSingleLine(true);
				if (C.API_LOLLIPOP)
				{
					holder.selectorCheckDrawable = new SelectorCheckDrawable();
					// noinspection RedundantCast
					((FrameLayout) convertView).setForeground(holder.selectorCheckDrawable);
				}
				else
				{
					holder.selectorBorderDrawable = new SelectorBorderDrawable(mInstance.context);
					// noinspection RedundantCast
					((FrameLayout) convertView).setForeground(holder.selectorBorderDrawable);
				}
				convertView.setTag(holder);
			}
			else holder = (GridViewHolder) convertView.getTag();
			float density = ResourceUtils.obtainDensity(mInstance.context);
			int padding = (int) (GRID_SPACING_DP * density);
			int size = (mGridView.getWidth() - (mGridRowCount - 1) * padding - mGridView.getPaddingLeft()
					- mGridView.getPaddingRight()) / mGridRowCount;
			convertView.getLayoutParams().width = size;
			convertView.getLayoutParams().height = size;
			GalleryItem galleryItem = getItem(position);
			holder.attachmentInfo.setText(StringUtils.getFileExtension(galleryItem.getFileUri(mInstance.locator)
					.toString()).toUpperCase(Locale.getDefault()) + (galleryItem.size > 0 ? " " +
					AttachmentItem.formatSize(galleryItem.size) : ""));
			ImageLoader imageLoader = ImageLoader.getInstance();
			imageLoader.unbind(holder.thumbnail);
			Uri thumbnailUri = galleryItem.getThumbnailUri(mInstance.locator);
			if (thumbnailUri != null)
			{
				CacheManager cacheManager = CacheManager.getInstance();
				if (mBusy)
				{
					holder.thumbnail.setAdditionalOverlay(0, false);
					String key = cacheManager.getCachedFileKey(thumbnailUri);
					if (cacheManager.isThumbnailCachedMemory(key))
					{
						imageLoader.loadImage(thumbnailUri, mInstance.chanName, key, true, holder.thumbnail, 0,
								R.attr.attachmentWarning);
					}
					else holder.thumbnail.setImage(null);
				}
				else displayThumbnail(holder, cacheManager, galleryItem);
			}
			else
			{
				holder.thumbnail.setImage(null);
				holder.thumbnail.setAdditionalOverlay(R.attr.attachmentWarning, false);
			}
			boolean checked = mGridView.isItemChecked(position);
			if (C.API_LOLLIPOP) holder.selectorCheckDrawable.setSelected(checked, false);
			else holder.selectorBorderDrawable.setSelected(checked);
			return convertView;
		}
		
		@Override
		public void setListViewBusy(boolean isBusy, AbsListView listView)
		{
			mBusy = isBusy;
			if (!mBusy)
			{
				CacheManager cacheManager = CacheManager.getInstance();
				int count = listView.getChildCount();
				for (int i = 0; i < count; i++)
				{
					View view = listView.getChildAt(i);
					int position = listView.getPositionForView(view);
					GalleryItem galleryItem = getItem(position);
					if (galleryItem.getThumbnailUri(mInstance.locator) != null)
					{
						displayThumbnail((GridViewHolder) view.getTag(), cacheManager, galleryItem);
					}
				}
			}
		}
		
		public void displayThumbnail(GridViewHolder holder, CacheManager cacheManager, GalleryItem galleryItem)
		{
			ImageLoader imageLoader = ImageLoader.getInstance();
			imageLoader.unbind(holder.thumbnail);
			holder.thumbnail.setAdditionalOverlay(0, true);
			Uri thumbnailUri = galleryItem.getThumbnailUri(mInstance.locator);
			if (thumbnailUri != null)
			{
				String key = cacheManager.getCachedFileKey(thumbnailUri);
				imageLoader.loadImage(thumbnailUri, mInstance.chanName, key, false, holder.thumbnail, 0,
						R.attr.attachmentWarning);
			}
		}
	}
	
	private class ScrollBarGridView extends GridView
	{
		private final Rect mRect = new Rect();
		private final Paint mPaint = new Paint();
		
		public ScrollBarGridView(Context context)
		{
			super(context);
		}
		
		@SuppressWarnings("unused") // Overrides hidden Android API protected method
		@TargetApi(Build.VERSION_CODES.KITKAT)
		protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b)
		{
			int spacing = getPaddingLeft();
			int size = b - t;
			int thickness = (int) (spacing * 2f / 3f + 0.5f);
			int scrollExtent = computeVerticalScrollExtent();
			int scrollRange = computeVerticalScrollRange();
			int length = size * scrollExtent / scrollRange;
			if (size > length)
			{
				int offset = (size - length) * computeVerticalScrollOffset() / (scrollRange - scrollExtent);
				if (length < 2 * thickness) length = 2 * thickness;
				if (offset + length > size) offset = size - length;
				mRect.set(r + spacing - thickness, t + offset, r + spacing, t + offset + length);
				mPaint.setColor(Color.argb(0x7f * (C.API_KITKAT ? scrollBar.getAlpha() : 0xff) / 0xff,
						0xff, 0xff, 0xff));
				canvas.drawRect(mRect, mPaint);
			}
		}
		
		private EdgeEffectHandler mEdgeEffectHandler;
		
		@Override
		public void setOverScrollMode(int mode)
		{
			super.setOverScrollMode(mode);
			if (mode == View.OVER_SCROLL_NEVER) mEdgeEffectHandler = null; else
			{
				EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, null);
				if (edgeEffectHandler != null)
				{
					edgeEffectHandler.setColor(mInstance.actionBarColor);
					mEdgeEffectHandler = edgeEffectHandler;
				}
			}
		}
		
		public void applyEdgeEffectShift(int top, int bottom)
		{
			if (mEdgeEffectHandler != null)
			{
				mEdgeEffectHandler.setShift(true, top);
				mEdgeEffectHandler.setShift(false, bottom);
			}
		}
	}
}