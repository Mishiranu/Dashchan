package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;

public class FavoritesFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_FAVORITES_ORDER, Preferences.VALUES_FAVORITES_ORDER,
				Preferences.DEFAULT_FAVORITES_ORDER, R.string.favorite_threads_order,
				Preferences.ENTRIES_FAVORITES_ORDER)
				.setOnAfterChangeListener(p -> FavoritesStorage.getInstance().sortIfNeeded());
		addList(Preferences.KEY_FAVORITE_ON_REPLY, Preferences.VALUES_FAVORITE_ON_REPLY,
				Preferences.DEFAULT_FAVORITE_ON_REPLY, R.string.add_thread_on_reply,
				Preferences.ENTRIES_FAVORITE_ON_REPLY);

		addHeader(R.string.favorites_watcher);
		addCheck(true, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY,
				Preferences.DEFAULT_WATCHER_REFRESH_PERIODICALLY, R.string.refresh_periodically, 0);
		addSeek(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.DEFAULT_WATCHER_REFRESH_INTERVAL,
				R.string.refresh_interval, R.string.every_number_sec__format, 15, 60, 5, 1f);
		addCheck(true, Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.DEFAULT_WATCHER_WIFI_ONLY, R.string.wifi_only, 0);
		addCheck(true, Preferences.KEY_WATCHER_WATCH_INITIALLY, Preferences.DEFAULT_WATCHER_WATCH_INITIALLY,
				R.string.watch_initially, R.string.watch_initially__summary);
		addCheck(true, Preferences.KEY_WATCHER_AUTO_DISABLE, Preferences.DEFAULT_WATCHER_AUTO_DISABLE,
				R.string.auto_disable, R.string.auto_disable__summary);

		addDependency(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
		addDependency(Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.KEY_WATCHER_REFRESH_PERIODICALLY, true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.favorites), null);
	}
}
