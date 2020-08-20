package com.mishiranu.dashchan.widget;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public final class ListPosition implements Parcelable {
	public final int position;
	public final int offset;

	public ListPosition(int position, int offset) {
		this.position = position;
		this.offset = offset;
	}

	public static ListPosition obtain(RecyclerView recyclerView) {
		int position = 0;
		int offset = 0;
		Rect rect = new Rect();
		int paddingTop = recyclerView.getPaddingTop();
		int paddingLeft = recyclerView.getPaddingLeft();
		for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
			View view = recyclerView.getChildAt(i);
			recyclerView.getDecoratedBoundsWithMargins(view, rect);
			if (rect.contains(paddingLeft, paddingTop)) {
				position = recyclerView.getChildAdapterPosition(view);
				offset = rect.top - paddingTop;
				break;
			}
		}
		return new ListPosition(position, offset);
	}

	public void apply(RecyclerView recyclerView) {
		LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
		layoutManager.scrollToPositionWithOffset(position, offset);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(position);
		dest.writeInt(offset);
	}

	public static final Creator<ListPosition> CREATOR = new Creator<ListPosition>() {
		@Override
		public ListPosition createFromParcel(Parcel in) {
			int position = in.readInt();
			int offset = in.readInt();
			return new ListPosition(position, offset);
		}

		@Override
		public ListPosition[] newArray(int size) {
			return new ListPosition[size];
		}
	};
}
