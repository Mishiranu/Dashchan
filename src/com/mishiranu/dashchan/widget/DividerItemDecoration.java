package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.util.ResourceUtils;

public class DividerItemDecoration extends RecyclerView.ItemDecoration {
	public static final class Configuration {
		private boolean need;
		private int start;
		private int end;
		private int top;
		private int bottom;
		private int height;
		private boolean translate = true;

		public Configuration need(boolean need) {
			this.need = need;
			return this;
		}

		public Configuration horizontal(int start, int end) {
			this.start = start;
			this.end = end;
			return this;
		}

		public Configuration vertical(int top, int bottom) {
			this.top = top;
			this.bottom = bottom;
			return this;
		}

		public Configuration height(int height) {
			this.height = height;
			return this;
		}

		public Configuration translate(boolean translate) {
			this.translate = translate;
			return this;
		}

		private int getHeight(Drawable drawable) {
			return Math.max(drawable.getIntrinsicWidth(), height);
		}

		private int getTotalHeight(Drawable drawable) {
			return top + getHeight(drawable) + bottom;
		}
	}

	public interface Callback {
		Configuration configure(Configuration configuration, int position);
	}

	public interface AboveCallback {
		boolean shouldPlaceAbove(int position);
	}

	private final Callback callback;
	private final Drawable drawable;
	private final Configuration configuration = new Configuration();
	private final Rect rect = new Rect();

	private AboveCallback aboveCallback;

	public DividerItemDecoration(Context context, Callback callback) {
		this.callback = callback;
		drawable = ResourceUtils.getDrawable(context, android.R.attr.listDivider, 0);
	}

	public void setAboveCallback(AboveCallback aboveCallback) {
		this.aboveCallback = aboveCallback;
	}

	private void drawDivider(Canvas canvas, int top, int left, int right, int height, View view, boolean translate) {
		if (translate) {
			top += view.getTranslationY();
		}
		drawable.setBounds(left, top, right, top + height);
		drawable.draw(canvas);
	}

	@Override
	public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		int childCount = parent.getChildCount();
		int left = parent.getPaddingLeft();
		int right = parent.getWidth() - parent.getPaddingRight();
		boolean rtl = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL;
		for (int i = 0; i < childCount; i++) {
			View view = parent.getChildAt(i);
			int position = parent.getChildAdapterPosition(view);
			if (position >= 0) {
				callback.configure(configuration, position);
				if (configuration.need) {
					int currentLeft = left + (rtl ? configuration.end : configuration.start);
					int currentRight = right - (rtl ? configuration.start : configuration.end);
					parent.getDecoratedBoundsWithMargins(view, rect);
					int height = configuration.getHeight(drawable);
					if (position == parent.getAdapter().getItemCount() - 1) {
						drawDivider(c, rect.bottom - configuration.bottom,
								currentLeft, currentRight, height, view, configuration.translate);
					} else {
						boolean toNext = aboveCallback != null && aboveCallback.shouldPlaceAbove(position + 1);
						int top = toNext ? rect.bottom + configuration.top
								: rect.bottom - configuration.bottom - height;
						drawDivider(c, top, currentLeft, currentRight, height, view, configuration.translate);
					}
				}
			}
		}
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
			@NonNull RecyclerView.State state) {
		int position = parent.getChildAdapterPosition(view);
		if (position >= 0) {
			boolean last = position == parent.getAdapter().getItemCount() - 1;
			int top = 0;
			int bottom = 0;
			if (position > 0 && aboveCallback != null && aboveCallback.shouldPlaceAbove(position)) {
				callback.configure(configuration, position - 1);
				top = configuration.need ? configuration.getTotalHeight(drawable) : 0;
			}
			if (!last && (aboveCallback == null || !aboveCallback.shouldPlaceAbove(position + 1))) {
				callback.configure(configuration, position);
				bottom = configuration.need ? configuration.getTotalHeight(drawable) : 0;
			}
			outRect.set(0, top, 0, bottom);
		} else {
			outRect.set(0, 0, 0, 0);
		}
	}
}
