package com.mishiranu.dashchan.widget;

import android.graphics.Canvas;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.ref.WeakReference;

public class SortableHelper<VH extends RecyclerView.ViewHolder> extends ItemTouchHelper.Callback {
	public interface Callback<VH extends RecyclerView.ViewHolder> {
		void onDragStart(VH holder);
		void onDragFinish(VH holder, boolean cancelled);
		boolean onDragCanMove(VH fromHolder, VH toHolder);
		boolean onDragMove(VH fromHolder, VH toHolder);
	}

	public static class DragState {
		private int from;
		private int to;

		public void set(int from, int to) {
			if (this.from == -1) {
				this.from = from;
			}
			this.to = to;
		}

		public void reset() {
			from = -1;
			to = -1;
		}

		public int getMovedTo() {
			return from >= 0 && to >= 0 && to != from ? to : -1;
		}
	}

	private final ItemTouchHelper helper;
	private final Callback<VH> callback;

	private boolean isDragging;

	public SortableHelper(RecyclerView recyclerView, Callback<VH> callback) {
		helper = new ItemTouchHelper(this);
		this.callback = callback;
		helper.attachToRecyclerView(recyclerView);
	}

	public void start(VH holder) {
		helper.startDrag(holder);
	}

	@SuppressWarnings("unchecked")
	private VH cast(RecyclerView.ViewHolder viewHolder) {
		return (VH) viewHolder;
	}

	@Override
	public boolean isLongPressDragEnabled() {
		return false;
	}

	@Override
	public boolean isItemViewSwipeEnabled() {
		return false;
	}

	@Override
	public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
		return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
	}

	@Override
	public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current,
			@NonNull RecyclerView.ViewHolder target) {
		return callback.onDragCanMove(cast(current), cast(target));
	}

	@Override
	public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
			@NonNull RecyclerView.ViewHolder target) {
		return callback.onDragMove(cast(viewHolder), cast(target));
	}

	private WeakReference<VH> startViewHolder;

	@Override
	public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
		super.onSelectedChanged(viewHolder, actionState);

		if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
			isDragging = true;
			VH holder = cast(viewHolder);
			startViewHolder = new WeakReference<>(holder);
			callback.onDragStart(holder);
		} else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
			boolean cancelled = !isDragging;
			callback.onDragFinish(viewHolder != null ? cast(viewHolder) : startViewHolder.get(), cancelled);
		}
	}

	@Override
	public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
			@NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
			int actionState, boolean isCurrentlyActive) {
		int position = viewHolder.getAdapterPosition();
		int count = recyclerView.getLayoutManager().getItemCount();
		if (position == 0) {
			dY = Math.max(0f, dY);
		}
		if (position + 1 == count) {
			dY = Math.min(dY, 0f);
		}
		if (position > 0 && dY < 0f) {
			int index = recyclerView.indexOfChild(viewHolder.itemView);
			if (index > 0) {
				RecyclerView.ViewHolder before = recyclerView
						.getChildViewHolder(recyclerView.getChildAt(index - 1));
				if (!canDropOver(recyclerView, viewHolder, before)) {
					dY = 0f;
				}
			} else {
				dY = 0f;
			}
		}
		if (position + 1 < count && dY > 0f) {
			int index = recyclerView.indexOfChild(viewHolder.itemView);
			if (index >= 0 && index + 1 < recyclerView.getChildCount()) {
				RecyclerView.ViewHolder after = recyclerView
						.getChildViewHolder(recyclerView.getChildAt(index + 1));
				if (!canDropOver(recyclerView, viewHolder, after)) {
					dY = 0f;
				}
			} else {
				dY = 0f;
			}
		}
		int itemHeight = viewHolder.itemView.getHeight();
		dY = Math.max(-itemHeight, Math.min(dY, itemHeight));
		super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
	}

	@Override
	public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
}
