package com.mishiranu.dashchan.preference.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public abstract class BaseListFragment extends Fragment implements AdapterView.OnItemClickListener {
	private ListView listView;
	private View emptyView;
	private TextView emptyText;

	@Override
	public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.activity_common, container, false);
		ListView listView = view.findViewById(android.R.id.list);
		emptyView = view.findViewById(R.id.error);
		emptyText = view.findViewById(R.id.error_text);
		emptyView.setVisibility(View.GONE);
		listView.setOnItemClickListener(this);
		registerForContextMenu(listView);
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(listView);
			listView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
		}
		this.listView = listView;
		return view;
	}

	public ListView getListView() {
		return listView;
	}

	public void setListAdapter(ListAdapter adapter) {
		listView.setAdapter(adapter);
	}

	public void setEmptyText(CharSequence text) {
		emptyText.setText(text);
		if (StringUtils.isEmpty(text)) {
			listView.setEmptyView(null);
			emptyView.setVisibility(View.GONE);
		} else {
			emptyView.setVisibility(View.VISIBLE);
			listView.setEmptyView(emptyView);
		}
	}

	public void setEmptyText(int resId) {
		setEmptyText(getString(resId));
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {}
}
