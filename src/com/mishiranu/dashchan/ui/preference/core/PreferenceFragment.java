package com.mishiranu.dashchan.ui.preference.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.ui.ContentFragment;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class PreferenceFragment extends ContentFragment {
	private final ArrayList<Preference<?>> preferences = new ArrayList<>();
	private final HashSet<Preference<?>> persistent = new HashSet<>();
	private final ArrayList<Dependency> dependencies = new ArrayList<>();

	private RecyclerView recyclerView;

	private static abstract class Dependency {
		public final String key, dependencyKey;
		public final boolean positive;

		public Dependency(String key, String dependencyKey, boolean positive) {
			this.key = key;
			this.dependencyKey = dependencyKey;
			this.positive = positive;
		}

		public abstract boolean checkDependency(Preference<?> dependencyPreference);
	}

	private static class BooleanDependency extends Dependency {
		public BooleanDependency(String key, String dependencyKey, boolean positive) {
			super(key, dependencyKey, positive);
		}

		@Override
		public boolean checkDependency(Preference<?> dependencyPreference) {
			if (dependencyPreference instanceof CheckPreference) {
				return ((CheckPreference) dependencyPreference).getValue() == positive;
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
		public boolean checkDependency(Preference<?> dependencyPreference) {
			String value;
			if (dependencyPreference instanceof EditPreference) {
				value = ((EditPreference) dependencyPreference).getValue();
			} else if (dependencyPreference instanceof ListPreference) {
				value = ((ListPreference) dependencyPreference).getValue();
			} else {
				return false;
			}
			return values.contains(value) == positive;
		}
	}

	protected abstract SharedPreferences getPreferences();

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		recyclerView = new PaddedRecyclerView(container.getContext());
		recyclerView.setId(android.R.id.list);
		recyclerView.setMotionEventSplittingEnabled(false);
		recyclerView.setClipToPadding(false);
		recyclerView.setVerticalScrollBarEnabled(true);
		recyclerView.setLayoutManager(new LinearLayoutManager(container.getContext()));
		recyclerView.setAdapter(new Adapter());
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), (c, position) -> {
			Preference<?> current = preferences.get(position);
			Preference<?> next = preferences.size() > position + 1 ? preferences.get(position + 1) : null;
			boolean need = !(current instanceof HeaderPreference) &&
					(!(next instanceof HeaderPreference) || C.API_LOLLIPOP);
			if (need && C.API_LOLLIPOP) {
				need = !(current instanceof CategoryPreference) && !(next instanceof CategoryPreference);
			}
			return c.need(need);
		}));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		ExpandedLayout layout = new ExpandedLayout(container.getContext(), true);
		layout.addView(recyclerView, ExpandedLayout.LayoutParams.MATCH_PARENT,
				ExpandedLayout.LayoutParams.MATCH_PARENT);
		return layout;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		preferences.clear();
		persistent.clear();
		dependencies.clear();
		recyclerView = null;
	}

	private <T> void onChange(Preference<T> preference, boolean newValue) {
		if (newValue) {
			if (persistent.contains(preference)) {
				preference.persist(getPreferences());
			}
			onPreferenceAfterChange(preference);
			preference.notifyAfterChange();
		}
		int index = preferences.indexOf(preference);
		if (index >= 0) {
			recyclerView.getAdapter().notifyItemChanged(index, SimpleViewHolder.EMPTY_PAYLOAD);
		}
	}

	public <T> void addPreference(Preference<T> preference, boolean persistent) {
		preferences.add(preference);
		if (preference.key != null && persistent) {
			preference.extract(getPreferences());
			this.persistent.add(preference);
		}
		preference.setOnChangeListener(newValue -> onChange(preference, newValue));
	}

	public void movePreference(Preference<?> which, Preference<?> after) {
		int removeIndex = preferences.indexOf(which);
		if (removeIndex < 0) {
			throw new IllegalStateException();
		}
		preferences.remove(removeIndex);
		recyclerView.getAdapter().notifyItemRemoved(removeIndex);
		int index = preferences.indexOf(after) + 1;
		preferences.add(index, which);
		recyclerView.getAdapter().notifyItemInserted(index);
	}

	public <T> void addDialogPreference(Preference<T> preference) {
		addPreference(preference, true);
		preference.setOnClickListener(p -> new PreferenceDialog(p.key).show(getChildFragmentManager(),
				PreferenceDialog.class.getName()));
	}

	public void removeAllPreferences() {
		preferences.clear();
		persistent.clear();
		recyclerView.getAdapter().notifyDataSetChanged();
	}

	public int removePreference(Preference<?> preference) {
		int index = preferences.indexOf(preference);
		if (index >= 0) {
			preferences.remove(index);
			persistent.remove(preference);
			recyclerView.getAdapter().notifyItemRemoved(index);
		}
		return preferences.size();
	}

	public Preference<Void> addHeader(int titleResId) {
		Preference<Void> preference = new HeaderPreference(requireContext(), getString(titleResId));
		addPreference(preference, false);
		return preference;
	}

	public Preference<Void> addButton(int titleResId, int summaryResId) {
		return addButton(titleResId != 0 ? getString(titleResId) : null,
				summaryResId != 0 ? getString(summaryResId) : null);
	}

	public Preference<Void> addButton(CharSequence title, CharSequence summary) {
		return addButton(title, p -> summary);
	}

	public Preference<Void> addButton(CharSequence title, Preference.SummaryProvider<Void> summaryProvider) {
		Preference<Void> preference = new ButtonPreference(requireContext(), title, summaryProvider);
		addPreference(preference, false);
		return preference;
	}

	public Preference<Void> addCategory(int titleResId) {
		return addCategory(getString(titleResId), null);
	}

	public Preference<Void> addCategory(int titleResId, int iconResId) {
		return addCategory(getString(titleResId), C.API_LOLLIPOP
				? ContextCompat.getDrawable(requireContext(), iconResId) : null);
	}

	public Preference<Void> addCategory(CharSequence title, Drawable icon) {
		Preference<Void> preference = new CategoryPreference(requireContext(), title, icon);
		addPreference(preference, false);
		return preference;
	}

	public void setCategoryTint(Preference<Void> preference, ColorStateList tintList) {
		if (!(preference instanceof CategoryPreference)) {
			throw new IllegalArgumentException();
		}
		((CategoryPreference) preference).setTint(tintList);
	}

	public CheckPreference addCheck(boolean persistent, String key, boolean defaultValue,
			int titleResId, int summaryResId) {
		return addCheck(persistent, key, defaultValue, titleResId != 0 ? getString(titleResId) : null,
				summaryResId != 0 ? getString(summaryResId) : null);
	}

	public CheckPreference addCheck(boolean persistent, String key, boolean defaultValue,
			CharSequence title, CharSequence summary) {
		CheckPreference preference = new CheckPreference(requireContext(), key, defaultValue, title, summary);
		addPreference(preference, persistent);
		preference.setOnClickListener(p -> p.setValue(!p.getValue()));
		return preference;
	}

	public EditPreference addEdit(String key, String defaultValue,
			int titleResId, CharSequence hint, int inputType) {
		return addEdit(key, defaultValue, titleResId, p -> {
			CharSequence summary = p.getValue();
			if (summary == null || summary.length() == 0) {
				summary = ((EditPreference) p).hint;
			}
			return summary;
		}, hint, inputType);
	}

	public EditPreference addEdit(String key, String defaultValue,
			int titleResId, int summaryResId, CharSequence hint, int inputType) {
		return addEdit(key, defaultValue, titleResId,
				p -> summaryResId != 0 ? getString(summaryResId) : null, hint, inputType);
	}

	public EditPreference addEdit(String key, String defaultValue,
			int titleResId, Preference.SummaryProvider<String> summaryProvider, CharSequence hint, int inputType) {
		EditPreference preference = new EditPreference(requireContext(), key, defaultValue,
				getString(titleResId), summaryProvider, hint, inputType);
		addDialogPreference(preference);
		return preference;
	}

	public List<Integer> createInputTypes(int count, int inputType) {
		ArrayList<Integer> inputTypes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			inputTypes.add(inputType);
		}
		return inputTypes;
	}

	public <T> MultipleEditPreference<T> addMultipleEdit(String key, int titleResId, int summaryResId,
			List<CharSequence> hints, List<Integer> inputTypes, MultipleEditPreference.ValueCodec<T> valueCodec) {
		return addMultipleEdit(key, titleResId, p -> summaryResId != 0 ? getString(summaryResId) : null,
				hints, inputTypes, valueCodec);
	}

	public <T> MultipleEditPreference<T> addMultipleEdit(String key, int titleResId, String summaryPattern,
			List<CharSequence> hints, List<Integer> inputTypes, MultipleEditPreference.ValueCodec<T> valueCodec) {
		return addMultipleEdit(key, titleResId,
				p -> MultipleEditPreference.formatValues(valueCodec, summaryPattern, p.getValue()),
				hints, inputTypes, valueCodec);
	}

	public <T> MultipleEditPreference<T> addMultipleEdit(String key, int titleResId,
			Preference.SummaryProvider<T> summaryProvider, List<CharSequence> hints, List<Integer> inputTypes,
			MultipleEditPreference.ValueCodec<T> valueCodec) {
		MultipleEditPreference<T> preference = new MultipleEditPreference<>(requireContext(), key,
				getString(titleResId), summaryProvider, hints, inputTypes, valueCodec);
		addDialogPreference(preference);
		return preference;
	}

	public ListPreference addList(String key, List<String> values,
			String defaultValue, int titleResId, List<CharSequence> entries) {
		ListPreference preference = new ListPreference(requireContext(), key, defaultValue, getString(titleResId),
				entries, values);
		addDialogPreference(preference);
		return preference;
	}

	public SeekPreference addSeek(String key, int defaultValue, int titleResId, int summaryFormatResId,
			Pair<Integer, Integer> specialValue, int minValue, int maxValue, int step) {
		return addSeek(key, defaultValue, titleResId != 0 ? getString(titleResId) : null,
				summaryFormatResId != 0 ? getString(summaryFormatResId) : null,
				specialValue != null ? new Pair<>(specialValue.first, getString(specialValue.second)) : null,
				minValue, maxValue, step);
	}

	public SeekPreference addSeek(String key, int defaultValue, String title, String summaryFormat,
			Pair<Integer, String> specialValue, int minValue, int maxValue, int step) {
		SeekPreference preference = new SeekPreference(requireContext(), key, defaultValue, title, summaryFormat,
				specialValue, minValue, maxValue, step);
		addDialogPreference(preference);
		return preference;
	}

	public void addDependency(String key, String dependencyKey, boolean positive) {
		Dependency dependency = new BooleanDependency(key, dependencyKey, positive);
		dependencies.add(dependency);
		updateDependency(dependency);
	}

	public void addDependency(String key, String dependencyKey, boolean positive, String... values) {
		Dependency dependency = new StringDependency(key, dependencyKey, positive, values);
		dependencies.add(dependency);
		updateDependency(dependency);
	}

	public interface EnumString<T extends Enum<T>> {
		String getString(T value);
	}

	public interface EnumStringResource<T extends Enum<T>> {
		int getResourceId(T value);
	}

	public <T extends Enum<T>> List<String> enumList(T[] enumValues, EnumString<T> callback) {
		ArrayList<String> list = new ArrayList<>(enumValues.length);
		for (T value : enumValues) {
			list.add(callback.getString(value));
		}
		return list;
	}

	public <T extends Enum<T>> List<CharSequence> enumResList(T[] enumValues, EnumStringResource<T> callback) {
		ArrayList<CharSequence> list = new ArrayList<>(enumValues.length);
		for (T value : enumValues) {
			list.add(getString(callback.getResourceId(value)));
		}
		return list;
	}

	public Preference<?> findPreference(String key) {
		for (Preference<?> preference : preferences) {
			if (key.equals(preference.key)) {
				return preference;
			}
		}
		return null;
	}

	private void updateDependency(Dependency dependency) {
		Preference<?> dependencyPreference = findPreference(dependency.dependencyKey);
		if (dependencyPreference != null) {
			updateDependency(dependency, dependencyPreference);
		}
	}

	private void updateDependency(Dependency dependency, Preference<?> dependencyPreference) {
		Preference<?> preference = findPreference(dependency.key);
		if (preference != null) {
			preference.setEnabled(dependency.checkDependency(dependencyPreference));
		}
	}

	private void onPreferenceAfterChange(Preference<?> preference) {
		for (Dependency dependency : dependencies) {
			if (preference.key.equals(dependency.dependencyKey)) {
				updateDependency(dependency, preference);
			}
		}
	}

	public AlertDialog getDialog(Preference<?> preference) {
		getChildFragmentManager().executePendingTransactions();
		PreferenceDialog preferenceDialog = (PreferenceDialog) getChildFragmentManager()
				.findFragmentByTag(PreferenceDialog.class.getName());
		return preferenceDialog != null && preferenceDialog.getPreference() == preference
				? (AlertDialog) preferenceDialog.getDialog() : null;
	}

	private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
		private class ViewHolder extends RecyclerView.ViewHolder
				implements ListViewUtils.ClickCallback<Void, ViewHolder> {
			private final Preference.ViewHolder viewHolder;

			public ViewHolder(View itemView, Preference.ViewHolder viewHolder) {
				super(itemView);
				this.viewHolder = viewHolder;
				ListViewUtils.bind(this, false, null, this);
				if (itemView.getBackground() == null) {
					ViewUtils.setSelectableItemBackground(itemView);
				}
			}

			@Override
			public boolean onItemClick(ViewHolder holder, int position, Void nothing, boolean longClick) {
				preferences.get(position).performClick();
				return true;
			}
		}

		private final HashMap<Preference.ViewType, Preference<?>> viewProviders = new HashMap<>();

		@Override
		public int getItemCount() {
			return preferences.size();
		}

		@Override
		public int getItemViewType(int position) {
			Preference<?> preference = preferences.get(position);
			viewProviders.put(preference.getViewType(), preference);
			return preference.getViewType().ordinal();
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			Preference.ViewHolder preferenceViewHolder = viewProviders
					.get(Preference.ViewType.values()[viewType]).createViewHolder(parent);
			return new ViewHolder(preferenceViewHolder.view, preferenceViewHolder);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			preferences.get(position).bindViewHolder(holder.viewHolder);
		}
	}

	private static class HeaderPreference extends Preference.Runtime<Void> {
		public HeaderPreference(Context context, CharSequence title) {
			super(context, null, null, title, null);
			setSelectable(false);
		}

		@Override
		public ViewType getViewType() {
			return ViewType.HEADER;
		}

		@Override
		public ViewHolder createViewHolder(ViewGroup parent) {
			FrameLayout layout = new FrameLayout(parent.getContext());
			TextView header = ViewFactory.makeListTextHeader(layout);
			layout.addView(header);
			layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			return new ViewHolder(layout, header, null, null);
		}
	}

	private static class ButtonPreference extends Preference.Runtime<Void> {
		public ButtonPreference(Context context, CharSequence title, SummaryProvider<Void> summaryProvider) {
			super(context, null, null, title, summaryProvider);
		}
	}

	private static class CategoryPreference extends ButtonPreference {
		private final Drawable icon;
		private ColorStateList tintList;

		public CategoryPreference(Context context, CharSequence title, Drawable icon) {
			super(context, title, null);
			this.icon = icon;
		}

		@Override
		public ViewType getViewType() {
			return ViewType.CATEGORY;
		}

		public void setTint(ColorStateList tintList) {
			this.tintList = tintList;
			invalidate();
		}

		@Override
		public ViewHolder createViewHolder(ViewGroup parent) {
			ViewHolder viewHolder = super.createViewHolder(parent);
			if (C.API_LOLLIPOP) {
				return createIconViewHolder(parent);
			} else  {
				TypedArray typedArray = parent.getContext()
						.obtainStyledAttributes(new int[] {android.R.attr.listPreferredItemHeightSmall});
				if (typedArray.hasValue(0)) {
					viewHolder.view.setMinimumHeight(typedArray.getDimensionPixelSize(0,
							viewHolder.view.getMinimumHeight()));
				}
				typedArray.recycle();
				return viewHolder;
			}
		}

		@Override
		public void bindViewHolder(ViewHolder viewHolder) {
			super.bindViewHolder(viewHolder);

			ColorStateList textColors = (ColorStateList) viewHolder.title.getTag(R.id.tag_text_colors);
			if (textColors == null) {
				textColors = viewHolder.title.getTextColors();
				viewHolder.title.setTag(R.id.tag_text_colors, textColors);
			}
			viewHolder.title.setTextColor(tintList != null ? tintList : textColors);

			if (viewHolder instanceof IconViewHolder) {
				IconViewHolder iconViewHolder = (IconViewHolder) viewHolder;
				iconViewHolder.icon.setImageDrawable(icon);
				iconViewHolder.icon.setVisibility(icon != null ? View.VISIBLE : View.GONE);
				iconViewHolder.icon.setImageTintList(tintList != null ? tintList : ColorStateList.valueOf(ResourceUtils
						.getColor(viewHolder.view.getContext(), android.R.attr.textColorSecondary)));
			}
		}
	}

	public static class PreferenceDialog extends DialogFragment {
		private static final String EXTRA_KEY = "key";

		public PreferenceDialog() {}

		public PreferenceDialog(String key) {
			Bundle args = new Bundle();
			args.putString(EXTRA_KEY, key);
			setArguments(args);
		}

		private DialogPreference<?> getPreference() {
			String key = requireArguments().getString(EXTRA_KEY);
			return (DialogPreference<?>) ((PreferenceFragment) getParentFragment()).findPreference(key);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			return getPreference().createDialog(savedInstanceState);
		}

		@Override
		public void onStart() {
			super.onStart();
			getPreference().startDialog((AlertDialog) getDialog());
		}

		@Override
		public void onStop() {
			getPreference().stopDialog((AlertDialog) getDialog());
			super.onStop();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			getPreference().saveState((AlertDialog) getDialog(), outState);
		}
	}
}
