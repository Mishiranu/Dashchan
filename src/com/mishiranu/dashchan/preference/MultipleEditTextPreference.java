/*
 * Copyright 2014-2017 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.preference;

import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import chan.util.StringUtils;

import com.mishiranu.dashchan.widget.DropdownView;
import com.mishiranu.dashchan.widget.SafePasteEditText;

public class MultipleEditTextPreference extends DialogPreference {
	private final ViewHolder[] viewHolders;
	private final String[] texts;

	private final int containerId;

	public MultipleEditTextPreference(Context context, int count) {
		super(context, null, android.R.attr.editTextPreferenceStyle);
		containerId = context.getResources().getIdentifier("edittext_container", "id", "android");
		viewHolders = new ViewHolder[count];
		texts = new String[count];
		for (int i = 0; i < count; i++) {
			viewHolders[i] = new EditTextViewHolder(context, i);
		}
	}

	private String packValues(String[] values) {
		return new JSONArray(Arrays.asList(values)).toString();
	}

	private String[] unpackValues(String value) {
		return Preferences.unpackOrCastMultipleValues(value, texts.length);
	}

	public void setTexts(String[] values) {
		int length = Math.min(values.length, texts.length);
		System.arraycopy(values, 0, texts, 0, length);
		persistString(packValues(values));
	}

	public String getText(int index) {
		return texts[index];
	}

	public String formatValues(String format) {
		StringBuilder builder = new StringBuilder(format);
		int index;
		for (int i = 0; i < texts.length && (index = builder.indexOf("%s")) >= 0; i++) {
			if (texts[i] == null) {
				return null;
			}
			builder.replace(index, index + 2, texts[i]);
		}
		return builder.toString();
	}

	public void setHints(CharSequence[] hints) {
		if (hints != null) {
			int length = Math.min(hints.length, texts.length);
			for (int i = 0; i < length; i++) {
				viewHolders[i].setHint(hints[i]);
			}
		}
	}

	public void setInputTypes(int[] types) {
		if (types != null) {
			int length = Math.min(types.length, texts.length);
			for (int i = 0; i < length; i++) {
				viewHolders[i].setInputType(types[i]);
			}
		}
	}

	public void replaceWithDropdown(int index, CharSequence[] entries, String[] values) {
		viewHolders[index] = new DropdownViewHolder(getContext(), index, entries, values);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		for (int i = 0; i < texts.length; i++) {
			ViewHolder holder = viewHolders[i];
			holder.setValue(texts[i]);
			View child = holder.getView();
			ViewParent oldParent = child.getParent();
			if (oldParent != view) {
				if (oldParent != null) {
					((ViewGroup) oldParent).removeView(child);
				}
				onAddEditTextToDialogView(view, child);
			}
		}
	}

	protected void onAddEditTextToDialogView(View dialogView, View view) {
		ViewGroup container = dialogView.findViewById(containerId);
		if (container != null) {
			container.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		if (positiveResult) {
			String[] values = new String[texts.length];
			for (int i = 0; i < values.length; i++) {
				values[i] = StringUtils.nullIfEmpty(viewHolders[i].getValue());
			}
			if (callChangeListener(values)) {
				setTexts(values);
			}
		}
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setTexts(unpackValues(restoreValue ? getPersistedString(packValues(texts)) : (String) defaultValue));
	}

	@Override
	public boolean shouldDisableDependents() {
		return false;
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			return superState;
		}
		return new SavedState(superState, texts);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			super.onRestoreInstanceState(state);
		} else {
			SavedState myState = (SavedState) state;
			super.onRestoreInstanceState(myState.getSuperState());
			setTexts(myState.values);
		}
	}

	private interface ViewHolder {
		public void setHint(CharSequence hint);
		public void setInputType(int type);
		public void setValue(String value);
		public String getValue();
		public View getView();
	}

	private static class EditTextViewHolder implements ViewHolder {
		private final SafePasteEditText editText;

		public EditTextViewHolder(Context context, int id) {
			editText = new SafePasteEditText(context);
			editText.setId(id);
		}

		@Override
		public void setHint(CharSequence hint) {
			editText.setHint(hint);
		}

		@Override
		public void setInputType(int type) {
			editText.setInputType(type);
		}

		@Override
		public void setValue(String value) {
			editText.setText(value);
		}

		@Override
		public String getValue() {
			return editText.getText().toString();
		}

		@Override
		public View getView() {
			return editText;
		}
	}

	private static class DropdownViewHolder implements ViewHolder {
		private final DropdownView dropdownView;
		private final List<String> values;

		public DropdownViewHolder(Context context, int id, CharSequence[] entries, String[] values) {
			dropdownView = new DropdownView(context);
			dropdownView.setId(id);
			dropdownView.setItems(Arrays.asList(entries));
			this.values = Arrays.asList(values);
		}

		@Override
		public void setHint(CharSequence hint) {}

		@Override
		public void setInputType(int type) {}

		@Override
		public void setValue(String value) {
			int position = values.indexOf(value);
			if (position == -1) {
				position = 0;
			}
			dropdownView.setSelection(position);
		}

		@Override
		public String getValue() {
			return values.get(dropdownView.getSelectedItemPosition());
		}

		@Override
		public View getView() {
			return dropdownView;
		}
	}

	private static class SavedState extends BaseSavedState {
		public final String[] values;

		public SavedState(Parcel source) {
			super(source);
			values = source.createStringArray();
		}

		public SavedState(Parcelable superState, String[] values) {
			super(superState);
			this.values = values;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeStringArray(values);
		}

		@SuppressWarnings("unused")
		public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
			public SavedState createFromParcel(Parcel source) {
				return new SavedState(source);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}
