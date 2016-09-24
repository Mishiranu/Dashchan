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

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.view.View;

import com.mishiranu.dashchan.ui.SeekBarForm;

public class SeekBarPreference extends DialogPreference
{
	private final SeekBarForm mSeekBarForm;
	private int mDefaultValue;

	public SeekBarPreference(Context context)
	{
		super(context, null);
		mSeekBarForm = new SeekBarForm(false);
	}

	public void setSeekBarConfiguration(int minValue, int maxValue, int step, float multiplier)
	{
		mSeekBarForm.setConfiguration(minValue, maxValue, step, multiplier);
	}

	@Override
	public void setSummary(CharSequence summary)
	{
		super.setSummary(summary);
		mSeekBarForm.setValueFormat(summary != null ? summary.toString() : null);
	}

	@Override
	public void setDefaultValue(Object defaultValue)
	{
		super.setDefaultValue(defaultValue);
		mDefaultValue = (int) defaultValue;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		int defaultValue = a.getInt(index, 50);
		mDefaultValue = defaultValue;
		return defaultValue;
	}

	@Override
	protected View onCreateDialogView()
	{
		mSeekBarForm.setCurrentValue(getPersistedInt(mDefaultValue));
		return mSeekBarForm.inflate(getContext());
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		if (!positiveResult) return;
		if (shouldPersist()) persistInt(mSeekBarForm.getCurrentValue());
		notifyChanged();
	}

	@Override
	public CharSequence getSummary()
	{
		String summary = super.getSummary().toString();
		if (summary != null)
		{
			int value = (int) (getPersistedInt(mDefaultValue) * mSeekBarForm.getMultipler());
			return String.format(summary, value);
		}
		return summary;
	}
}