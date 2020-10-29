package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.widget.SafePasteEditText;

public class EditPreference extends DialogPreference<String> {
	public final CharSequence hint;
	public final int inputType;

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
		pair.second.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		return super.configureDialog(savedInstanceState, builder).setView(pair.first)
				.setPositiveButton(android.R.string.ok, (d, which) -> ConcurrentUtils.HANDLER
						.post(() -> setValue(editText.getText().toString())));
	}
}
