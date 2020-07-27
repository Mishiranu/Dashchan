package com.mishiranu.dashchan.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import chan.util.StringUtils;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.widget.DropdownView;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;

public class MultipleEditTextPreference extends DialogPreference<String[]> {
	private static final String EXTRA_FOCUS = "focus";

	public final CharSequence[] hints;
	public final int[] inputTypes;
	private final SparseArray<Pair<CharSequence[], String[]>> values = new SparseArray<>();

	public MultipleEditTextPreference(Context context, String key,
			CharSequence title, SummaryProvider<String[]> summaryProvider, CharSequence[] hints, int[] inputTypes) {
		super(context, key, null, title, summaryProvider);
		this.hints = hints;
		this.inputTypes = inputTypes;
	}

	private static String packValues(String[] values) {
		return new JSONArray(Arrays.asList(values)).toString();
	}

	private static String[] unpackValues(String value, int count) {
		return Preferences.unpackOrCastMultipleValues(value, count);
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(unpackValues(preferences.getString(key, null), inputTypes.length));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putString(key, packValues(getValue())).commit();
	}

	public static String formatValues(String format, String[] values) {
		StringBuilder builder = new StringBuilder(format);
		int index;
		for (int i = 0; i < values.length && (index = builder.indexOf("%s")) >= 0; i++) {
			if (values[i] == null) {
				return null;
			}
			builder.replace(index, index + 2, values[i]);
		}
		return builder.toString();
	}

	public void setValues(int index, CharSequence[] entries, String[] values) {
		if (entries == null || values == null) {
			this.values.remove(index);
		} else {
			if (entries.length != values.length) {
				throw new IllegalArgumentException();
			}
			this.values.put(index, new Pair<>(entries, values));
		}
	}

	@Override
	protected AlertDialog createDialog(Bundle savedInstanceState) {
		AlertDialog alertDialog = super.createDialog(savedInstanceState);
		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		return alertDialog;
	}

	@Override
	protected AlertDialog.Builder configureDialog(Bundle savedInstanceState, AlertDialog.Builder builder) {
		Pair<View, LinearLayout> pair = EditPreference.createDialogLayout(builder.getContext());
		ArrayList<ViewHolder> viewHolders = new ArrayList<>();
		for (int i = 0; i < inputTypes.length; i++) {
			Pair<CharSequence[], String[]> values = this.values.get(i);
			String value = getValue()[i];
			ViewHolder viewHolder;
			if (values != null) {
				viewHolder = new DropdownViewHolder(builder.getContext(), values.first, values.second, value);
			} else {
				viewHolder = new EditTextViewHolder(builder.getContext(),
						hints != null && hints.length > i ? hints[i] : null, inputTypes[i], value);
			}
			viewHolders.add(viewHolder);
			pair.second.addView(viewHolder.getView());
			viewHolder.getView().setTag(viewHolder);
			if (savedInstanceState != null) {
				viewHolder.restoreState(savedInstanceState, i);
				if (savedInstanceState.getInt(EXTRA_FOCUS, -1) == i) {
					viewHolder.getView().requestFocus();
				}
			}
		}
		pair.second.setId(android.R.id.widget_frame);
		return super.configureDialog(savedInstanceState, builder).setView(pair.first)
				.setPositiveButton(android.R.string.ok, (d, which) -> new Handler().post(() -> {
					String[] value = new String[viewHolders.size()];
					for (int i = 0; i < viewHolders.size(); i++) {
						value[i] = StringUtils.nullIfEmpty(viewHolders.get(i).getValue());
					}
					setValue(value);
				}));
	}

	@Override
	protected void saveState(AlertDialog dialog, Bundle outState) {
		super.saveState(dialog, outState);

		LinearLayout linearLayout = dialog.findViewById(android.R.id.widget_frame);
		for (int i = 0; i < linearLayout.getChildCount(); i++) {
			ViewHolder viewHolder = (ViewHolder) linearLayout.getChildAt(i).getTag();
			viewHolder.saveState(outState, i);
			if (viewHolder.getView().hasFocus()) {
				outState.putInt(EXTRA_FOCUS, i);
			}
		}
	}

	private interface ViewHolder {
		public String getValue();
		public View getView();
		public void restoreState(Bundle bundle, int index);
		public void saveState(Bundle bundle, int index);
	}

	private static class EditTextViewHolder implements ViewHolder {
		private final SafePasteEditText editText;

		public EditTextViewHolder(Context context, CharSequence hint, int inputType, String value) {
			editText = new SafePasteEditText(context);
			editText.setHint(hint);
			editText.setInputType(inputType);
			editText.setText(value);
			editText.setSelection(editText.getText().length());
		}

		@Override
		public String getValue() {
			return editText.getText().toString();
		}

		@Override
		public View getView() {
			return editText;
		}

		@Override
		public void restoreState(Bundle bundle, int index) {
			editText.setText(bundle.getCharSequence("text" + index));
			editText.setSelection(bundle.getInt("selectionStart" + index), bundle.getInt("selectionEnd" + index));
		}

		@Override
		public void saveState(Bundle bundle, int index) {
			bundle.putCharSequence("text" + index, editText.getText());
			bundle.putInt("selectionStart" + index, editText.getSelectionStart());
			bundle.putInt("selectionEnd" + index, editText.getSelectionEnd());
		}
	}

	private static class DropdownViewHolder implements ViewHolder {
		private final DropdownView dropdownView;
		private final String[] values;

		public DropdownViewHolder(Context context, CharSequence[] entries, String[] values, String value) {
			dropdownView = new DropdownView(context);
			dropdownView.setItems(Arrays.asList(entries));
			this.values = values;
			dropdownView.setSelection(Math.max(0, Arrays.asList(values).indexOf(value)));
		}

		@Override
		public String getValue() {
			return values[dropdownView.getSelectedItemPosition()];
		}

		@Override
		public View getView() {
			return dropdownView;
		}

		@Override
		public void restoreState(Bundle bundle, int index) {
			dropdownView.setSelection(bundle.getInt("value" + index));
		}

		@Override
		public void saveState(Bundle bundle, int index) {
			bundle.putInt("value" + index, dropdownView.getSelectedItemPosition());
		}
	}
}
