package com.mishiranu.dashchan.preference.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.core.PreferenceFragment;

public class FavoritesFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_FAVORITES_ORDER, Preferences.VALUES_FAVORITES_ORDER,
				Preferences.DEFAULT_FAVORITES_ORDER, R.string.preference_favorites_order,
				R.array.preference_favorites_order_choices)
				.setOnAfterChangeListener(p -> FavoritesStorage.getInstance().sortIfNeeded());
		addCheck(true, Preferences.KEY_FAVORITE_ON_REPLY, Preferences.DEFAULT_FAVORITE_ON_REPLY,
				R.string.preference_favorite_on_reply, 0);

		addHeader(R.string.preference_category_watcher);
		addCheck(true, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY,
				Preferences.DEFAULT_WATCHER_REFRESH_PERIODICALLY, R.string.preference_watcher_refresh_periodically, 0);
		addSeek(Preferences.KEY_WATCHER_REFRESH_INTERVAL,
				Preferences.DEFAULT_WATCHER_REFRESH_INTERVAL, R.string.preference_watcher_refresh_interval,
				R.string.preference_watcher_refresh_interval_summary_format, 15, 60, 5, 1f);
		addCheck(true, Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.DEFAULT_WATCHER_WIFI_ONLY,
				R.string.preference_watcher_wifi_only, 0);
		addCheck(true, Preferences.KEY_WATCHER_WATCH_INITIALLY,
				Preferences.DEFAULT_WATCHER_WATCH_INITIALLY, R.string.preference_watcher_watch_initially,
				R.string.preference_watcher_watch_initially_summary);
		addCheck(true, Preferences.KEY_WATCHER_AUTO_DISABLE,
				Preferences.DEFAULT_WATCHER_AUTO_DISABLE, R.string.preference_watcher_auto_disable,
				R.string.preference_watcher_auto_disable_summary);

		addDependency(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
		addDependency(Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		requireActivity().setTitle(R.string.preference_header_favorites);
		requireActivity().getActionBar().setSubtitle(null);
	}
}
