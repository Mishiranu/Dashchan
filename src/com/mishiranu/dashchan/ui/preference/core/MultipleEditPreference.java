package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.widget.DropdownView;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MultipleEditPreference<T> extends DialogPreference<T> {
	private static final String EXTRA_FOCUS = "focus";

	public final List<CharSequence> hints;
	public final List<Integer> inputTypes;
	private final SparseArray<Pair<List<CharSequence>, List<String>>> values = new SparseArray<>();
	private final ValueCodec<T> valueCodec;
	private int lastFocusIndex;

	public MultipleEditPreference(Context context, String key, CharSequence title,
			SummaryProvider<T> summaryProvider, List<CharSequence> hints, List<Integer> inputTypes,
			ValueCodec<T> valueCodec) {
		super(context, key, null, title, summaryProvider);
		this.hints = hints;
		this.inputTypes = inputTypes;
		this.valueCodec = valueCodec;
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(valueCodec.fromString(preferences.getString(key, null)));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putString(key, valueCodec.toString(getValue())).commit();
	}

	public static <T> String formatValues(ValueCodec<T> valueCodec, String format, T value) {
		StringBuilder builder = new StringBuilder(format);
		int index;
		for (int i = 0; i < valueCodec.getCount() && (index = builder.indexOf("%s")) >= 0; i++) {
			String stringValue = valueCodec.getValueAt(value, i);
			if (stringValue == null) {
				return null;
			}
			builder.replace(index, index + 2, stringValue);
		}
		return builder.toString();
	}

	public void setValues(int index, List<CharSequence> entries, List<String> values) {
		if (entries == null || values == null) {
			this.values.remove(index);
		} else {
			if (entries.size() != values.size()) {
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
		Pair<View, LinearLayout> pair = createDialogLayout(builder.getContext());
		ArrayList<ViewHolder> viewHolders = new ArrayList<>();
		for (int i = 0; i < valueCodec.getCount(); i++) {
			Pair<List<CharSequence>, List<String>> values = this.values.get(i);
			String value = valueCodec.getValueAt(getValue(), i);
			ViewHolder viewHolder;
			if (values != null) {
				viewHolder = new DropdownViewHolder(builder.getContext(), values.first, values.second, value);
			} else {
				CharSequence hint = hints != null && hints.size() > i ? hints.get(i) : null;
				int inputType = inputTypes != null && inputTypes.size() > i
						? inputTypes.get(i) : InputType.TYPE_CLASS_TEXT;
				viewHolder = new EditTextViewHolder(builder.getContext(), hint, inputType, value);
			}
			viewHolders.add(viewHolder);
			pair.second.addView(viewHolder.getView());
			viewHolder.getView().setTag(viewHolder);
			if (savedInstanceState != null) {
				viewHolder.restoreState(savedInstanceState, i);
			}
		}
		lastFocusIndex = -1;
		int focusIndex = 0;
		if (savedInstanceState != null) {
			focusIndex = savedInstanceState.getInt(EXTRA_FOCUS, -1);
		}
		if (focusIndex >= 0) {
			restoreFocusIndex(pair.second, focusIndex);
		}
		return super.configureDialog(savedInstanceState, builder).setView(pair.first)
				.setPositiveButton(android.R.string.ok, (d, which) -> ConcurrentUtils.HANDLER.post(() -> {
					List<String> values = new ArrayList<>(viewHolders.size());
					for (ViewHolder viewHolder : viewHolders) {
						values.add(StringUtils.nullIfEmpty(viewHolder.getValue()));
					}
					setValue(valueCodec.createValue(values));
				}));
	}

	private void restoreFocusIndex(LinearLayout layout, int focusIndex) {
		int index = 0;
		int childCount = layout.getChildCount();
		for (int i = 0; i < childCount; i++) {
			ViewHolder viewHolder = (ViewHolder) layout.getChildAt(i).getTag();
			if (viewHolder != null) {
				if (index++ == focusIndex) {
					viewHolder.getView().requestFocus();
					return;
				}
			}
		}
	}

	private int getFocusIndex(LinearLayout layout) {
		int index = 0;
		int childCount = layout.getChildCount();
		for (int i = 0; i < childCount; i++) {
			ViewHolder viewHolder = (ViewHolder) layout.getChildAt(i).getTag();
			if (viewHolder != null) {
				if (viewHolder.getView().hasFocus()) {
					return index;
				}
				index++;
			}
		}
		return -1;
	}

	@Override
	protected void startDialog(AlertDialog dialog) {
		super.startDialog(dialog);

		if (lastFocusIndex >= 0) {
			restoreFocusIndex(getDialogLayout(dialog), lastFocusIndex);
			lastFocusIndex = -1;
		}
	}

	@Override
	protected void stopDialog(AlertDialog dialog) {
		super.stopDialog(dialog);
		lastFocusIndex = getFocusIndex(getDialogLayout(dialog));
	}

	@Override
	protected void saveState(AlertDialog dialog, Bundle outState) {
		super.saveState(dialog, outState);

		int index = 0;
		LinearLayout layout = getDialogLayout(dialog);
		int childCount = layout.getChildCount();
		for (int i = 0; i < childCount; i++) {
			ViewHolder viewHolder = (ViewHolder) layout.getChildAt(i).getTag();
			if (viewHolder != null) {
				viewHolder.saveState(outState, index++);
			}
		}
		int focusIndex = lastFocusIndex;
		if (focusIndex == -1) {
			focusIndex = getFocusIndex(layout);
		}
		outState.putInt(EXTRA_FOCUS, focusIndex);
	}

	private interface ViewHolder {
		String getValue();
		View getView();
		void restoreState(Bundle bundle, int index);
		void saveState(Bundle bundle, int index);
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
		private final List<String> values;

		public DropdownViewHolder(Context context, List<CharSequence> entries, List<String> values, String value) {
			dropdownView = new DropdownView(context);
			dropdownView.setItems(entries);
			this.values = values;
			dropdownView.setSelection(Math.max(0, values.indexOf(value)));
		}

		@Override
		public String getValue() {
			return values.get(dropdownView.getSelectedItemPosition());
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

	public interface ValueCodec<T> {
		int getCount();
		T fromString(String value);
		String toString(T value);
		String getValueAt(T value, int index);
		T createValue(List<String> values);
	}

	public static class ListValueCodec implements ValueCodec<List<String>> {
		private final int count;

		public ListValueCodec(int count) {
			this.count = count;
		}

		@Override
		public int getCount() {
			return count;
		}

		@Override
		public List<String> fromString(String value) {
			return Preferences.unpackOrCastMultipleValues(value, count);
		}

		@Override
		public String toString(List<String> value) {
			return new JSONArray(value).toString();
		}

		@Override
		public String getValueAt(List<String> value, int index) {
			return value.get(index);
		}

		@Override
		public List<String> createValue(List<String> values) {
			return values;
		}
	}

	public static class MapValueCodec implements ValueCodec<Map<String, String>> {
		private final List<String> keys;

		public MapValueCodec(List<String> keys) {
			this.keys = keys;
		}

		@Override
		public int getCount() {
			return keys.size();
		}

		@Override
		public Map<String, String> fromString(String value) {
			return Preferences.unpackOrCastMultipleValues(value, keys);
		}

		@Override
		public String toString(Map<String, String> value) {
			JSONObject jsonObject = new JSONObject();
			for (Map.Entry<String, String> entry : value.entrySet()) {
				try {
					jsonObject.put(entry.getKey(), entry.getValue());
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
			return jsonObject.toString();
		}

		@Override
		public String getValueAt(Map<String, String> value, int index) {
			return value.get(keys.get(index));
		}

		@Override
		public Map<String, String> createValue(List<String> values) {
			HashMap<String, String> map = new HashMap<>();
			for (int i = 0; i < keys.size(); i++) {
				String value = values.get(i);
				if (!StringUtils.isEmpty(value)) {
					map.put(keys.get(i), value);
				}
			}
			return map;
		}
	}
}
