package com.mishiranu.dashchan.widget;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.core.widget.TextViewCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class ViewFactory {
	public static final int FEATURE_WIDGET = 0x00000001;
	public static final int FEATURE_SINGLE_LINE = 0x00000002;
	public static final int FEATURE_TEXT2_END = 0x00000004;

	public static TextView makeListTextHeader(ViewGroup parent) {
		TextView textView;
		if (C.API_LOLLIPOP) {
			textView = new TextView(parent.getContext());
			float density = ResourceUtils.obtainDensity(parent);
			textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			textView.setMinHeight((int) (48f * density));
			textView.setGravity(Gravity.CENTER_VERTICAL);
			textView.setTextColor(ThemeEngine.getTheme(textView.getContext()).accent);
			textView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			ViewUtils.setTextSizeScaled(textView, 14);
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
		public final View view;
		public final TextView text1;
		public final TextView text2;
		public final TextView text2End;
		public final LinearLayout widgetFrame;

		public TwoLinesViewHolder(View view, TextView text1, TextView text2,
				TextView text2End, LinearLayout widgetFrame) {
			this.view = view;
			this.text1 = text1;
			this.text2 = text2;
			this.text2End = text2End;
			this.widgetFrame = widgetFrame;
		}
	}

	private static final int[] ATTRS_TWO_LINES = {
			C.API_LOLLIPOP ? android.R.attr.listPreferredItemHeightSmall : android.R.attr.listPreferredItemHeight,
			C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem : android.R.attr.textAppearanceMedium,
			C.API_LOLLIPOP ? android.R.attr.textAppearanceListItemSecondary : android.R.attr.textAppearanceSmall,
			android.R.attr.textColorSecondary
	};

	public static TwoLinesViewHolder makeTwoLinesListItem(ViewGroup parent, int features) {
		float density = ResourceUtils.obtainDensity(parent);
		LinearLayout outerLayout = new LinearLayout(parent.getContext());
		outerLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		int outerPaddingHorizontal = (int) ((C.API_LOLLIPOP ? 16f : 8f) * density + 0.5f);
		int innerPaddingVertical = (int) ((C.API_LOLLIPOP ? 16f : 6f) * density + 0.5f);
		boolean featureWidgetFrame = FlagUtils.get(features, FEATURE_WIDGET);
		LinearLayout innerLayout;
		if (featureWidgetFrame) {
			outerLayout.setOrientation(LinearLayout.HORIZONTAL);
			outerLayout.setBaselineAligned(false);
			innerLayout = new LinearLayout(outerLayout.getContext());
			innerLayout.setOrientation(LinearLayout.VERTICAL);
			outerLayout.addView(innerLayout, 0, LinearLayout.LayoutParams.WRAP_CONTENT);
			((LinearLayout.LayoutParams) innerLayout.getLayoutParams()).weight = 1;
			outerLayout.setPaddingRelative(outerPaddingHorizontal, 0, outerPaddingHorizontal, 0);
			innerLayout.setPaddingRelative(0, innerPaddingVertical, 0, innerPaddingVertical);
		} else {
			outerLayout.setOrientation(LinearLayout.VERTICAL);
			outerLayout.setPaddingRelative(outerPaddingHorizontal, innerPaddingVertical,
					outerPaddingHorizontal, innerPaddingVertical);
			innerLayout = outerLayout;
		}
		outerLayout.setGravity(Gravity.CENTER_VERTICAL);
		TypedArray typedArray = parent.getContext().obtainStyledAttributes(ATTRS_TWO_LINES);
		try {
			outerLayout.setMinimumHeight(typedArray.getDimensionPixelSize(0, 0));
			TextView text1 = new TextView(parent.getContext());
			TextViewCompat.setTextAppearance(text1, typedArray.getResourceId(1, 0));
			text1.setSingleLine(true);
			text1.setEllipsize(TextUtils.TruncateAt.END);
			TextView text2 = new TextView(parent.getContext());
			TextViewCompat.setTextAppearance(text2, typedArray.getResourceId(2, 0));
			text2.setTextColor(typedArray.getColorStateList(3));
			boolean featureText2End = FlagUtils.get(features, FEATURE_TEXT2_END);
			if (FlagUtils.get(features, FEATURE_SINGLE_LINE) || featureText2End) {
				text2.setSingleLine(true);
				text2.setEllipsize(TextUtils.TruncateAt.END);
			} else {
				text2.setMaxLines(10);
			}
			TextView text2End;
			if (featureText2End) {
				text2End = new TextView(parent.getContext());
				TextViewCompat.setTextAppearance(text2End, typedArray.getResourceId(2, 0));
				text2End.setTextColor(typedArray.getColorStateList(3));
				text2.setSingleLine(true);
				text2.setEllipsize(TextUtils.TruncateAt.END);
			} else {
				text2End = null;
			}
			innerLayout.addView(text1, LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			if (featureText2End) {
				LinearLayout text2Layout = new LinearLayout(parent.getContext());
				text2Layout.setOrientation(LinearLayout.HORIZONTAL);
				innerLayout.addView(text2Layout, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				text2Layout.addView(text2, 0, LinearLayout.LayoutParams.WRAP_CONTENT);
				((LinearLayout.LayoutParams) text2.getLayoutParams()).weight = 1;
				text2Layout.addView(text2End, LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				ViewUtils.setNewMarginRelative(text2End, (int) (8f * density + 0.5f), null, null, null);
			} else {
				innerLayout.addView(text2, LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
			}
			LinearLayout widgetFrame;
			if (featureWidgetFrame) {
				widgetFrame = new LinearLayout(parent.getContext());
				widgetFrame.setOrientation(LinearLayout.VERTICAL);
				widgetFrame.setGravity(Gravity.CENTER);
				widgetFrame.setVisibility(View.GONE);
				outerLayout.addView(widgetFrame, LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.MATCH_PARENT);
				if (C.API_LOLLIPOP) {
					ViewUtils.setNewMarginRelative(widgetFrame, outerPaddingHorizontal, null, null, null);
				} else {
					widgetFrame.setMinimumWidth((int) (48f * density + 0.5f));
				}
			} else {
				widgetFrame = null;
			}
			ViewUtils.setSelectableItemBackground(outerLayout);
			TwoLinesViewHolder holder = new TwoLinesViewHolder(outerLayout, text1, text2, text2End, widgetFrame);
			outerLayout.setTag(holder);
			return holder;
		} finally {
			typedArray.recycle();
		}
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

	public static class ErrorHolder {
		public final View layout;
		public final TextView text;

		public ErrorHolder(View layout, TextView text) {
			this.layout = layout;
			this.text = text;
		}
	}

	public static ErrorHolder createErrorLayout(ViewGroup parent) {
		LinearLayout layout = new LinearLayout(parent.getContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setGravity(Gravity.CENTER);
		ImageView image = new ImageView(layout.getContext());
		image.setImageDrawable(ResourceUtils.getDrawable(image.getContext(), R.attr.iconButtonWarning, 0));
		if (C.API_LOLLIPOP) {
			image.setImageTintList(ResourceUtils.getColorStateList(image.getContext(),
					android.R.attr.textColorSecondary));
		}
		layout.addView(image, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		TextView text = new TextView(layout.getContext());
		TextViewCompat.setTextAppearance(text, ResourceUtils.getResourceId(text.getContext(),
				android.R.attr.textAppearanceMedium, 0));
		text.setGravity(Gravity.CENTER);
		float density = ResourceUtils.obtainDensity(parent);
		text.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
		layout.addView(text, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		return new ErrorHolder(layout, text);
	}
}
