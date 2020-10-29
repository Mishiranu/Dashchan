package com.mishiranu.dashchan.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;

public class SeekBarForm {
	private final boolean showSwitch;
	private final int minValue;
	private final int maxValue;
	private final int step;
	private final String valueFormat;

	private int currentValue;
	private boolean switchValue;

	private SeekBar seekBar;
	private TextView valueText;

	public SeekBarForm(boolean showSwitch, int minValue, int maxValue, int step, String valueFormat) {
		this.showSwitch = showSwitch;
		this.maxValue = maxValue;
		this.minValue = minValue;
		this.step = step;
		this.valueFormat = valueFormat;
	}

	@SuppressLint("InflateParams")
	public View inflate(Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dialog_seek_bar_preference, null);
		((TextView) view.findViewById(R.id.min_value)).setText(Integer.toString(minValue));
		((TextView) view.findViewById(R.id.max_value)).setText(Integer.toString(maxValue));
		seekBar = view.findViewById(R.id.seek_bar);
		seekBar.setMax((maxValue - minValue) / step);
		seekBar.setProgress((currentValue - minValue) / step);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				currentValue = progress * step + minValue;
				updateCurrentValueText();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		seekBar.setEnabled(!showSwitch || switchValue);
		Switch switchView = view.findViewById(R.id.switch_view);
		if (!showSwitch) {
			switchView.setVisibility(View.GONE);
		} else {
			switchView.setChecked(switchValue);
			switchView.setOnCheckedChangeListener((v, isChecked) -> {
				switchValue = isChecked;
				seekBar.setEnabled(isChecked);
			});
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
		return Math.max(minValue, Math.min(currentValue, maxValue));
	}

	public boolean getSwitchValue() {
		return switchValue;
	}

	public void updateCurrentValueText() {
		int value = getCurrentValue();
		String text = valueFormat != null ? String.format(valueFormat, value) : Integer.toString(value);
		valueText.setText(text);
	}

	public SeekBar getSeekBar() {
		return seekBar;
	}
}
