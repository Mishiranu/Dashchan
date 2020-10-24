package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.database.HistoryDatabase;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.Calendar;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback extends ListViewUtils.SimpleCallback<HistoryDatabase.HistoryItem> {}

	private enum Header {
		TODAY(R.string.today, 0),
		YESTERDAY(R.string.yesterday, 24 * 60 * 60 * 1000),
		WEEK(R.string.this_week, 7 * 24 * 60 * 60 * 1000),
		OLD(R.string.older_than_seven_days, Long.MAX_VALUE);

		public final int titleResId;
		public final long threshold;

		Header(int titleResId, long threshold) {
			this.titleResId = titleResId;
			this.threshold = threshold;
		}

		public static Header find(long dayStart, long time) {
			long delta = dayStart - time;
			for (Header header : values()) {
				if (delta <= header.threshold) {
					return header;
				}
			}
			return OLD;
		}
	}

	private final Callback callback;
	private final String chanName;
	private final PostDateFormatter postDateFormatter;
	private final HistoryDatabase.HistoryItem historyItem = new HistoryDatabase.HistoryItem();

	private HistoryDatabase.HistoryCursor cursor;
	private long queryDayStart;

	public HistoryAdapter(Context context, Callback callback, String chanName) {
		this.callback = callback;
		this.chanName = chanName;
		postDateFormatter = new PostDateFormatter(context);
		setHasStableIds(true);
	}

	public void setCursor(HistoryDatabase.HistoryCursor cursor) {
		if (this.cursor != cursor) {
			if (this.cursor != null) {
				this.cursor.close();
			}
			this.cursor = cursor;
			Calendar calendar = Calendar.getInstance();
			calendar.set(Calendar.HOUR_OF_DAY, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			queryDayStart = calendar.getTimeInMillis();
			notifyDataSetChanged();
		}
	}

	@Override
	public int getItemCount() {
		return cursor != null ? cursor.getCount() : 0;
	}

	@Override
	public long getItemId(int position) {
		cursor.moveToPosition(position);
		int index = cursor.getColumnIndex("rowid");
		return index >= 0 ? cursor.getLong(index) : -1;
	}

	private HistoryDatabase.HistoryItem getTransientItem(int position) {
		cursor.moveToPosition(position);
		historyItem.update(cursor);
		return historyItem;
	}

	private HistoryDatabase.HistoryItem copyItem(int position) {
		return getTransientItem(position).copy();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return ListViewUtils.bind(new SimpleViewHolder(ViewFactory.makeTwoLinesListItem(parent,
				ViewFactory.FEATURE_TEXT2_END).view), true, this::copyItem, callback);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		HistoryDatabase.HistoryItem historyItem = getTransientItem(position);
		ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
		viewHolder.text1.setText(StringUtils.isEmpty(historyItem.title)
				? StringUtils.formatThreadTitle(historyItem.chanName, historyItem.boardName,
				historyItem.threadNumber) : historyItem.title);
		Chan chan = Chan.get(historyItem.chanName);
		String title = chan.configuration.getBoardTitle(historyItem.boardName);
		title = StringUtils.isEmpty(historyItem.boardName) ? title
				: StringUtils.formatBoardTitle(historyItem.chanName, historyItem.boardName, title);
		if (chanName == null) {
			title = chan.configuration.getTitle() + " — " + title;
		}
		viewHolder.text2.setText(title);
		viewHolder.text2End.setText(postDateFormatter.formatDate(historyItem.time));
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		if (C.API_LOLLIPOP) {
			return configuration.need(true);
		} else {
			Header header = position + 1 < getItemCount() ? getItemHeader(position + 1) : null;
			return configuration.need(header == null);
		}
	}

	private Header getItemHeader(int position) {
		if (cursor != null && cursor.filtered) {
			return null;
		} else if (position == 0) {
			return Header.find(queryDayStart, getTransientItem(0).time);
		} else {
			Header previous = Header.find(queryDayStart, getTransientItem(position - 1).time);
			Header current = Header.find(queryDayStart, getTransientItem(position).time);
			return previous != current ? current : null;
		}
	}

	public final RecyclerView.ItemDecoration headerItemDecoration = new RecyclerView.ItemDecoration() {
		private final Rect rect = new Rect();
		private TextView headerView;

		private View prepareHeaderView(RecyclerView parent, int titleResId, boolean layout) {
			if (headerView == null) {
				headerView = ViewFactory.makeListTextHeader(parent);
			}
			headerView.setText(titleResId);
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
					Header header = getItemHeader(position);
					if (header != null) {
						parent.getDecoratedBoundsWithMargins(view, rect);
						c.save();
						c.translate(rect.left, rect.top);
						prepareHeaderView(parent, header.titleResId, true).draw(c);
						c.restore();
					}
				}
			}
		}

		@Override
		public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
				@NonNull RecyclerView.State state) {
			int position = parent.getChildAdapterPosition(view);
			Header header = null;
			if (position >= 0) {
				header = getItemHeader(position);
			}
			if (header != null) {
				outRect.set(0, prepareHeaderView(parent, header.titleResId, false).getMeasuredHeight(), 0, 0);
			} else {
				outRect.set(0, 0, 0, 0);
			}
		}
	};
}
