package com.mishiranu.dashchan.widget;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ViewFactory {
	public static TextView makeListTextHeader(ViewGroup parent, boolean tiny) {
		TextView textView;
		if (C.API_LOLLIPOP) {
			textView = new TextView(parent.getContext());
			TextViewCompat.setTextAppearance(textView, R.style.Widget_CategoryHeader);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
					AbsListView.LayoutParams.WRAP_CONTENT));
			textView.setGravity(Gravity.CENTER_VERTICAL);
			textView.setPadding((int) (16f * density), (int) (8f * density), (int) (16f * density),
					tiny ? 0 : (int) (8f * density));
		} else {
			textView = (TextView) LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.preference_category, parent, false);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setPadding((int) (12f * density), textView.getPaddingTop(), (int) (12f * density),
					textView.getPaddingBottom());
		}
		return textView;
	}

	public static class TwoLinesViewHolder {
		public final TextView text1;
		public final TextView text2;

		public TwoLinesViewHolder(TextView text1, TextView text2) {
			this.text1 = text1;
			this.text2 = text2;
		}
	}

	public static View makeTwoLinesListItem(ViewGroup parent, boolean singleLine) {
		ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_preference, parent, false);
		TwoLinesViewHolder holder = new TwoLinesViewHolder(view.findViewById(android.R.id.text1),
				view.findViewById(android.R.id.text2));
		view.removeView(view.findViewById(android.R.id.widget_frame));
		if (singleLine) {
			holder.text2.setSingleLine(true);
		}
		view.setTag(holder);
		return view;
	}
}
