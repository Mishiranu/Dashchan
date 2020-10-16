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

	public interface PositionTest {
		boolean isPositionAllowed(int position);
	}

	public static ListPosition obtain(RecyclerView recyclerView, PositionTest positionTest) {
		Rect rect = new Rect();
		int paddingTop = recyclerView.getPaddingTop();
		for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
			View view = recyclerView.getChildAt(i);
			recyclerView.getDecoratedBoundsWithMargins(view, rect);
			if (rect.bottom > paddingTop) {
				int position = recyclerView.getChildLayoutPosition(view);
				if (position >= 0 && (positionTest == null || positionTest.isPositionAllowed(position))) {
					int offset = rect.top - paddingTop;
					return new ListPosition(position, offset);
				}
			}
		}
		return null;
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
