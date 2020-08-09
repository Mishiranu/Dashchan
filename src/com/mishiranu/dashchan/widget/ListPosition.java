package com.mishiranu.dashchan.widget;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.ListView;

public final class ListPosition implements Parcelable {
	public final int position;
	public final int y;

	public ListPosition(int position, int y) {
		this.position = position;
		this.y = y;
	}

	public static ListPosition obtain(ListView listView) {
		int position = listView.getFirstVisiblePosition();
		int y = 0;
		Rect rect = new Rect();
		int paddingTop = listView.getPaddingTop();
		int paddingLeft = listView.getPaddingLeft();
		for (int i = 0, count = listView.getChildCount(); i < count; i++) {
			View view = listView.getChildAt(i);
			view.getHitRect(rect);
			if (rect.contains(paddingLeft, paddingTop)) {
				position += i;
				y = rect.top - paddingTop;
				break;
			}
		}
		return new ListPosition(position, y);
	}

	public void apply(final ListView listView) {
		if (listView.getHeight() == 0) {
			// Dirty hack. Will be hopefully removed after RecyclerView migration.
			listView.post(() -> {
				listView.setSelectionFromTop(position, y);
				listView.post(() -> listView.setSelectionFromTop(position, y));
			});
		} else {
			listView.setSelectionFromTop(position, y);
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(position);
		dest.writeInt(y);
	}

	public static final Creator<ListPosition> CREATOR = new Creator<ListPosition>() {
		@Override
		public ListPosition createFromParcel(Parcel in) {
			int position = in.readInt();
			int y = in.readInt();
			return new ListPosition(position, y);
		}

		@Override
		public ListPosition[] newArray(int size) {
			return new ListPosition[size];
		}
	};
}
