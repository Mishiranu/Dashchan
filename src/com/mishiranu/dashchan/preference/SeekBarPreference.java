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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class SeekBarPreference extends DialogPreference
{
	public static class Holder implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener
	{
		private boolean mShowSwitch;
		private int mMaxValue;
		private int mMinValue;
		private int mStep;
		private float mMultipler;
		private String mValueFormat;
		
		private int mCurrentValue;
		private boolean mSwitchValue;
		
		private SeekBar mSeekBar;
		private Switch mSwitch;
		private TextView mValueText;
		
		public Holder(boolean showSwitch, int minValue, int maxValue, int step, float multipler, String valueFormat)
		{
			mShowSwitch = showSwitch;
			mMaxValue = maxValue;
			mMinValue = minValue;
			mStep = step;
			mMultipler = multipler;
			mValueFormat = valueFormat;
		}
		
		@SuppressLint("InflateParams")
		public View create(Context context)
		{
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View view = inflater.inflate(R.layout.dialog_seek_bar_preference, null);
			((TextView) view.findViewById(R.id.min_value)).setText(Integer.toString((int) (mMinValue * mMultipler)));
			((TextView) view.findViewById(R.id.max_value)).setText(Integer.toString((int) (mMaxValue * mMultipler)));
			mSeekBar = (SeekBar) view.findViewById(R.id.seek_bar);
			mSeekBar.setMax((mMaxValue - mMinValue) / mStep);
			mSeekBar.setProgress((mCurrentValue - mMinValue) / mStep);
			mSeekBar.setOnSeekBarChangeListener(this);
			mSwitch = (Switch) view.findViewById(R.id.switch_view);
			if (!mShowSwitch) mSwitch.setVisibility(View.GONE); else
			{
				mSwitch.setChecked(mSwitchValue);
				mSwitch.setOnCheckedChangeListener(this);
				if (C.API_LOLLIPOP) ((ViewGroup.MarginLayoutParams) mSwitch.getLayoutParams()).rightMargin = 0;
			}
			mValueText = (TextView) view.findViewById(R.id.current_value);
			updateCurrentValueText();
			return view;
		}
		
		public void setCurrentValue(int currentValue)
		{
			mCurrentValue = currentValue;
		}
		
		public void setSwitchValue(boolean switchValue)
		{
			mSwitchValue = switchValue;
		}
		
		public int getCurrentValue()
		{
			return mCurrentValue;
		}
		
		public boolean getSwitchValue()
		{
			return mSwitchValue;
		}
		
		public void updateCurrentValueText()
		{
			int currentValue = (int) (mMultipler * mCurrentValue);
			String currentValueText = mValueFormat != null ? String.format(mValueFormat, currentValue)
					: Integer.toString(currentValue);
			mValueText.setText(currentValueText);
		}
		
		@Override
		public void onProgressChanged(SeekBar seek, int value, boolean fromTouch)
		{
			mCurrentValue = value * mStep + mMinValue;
			updateCurrentValueText();
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seek)
		{
			
		}
		
		@Override
		public void onStopTrackingTouch(SeekBar seek)
		{
			
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			mSwitchValue = isChecked;
		}
		
		public SeekBar getSeekBar()
		{
			return mSeekBar;
		}
	}
	
	private final Holder mHolder;
	private int mDefaultValue;
	
	public SeekBarPreference(Context context)
	{
		super(context, null);
		mHolder = new Holder(false, 0, 100, 10, 1f, null);
	}
	
	public void setSeekBarConfig(int minValue, int maxValue, int step, float multiplier)
	{
		mHolder.mMinValue = minValue;
		mHolder.mMaxValue = maxValue;
		mHolder.mStep = step;
		mHolder.mMultipler = multiplier;
	}
	
	@Override
	public void setSummary(CharSequence summary)
	{
		super.setSummary(summary);
		mHolder.mValueFormat = summary != null ? summary.toString() : null;
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
		mHolder.setCurrentValue(getPersistedInt(mDefaultValue));
		return mHolder.create(getContext());
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		if (!positiveResult) return;
		if (shouldPersist()) persistInt(mHolder.getCurrentValue());
		notifyChanged();
	}
	
	@Override
	public CharSequence getSummary()
	{
		String summary = super.getSummary().toString();
		if (summary != null)
		{
			int value = (int) (getPersistedInt(mDefaultValue) * mHolder.mMultipler);
			return String.format(summary, value);
		}
		return summary;
	}
}