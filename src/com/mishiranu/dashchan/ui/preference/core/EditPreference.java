package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.SafePasteEditText;

public class EditPreference extends DialogPreference<String> {
	public final CharSequence hint;
	public final int inputType;

	private CharSequence description;

	public EditPreference(Context context, String key, String defaultValue,
			CharSequence title, SummaryProvider<String> summaryProvider, CharSequence hint, int inputType) {
		super(context, key, defaultValue, title, summaryProvider);
		this.hint = hint;
		this.inputType = inputType;
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(preferences.getString(key, defaultValue));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putString(key, getValue()).commit();
	}

	protected static Pair<View, LinearLayout> createDialogLayout(Context context) {
		float density = ResourceUtils.obtainDensity(context);
		int padding = (int) ((C.API_LOLLIPOP ? 20f : 5f) * density);
		ScrollView scrollView = new ScrollView(context);
		scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS);
		LinearLayout linearLayout = new LinearLayout(scrollView.getContext());
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setPadding(padding, padding, padding, padding);
		scrollView.addView(linearLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		return new Pair<>(scrollView, linearLayout);
	}

	@Override
	protected AlertDialog createDialog(Bundle savedInstanceState) {
		AlertDialog alertDialog = super.createDialog(savedInstanceState);
		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		return alertDialog;
	}

	@Override
	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		Pair<View, LinearLayout> pair = createDialogLayout(builder.getContext());
		SafePasteEditText editText = new SafePasteEditText(pair.second.getContext());
		editText.setId(android.R.id.edit);
		editText.setHint(hint);
		editText.setInputType(inputType);
		editText.setText(getValue());
		editText.setSelection(editText.getText().length());
		editText.requestFocus();
		if (!StringUtils.isEmpty(description)) {
			float density = ResourceUtils.obtainDensity(context);
			TextView descriptionView = new TextView(pair.second.getContext(), null,
					android.R.attr.textAppearanceListItemSmall);
			descriptionView.setPadding(editText.getPaddingLeft(), 0, editText.getPaddingRight(), (int) (8f * density));
			descriptionView.setText(description);
			pair.second.addView(descriptionView, 0, new LinearLayout
					.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		}
		pair.second.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		return super.configureDialog(savedInstanceState, builder).setView(pair.first)
				.setPositiveButton(android.R.string.ok, (d, which) -> new Handler()
						.post(() -> setValue(editText.getText().toString())));
	}

	public void setInput(AlertDialog dialog, String input) {
		SafePasteEditText editText = dialog.findViewById(android.R.id.edit);
		if (editText != null) {
			editText.setText(input);
		}
	}

	public void setDescription(CharSequence description) {
		this.description = description;
	}
}
