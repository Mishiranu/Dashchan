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

package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class SeekBarForm implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener
{
	private final boolean mShowSwitch;
	private int mMinValue = 0;
	private int mMaxValue = 100;
	private int mStep = 10;
	private float mMultipler = 1f;
	private String mValueFormat;

	private int mCurrentValue;
	private boolean mSwitchValue;

	private SeekBar mSeekBar;
	private Switch mSwitch;
	private TextView mValueText;

	public SeekBarForm(boolean showSwitch)
	{
		mShowSwitch = showSwitch;
	}

	public void setConfiguration(int minValue, int maxValue, int step, float multipler)
	{
		mMaxValue = maxValue;
		mMinValue = minValue;
		mStep = step;
		mMultipler = multipler;
	}

	public void setValueFormat(String valueFormat)
	{
		mValueFormat = valueFormat;
	}

	@SuppressLint("InflateParams")
	public View inflate(Context context)
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

	public float getMultipler()
	{
		return mMultipler;
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