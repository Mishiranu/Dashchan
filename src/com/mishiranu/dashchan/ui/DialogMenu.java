package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.List;

public class DialogMenu {
	private final Context context;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private CharSequence title;

	private enum ViewType {ITEM, MORE, CHECK}

	private static class ListItem {
		public final ViewType viewType;
		public final String title;
		public final boolean checked;
		public final Runnable runnable;

		public ListItem(ViewType viewType, String title, boolean checked, Runnable runnable) {
			this.viewType = viewType;
			this.title = title;
			this.checked = checked;
			this.runnable = runnable;
		}
	}

	public DialogMenu(Context context) {
		this.context = context;
	}

	public DialogMenu setTitle(CharSequence title) {
		this.title = title;
		return this;
	}

	private DialogMenu add(ViewType viewType, String title, boolean checked, Runnable callback) {
		listItems.add(new ListItem(viewType, title, checked, callback));
		return this;
	}

	public DialogMenu add(int titleRes, Runnable runnable) {
		return add(ViewType.ITEM, context.getString(titleRes), false, runnable);
	}

	public DialogMenu add(String title, Runnable runnable) {
		return add(ViewType.ITEM, title, false, runnable);
	}

	public DialogMenu addMore(int titleRes, Runnable runnable) {
		return add(ViewType.MORE, context.getString(titleRes), false, runnable);
	}

	public DialogMenu addCheck(int titleRes, boolean checked, Runnable runnable) {
		return add(ViewType.CHECK, context.getString(titleRes), checked, runnable);
	}

	private RecyclerView getRecyclerView(AlertDialog dialog) {
		FrameLayout custom = dialog.findViewById(android.R.id.custom);
		return (RecyclerView) custom.getChildAt(0);
	}

	private void setAdapter(AlertDialog dialog, RecyclerView recyclerView) {
		recyclerView.setAdapter(new Adapter(dialog.getContext(), dialog::dismiss, new ArrayList<>(listItems)));
	}

