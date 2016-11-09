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

public class SeekBarForm implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
	private final boolean showSwitch;
	private int minValue = 0;
	private int maxValue = 100;
	private int step = 10;
	private float multipler = 1f;
	private String valueFormat;

	private int currentValue;
	private boolean switchValue;

	private SeekBar seekBar;
	private TextView valueText;

	public SeekBarForm(boolean showSwitch) {
		this.showSwitch = showSwitch;
	}

	public void setConfiguration(int minValue, int maxValue, int step, float multipler) {
		this.maxValue = maxValue;
		this.minValue = minValue;
		this.step = step;
		this.multipler = multipler;
	}

	public void setValueFormat(String valueFormat) {
		this.valueFormat = valueFormat;
	}

	@SuppressLint("InflateParams")
	public View inflate(Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dialog_seek_bar_preference, null);
		((TextView) view.findViewById(R.id.min_value)).setText(Integer.toString((int) (minValue * multipler)));
		((TextView) view.findViewById(R.id.max_value)).setText(Integer.toString((int) (maxValue * multipler)));
		seekBar = (SeekBar) view.findViewById(R.id.seek_bar);
		seekBar.setMax((maxValue - minValue) / step);
		seekBar.setProgress((currentValue - minValue) / step);
		seekBar.setOnSeekBarChangeListener(this);
		Switch switchView = (Switch) view.findViewById(R.id.switch_view);
		if (!showSwitch) {
			switchView.setVisibility(View.GONE);
		} else {
			switchView.setChecked(switchValue);
			switchView.setOnCheckedChangeListener(this);
			if (C.API_LOLLIPOP) {
				((ViewGroup.MarginLayoutParams) switchView.getLayoutParams()).rightMargin = 0;
			}
		}
		valueText = (TextView) view.findViewById(R.id.current_value);
		updateCurrentValueText();
		return view;
	}

	public void setCurrentValue(int currentValue) {
		this.currentValue = currentValue;
	}

	public void setSwitchValue(boolean switchValue) {
		this.switchValue = switchValue;
	}

	public int getCurrentValue() {
		return currentValue;
	}

	public float getMultipler() {
		return multipler;
	}

	public boolean getSwitchValue() {
		return switchValue;
	}

	public void updateCurrentValueText() {
		int currentValue = (int) (multipler * this.currentValue);
		String currentValueText = valueFormat != null ? String.format(valueFormat, currentValue)
				: Integer.toString(currentValue);
		valueText.setText(currentValueText);
	}

	@Override
	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
		currentValue = value * step + minValue;
		updateCurrentValueText();
	}

	@Override
	public void onStartTrackingTouch(SeekBar seek) {}

	@Override
	public void onStopTrackingTouch(SeekBar seek) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		switchValue = isChecked;
	}

	public SeekBar getSeekBar() {
		return seekBar;
	}
}