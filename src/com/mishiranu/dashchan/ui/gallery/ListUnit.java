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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.ActionMode;
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
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.EdgeEffectHandler;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

public class ListUnit implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
		ActionMode.Callback, ImageLoader.Observer {
	private static final int GRID_SPACING_DP = 4;

	private final GalleryInstance instance;

	private final ScrollBarGridView gridView;
	private final GridAdapter gridAdapter;

	private int gridRowCount;
	private ActionMode selectionMode;

	private long scrollStateChanged;
	private long restoredScrollPositionChanged;
	private int restoredScrollPosition;

	public ListUnit(GalleryInstance instance) {
		this.instance = instance;
		float density = ResourceUtils.obtainDensity(instance.context);
		int spacing = (int) (GRID_SPACING_DP * density);
		gridView = new ScrollBarGridView(instance.context);
		gridView.setClipToPadding(false);
		gridView.setPadding(spacing, spacing, spacing, spacing);
		gridView.setHorizontalSpacing(spacing);
		gridView.setVerticalSpacing(spacing);
		gridView.setId(android.R.id.list);
		gridAdapter = new GridAdapter(instance.galleryItems);
		gridView.setAdapter(gridAdapter);
		ScrollListenerComposite scrollListenerComposite = new ScrollListenerComposite();
		scrollListenerComposite.add(new BusyScrollListener(gridAdapter));
		scrollListenerComposite.add(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				scrollStateChanged = System.currentTimeMillis();
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
		});
		gridView.setOnScrollListener(scrollListenerComposite);
		gridView.setOnItemClickListener(this);
		gridView.setOnItemLongClickListener(this);
		updateGridMetrics(true, instance.context.getResources().getConfiguration());
		ImageLoader.getInstance().observable().register(this);
	}

	public void onFinish() {
		ImageLoader.getInstance().observable().unregister(this);
	}

	public AbsListView getListView() {
		return gridView;
	}

	public void setListSelection(int position, boolean checkVisibility) {
		if (checkVisibility) {
			int delta = position - gridView.getFirstVisiblePosition();
			if (delta >= 0 && delta <= gridView.getChildCount()) {
				return;
			}
		}
		gridView.setSelection(position);
		restoredScrollPosition = position;
		restoredScrollPositionChanged = System.currentTimeMillis();
	}

	public boolean areItemsSelectable() {
		return gridAdapter.getCount() > 0;
	}

	public void startSelectionMode() {
		selectionMode = gridView.startActionMode(this);
	}

	public boolean onApplyWindowPaddings(Rect rect) {
		if (C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(instance.context);
			int spacing = (int) (GRID_SPACING_DP * density);
			// Add spacing to right to fix padding when navbar is right
			gridView.setPadding(spacing, rect.top + spacing, rect.right + spacing, rect.bottom + spacing);
			gridView.applyEdgeEffectShift(rect.top, rect.bottom);
			return true;
		}
		return false;
	}

	private static final float GRID_SCALE = 1.1f;

	public void switchMode(boolean galleryMode, int duration) {
		if (galleryMode) {
			gridView.setVisibility(View.VISIBLE);
			gridAdapter.activate();
			if (duration > 0) {
				gridView.setAlpha(0f);
				gridView.setScaleX(GRID_SCALE);
				gridView.setScaleY(GRID_SCALE);
				gridView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setListener(null).start();
			}
		} else {
			if (duration > 0) {
				gridView.setAlpha(1f);
				gridView.setScaleX(1f);
				gridView.setScaleY(1f);
				gridView.animate().alpha(0f).scaleX(GRID_SCALE).scaleY(GRID_SCALE).setDuration(duration)
						.setListener(new AnimationUtils.VisibilityListener(gridView, View.GONE)).start();
			} else {
				gridView.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (selectionMode != null) {
			int index = position - gridView.getFirstVisiblePosition();
			updateGalleryItemChecked(gridView.getChildAt(index), position);
			selectionMode.setTitle(instance.context.getString(R.string.text_selected_format,
					gridView.getCheckedItemCount()));
		} else {
			instance.callback.navigatePageFromList(position);
		}
	}

	private static final int MENU_DOWNLOAD_FILE = 0;
	private static final int MENU_SEARCH_IMAGE = 1;
	private static final int MENU_COPY_LINK = 2;
	private static final int MENU_GO_TO_POST = 3;
	private static final int MENU_SHARE_LINK = 4;

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id0) {
		if (selectionMode != null) {
			return false;
		}
		GalleryItem galleryItem = gridAdapter.getItem(position);
		DialogMenu dialogMenu = new DialogMenu(instance.context, (context, id, extra) -> {
			switch (id) {
				case MENU_DOWNLOAD_FILE: {
					instance.callback.downloadGalleryItem(galleryItem);
					break;
				}
				case MENU_SEARCH_IMAGE: {
					NavigationUtils.searchImage(instance.context, instance.chanName, galleryItem
							.getDisplayImageUri(instance.locator));
					break;
				}
				case MENU_COPY_LINK: {
					StringUtils.copyToClipboard(instance.context, galleryItem.getFileUri(instance.locator)
							.toString());
					break;
				}
				case MENU_GO_TO_POST: {
					instance.callback.navigatePost(galleryItem, true);
					break;
				}
				case MENU_SHARE_LINK: {
					NavigationUtils.shareLink(instance.context, null, galleryItem.getFileUri(instance.locator));
					break;
				}
			}

		});

		dialogMenu.setTitle(galleryItem.originalName != null ? galleryItem.originalName
				: galleryItem.getFileName(instance.locator), true);
		dialogMenu.addItem(MENU_DOWNLOAD_FILE, R.string.action_download_file);
		if (galleryItem.getDisplayImageUri(instance.locator) != null) {
			dialogMenu.addItem(MENU_SEARCH_IMAGE, R.string.action_search_image);
		}
		dialogMenu.addItem(MENU_COPY_LINK, R.string.action_copy_link);
		if (instance.callback.isAllowNavigatePost(false) && galleryItem.postNumber != null) {
			dialogMenu.addItem(MENU_GO_TO_POST, R.string.action_go_to_post);
		}
		dialogMenu.addItem(MENU_SHARE_LINK, R.string.action_share_link);
		dialogMenu.show();
		return true;
	}

	public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
			updateGridMetrics(false, newConfig);
		}
	}

	private void updateGalleryItemChecked(View view, int position) {
		boolean checked = gridView.isItemChecked(position);
		GridViewHolder holder = (GridViewHolder) view.getTag();
		if (C.API_LOLLIPOP) {
			holder.selectorCheckDrawable.setSelected(checked, true);
		} else {
			holder.selectorBorderDrawable.setSelected(checked);
		}
	}

	private void updateAllGalleryItemsChecked() {
		int startPosition = gridView.getFirstVisiblePosition();
		int count = gridView.getChildCount();
		for (int i = 0; i < count; i++) {
			updateGalleryItemChecked(gridView.getChildAt(i), startPosition + i);
		}
	}

	private static final int ACTION_MENU_SELECT_ALL = 0;
	private static final int ACTION_MENU_DOWNLOAD_FILES = 1;

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		gridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
		gridView.clearChoices();
		mode.setTitle(instance.context.getString(R.string.text_selected_format, 0));
		int selectAllResId = ResourceUtils.getSystemSelectionIcon(instance.context, "actionModeSelectAllDrawable",
				"ic_menu_selectall_holo_dark");
		int flags = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
		ActionIconSet set = new ActionIconSet(instance.context);
		menu.add(0, ACTION_MENU_SELECT_ALL, 0, R.string.action_select_all)
				.setIcon(selectAllResId).setShowAsAction(flags);
		menu.add(0, ACTION_MENU_DOWNLOAD_FILES, 0, R.string.action_download_files)
				.setIcon(set.getId(R.attr.actionDownload)).setShowAsAction(flags);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case ACTION_MENU_SELECT_ALL: {
				for (int i = 0; i < gridView.getCount(); i++) {
					gridView.setItemChecked(i, true);
				}
				selectionMode.setTitle(instance.context.getString(R.string.text_selected_format,
						gridView.getCount()));
				updateAllGalleryItemsChecked();
				return true;
			}
			case ACTION_MENU_DOWNLOAD_FILES: {
				ArrayList<GalleryItem> galleryItems = new ArrayList<>();
				for (int i = 0; i < gridView.getCount(); i++) {
					if (gridView.isItemChecked(i)) {
						galleryItems.add(gridAdapter.getItem(i));
					}
				}
				instance.callback.downloadGalleryItems(galleryItems);
				mode.finish();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		selectionMode = null;
		gridView.setChoiceMode(GridView.CHOICE_MODE_NONE);
		updateAllGalleryItemsChecked();
	}

	private void updateGridMetrics(boolean init, Configuration configuration) {
		int position;
		// Don't allow list to move randomly after multiple screen rotations
		if (restoredScrollPositionChanged >= scrollStateChanged) {
			position = restoredScrollPosition;
		} else {
			position = gridView.getFirstVisiblePosition();
			restoredScrollPosition = position;
			restoredScrollPositionChanged = System.currentTimeMillis();
		}
		if (!C.API_LOLLIPOP) {
			// Update top padding on old devices, on new devices paddings will be updated in onApplyWindowPaddings
			TypedArray typedArray = instance.context.obtainStyledAttributes
					(new int[] {android.R.attr.actionBarSize});
			int height = typedArray.getDimensionPixelSize(0, 0);
			typedArray.recycle();
			float density = ResourceUtils.obtainDensity(instance.context);
			int spacing = (int) (GRID_SPACING_DP * density);
			gridView.setPadding(spacing, spacing + height, spacing, spacing);
			gridView.applyEdgeEffectShift(height, 0);
		}
		// Items count in row must fit to this inequality: (widthDp - (i + 1) * GRID_SPACING_DP) / i >= SIZE
		// Where SIZE - size of item in grid, i - items count in row, unknown quantity
		// The solution is: i <= (widthDp + GRID_SPACING_DP) / (SIZE + GRID_SPACING_DP)
		int widthDp = configuration.screenWidthDp;
		int size = ResourceUtils.isTablet(configuration) ? 160 : 100;
		gridRowCount = (widthDp - GRID_SPACING_DP) / (size + GRID_SPACING_DP);
		gridView.setNumColumns(gridRowCount);
		if (!init) {
			gridView.post(() -> {
				float density = ResourceUtils.obtainDensity(instance.context);
				int spaceForRows = gridView.getWidth() - gridView.getPaddingLeft() - gridView.getPaddingRight()
						- (int) ((gridRowCount - 1) * GRID_SPACING_DP * density);
				int unusedSpace = (spaceForRows - spaceForRows / gridRowCount * gridRowCount) / 2;
				if (unusedSpace > 0) {
					gridView.setPadding(gridView.getPaddingLeft() + unusedSpace, gridView.getPaddingTop(),
							gridView.getPaddingRight() + unusedSpace, gridView.getPaddingBottom());
				}
				gridView.post(new Runnable() {
					@Override
					public void run() {
						int firstVisiblePosition = gridView.getFirstVisiblePosition();
						int count = gridView.getChildCount();
						if (firstVisiblePosition > position || firstVisiblePosition + count <= position) {
							gridView.setSelection(position);
							gridView.post(this);
						}
					}
				});
			});
		}
	}

	private static class GridViewHolder {
		public AttachmentView thumbnail;
		public TextView attachmentInfo;
		public SelectorBorderDrawable selectorBorderDrawable;
		public SelectorCheckDrawable selectorCheckDrawable;
	}

	private class GridAdapter extends BaseAdapter implements BusyScrollListener.Callback {
		private final ArrayList<GalleryItem> galleryItems;

		private boolean enabled = false;
		private boolean busy = false;

		public GridAdapter(ArrayList<GalleryItem> galleryItems) {
			this.galleryItems = galleryItems;
		}

		public void activate() {
			enabled = true;
		}

		@Override
		public int getCount() {
			return enabled ? galleryItems.size() : 0;
		}

		@Override
		public GalleryItem getItem(int position) {
			return galleryItems.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			GridViewHolder holder;
			if (convertView == null) {
				holder = new GridViewHolder();
				convertView = LayoutInflater.from(instance.context).inflate(R.layout.list_item_attachment,
						parent, false);
				holder.thumbnail = (AttachmentView) convertView.findViewById(R.id.thumbnail);
				holder.thumbnail.setBackgroundColor(0xff333333);
				holder.thumbnail.setCropEnabled(true);
				holder.attachmentInfo = (TextView) convertView.findViewById(R.id.attachment_info);
				holder.attachmentInfo.setBackgroundColor(0xaa111111);
				holder.attachmentInfo.setGravity(Gravity.CENTER);
				holder.attachmentInfo.setSingleLine(true);
				if (C.API_LOLLIPOP) {
					holder.selectorCheckDrawable = new SelectorCheckDrawable();
					// noinspection RedundantCast
					((FrameLayout) convertView).setForeground(holder.selectorCheckDrawable);
				} else {
					holder.selectorBorderDrawable = new SelectorBorderDrawable(instance.context);
					// noinspection RedundantCast
					((FrameLayout) convertView).setForeground(holder.selectorBorderDrawable);
				}
				convertView.setTag(holder);
			} else {
				holder = (GridViewHolder) convertView.getTag();
			}
			float density = ResourceUtils.obtainDensity(instance.context);
			int padding = (int) (GRID_SPACING_DP * density);
			int size = (gridView.getWidth() - (gridRowCount - 1) * padding - gridView.getPaddingLeft()
					- gridView.getPaddingRight()) / gridRowCount;
			convertView.getLayoutParams().width = size;
			convertView.getLayoutParams().height = size;
			GalleryItem galleryItem = getItem(position);
			holder.attachmentInfo.setText(StringUtils.getFileExtension(galleryItem
					.getFileUri(instance.locator).getPath()).toUpperCase(Locale.getDefault()) +
					(galleryItem.size > 0 ? " " + AttachmentItem.formatSize(galleryItem.size) : ""));
			Uri thumbnailUri = galleryItem.getThumbnailUri(instance.locator);
			if (thumbnailUri != null) {
				CacheManager cacheManager = CacheManager.getInstance();
				String key = cacheManager.getCachedFileKey(thumbnailUri);
				holder.thumbnail.resetImage(key, AttachmentView.Overlay.NONE);
				loadImage(holder.thumbnail, thumbnailUri, key);
			} else {
				holder.thumbnail.resetImage(null, AttachmentView.Overlay.WARNING);
			}
			boolean checked = gridView.isItemChecked(position);
			if (C.API_LOLLIPOP) {
				holder.selectorCheckDrawable.setSelected(checked, false);
			} else {
				holder.selectorBorderDrawable.setSelected(checked);
			}
			return convertView;
		}

		@Override
		public void setListViewBusy(boolean isBusy, AbsListView listView) {
			busy = isBusy;
			if (!busy) {
				CacheManager cacheManager = CacheManager.getInstance();
				int count = listView.getChildCount();
				for (int i = 0; i < count; i++) {
					View view = listView.getChildAt(i);
					int position = listView.getPositionForView(view);
					GalleryItem galleryItem = getItem(position);
					Uri thumbnailUri = galleryItem.getThumbnailUri(instance.locator);
					if (thumbnailUri != null) {
						GridViewHolder holder = (GridViewHolder) view.getTag();
						String key = cacheManager.getCachedFileKey(thumbnailUri);
						holder.thumbnail.resetImage(key, AttachmentView.Overlay.NONE);
						loadImage(holder.thumbnail, thumbnailUri, key);
					}
				}
			}
		}

		private void loadImage(AttachmentView view, Uri thumbnailUri, String key) {
			ImageLoader.BitmapResult result = ImageLoader.getInstance().loadImage(thumbnailUri, instance.chanName,
					key, null, false);
			if (result != null) {
				view.handleLoadedImage(key, result.bitmap, result.error, true);
			}
		}
	}

	@Override
	public void onImageLoadComplete(String key, Bitmap bitmap, boolean error) {
		for (int i = 0; i < gridView.getChildCount(); i++) {
			GridViewHolder holder = (GridViewHolder) gridView.getChildAt(i).getTag();
			holder.thumbnail.handleLoadedImage(key, bitmap, error, false);
		}
	}

	private class ScrollBarGridView extends GridView {
		private final Rect rect = new Rect();
		private final Paint paint = new Paint();

		public ScrollBarGridView(Context context) {
			super(context);
		}

		@SuppressWarnings("unused") // Overrides hidden Android API protected method
		@TargetApi(Build.VERSION_CODES.KITKAT)
		protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b) {
			int spacing = getPaddingLeft();
			int size = b - t;
			int thickness = (int) (spacing * 2f / 3f + 0.5f);
			int scrollExtent = computeVerticalScrollExtent();
			int scrollRange = computeVerticalScrollRange();
			int length = size * scrollExtent / scrollRange;
			if (size > length) {
				int offset = (size - length) * computeVerticalScrollOffset() / (scrollRange - scrollExtent);
				if (length < 2 * thickness) {
					length = 2 * thickness;
				}
				if (offset + length > size) {
					offset = size - length;
				}
				rect.set(r + spacing - thickness, t + offset, r + spacing, t + offset + length);
				paint.setColor(Color.argb(0x7f * (C.API_KITKAT ? scrollBar.getAlpha() : 0xff) / 0xff,
						0xff, 0xff, 0xff));
				canvas.drawRect(rect, paint);
			}
		}

		private EdgeEffectHandler edgeEffectHandler;

		@Override
		public void setOverScrollMode(int mode) {
			super.setOverScrollMode(mode);
			if (mode == View.OVER_SCROLL_NEVER) {
				edgeEffectHandler = null;
			} else {
				EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, null);
				if (edgeEffectHandler != null) {
					edgeEffectHandler.setColor(instance.actionBarColor);
					this.edgeEffectHandler = edgeEffectHandler;
				}
			}
		}

		public void applyEdgeEffectShift(int top, int bottom) {
			if (edgeEffectHandler != null) {
				edgeEffectHandler.setShift(true, top);
				edgeEffectHandler.setShift(false, bottom);
			}
		}
	}
}