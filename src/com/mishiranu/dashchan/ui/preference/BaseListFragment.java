package com.mishiranu.dashchan.ui.preference;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.ui.ContentFragment;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.ViewFactory;

public abstract class BaseListFragment extends ContentFragment {
	private RecyclerView recyclerView;
	private ViewFactory.ErrorHolder errorHolder;

	@Override
	public final View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		FrameLayout view = new FrameLayout(container.getContext());
		view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		recyclerView = new PaddedRecyclerView(view.getContext());
		recyclerView.setId(android.R.id.list);
		recyclerView.setMotionEventSplittingEnabled(false);
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), this::configureDivider));
		view.addView(recyclerView, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		errorHolder = ViewFactory.createErrorLayout(view);
		errorHolder.layout.setVisibility(View.GONE);
		view.addView(errorHolder.layout);
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		recyclerView = null;
		errorHolder = null;
	}

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

	public FrameLayout getContentView() {
		return (FrameLayout) getView();
	}

	public void setErrorText(CharSequence text) {
		errorHolder.layout.setVisibility(StringUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
		errorHolder.text.setText(text);
	}

	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return configuration.need(true);
	}
}
