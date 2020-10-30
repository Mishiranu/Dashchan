package com.mishiranu.dashchan.widget;

import android.database.Cursor;
import androidx.recyclerview.widget.RecyclerView;

public abstract class CursorAdapter<C extends Cursor, VH extends RecyclerView.ViewHolder>
		extends RecyclerView.Adapter<VH> {
	private C cursor;

	@Override
	public final int getItemCount() {
		return cursor != null ? cursor.getCount() : 0;
	}

	@Override
	public final long getItemId(int position) {
		cursor.moveToPosition(position);
		int index = cursor.getColumnIndex("rowid");
		return index >= 0 ? cursor.getLong(index) : RecyclerView.NO_ID;
	}

	public final void setCursor(C cursor) {
		if (this.cursor != cursor) {
			if (this.cursor != null) {
				this.cursor.close();
			}
			this.cursor = cursor;
			onCursorChanged();
			notifyDataSetChanged();
		}
	}

	public final C getCursor() {
		return cursor;
	}

	protected C moveTo(int position) {
		if (cursor != null) {
			cursor.moveToPosition(position);
		}
		return cursor;
	}

	protected void onCursorChanged() {}
}
