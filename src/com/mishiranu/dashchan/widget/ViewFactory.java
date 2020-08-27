package com.mishiranu.dashchan.widget;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.core.widget.TextViewCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class ViewFactory {
	public static TextView makeListTextHeader(ViewGroup parent) {
		TextView textView;
		if (C.API_LOLLIPOP) {
			textView = new TextView(parent.getContext());
			TextViewCompat.setTextAppearance(textView, R.style.Widget_CategoryHeader);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			textView.setGravity(Gravity.CENTER_VERTICAL);
			textView.setTextColor(ThemeEngine.getTheme(textView.getContext()).accent);
			textView.setPadding((int) (16f * density), (int) (16f * density), (int) (16f * density),
					(int) (8f * density));
		} else {
			textView = (TextView) LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.preference_category, parent, false);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setPadding((int) (8f * density), textView.getPaddingTop(), (int) (8f * density),
					textView.getPaddingBottom());
		}
		return textView;
	}

	public static View makeSingleLineListItem(ViewGroup parent) {
		float density = ResourceUtils.obtainDensity(parent);
		TextView textView = new TextView(parent.getContext());
		if (C.API_LOLLIPOP) {
			textView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
		} else {
			textView.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
		}
		TypedArray typedArray = textView.getContext().obtainStyledAttributes(new int[] {C.API_LOLLIPOP
				? android.R.attr.textAppearanceListItem : android.R.attr.textAppearanceMedium,
				android.R.attr.listPreferredItemHeightSmall});
		TextViewCompat.setTextAppearance(textView, typedArray.getResourceId(0, 0));
		textView.setMinimumHeight(typedArray.getDimensionPixelSize(1, 0));
		typedArray.recycle();
		ViewUtils.setSelectableItemBackground(textView);
		textView.setGravity(Gravity.CENTER_VERTICAL);
		textView.setSingleLine(true);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
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

	public static View makeTwoLinesListItem(ViewGroup parent) {
		ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_preference, parent, false);
		TwoLinesViewHolder holder = new TwoLinesViewHolder(view.findViewById(android.R.id.text1),
				view.findViewById(android.R.id.text2));
		view.removeView(view.findViewById(android.R.id.widget_frame));
		ViewUtils.setSelectableItemBackground(view);
		view.setTag(holder);
		return view;
	}

	public static class ToolbarHolder {
		public final ViewGroup toolbar;
		public final View layout;
		private final TextView title;
		private final TextView subtitle;

		private ToolbarHolder(Toolbar toolbar, View layout, TextView title, TextView subtitle) {
			this.toolbar = toolbar;
			this.layout = layout;
			this.title = title;
			this.subtitle = subtitle;
		}

		public void update(CharSequence title, CharSequence subtitle) {
			this.title.setText(title);
			this.subtitle.setText(subtitle);
			this.subtitle.setVisibility(StringUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
		}

		public Toolbar getToolbar() {
			return (Toolbar) toolbar;
		}
	}

	public static ToolbarHolder addToolbarTitle(Toolbar toolbar) {
		LinearLayout layout = new LinearLayout(toolbar.getContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		toolbar.addView(layout, Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT);
		TextView title = new TextView(layout.getContext());
		layout.addView(title, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		TextViewCompat.setTextAppearance(title, android.R.style.TextAppearance_Material_Widget_Toolbar_Title);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		TextView subtitle = new TextView(layout.getContext());
		layout.addView(subtitle, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		TextViewCompat.setTextAppearance(subtitle, android.R.style.TextAppearance_Material_Widget_Toolbar_Subtitle);
		subtitle.setSingleLine(true);
		subtitle.setEllipsize(TextUtils.TruncateAt.END);
		Configuration configuration = toolbar.getResources().getConfiguration();
		if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !ResourceUtils.isTablet(configuration)) {
			float density = ResourceUtils.obtainDensity(toolbar);
			ViewUtils.setNewMargin(subtitle, null, (int) (-2f * density), null, null);
			subtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (int) (subtitle.getTextSize() * 0.85f + 0.5f));
		}
		return new ToolbarHolder(toolbar, layout, title, subtitle);
	}
}
