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

import org.json.JSONArray;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;

import chan.util.StringUtils;

import com.mishiranu.dashchan.widget.SafePasteEditText;

public class MultipleEditTextPreference extends DialogPreference
{
	private final EditText[] mEditTexts;
	private final String[] mTexts;
	
	private final int mContainerId;
	
	public MultipleEditTextPreference(Context context, int count)
	{
		super(context, null, android.R.attr.editTextPreferenceStyle);
		mContainerId = context.getResources().getIdentifier("edittext_container", "id", "android");
		mEditTexts = new SafePasteEditText[count];
		mTexts = new String[count];
		for (int i = 0; i < count; i++)
		{
			EditText editText = new SafePasteEditText(context);
			editText.setId(i + 1);
			mEditTexts[i] = editText;
		}
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
		for (int i = 0; i < length; i++) mTexts[i] = values[i]; 
		persistString(packValues(values));
	}
	
	public String getText(int index)
	{
		return mTexts[index];
	}
	
	public String formatValues(String format)
	{
		for (int i = 0; i < mTexts.length; i++)
		{
			if (StringUtils.isEmpty(mTexts[i])) return null;
		}
		return String.format(format, (Object[]) mTexts);
	}
	
	public void setHints(CharSequence[] hints)
	{
		if (hints != null)
		{
			int length = Math.min(hints.length, mEditTexts.length);
			for (int i = 0; i < length; i++) mEditTexts[i].setHint(hints[i]);
		}
	}
	
	public void setInputTypes(int[] types)
	{
		if (types != null)
		{
			int length = Math.min(types.length, mEditTexts.length);
			for (int i = 0; i < length; i++) mEditTexts[i].setInputType(types[i]);
		}
	}
	
	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);
		for (int i = 0; i < mEditTexts.length; i++)
		{
			EditText editText = mEditTexts[i];
			editText.setText(mTexts[i]);
			ViewParent oldParent = editText.getParent();
			if (oldParent != view)
			{
				if (oldParent != null) ((ViewGroup) oldParent).removeView(editText);
				onAddEditTextToDialogView(view, editText);
			}
		}
	}
	
	protected void onAddEditTextToDialogView(View dialogView, EditText editText)
	{
		ViewGroup container = (ViewGroup) dialogView.findViewById(mContainerId);
		if (container != null)
		{
			container.addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		if (positiveResult)
		{
			String[] values = new String[mEditTexts.length];
			for (int i = 0; i < values.length; i++)
			{
				values[i] = StringUtils.nullIfEmpty(mEditTexts[i].getText().toString());
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