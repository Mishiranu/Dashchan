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
	private float multiplier = 1f;
	private String valueFormat;

	private int currentValue;
	private boolean switchValue;

	private SeekBar seekBar;
	private TextView valueText;

	public SeekBarForm(boolean showSwitch) {
		this.showSwitch = showSwitch;
	}

	public void setConfiguration(int minValue, int maxValue, int step, float multiplier) {
		this.maxValue = maxValue;
		this.minValue = minValue;
		this.step = step;
		this.multiplier = multiplier;
	}

	public void setValueFormat(String valueFormat) {
		this.valueFormat = valueFormat;
	}

	@SuppressLint("InflateParams")
	public View inflate(Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dialog_seek_bar_preference, null);
		((TextView) view.findViewById(R.id.min_value)).setText(Integer.toString((int) (minValue * multiplier)));
		((TextView) view.findViewById(R.id.max_value)).setText(Integer.toString((int) (maxValue * multiplier)));
		seekBar = view.findViewById(R.id.seek_bar);
		seekBar.setMax((maxValue - minValue) / step);
		seekBar.setProgress((currentValue - minValue) / step);
		seekBar.setOnSeekBarChangeListener(this);
		Switch switchView = view.findViewById(R.id.switch_view);
		if (!showSwitch) {
			switchView.setVisibility(View.GONE);
		} else {
			switchView.setChecked(switchValue);
			switchView.setOnCheckedChangeListener(this);
			if (C.API_LOLLIPOP) {
				((ViewGroup.MarginLayoutParams) switchView.getLayoutParams()).rightMargin = 0;
			}
		}
		valueText = view.findViewById(R.id.current_value);
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

	public float getMultiplier() {
		return multiplier;
	}

	public boolean getSwitchValue() {
		return switchValue;
	}

	public void updateCurrentValueText() {
		int value = (int) (multiplier * currentValue);
		String text = valueFormat != null ? String.format(valueFormat, value) : Integer.toString(value);
		valueText.setText(text);
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
