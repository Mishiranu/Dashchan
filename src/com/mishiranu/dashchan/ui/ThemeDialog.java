package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.util.ConfigurationLock;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.Collections;
import java.util.List;

public class ThemeDialog {
	public interface Callback extends ListViewUtils.ClickCallback<ThemeEngine.Theme, RecyclerView.ViewHolder> {
		void onThemeSelected(ThemeEngine.Theme theme);

		@Override
		default boolean onItemClick(RecyclerView.ViewHolder holder,
				int position, ThemeEngine.Theme theme, boolean longClick) {
			onThemeSelected(theme);
			return true;
		}
	}

	public static void show(Context context, ConfigurationLock configurationLock, Callback callback) {
		PaddedRecyclerView recyclerView = new PaddedRecyclerView(context);
		recyclerView.setLayoutManager(new LinearLayoutManager(context));
		if (C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(context);
			recyclerView.setPadding(0, (int) (12f * density), 0, 0);
		} else {
			recyclerView.addItemDecoration(new DividerItemDecoration(context, (c, p) -> c.need(true)));
		}
		final AlertDialog dialog = new AlertDialog.Builder(context)
				.setTitle(R.string.change_theme)
				.setView(recyclerView)
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.more_themes, (d, w) -> callback.onThemeSelected(null))
				.create();
		List<ThemeEngine.Theme> themes = ThemeEngine.getThemes();
		Collections.sort(themes);
		recyclerView.setAdapter(new Adapter(context, themes, theme -> {
			dialog.dismiss();
			if (theme != ThemeEngine.getTheme(context)) {
				callback.onThemeSelected(theme);
			}
		}));
		configurationLock.lockConfiguration(dialog);
		dialog.show();
	}

	private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private static class ItemViewHolder extends RecyclerView.ViewHolder {
			public final Preference.Runtime.IconViewHolder holder;

			public ItemViewHolder(Preference.Runtime.IconViewHolder iconViewHolder) {
				super(iconViewHolder.view);
				ViewUtils.setSelectableItemBackground(itemView);
				this.holder = iconViewHolder;
				iconViewHolder.summary.setVisibility(View.GONE);
				float density = ResourceUtils.obtainDensity(itemView);
				itemView.setPadding(itemView.getPaddingLeft() + (int) (8f * density), itemView.getPaddingTop(),
						itemView.getPaddingRight() + (int) (8f * density), itemView.getPaddingBottom());
				if (!C.API_LOLLIPOP) {
					itemView.setMinimumHeight((int) (56f * density));
				}
			}
		}

		private final Callback callback;
		private final Preference.Runtime<?> iconPreference;
		private final List<ThemeEngine.Theme> themes;

		public Adapter(Context context, List<ThemeEngine.Theme> themes, Callback callback) {
			this.callback = callback;
			iconPreference = new Preference.Runtime<>(context, "", null, "title", p -> null);
			this.themes = themes;
		}

		@Override
		public int getItemCount() {
			return themes.size();
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return ListViewUtils.bind(new ItemViewHolder(iconPreference.createIconViewHolder(parent)),
					false, themes::get, callback);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			Preference.Runtime.IconViewHolder viewHolder = ((ItemViewHolder) holder).holder;
			ThemeEngine.Theme theme = themes.get(position);
			viewHolder.icon.setImageDrawable(theme.createThemeChoiceDrawable());
			viewHolder.title.setText(theme.name);
		}
	}
}
