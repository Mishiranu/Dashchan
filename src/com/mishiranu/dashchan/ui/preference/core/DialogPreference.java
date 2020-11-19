package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ThemeEngine;

public abstract class DialogPreference<T> extends Preference<T> {
	private CharSequence neutralButtonText;
	private Runnable neutralButtonListener;
	private CharSequence description;

	public DialogPreference(Context context, String key, T defaultValue,
			CharSequence title, SummaryProvider<T> summaryProvider) {
		super(context, key, defaultValue, title, summaryProvider);
	}

	protected AlertDialog createDialog(Bundle savedInstanceState) {
		AlertDialog dialog = configureDialog(savedInstanceState, new AlertDialog.Builder(context)).create();
		if (neutralButtonText != null) {
			dialog.setButton(AlertDialog.BUTTON_NEUTRAL, neutralButtonText, (AlertDialog.OnClickListener) null);
			dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
					.setOnClickListener(v -> neutralButtonListener.run()));
		}
		return dialog;
	}

	protected Pair<View, LinearLayout> createDialogLayout(Context context) {
		float density = ResourceUtils.obtainDensity(context);
		int padding = (int) ((C.API_LOLLIPOP ? 20f : 5f) * density);
		ScrollView scrollView = new ScrollView(context);
		scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS);
		ThemeEngine.applyStyle(scrollView);
		LinearLayout linearLayout = new LinearLayout(scrollView.getContext());
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setPadding(padding, padding, padding, padding);
		scrollView.addView(linearLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		if (!StringUtils.isEmpty(description)) {
			EditText testEditText = new EditText(linearLayout.getContext());
			TextView descriptionView = new TextView(linearLayout.getContext(), null,
					android.R.attr.textAppearanceListItemSmall);
			descriptionView.setPadding(testEditText.getPaddingLeft(), 0,
					testEditText.getPaddingRight(), (int) (8f * density));
			descriptionView.setText(description);
			linearLayout.addView(descriptionView, 0, new LinearLayout
					.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		}
		linearLayout.setId(android.R.id.widget_frame);
		return new Pair<>(scrollView, linearLayout);
	}

	protected LinearLayout getDialogLayout(AlertDialog dialog) {
		return dialog.findViewById(android.R.id.widget_frame);
	}

	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		return builder.setTitle(title)
				.setNegativeButton(android.R.string.cancel, null);
	}

	protected void startDialog(AlertDialog dialog) {}
	protected void stopDialog(AlertDialog dialog) {}
	protected void saveState(AlertDialog dialog, Bundle outState) {}

	public void setNeutralButton(CharSequence text, Runnable listener) {
		neutralButtonText = text;
		neutralButtonListener = listener;
	}

	public void setDescription(CharSequence description) {
		this.description = description;
	}
}
