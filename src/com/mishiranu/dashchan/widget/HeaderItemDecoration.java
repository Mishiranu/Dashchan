package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class HeaderItemDecoration extends RecyclerView.ItemDecoration {
	public interface Configuration {
		void configureHeaderView(Context context, TextView headerView);
	}

	public interface Provider {
		String getHeader(Context context, int position);
	}

	private final Configuration configuration;
	private final Provider provider;
	private final Rect rect = new Rect();
	private TextView headerView;

	public HeaderItemDecoration(Provider provider) {
		this(null, provider);
	}

	public HeaderItemDecoration(Configuration configuration, Provider provider) {
		this.configuration = configuration;
		this.provider = provider;
	}

	private View prepareHeaderView(RecyclerView parent, String header, boolean layout) {
		if (headerView == null) {
			headerView = ViewFactory.makeListTextHeader(parent);
			if (configuration != null) {
				configuration.configureHeaderView(parent.getContext(), headerView);
			}
		}
		headerView.setText(header);
		headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		if (layout) {
			headerView.layout(0, 0, headerView.getMeasuredWidth(), headerView.getMeasuredHeight());
		}
		return headerView;
	}

	@Override
	public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		int childCount = parent.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View view = parent.getChildAt(i);
			int position = parent.getChildAdapterPosition(view);
			if (position >= 0) {
				String header = provider.getHeader(view.getContext(), position);
				if (header != null) {
					parent.getDecoratedBoundsWithMargins(view, rect);
					c.save();
					c.translate(rect.left, rect.top);
					prepareHeaderView(parent, header, true).draw(c);
					c.restore();
				}
			}
		}
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
			@NonNull RecyclerView.State state) {
		int position = parent.getChildAdapterPosition(view);
		String header = position >= 0 ? provider.getHeader(view.getContext(), position) : null;
		if (header != null) {
			outRect.set(0, prepareHeaderView(parent, header, false).getMeasuredHeight(), 0, 0);
		} else {
			outRect.set(0, 0, 0, 0);
		}
	}
}
