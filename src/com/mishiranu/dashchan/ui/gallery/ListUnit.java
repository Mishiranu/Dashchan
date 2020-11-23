package com.mishiranu.dashchan.ui.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseIntArray;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.SelectorBorderDrawable;
import com.mishiranu.dashchan.graphics.SelectorCheckDrawable;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.SearchImageDialog;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.EdgeEffectHandler;
import com.mishiranu.dashchan.widget.InsetsLayout;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ListUnit implements ActionMode.Callback {
	private static final int GRID_SPACING_DP = 4;

	private final GalleryInstance instance;

	private final PaddedRecyclerView recyclerView;
	private final SparseIntArray selected = new SparseIntArray();

	private ActionMode selectionMode;

	public ListUnit(GalleryInstance instance) {
		this.instance = instance;
		float density = ResourceUtils.obtainDensity(instance.context);
		int spacing = (int) (GRID_SPACING_DP * density);
		recyclerView = new GalleryRecyclerView(instance.context, spacing);
		recyclerView.setId(android.R.id.list);
		recyclerView.setMotionEventSplittingEnabled(false);
		recyclerView.setClipToPadding(false);
		recyclerView.setLayoutManager(new GridLayoutManager(recyclerView.getContext(), 1));
		recyclerView.addItemDecoration(new SpacingItemDecoration(spacing));
		GridAdapter adapter = new GridAdapter(callback, instance.chanName, instance.galleryItems);
		recyclerView.setAdapter(adapter);
		updateGridMetrics(instance.context.getResources().getConfiguration());
	}

	private final GridAdapter.Callback callback = new GridAdapter.Callback() {
		@Override
		public boolean isItemChecked(int position) {
			return selected.indexOfKey(position) >= 0;
		}

		@Override
		public void onItemClick(View view, int position) {
			ListUnit.this.onItemClick(view, position);
		}

		@Override
		public boolean onItemLongClick(int position) {
			return ListUnit.this.onItemLongClick(position);
		}
	};

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

	private GridAdapter getAdapter() {
		return (GridAdapter) recyclerView.getAdapter();
	}

	public int[] getSelectedPositions() {
		if (selectionMode != null) {
			SparseIntArray array = new SparseIntArray();
			int count = getAdapter().getItemCount();
			for (int i = 0; i < count; i++) {
				if (callback.isItemChecked(i)) {
					array.put(i, i);
				}
			}
			int[] result = new int[array.size()];
			for (int i = 0; i < array.size(); i++) {
				result[i] = array.keyAt(i);
			}
			return result;
		}
		return null;
	}

	public void scrollListToPosition(int position, boolean checkVisibility) {
		GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
		if (checkVisibility) {
			if (position >= layoutManager.findFirstCompletelyVisibleItemPosition() &&
					position <= layoutManager.findLastCompletelyVisibleItemPosition()) {
				return;
			}
		}
		layoutManager.scrollToPositionWithOffset(position, 0);
	}

	public boolean areItemsSelectable() {
		return getAdapter().getItemCount() > 0;
	}

	public void startSelectionMode(int[] selected) {
		this.selected.clear();
		selectionMode = recyclerView.startActionMode(this);
		if (selectionMode != null && selected != null) {
			int selectedCount = 0;
			int count = getAdapter().getItemCount();
			for (int position : selected) {
				if (position >= 0 && position < count) {
					this.selected.append(position, position);
					selectedCount++;
				}
			}
			updateAllGalleryItemsChecked();
			selectionMode.setTitle(ResourceUtils.getColonString(instance.context.getResources(),
					R.string.selected, selectedCount));
		}
	}

	public boolean onApplyWindowInsets(InsetsLayout.Insets insets) {
		if (C.API_LOLLIPOP) {
			int top = insets.top + (C.API_R ? getActionBarHeight() : 0);
			ViewUtils.setNewMargin(recyclerView, insets.left, null, insets.right, null);
			ViewUtils.setNewPadding(recyclerView, null, top, null, insets.bottom);
			return true;
		}
		return false;
	}

	private static final float GRID_SCALE = 1.1f;

	public void switchMode(boolean galleryMode, int duration) {
		if (galleryMode) {
			recyclerView.setVisibility(View.VISIBLE);
			getAdapter().activate();
			if (duration > 0) {
				recyclerView.setAlpha(0f);
				recyclerView.setScaleX(GRID_SCALE);
				recyclerView.setScaleY(GRID_SCALE);
				recyclerView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setListener(null).start();
			}
		} else {
			if (duration > 0) {
				recyclerView.setAlpha(1f);
				recyclerView.setScaleX(1f);
				recyclerView.setScaleY(1f);
				recyclerView.animate().alpha(0f).scaleX(GRID_SCALE).scaleY(GRID_SCALE).setDuration(duration)
						.setListener(new AnimationUtils.VisibilityListener(recyclerView, View.GONE)).start();
			} else {
				recyclerView.setVisibility(View.GONE);
			}
		}
	}

	private void onItemClick(View view, int position) {
		if (selectionMode != null) {
			int index = selected.indexOfKey(position);
			if (index >= 0) {
				selected.removeAt(index);
			} else {
				selected.put(position, position);
			}
			updateGalleryItemChecked(view, position);
			selectionMode.setTitle(ResourceUtils.getColonString(instance.context.getResources(),
					R.string.selected, selected.size()));
		} else {
			instance.callback.navigatePageFromList(position);
		}
	}

	private boolean onItemLongClick(int position) {
		if (selectionMode != null) {
			return false;
		}
		showItemMenu(instance.callback.getChildFragmentManager(), instance.chanName,
				getAdapter().getItem(position), instance.callback.isAllowNavigatePostManually(false));
		return true;
	}

	private static void showItemMenu(FragmentManager fragmentManager,
			String chanName, GalleryItem galleryItem, boolean allowNavigatePostManually) {
		new InstanceDialog(fragmentManager, null, provider -> createItemMenu(provider,
				chanName, galleryItem, allowNavigatePostManually));
	}

	private static AlertDialog createItemMenu(InstanceDialog.Provider provider,
			String chanName, GalleryItem galleryItem, boolean allowNavigatePostManually) {
		GalleryInstance.Callback callback = GalleryInstance.getCallback(provider);
		Context context = callback.getWindow().getContext();
		Chan chan = Chan.get(chanName);
		DialogMenu dialogMenu = new DialogMenu(context);
		dialogMenu.setTitle(!StringUtils.isEmpty(galleryItem.originalName)
				? galleryItem.originalName : galleryItem.getFileName(chan));
		dialogMenu.add(R.string.download_file, () -> callback.downloadGalleryItem(galleryItem));
		if (galleryItem.getDisplayImageUri(chan) != null) {
			dialogMenu.add(R.string.search_image, () -> new SearchImageDialog(chanName,
					galleryItem.getDisplayImageUri(chan)).show(provider.getFragmentManager(), null));
		}
		dialogMenu.add(R.string.copy_link, () -> StringUtils.copyToClipboard(context,
				galleryItem.getFileUri(chan).toString()));
		if (allowNavigatePostManually && galleryItem.postNumber != null) {
			dialogMenu.add(R.string.go_to_post, () -> callback.navigatePost(galleryItem, true, true));
		}
		dialogMenu.add(R.string.share_link, () -> NavigationUtils.shareLink(context, null,
				galleryItem.getFileUri(chan)));
		return dialogMenu.create();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
			updateGridMetrics(newConfig);
		}
	}

	private void updateGalleryItemChecked(View view, int position) {
		boolean checked = callback.isItemChecked(position);
		GridAdapter.ViewHolder holder = (GridAdapter.ViewHolder) recyclerView.getChildViewHolder(view);
		if (C.API_LOLLIPOP) {
			holder.selectorCheckDrawable.setSelected(checked, true);
		} else {
			holder.selectorBorderDrawable.setSelected(checked);
		}
	}

	private void updateAllGalleryItemsChecked() {
		int childCount = recyclerView.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View view = recyclerView.getChildAt(i);
			int position = recyclerView.getChildAdapterPosition(view);
			updateGalleryItemChecked(view, position);
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		selected.clear();
		mode.setTitle(ResourceUtils.getColonString(instance.context.getResources(), R.string.selected, 0));
		menu.add(0, R.id.menu_select_all, 0, R.string.select_all)
				.setIcon(ResourceUtils.getActionBarIcon(instance.context, R.attr.iconActionSelectAll))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_download, 0, R.string.download_files)
				.setIcon(ResourceUtils.getActionBarIcon(instance.context, R.attr.iconActionDownload))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_select_all: {
				int count = getAdapter().getItemCount();
				for (int i = 0; i < count; i++) {
					selected.put(i, i);
				}
				selectionMode.setTitle(ResourceUtils.getColonString(instance.context.getResources(),
						R.string.selected, count));
				updateAllGalleryItemsChecked();
				return true;
			}
			case R.id.menu_download: {
				ArrayList<GalleryItem> galleryItems = new ArrayList<>();
				GridAdapter adapter = getAdapter();
				for (int i = 0; i < adapter.getItemCount(); i++) {
					if (callback.isItemChecked(i)) {
						galleryItems.add(adapter.getItem(i));
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
		selected.clear();
		updateAllGalleryItemsChecked();
	}

	private int getActionBarHeight() {
		TypedArray typedArray = instance.context.obtainStyledAttributes(new int[] {android.R.attr.actionBarSize});
		try {
			return typedArray.getDimensionPixelSize(0, 0);
		} finally {
			typedArray.recycle();
		}
	}

	private void updateGridMetrics(Configuration configuration) {
		// Items count in row must fit to this inequality: (widthDp - (i + 1) * GRID_SPACING_DP) / i >= SIZE
		// Where SIZE - size of item in grid, i - items count in row, unknown quantity
		// The solution is: i <= (widthDp + GRID_SPACING_DP) / (SIZE + GRID_SPACING_DP)
		int widthDp = configuration.screenWidthDp;
		int size = ResourceUtils.isTablet(configuration) ? 160 : 100;
		int spanCount = (widthDp - GRID_SPACING_DP) / (size + GRID_SPACING_DP);
		((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(spanCount);
		if (!C.API_LOLLIPOP) {
			// Update top padding on old devices, on new devices paddings will be updated in onApplyWindowPaddings
			recyclerView.setPadding(0, getActionBarHeight(), 0, 0);
		}
	}

	private static class GalleryRecyclerView extends PaddedRecyclerView {
		private final int spacing;

		private final Rect rect = new Rect();
		private final Paint paint = new Paint();

		public GalleryRecyclerView(Context context, int spacing) {
			super(context);
			this.spacing = spacing;
			setVerticalScrollBarEnabled(true);
		}

		@Override
		protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b) {
			int spacing = this.spacing;
			int thickness = (int) (spacing * 2f / 3f + 0.5f);
			Rect rect = this.rect;
			if (l == 0) {
				rect.left = 0;
				rect.right = thickness;
			} else if (r == getWidth()) {
				rect.left = r - thickness;
				rect.right = r;
			} else {
				return;
			}
			if (b - t == getHeight()) {
				t += getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
				b -= getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
			}
			t += spacing;
			b -= spacing;
			int range = computeVerticalScrollRange();
			if (range > 0) {
				int height = b - t;
				int extent = computeVerticalScrollExtent();
				int length = (int) (height * ((float) extent / range) + 0.5f);
				length = Math.max(length, 2 * spacing);
				int offset = (int) ((height - length) * ((float) computeVerticalScrollOffset() /
						(range - extent)) + 0.5f);
				if (length > 0) {
					rect.top = t + offset;
					rect.bottom = t + offset + length;
					paint.setColor(Color.argb(0x7f * (C.API_KITKAT ? scrollBar.getAlpha() : 0xff) / 0xff,
							0xff, 0xff, 0xff));
					canvas.drawRect(rect, paint);
				}
			}
		}
	}

	private static class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {
		public interface Callback extends ListViewUtils.ClickCallback<Void, ViewHolder> {
			boolean isItemChecked(int position);
			void onItemClick(View view, int position);
			boolean onItemLongClick(int position);

			@Override
			default boolean onItemClick(ViewHolder holder, int position, Void nothing, boolean longClick) {
				if (longClick) {
					return onItemLongClick(position);
				} else {
					onItemClick(holder.itemView, position);
					return true;
				}
			}
		}

		private static class ViewHolder extends RecyclerView.ViewHolder {
			public final AttachmentView thumbnail;
			public final TextView attachmentInfo;
			public final SelectorBorderDrawable selectorBorderDrawable;
			public final SelectorCheckDrawable selectorCheckDrawable;

			public ViewHolder(ViewGroup parent, Callback callback) {
				super(new FrameLayout(parent.getContext()) {
					@Override
					protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
						super.onMeasure(widthMeasureSpec, widthMeasureSpec);
					}
				});

				FrameLayout layout = (FrameLayout) itemView;
				layout.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
						RecyclerView.LayoutParams.WRAP_CONTENT));
				LayoutInflater.from(layout.getContext()).inflate(R.layout.list_item_attachment, layout);
				FrameLayout child = (FrameLayout) layout.getChildAt(0);
				child.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
				child.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
				thumbnail = itemView.findViewById(R.id.thumbnail);
				thumbnail.setBackgroundColor(0xff333333);
				thumbnail.setCropEnabled(true);
				attachmentInfo = itemView.findViewById(R.id.attachment_info);
				attachmentInfo.setBackgroundColor(0xaa111111);
				attachmentInfo.setGravity(Gravity.CENTER);
				attachmentInfo.setSingleLine(true);
				if (C.API_LOLLIPOP) {
					attachmentInfo.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
				}
				ListViewUtils.bind(this, itemView.findViewById(R.id.attachment_click), true, null, callback);
				if (C.API_LOLLIPOP) {
					selectorBorderDrawable = null;
					selectorCheckDrawable = new SelectorCheckDrawable();
					child.setForeground(selectorCheckDrawable);
				} else {
					selectorBorderDrawable = new SelectorBorderDrawable(parent.getContext());
					selectorCheckDrawable = null;
					child.setForeground(selectorBorderDrawable);
				}
			}
		}

		private final Callback callback;
		private final String chanName;
		private final List<GalleryItem> galleryItems;

		private boolean enabled = false;

		public GridAdapter(Callback callback, String chanName, List<GalleryItem> galleryItems) {
			this.callback = callback;
			this.chanName = chanName;
			this.galleryItems = galleryItems;
		}

		public void activate() {
			enabled = true;
		}

		@Override
		public int getItemCount() {
			return enabled ? galleryItems.size() : 0;
		}

		public GalleryItem getItem(int position) {
			return galleryItems.get(position);
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new ViewHolder(parent, callback);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			GalleryItem galleryItem = getItem(position);
			Chan chan = Chan.get(chanName);
			holder.attachmentInfo.setText(StringUtils
					.getFileExtension(galleryItem.getFileName(chan)).toUpperCase(Locale.getDefault()) +
					(galleryItem.size > 0 ? " " + StringUtils.formatFileSize(galleryItem.size, true) : ""));
			Uri thumbnailUri = galleryItem.getThumbnailUri(chan);
			if (thumbnailUri != null) {
				CacheManager cacheManager = CacheManager.getInstance();
				String key = cacheManager.getCachedFileKey(thumbnailUri);
				holder.thumbnail.resetImage(key, AttachmentView.Overlay.NONE);
				ImageLoader.getInstance().loadImage(chan, thumbnailUri, key, false, holder.thumbnail);
			} else {
				holder.thumbnail.resetImage(null, AttachmentView.Overlay.WARNING);
				ImageLoader.getInstance().cancel(holder.thumbnail);
			}
			boolean checked = callback.isItemChecked(position);
			if (C.API_LOLLIPOP) {
				holder.selectorCheckDrawable.setSelected(checked, false);
			} else {
				holder.selectorBorderDrawable.setSelected(checked);
			}
		}
	}

	private static class SpacingItemDecoration extends RecyclerView.ItemDecoration {
		private final int spacing;

		public SpacingItemDecoration(int spacing) {
			this.spacing = spacing;
		}

		@Override
		public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
				@NonNull RecyclerView.State state) {
			int position = parent.getChildAdapterPosition(view);
			int column = ((GridLayoutManager.LayoutParams) view.getLayoutParams()).getSpanIndex();
			int columns = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
			int left;
			int right;
			if (columns >= 2) {
				int total = (columns + 1) * spacing;
				float average = (float) total / columns;
				left = (int) AnimationUtils.lerp(spacing, average - spacing, (float) column / (columns - 1));
				right = (int) average - left;
			} else {
				left = spacing;
				right = spacing;
			}
			boolean firstRow = position - column == 0;
			outRect.set(left, firstRow ? spacing : 0, right, spacing);
		}
	}
}
