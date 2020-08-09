package com.mishiranu.dashchan.widget.callback;

import android.widget.AbsListView;
import com.mishiranu.dashchan.R;
import java.util.ArrayList;

public class ScrollListenerComposite implements AbsListView.OnScrollListener {
	private final ArrayList<AbsListView.OnScrollListener> listeners = new ArrayList<>();

	private ScrollListenerComposite() {}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		for (AbsListView.OnScrollListener listener : listeners) {
			listener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		for (AbsListView.OnScrollListener listener : listeners) {
			listener.onScrollStateChanged(view, scrollState);
		}
	}

	public void add(AbsListView.OnScrollListener listener) {
		listeners.add(listener);
	}

	public void remove(AbsListView.OnScrollListener listener) {
		listeners.remove(listener);
	}

	public static ScrollListenerComposite obtain(AbsListView listView) {
		ScrollListenerComposite listener = (ScrollListenerComposite) listView.getTag(R.id.scroll_view);
		if (listener == null) {
			listener = new ScrollListenerComposite();
			listView.setTag(R.id.scroll_view, listener);
			listView.setOnScrollListener(listener);
		}
		return listener;
	}
}
