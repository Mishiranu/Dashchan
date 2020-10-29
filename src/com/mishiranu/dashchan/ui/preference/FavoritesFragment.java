package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
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

		addList(Preferences.KEY_FAVORITES_ORDER, enumList(Preferences.FavoritesOrder.values(), o -> o.value),
				Preferences.DEFAULT_FAVORITES_ORDER.value, R.string.favorite_threads_order,
				enumResList(Preferences.FavoritesOrder.values(), o -> o.titleResId))
				.setOnAfterChangeListener(p -> FavoritesStorage.getInstance().sortIfNeeded());
		addList(Preferences.KEY_FAVORITE_ON_REPLY, enumList(Preferences.FavoriteOnReplyMode.values(), o -> o.value),
				Preferences.DEFAULT_FAVORITE_ON_REPLY.value, R.string.add_thread_on_reply,
				enumResList(Preferences.FavoriteOnReplyMode.values(), o -> o.titleResId));

		addHeader(R.string.favorites_watcher);
		addSeek(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.DEFAULT_WATCHER_REFRESH_INTERVAL,
				R.string.refresh_favorites, R.string.every_number_sec__format,
				new Pair<>(Preferences.DISABLED_WATCHER_REFRESH_INTERVAL, R.string.disabled),
				Preferences.MIN_WATCHER_REFRESH_INTERVAL, Preferences.MAX_WATCHER_REFRESH_INTERVAL,
				Preferences.STEP_WATCHER_REFRESH_INTERVAL);
		addCheck(true, Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.DEFAULT_WATCHER_WIFI_ONLY, R.string.wifi_only, 0);
		addCheck(true, Preferences.KEY_WATCHER_WATCH_INITIALLY, Preferences.DEFAULT_WATCHER_WATCH_INITIALLY,
				R.string.watch_initially, R.string.watch_initially__summary);
		addCheck(true, Preferences.KEY_WATCHER_AUTO_DISABLE, Preferences.DEFAULT_WATCHER_AUTO_DISABLE,
				R.string.auto_disable, R.string.auto_disable__summary);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.favorites), null);
	}
}
