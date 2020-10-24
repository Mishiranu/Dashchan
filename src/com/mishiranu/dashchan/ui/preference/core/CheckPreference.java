package com.mishiranu.dashchan.ui.preference.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class CheckPreference extends Preference<Boolean> {
	public CheckPreference(Context context, String key, boolean defaultValue,
			CharSequence title, CharSequence summary) {
		super(context, key, defaultValue, title, p -> summary);
	}

	@Override
	protected void extract(SharedPreferences preferences) {
		setValue(preferences.getBoolean(key, defaultValue));
	}

	@Override
	protected void persist(SharedPreferences preferences) {
		preferences.edit().putBoolean(key, getValue()).commit();
	}

	@Override
	public ViewType getViewType() {
		return ViewType.CHECK;
	}

	public static class CheckViewHolder extends ViewHolder {
		public final CheckBox check;

		public CheckViewHolder(ViewHolder viewHolder, CheckBox check) {
			super(viewHolder);
			this.check = check;
		}
	}

	@Override
	public CheckViewHolder createViewHolder(ViewGroup parent) {
		ViewHolder viewHolder = super.createViewHolder(parent);
		viewHolder.widgetFrame.setVisibility(View.VISIBLE);
		CheckBox check = new CheckBox(viewHolder.widgetFrame.getContext());
		ThemeEngine.applyStyle(check);
		check.setClickable(false);
		check.setFocusable(false);
		viewHolder.widgetFrame.addView(check, ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		return new CheckViewHolder(viewHolder, check);
	}

	@Override
	public void bindViewHolder(ViewHolder viewHolder) {
		super.bindViewHolder(viewHolder);

		if (viewHolder instanceof CheckViewHolder) {
			CheckViewHolder checkViewHolder = (CheckViewHolder) viewHolder;
			if (checkViewHolder.check != null) {
				checkViewHolder.check.setChecked(getValue());
				checkViewHolder.check.setEnabled(isEnabled());
			}
		}
	}
}