	private void updateInternal(AlertDialog dialog, RecyclerView recyclerView) {
		dialog.setTitle(title);
		if (dialog.isShowing()) {
			setAdapter(dialog, recyclerView != null ? recyclerView : getRecyclerView(dialog));
		} else if (recyclerView != null) {
			dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_LONGER_TITLE);
			setAdapter(dialog, recyclerView);
		} else {
			dialog.setOnShowListener(d -> {
				ViewUtils.ALERT_DIALOG_LONGER_TITLE.onShow(d);
				setAdapter(dialog, getRecyclerView(dialog));
			});
		}
	}

	public void update(AlertDialog dialog) {
		updateInternal(dialog, null);
	}

	public AlertDialog create() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		RecyclerView recyclerView = new PaddedRecyclerView(builder.getContext());
		recyclerView.setMotionEventSplittingEnabled(false);
		recyclerView.setVerticalScrollBarEnabled(true);
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			recyclerView.setClipToPadding(false);
			recyclerView.setPadding(0, (int) (8f * density), 0, (int) (8f * density));
		} else {
			recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
					(c, position) -> c.need(true)));
		}
		AlertDialog dialog = builder.setView(recyclerView).create();
		updateInternal(dialog, recyclerView);
		return dialog;
	}

	private static class Adapter extends RecyclerView.Adapter<ViewHolder>
			implements ListViewUtils.ClickCallback<ListItem, ViewHolder> {
		private final Runnable dismiss;
		private final int layoutResId;
		private final List<ListItem> listItems;

		public Adapter(Context context, Runnable dismiss, List<ListItem> listItems) {
			this.dismiss = dismiss;
			layoutResId = ResourceUtils.obtainAlertDialogLayoutResId(context, ResourceUtils.DialogLayout.SIMPLE);
			this.listItems = listItems;
		}

		@Override
		public int getItemViewType(int position) {
			return listItems.get(position).viewType.ordinal();
		}

		@Override
		public int getItemCount() {
			return listItems.size();
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ViewHolder holder = new ViewHolder(parent, layoutResId, ViewType.values()[viewType]);
			ListViewUtils.bind(holder, false, listItems::get, this);
			return holder;
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			ListItem listItem = listItems.get(position);
			holder.textView.setText(listItem.title);
			if (holder.checkBox != null) {
				holder.checkBox.setChecked(listItem.checked);
			}
		}

		@Override
		public boolean onItemClick(ViewHolder holder, int position, ListItem listItem, boolean longClick) {
			dismiss.run();
			ConcurrentUtils.HANDLER.post(listItem.runnable);
			return true;
		}
	}

	private static class ViewHolder extends RecyclerView.ViewHolder {
		public final TextView textView;
		public final CheckBox checkBox;

		public ViewHolder(ViewGroup parent, int layoutResId, ViewType viewType) {
			super(viewType != ViewType.ITEM ? new LinearLayout(parent.getContext())
					: LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false));
			if (viewType != ViewType.ITEM) {
				LinearLayout linearLayout = (LinearLayout) itemView;
				linearLayout.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
						RecyclerView.LayoutParams.WRAP_CONTENT));
				linearLayout.setOrientation(LinearLayout.HORIZONTAL);
				linearLayout.setGravity(Gravity.CENTER_VERTICAL);
				View view = LayoutInflater.from(parent.getContext()).inflate(layoutResId, linearLayout, false);
				linearLayout.addView(view, new LinearLayout.LayoutParams(0,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
				float density = ResourceUtils.obtainDensity(view);
				// Align to checkbox inner padding
				int padding = ViewCompat.getPaddingEnd(view) - (int) (2f * density);
				ViewCompat.setPaddingRelative(view, ViewCompat.getPaddingStart(view),
						view.getPaddingTop(), 0, view.getPaddingBottom());
				int contentSize = (int) (24f * density);
				FrameLayout contentLayout = new FrameLayout(parent.getContext());
				linearLayout.addView(contentLayout, padding + contentSize + padding,
						LinearLayout.LayoutParams.MATCH_PARENT);
				if (viewType == ViewType.MORE) {
					Drawable drawable = null;
					if (C.API_NOUGAT) {
						int[] attrs = {android.R.attr.subMenuArrow};
						TypedArray typedArray = parent.getContext().obtainStyledAttributes(null,
								attrs, android.R.attr.listMenuViewStyle, 0);
						try {
							drawable = typedArray.getDrawable(0);
						} finally {
							typedArray.recycle();
						}
					}
					if (drawable == null) {
						drawable = new SubMenuArrowDrawable(parent);
					}
					ImageView imageView = new ImageView(parent.getContext());
					imageView.setScaleType(ImageView.ScaleType.CENTER);
					imageView.setImageDrawable(drawable);
					contentLayout.addView(imageView, FrameLayout.LayoutParams.WRAP_CONTENT,
							FrameLayout.LayoutParams.WRAP_CONTENT);
					((FrameLayout.LayoutParams) imageView.getLayoutParams()).gravity = Gravity.CENTER;
				}
				if (viewType == ViewType.CHECK) {
					checkBox = new CheckBox(parent.getContext());
					ThemeEngine.applyStyle(checkBox);
					checkBox.setClickable(false);
					checkBox.setFocusable(false);
					contentLayout.addView(checkBox, LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
					((FrameLayout.LayoutParams) checkBox.getLayoutParams()).gravity = Gravity.CENTER;
				} else {
					checkBox = null;
				}
			} else {
				checkBox = null;
			}
			textView = itemView.findViewById(android.R.id.text1);
			ViewUtils.setSelectableItemBackground(itemView);
		}
	}

	private static class SubMenuArrowDrawable extends BaseDrawable {
		private static final int SIZE_DP = 24;

		private final Paint paint = new Paint();
		private final Path path = new Path();
		private final ColorStateList color;
		private final boolean rtl;
		private final int size;

		public SubMenuArrowDrawable(View view) {
			float density = ResourceUtils.obtainDensity(view);
			color = ResourceUtils.getColorStateList(view.getContext(), android.R.attr.textColorSecondary);
			rtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
			size = (int) (SIZE_DP * density);
		}

		@Override
		public int getIntrinsicWidth() {
			return size;
		}

		@Override
		public int getIntrinsicHeight() {
			return size;
		}

		@Override
		public void setBounds(int left, int top, int right, int bottom) {
			super.setBounds(left, top, right, bottom);

			int size = Math.min(right - left, bottom - top);
			float scale = (float) size / SIZE_DP;
			path.rewind();
			if (rtl) {
				path.moveTo(14 * scale, 7 * scale);
				path.rLineTo(-5 * scale, 5 * scale);
				path.rLineTo(5 * scale, 5 * scale);
			} else {
				path.moveTo(10 * scale, 7 * scale);
				path.rLineTo(5 * scale, 5 * scale);
				path.rLineTo(-5 * scale, 5 * scale);
			}
			path.close();
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		protected boolean onStateChange(int[] state) {
			invalidateSelf();
			return true;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			canvas.save();
			int left;
			int top;
			Rect bounds = getBounds();
			int width = bounds.width();
			int height = bounds.height();
			if (width > height) {
				left = bounds.left + (width - height) / 2;
				top = bounds.top;
			} else {
				left = bounds.left;
				top = bounds.top + (height - width) / 2;
			}
			canvas.translate(left, top);
			paint.setColor(color.getColorForState(getState(), color.getDefaultColor()));
			canvas.drawPath(path, paint);
			canvas.restore();
		}
	}
}
