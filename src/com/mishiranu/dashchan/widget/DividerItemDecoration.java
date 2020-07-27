package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.util.ResourceUtils;

public class DividerItemDecoration extends RecyclerView.ItemDecoration {
	public static final class Configuration {
		private boolean need;

		public void configure(boolean need) {
			this.need = need;
		}
	}

	public interface Callback {
		void configure(Configuration configuration, int position);
	}

	private final Callback callback;
	private final Drawable drawable;
	private final Configuration configuration = new Configuration();
	private final Rect rect = new Rect();

	public DividerItemDecoration(Context context, Callback callback) {
		this.callback = callback;
		drawable = ResourceUtils.getDrawable(context, android.R.attr.listDivider, 0);
	}

	private void drawDivider(Canvas canvas, int top, int left, int right, View view) {
		top += view.getTranslationY();
		drawable.setBounds(left, top, right, top + drawable.getIntrinsicHeight());
		drawable.draw(canvas);
	}

	@Override
	public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		int childCount = parent.getChildCount();
		int left = parent.getPaddingLeft();
		int right = parent.getWidth() - parent.getPaddingRight();
		for (int i = 0; i < childCount; i++) {
			View view = parent.getChildAt(i);
			int position = parent.getChildAdapterPosition(view);
			if (position >= 0) {
				callback.configure(configuration, position);
				if (configuration.need) {
					parent.getDecoratedBoundsWithMargins(view, rect);
					if (position == parent.getAdapter().getItemCount() - 1) {
						drawDivider(c, rect.bottom, left, right, view);
					} else {
						drawDivider(c, rect.bottom - drawable.getIntrinsicHeight(), left, right, view);
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
			callback.configure(configuration, position);
		} else {
			configuration.configure(false);
		}
		outRect.set(0, 0, 0, configuration.need && position < parent.getAdapter().getItemCount() - 1
				? drawable.getIntrinsicHeight() : 0);
	}
}
