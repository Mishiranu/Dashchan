/*
 * Copyright 2014-2016 Fukurou Mishiranu
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

public class MultipleEditTextPreference extends DialogPreference
{
	private final ViewHolder[] mViewHolders;
	private final String[] mTexts;
	
	private final int mContainerId;
	
	public MultipleEditTextPreference(Context context, int count)
	{
		super(context, null, android.R.attr.editTextPreferenceStyle);
		mContainerId = context.getResources().getIdentifier("edittext_container", "id", "android");
		mViewHolders = new ViewHolder[count];
		mTexts = new String[count];
		for (int i = 0; i < count; i++) mViewHolders[i] = new EditTextViewHolder(context, i);
	}
	
	private String packValues(String[] values)
	{
		return new JSONArray(Arrays.asList(values)).toString();
	}
	
	private String[] unpackValues(String value)
	{
		return Preferences.unpackOrCastMultipleValues(value, mTexts.length);
	}
	
	public void setTexts(String[] values)
	{
		int length = Math.min(values.length, mTexts.length);
		System.arraycopy(values, 0, mTexts, 0, length);
		persistString(packValues(values));
	}
	
	public String getText(int index)
	{
		return mTexts[index];
	}
	
	public String formatValues(String format)
	{
		StringBuilder builder = new StringBuilder(format);
		int index;
		for (int i = 0; i < mTexts.length && (index = builder.indexOf("%s")) >= 0; i++)
		{
			if (mTexts[i] == null) return null;
			builder.replace(index, index + 2, mTexts[i]);
		}
		return builder.toString();
	}
	
	public void setHints(CharSequence[] hints)
	{
		if (hints != null)
		{
			int length = Math.min(hints.length, mTexts.length);
			for (int i = 0; i < length; i++) mViewHolders[i].setHint(hints[i]);
		}
	}
	
	public void setInputTypes(int[] types)
	{
		if (types != null)
		{
			int length = Math.min(types.length, mTexts.length);
			for (int i = 0; i < length; i++) mViewHolders[i].setInputType(types[i]);
		}
	}
	
	public void replaceWithDropdown(int index, CharSequence[] entries, String[] values)
	{
		mViewHolders[index] = new DropdownViewHolder(getContext(), index, entries, values);
	}
	
	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);
		for (int i = 0; i < mTexts.length; i++)
		{
			ViewHolder holder = mViewHolders[i];
			holder.setValue(mTexts[i]);
			View child = holder.getView();
			ViewParent oldParent = child.getParent();
			if (oldParent != view)
			{
				if (oldParent != null) ((ViewGroup) oldParent).removeView(child);
				onAddEditTextToDialogView(view, child);
			}
		}
	}
	
	protected void onAddEditTextToDialogView(View dialogView, View view)
	{
		ViewGroup container = (ViewGroup) dialogView.findViewById(mContainerId);
		if (container != null)
		{
			container.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		if (positiveResult)
		{
			String[] values = new String[mTexts.length];
			for (int i = 0; i < values.length; i++)
			{
				values[i] = StringUtils.nullIfEmpty(mViewHolders[i].getValue());
			}
			if (callChangeListener(values)) setTexts(values);
		}
	}
	
	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		setTexts(unpackValues(restoreValue ? getPersistedString(packValues(mTexts)) : (String) defaultValue));
	}
	
	@Override
	public boolean shouldDisableDependents()
	{
		return false;
	}
	
	@Override
	protected Parcelable onSaveInstanceState()
	{
		Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) return superState;
		return new SavedState(superState, mTexts);
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (state == null || !state.getClass().equals(SavedState.class))
		{
			super.onRestoreInstanceState(state);
			return;
		}
		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setTexts(myState.values);
	}
	
	private interface ViewHolder
	{
		public void setHint(CharSequence hint);
		public void setInputType(int type);
		public void setValue(String value);
		public String getValue();
		public View getView();
	}
	
	private static class EditTextViewHolder implements ViewHolder
	{
		private final SafePasteEditText mEditText;
		
		public EditTextViewHolder(Context context, int id)
		{
			mEditText = new SafePasteEditText(context);
			mEditText.setId(id);
		}
		
		@Override
		public void setHint(CharSequence hint)
		{
			mEditText.setHint(hint);
		}
		
		@Override
		public void setInputType(int type)
		{
			mEditText.setInputType(type);
		}
		
		@Override
		public void setValue(String value)
		{
			mEditText.setText(value);
		}
		
		@Override
		public String getValue()
		{
			return mEditText.getText().toString();
		}
		
		@Override
		public View getView()
		{
			return mEditText;
		}
	}
	
	private static class DropdownViewHolder implements ViewHolder
	{
		private final DropdownView mDropdownView;
		private final List<String> mValues;
		
		public DropdownViewHolder(Context context, int id, CharSequence[] entries, String[] values)
		{
			mDropdownView = new DropdownView(context);
			mDropdownView.setId(id);
			mDropdownView.setItems(Arrays.asList(entries));
			mValues = Arrays.asList(values);
		}
		
		@Override
		public void setHint(CharSequence hint)
		{
			
		}
		
		@Override
		public void setInputType(int type)
		{
			
		}
		
		@Override
		public void setValue(String value)
		{
			int position = mValues.indexOf(value);
			if (position == -1) position = 0;
			mDropdownView.setSelection(position);
		}
		
		@Override
		public String getValue()
		{
			return mValues.get(mDropdownView.getSelectedItemPosition());
		}
		
		@Override
		public View getView()
		{
			return mDropdownView;
		}
	}
	
	private static class SavedState extends BaseSavedState
	{
		public final String[] values;
		
		public SavedState(Parcel source)
		{
			super(source);
			values = source.createStringArray();
		}
		
		public SavedState(Parcelable superState, String[] values)
		{
			super(superState);
			this.values = values;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			super.writeToParcel(dest, flags);
			dest.writeStringArray(values);
		}
		
		@SuppressWarnings("unused")
		public static final Creator<SavedState> CREATOR = new Creator<SavedState>()
		{
			public SavedState createFromParcel(Parcel source)
			{
				return new SavedState(source);
			}
			
			public SavedState[] newArray(int size)
			{
				return new SavedState[size];
			}
		};
	}
}