package com.mishiranu.dashchan.ui.preference;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StatisticsFragment extends BaseListFragment {
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		long startTime = StatisticsStorage.getInstance().getStartTime();
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.statistics),
				startTime > 0 ? getString(R.string.since_date__format,
						new PostDateFormatter(requireContext()).formatDateTime(startTime)) : null);

		ArrayList<Adapter.ListItem> listItems = new ArrayList<>();
		listItems.add(new Adapter.ListItem(null, getString(R.string.views),
				getString(R.string.posts), getString(R.string.threads)));

		HashMap<String, StatisticsStorage.StatisticsItem> statisticsItems = StatisticsStorage.getInstance().getItems();
		int totalThreadsViewed = 0;
		int totalPostsSent = 0;
		int totalThreadsCreated = 0;
		for (HashMap.Entry<String, StatisticsStorage.StatisticsItem> entry : statisticsItems.entrySet()) {
			ChanConfiguration.Statistics statistics = Chan.get(entry.getKey()).configuration.safe().obtainStatistics();
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
		listItems.add(new Adapter.ListItem(getString(R.string.total), Integer.toString(totalThreadsViewed),
				Integer.toString(totalPostsSent), Integer.toString(totalThreadsCreated)));

		for (Chan chan : ChanManager.getInstance().getAvailableChans()) {
			StatisticsStorage.StatisticsItem statisticsItem = statisticsItems.get(chan.name);
			if (statisticsItem != null) {
				ChanConfiguration.Statistics statistics = chan.configuration.safe().obtainStatistics();
				if (statistics.threadsViewed && statistics.postsSent && statistics.threadsCreated) {
					int threadsViewed = statistics.threadsViewed ? statisticsItem.threadsViewed : -1;
					int postsSent = statistics.postsSent ? statisticsItem.postsSent : -1;
					int threadsCreated = statistics.threadsCreated ? statisticsItem.threadsCreated : -1;
					String title = chan.configuration.getTitle();
					if (StringUtils.isEmpty(title)) {
						title = chan.name;
					}
					listItems.add(new Adapter.ListItem(title, Integer.toString(threadsViewed),
							Integer.toString(postsSent), Integer.toString(threadsCreated)));
				}
			}
		}

		getRecyclerView().setAdapter(new Adapter(listItems));
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_clear, 0, R.string.clear)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionDelete))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_clear: {
				StatisticsStorage.getInstance().clear();
				((FragmentHandler) requireActivity()).removeFragment();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	private static class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
		public static class ListItem {
			public final String text1;
			public final String text2;
			public final String text3;
			public final String text4;

			public ListItem(String text1, String text2, String text3, String text4) {
				this.text1 = text1;
				this.text2 = text2;
				this.text3 = text3;
				this.text4 = text4;
			}
		}

		private static class ViewHolder extends RecyclerView.ViewHolder {
			public final TextView text1;
			public final TextView text2;
			public final TextView text3;
			public final TextView text4;

			public ViewHolder(@NonNull View itemView, TextView text1, TextView text2, TextView text3, TextView text4) {
				super(itemView);
				this.text1 = text1;
				this.text2 = text2;
				this.text3 = text3;
				this.text4 = text4;
			}
		}

		private final List<ListItem> listItems;

		public Adapter(List<ListItem> listItems) {
			this.listItems = listItems;
		}

		@Override
		public int getItemCount() {
			return listItems.size();
		}

		private TextView addTextView(LinearLayout parent, boolean end, float weight, int padding) {
			TextView textView = new TextView(parent.getContext());
			TextViewCompat.setTextAppearance(textView, ResourceUtils.getResourceId(textView.getContext(),
					C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem : android.R.attr.textAppearanceMedium,
					android.R.style.TextAppearance_Medium));
			textView.setSingleLine(true);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			textView.setGravity(Gravity.CENTER_VERTICAL | (end ? Gravity.END : Gravity.START));
			LinearLayout.LayoutParams layoutParams = new LinearLayout
					.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
			layoutParams.leftMargin = padding;
			parent.addView(textView, layoutParams);
			return textView;
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LinearLayout linearLayout = new LinearLayout(parent.getContext());
			linearLayout.setOrientation(LinearLayout.HORIZONTAL);
			float density = ResourceUtils.obtainDensity(linearLayout);
			int outerPadding = (int) ((C.API_LOLLIPOP ? 16f : 6f) * density);
			int innerPadding = (int) ((C.API_LOLLIPOP ? 8f : 6f) * density);
			linearLayout.setPadding(outerPadding - innerPadding, 0, outerPadding, 0);
			TextView text1 = addTextView(linearLayout, false, 3f, innerPadding);
			TextView text2 = addTextView(linearLayout, true, 2f, innerPadding);
			TextView text3 = addTextView(linearLayout, true, 2f, innerPadding);
			TextView text4 = addTextView(linearLayout, true, 2f, innerPadding);
			linearLayout.setLayoutParams(new RecyclerView.LayoutParams
					(RecyclerView.LayoutParams.MATCH_PARENT, (int) (48f * density)));
			return new ViewHolder(linearLayout, text1, text2, text3, text4);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			ListItem listItem = listItems.get(position);
			holder.text1.setText(listItem.text1);
			holder.text2.setText(listItem.text2);
			holder.text3.setText(listItem.text3);
			holder.text4.setText(listItem.text4);
		}
	}
}
