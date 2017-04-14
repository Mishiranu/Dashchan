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

package com.mishiranu.dashchan.preference.fragment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.widget.EditText;

import chan.util.StringUtils;

import com.mishiranu.dashchan.preference.ExtendedEditTextPreference;
import com.mishiranu.dashchan.preference.MultipleEditTextPreference;
import com.mishiranu.dashchan.preference.SeekBarPreference;

public abstract class BasePreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener,
		Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
	private ArrayList<EditTextPreference> summaryListenersEditText;
	private HashMap<MultipleEditTextPreference, String> summaryListenersMultipleEditText;

	private ArrayList<Dependency> dependencies;

	private static abstract class Dependency {
		public final String key, dependencyKey;
		public final boolean positive;

		public Dependency(String key, String dependencyKey, boolean positive) {
			this.key = key;
			this.dependencyKey = dependencyKey;
			this.positive = positive;
		}

		public abstract boolean checkDependency(Preference dependencyPreference);
	}

	private static class BooleanDependency extends Dependency {
		public BooleanDependency(String key, String dependencyKey, boolean positive) {
			super(key, dependencyKey, positive);
		}

		@Override
		public boolean checkDependency(Preference dependencyPreference) {
			if (dependencyPreference instanceof TwoStatePreference) {
				return ((TwoStatePreference) dependencyPreference).isChecked() == positive;
			}
			return false;
		}
	}

	private static class StringDependency extends Dependency {
		private final HashSet<String> values = new HashSet<>();

		public StringDependency(String key, String dependencyKey, boolean positive, String... values) {
			super(key, dependencyKey, positive);
			Collections.addAll(this.values, values);
		}

		@Override
		public boolean checkDependency(Preference dependencyPreference) {
			String value;
			if (dependencyPreference instanceof EditTextPreference) {
				value = ((EditTextPreference) dependencyPreference).getText();
			} else if (dependencyPreference instanceof ListPreference) {
				value = ((ListPreference) dependencyPreference).getValue();
			} else {
				return false;
			}
			return values.contains(value) == positive;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
		getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	public void addPreference(PreferenceGroup parent, Preference preference) {
		if (parent != null) {
			parent.addPreference(preference);
		} else {
			getPreferenceScreen().addPreference(preference);
		}
		preference.setOnPreferenceChangeListener(this);
		preference.setOnPreferenceClickListener(this);
	}

	public PreferenceCategory makeCategory(int titleResId) {
		PreferenceCategory category = new PreferenceCategory(getActivity());
		category.setTitle(titleResId);
		getPreferenceScreen().addPreference(category);
		return category;
	}

	public Preference makeButton(PreferenceGroup parent, int titleResId, int summaryResId, boolean information) {
		return makeButton(parent, titleResId != 0 ? getString(titleResId) : null,
				summaryResId != 0 ? getString(summaryResId) : null, information);
	}

	public Preference makeButton(PreferenceGroup parent, CharSequence title, CharSequence summary,
			boolean information) {
		Preference preference = information ? new Preference(getActivity(), null,
				android.R.attr.preferenceInformationStyle) : new Preference(getActivity());
		preference.setTitle(title);
		preference.setSummary(summary);
		addPreference(parent, preference);
		return preference;
	}

	public CheckBoxPreference makeCheckBox(PreferenceGroup parent, boolean persistent, String key, boolean defaultValue,
			int titleResId, int summaryResId) {
		return makeCheckBox(parent, persistent, key, defaultValue, titleResId != 0 ? getString(titleResId) : null,
				summaryResId != 0 ? getString(summaryResId) : null);
	}

	public CheckBoxPreference makeCheckBox(PreferenceGroup parent, boolean persistent, String key,
			boolean defaultValue, CharSequence title, CharSequence summary) {
		CheckBoxPreference preference = new CheckBoxPreference(getActivity());
		preference.setPersistent(persistent);
		preference.setKey(key);
		preference.setTitle(title);
		if (summary != null) {
			preference.setSummary(summary);
		}
		preference.setDefaultValue(defaultValue);
		addPreference(parent, preference);
		return preference;
	}

	public ExtendedEditTextPreference makeEditText(PreferenceGroup parent, String key, String defaultValue,
			int titleResId, int summaryResId, CharSequence hint, int inputType, boolean repeatValueInSummary) {
		ExtendedEditTextPreference preference = new ExtendedEditTextPreference(getActivity());
		preference.setKey(key);
		preference.setTitle(titleResId);
		preference.setDialogTitle(titleResId);
		if (summaryResId != 0) {
			preference.setSummary(summaryResId);
		}
		EditText editText = preference.getEditText();
		if (hint != null) {
			editText.setHint(hint);
		}
		if (defaultValue != null) {
			preference.setDefaultValue(defaultValue);
		}
		editText.setInputType(inputType);
		addPreference(parent, preference);
		if (repeatValueInSummary) {
			if (summaryListenersEditText == null) {
				summaryListenersEditText = new ArrayList<>();
			}
			summaryListenersEditText.add(preference);
			updateEditTextSummary(preference);
		}
		return preference;
	}

	public MultipleEditTextPreference makeMultipleEditText(PreferenceGroup parent, String key, String defaultValue,
			int titleResId, int summaryResId, int count, CharSequence[] hints, int inputType,
			String repeatValueInSummaryPattern) {
		int[] inputTypes = new int[count];
		for (int i = 0; i < count; i++) {
			inputTypes[i] = inputType;
		}
		return makeMultipleEditText(parent, key, defaultValue, titleResId, summaryResId, count, hints, inputTypes,
				repeatValueInSummaryPattern);
	}

	public MultipleEditTextPreference makeMultipleEditText(PreferenceGroup parent, String key, String defaultValue,
			int titleResId, int summaryResId, int count, CharSequence[] hints, int[] inputTypes,
			String repeatValueInSummaryPattern) {
		MultipleEditTextPreference preference = new MultipleEditTextPreference(getActivity(), count);
		preference.setKey(key);
		preference.setTitle(titleResId);
		preference.setDialogTitle(titleResId);
		if (summaryResId != 0) {
			preference.setSummary(summaryResId);
		}
		if (hints != null) {
			preference.setHints(hints);
		}
		if (defaultValue != null) {
			preference.setDefaultValue(defaultValue);
		}
		preference.setInputTypes(inputTypes);
		addPreference(parent, preference);
		if (repeatValueInSummaryPattern != null) {
			if (summaryListenersMultipleEditText == null) {
				summaryListenersMultipleEditText = new HashMap<>();
			}
			summaryListenersMultipleEditText.put(preference, repeatValueInSummaryPattern);
			updateMultipleEditTextSummary(preference, repeatValueInSummaryPattern);
		}
		return preference;
	}

	public ListPreference makeList(PreferenceGroup parent, String key, CharSequence[] values,
			String defaultValue, int titleResId, int entriesResId) {
		return makeList(parent, key, values, defaultValue, titleResId, getResources().getStringArray(entriesResId));
	}

	public ListPreference makeList(PreferenceGroup parent, String key, CharSequence[] values,
			String defaultValue, int titleResId, CharSequence[] entries) {
		ListPreference preference = new ListPreference(getActivity());
		preference.setKey(key);
		preference.setTitle(titleResId);
		preference.setDialogTitle(titleResId);
		preference.setEntries(entries);
		preference.setEntryValues(values);
		if (defaultValue != null) {
			preference.setDefaultValue(defaultValue);
		}
		addPreference(parent, preference);
		updateListSummary(preference);
		return preference;
	}

	public SeekBarPreference makeSeekBar(PreferenceGroup parent, String key, int defaultValue,
			int titleResId, int summaryResId, int minValue, int maxValue, int step, float multiplier) {
		return makeSeekBar(parent, key, defaultValue, titleResId != 0 ? getString(titleResId) : null,
				summaryResId != 0 ? getString(summaryResId) : null, minValue, maxValue, step, multiplier);
	}

	public SeekBarPreference makeSeekBar(PreferenceGroup parent, String key, int defaultValue,
			String title, String summary, int minValue, int maxValue, int step, float multiplier) {
		SeekBarPreference preference = new SeekBarPreference(getActivity());
		preference.setKey(key);
		preference.setTitle(title);
		preference.setSummary(summary);
		preference.setDialogTitle(title);
		preference.setDefaultValue(defaultValue);
		preference.setSeekBarConfiguration(minValue, maxValue, step, multiplier);
		addPreference(parent, preference);
		return preference;
	}

	public void addDependency(String key, String dependencyKey, boolean positive) {
		if (dependencies == null) {
			dependencies = new ArrayList<>();
		}
		Dependency dependency = new BooleanDependency(key, dependencyKey, positive);
		dependencies.add(dependency);
		updateDependency(dependency);
	}

	public void addDependency(String key, String dependencyKey, boolean positive, String... values) {
		if (dependencies == null) {
			dependencies = new ArrayList<>();
		}
		Dependency dependency = new StringDependency(key, dependencyKey, positive, values);
		dependencies.add(dependency);
		updateDependency(dependency);
	}

	private void updateDependency(Dependency dependency) {
		Preference dependencyPreference = findPreference(dependency.dependencyKey);
		updateDependency(dependency, dependencyPreference);
	}

	private void updateDependency(Dependency dependency, Preference dependencyPreference) {
		findPreference(dependency.key).setEnabled(dependency.checkDependency(dependencyPreference));
	}

	private void updateEditTextSummary(EditTextPreference preference) {
		String text = preference.getText();
		if (StringUtils.isEmpty(text)) {
			CharSequence hint = preference.getEditText().getHint();
			text = hint != null ? hint.toString() : null;
		}
		preference.setSummary(text);
	}

	private void updateMultipleEditTextSummary(MultipleEditTextPreference preference, String format) {
		preference.setSummary(preference.formatValues(format));
	}

	private void updateListSummary(ListPreference preference) {
		preference.setSummary(preference.getEntry());
	}

	protected void expandDialog(Preference preference) {
		if (preference instanceof DialogPreference) {
			try {
				if (preference.getPreferenceManager() == null) {
					Method onAttachedToHierarchy = Preference.class.getDeclaredMethod("onAttachedToHierarchy",
							PreferenceManager.class);
					onAttachedToHierarchy.setAccessible(true);
					onAttachedToHierarchy.invoke(preference, getPreferenceManager());
				}
				Method onClickMethod = Preference.class.getDeclaredMethod("onClick");
				onClickMethod.setAccessible(true);
				onClickMethod.invoke(preference);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public final void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Preference preference = findPreference(key);
		if (preference != null) {
			onPreferenceAfterChange(preference);
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		return true;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		return false;
	}

	public void onPreferenceAfterChange(Preference preference) {
		if (summaryListenersEditText != null) {
			if (preference instanceof EditTextPreference && summaryListenersEditText.contains(preference)) {
				updateEditTextSummary((EditTextPreference) preference);
			}
		}
		if (summaryListenersMultipleEditText != null) {
			if (preference instanceof MultipleEditTextPreference && summaryListenersMultipleEditText
					.containsKey(preference)) {
				updateMultipleEditTextSummary((MultipleEditTextPreference) preference,
						summaryListenersMultipleEditText.get(preference));
			}
		}
		if (preference instanceof ListPreference) {
			updateListSummary((ListPreference) preference);
		}
		if (dependencies != null) {
			for (Dependency dependency : dependencies) {
				if (preference.getKey().equals(dependency.dependencyKey)) {
					updateDependency(dependency, preference);
				}
			}
		}
	}

	private static PreferenceGroup getParentGroup(PreferenceGroup preferenceGroup, Preference preference) {
		for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
			Preference childPreference = preferenceGroup.getPreference(i);
			if (childPreference == preference) {
				return preferenceGroup;
			}
			if (childPreference instanceof PreferenceGroup) {
				PreferenceGroup foundPreferenceGroup = getParentGroup((PreferenceGroup) childPreference,
						preference);
				if (foundPreferenceGroup != null) {
					return foundPreferenceGroup;
				}
			}
		}
		return null;
	}

	protected PreferenceGroup getParentGroup(Preference preference) {
		return getParentGroup(getPreferenceScreen(), preference);
	}
}