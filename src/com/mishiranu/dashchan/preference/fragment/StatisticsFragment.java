package com.mishiranu.dashchan.preference.fragment;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.HashMap;

public class StatisticsFragment extends BaseListFragment {
	private static class ListItem {
		public final String title;
		public final int views;
		public final int posts;
		public final int threads;

		public ListItem(String title, int views, int posts, int threads) {
			this.title = title;
			this.views = views;
			this.posts = posts;
			this.threads = threads;
		}
	}

	private final ArrayList<ListItem> listItems = new ArrayList<>();

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		requireActivity().setTitle(R.string.preference_statistics);
		long startTime = StatisticsStorage.getInstance().getStartTime();
		requireActivity().getActionBar().setSubtitle(startTime > 0 ? getString(R.string.text_since_format,
				new PostDateFormatter(requireContext()).format(startTime)) : null);

		HashMap<String, StatisticsStorage.StatisticsItem> statisticsItems = StatisticsStorage.getInstance().getItems();
		int totalThreadsViewed = 0;
		int totalPostsSent = 0;
		int totalThreadsCreated = 0;
		for (HashMap.Entry<String, StatisticsStorage.StatisticsItem> entry : statisticsItems.entrySet()) {
			ChanConfiguration.Statistics statistics = ChanConfiguration.get(entry.getKey()).safe().obtainStatistics();
			StatisticsStorage.StatisticsItem statisticsItem = entry.getValue();
			if (statistics.threadsViewed && statisticsItem.threadsViewed > 0) {
				totalThreadsViewed += statisticsItem.threadsViewed;
			}
			if (statistics.postsSent && statisticsItem.postsSent > 0) {
				totalPostsSent += statisticsItem.postsSent;
			}
			if (statistics.threadsCreated && statisticsItem.threadsCreated > 0) {
				totalThreadsCreated += statisticsItem.threadsCreated;
			}
		}
		listItems.add(new ListItem(getString(R.string.text_general), totalThreadsViewed, totalPostsSent,
				totalThreadsCreated));

		for (String chanName : ChanManager.getInstance().getAvailableChanNames()) {
			StatisticsStorage.StatisticsItem statisticsItem = statisticsItems.get(chanName);
			if (statisticsItem != null) {
				ChanConfiguration.Statistics statistics = ChanConfiguration.get(chanName).safe().obtainStatistics();
				if (statistics.threadsViewed && statistics.postsSent && statistics.threadsCreated) {
					int threadsViewed = statistics.threadsViewed ? statisticsItem.threadsViewed : -1;
					int postsSent = statistics.postsSent ? statisticsItem.postsSent : -1;
					int threadsCreated = statistics.threadsCreated ? statisticsItem.threadsCreated : -1;
					String title = ChanConfiguration.get(chanName).getTitle();
					if (StringUtils.isEmpty(title)) {
						title = chanName;
					}
					listItems.add(new ListItem(title, threadsViewed, postsSent, threadsCreated));
				}
			}
		}

		getRecyclerView().setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			@Override
			public int getItemCount() {
				return listItems.size();
			}

			@NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				return new RecyclerView.ViewHolder(ViewFactory.makeTwoLinesListItem(parent, false)) {};
			}

			@Override
			public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
				ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
				ListItem listItem = listItems.get(position);
				viewHolder.text1.setText(listItem.title);
				SpannableStringBuilder spannable = new SpannableStringBuilder();
				appendSpannedLine(spannable, R.string.text_threads_viewed, listItem.views);
				appendSpannedLine(spannable, R.string.text_posts_sent, listItem.posts);
				appendSpannedLine(spannable, R.string.text_threads_created, listItem.threads);
				viewHolder.text2.setText(spannable);
			}

			private void appendSpannedLine(SpannableStringBuilder spannable, int resId, int value) {
				if (value >= 0) {
					if (spannable.length() > 0) {
						spannable.append('\n');
					}
					spannable.append(getString(resId)).append(": ");
					StringUtils.appendSpan(spannable, Integer.toString(value), getBoldSpan());
				}
			}

			private Object getBoldSpan() {
				return C.API_LOLLIPOP ? new TypefaceSpan("sans-serif-medium") : new StyleSpan(Typeface.BOLD);
			}
		});
	}

	private static final int OPTIONS_MENU_CLEAR = 0;

	@Override
	public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		ActionIconSet set = new ActionIconSet(requireContext());
		menu.add(0, OPTIONS_MENU_CLEAR, 0, R.string.action_clear).setIcon(set.getId(R.attr.actionDelete))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_CLEAR: {
				StatisticsStorage.getInstance().clear();
				((FragmentHandler) requireActivity()).removeFragment();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
}
