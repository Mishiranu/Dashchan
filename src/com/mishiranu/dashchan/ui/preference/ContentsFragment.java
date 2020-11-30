package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.SharedPreferences;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ContentsFragment extends PreferenceFragment {
	private CheckPreference replyNotifications;
	private Preference<?> clearCachePreference;

	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addHeader(R.string.threads);
		addSeek(Preferences.KEY_AUTO_REFRESH_INTERVAL, Preferences.DEFAULT_AUTO_REFRESH_INTERVAL,
				R.string.refresh_open_thread, R.string.every_number_sec__format,
				new Pair<>(Preferences.DISABLED_AUTO_REFRESH_INTERVAL, R.string.disabled),
				Preferences.MIN_AUTO_REFRESH_INTERVAL, Preferences.MAX_AUTO_REFRESH_INTERVAL,
				Preferences.STEP_AUTO_REFRESH_INTERVAL);
		addList(Preferences.KEY_CYCLICAL_REFRESH, enumList(Preferences.CyclicalRefreshMode.values(), v -> v.value),
				Preferences.DEFAULT_CYCLICAL_REFRESH.value, R.string.cyclical_threads_refresh_mode,
				enumResList(Preferences.CyclicalRefreshMode.values(), v -> v.titleResId));

		addHeader(R.string.favorites);
		addList(Preferences.KEY_FAVORITES_ORDER, enumList(Preferences.FavoritesOrder.values(), o -> o.value),
				Preferences.DEFAULT_FAVORITES_ORDER.value, R.string.favorite_threads_order,
				enumResList(Preferences.FavoritesOrder.values(), o -> o.titleResId))
				.setOnAfterChangeListener(p -> FavoritesStorage.getInstance().sortIfNeeded());
		addList(Preferences.KEY_FAVORITE_ON_REPLY, enumList(Preferences.FavoriteOnReplyMode.values(), o -> o.value),
				Preferences.DEFAULT_FAVORITE_ON_REPLY.value, R.string.add_thread_on_reply,
				enumResList(Preferences.FavoriteOnReplyMode.values(), o -> o.titleResId));
		addCheck(true, Preferences.KEY_WATCHER_WATCH_INITIALLY, Preferences.DEFAULT_WATCHER_WATCH_INITIALLY,
				R.string.watch_initially, R.string.watch_initially__summary);

		addHeader(R.string.favorites_watcher);
		addSeek(Preferences.KEY_WATCHER_REFRESH_INTERVAL, Preferences.DEFAULT_WATCHER_REFRESH_INTERVAL,
				R.string.refresh_favorites, R.string.every_number_sec__format,
				new Pair<>(Preferences.DISABLED_WATCHER_REFRESH_INTERVAL, R.string.disabled),
				Preferences.MIN_WATCHER_REFRESH_INTERVAL, Preferences.MAX_WATCHER_REFRESH_INTERVAL,
				Preferences.STEP_WATCHER_REFRESH_INTERVAL);
		addCheck(true, Preferences.KEY_WATCHER_WIFI_ONLY, Preferences.DEFAULT_WATCHER_WIFI_ONLY, R.string.wifi_only, 0);
		replyNotifications = addCheck(false, "reply_notifications", false,
				R.string.reply_notifications, R.string.reply_notifications__format);
		replyNotifications.setOnClickListener(p -> {
			if (C.API_OREO) {
				Preferences.setWatcherNotifications(p.getValue() ? Collections.emptySet()
						: Collections.singleton(Preferences.NotificationFeature.ENABLED));
				invalidateReplyNotifications();
			} else {
				WatcherNotificationsDialog dialog = new WatcherNotificationsDialog();
				dialog.show(getChildFragmentManager(), WatcherNotificationsDialog.class.getName());
			}
		});
		invalidateReplyNotifications();

		addHeader(R.string.additional);
		clearCachePreference = addButton(getString(R.string.clear_cache),
				p -> StringUtils.formatFileSizeMegabytes(PagesDatabase.getInstance().getSize()));
		clearCachePreference.setOnClickListener(p -> {
			ClearCacheDialog dialog = new ClearCacheDialog();
			dialog.show(getChildFragmentManager(), ClearCacheDialog.class.getName());
		});
		clearCachePreference.invalidate();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		clearCachePreference = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.contents), null);
	}

	private void invalidateReplyNotifications() {
		replyNotifications.setValue(Preferences.getWatcherNotifications()
				.contains(Preferences.NotificationFeature.ENABLED));
	}

	public static class WatcherNotificationsDialog extends DialogFragment {
		private static final String EXTRA_CHECKED_ITEMS = "checkedItems";

		private boolean[] checkedItems;

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String[] items = new String[Preferences.NotificationFeature.values().length];
			for (int i = 0; i < items.length; i++) {
				items[i] = getString(Preferences.NotificationFeature.values()[i].titleResId);
			}
			if (savedInstanceState != null) {
				checkedItems = savedInstanceState.getBooleanArray(EXTRA_CHECKED_ITEMS);
			} else {
				checkedItems = new boolean[Preferences.NotificationFeature.values().length];
				Set<Preferences.NotificationFeature> notificationFeatures = Preferences.getWatcherNotifications();
				for (int i = 0; i < checkedItems.length; i++) {
					checkedItems[i] = notificationFeatures.contains(Preferences.NotificationFeature.values()[i]);
				}
			}
			return new AlertDialog.Builder(requireContext())
					.setTitle(R.string.reply_notifications)
					.setMultiChoiceItems(items, checkedItems, (d, which, isChecked) -> checkedItems[which] = isChecked)
					.setPositiveButton(android.R.string.ok, (d, which) -> {
						HashSet<Preferences.NotificationFeature> notificationFeatures = new HashSet<>();
						for (int i = 0; i < checkedItems.length; i++) {
							if (checkedItems[i]) {
								notificationFeatures.add(Preferences.NotificationFeature.values()[i]);
							}
						}
						Preferences.setWatcherNotifications(notificationFeatures);
						((ContentsFragment) getParentFragment()).invalidateReplyNotifications();
					})
					.setNegativeButton(android.R.string.cancel, null)
					.create();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED_ITEMS, checkedItems);
		}
	}

	public static class ClearCacheDialog extends DialogFragment {
		private static final String EXTRA_CHECKED_INDEX = "checkedIndex";

		private int checkedIndex;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			checkedIndex = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_CHECKED_INDEX) : 0;
			String[] items = {getString(R.string.old_threads), getString(R.string.all_threads)};
			return new AlertDialog.Builder(requireContext())
					.setTitle(getString(R.string.clear_cache))
					.setSingleChoiceItems(items, checkedIndex, (d, which) -> checkedIndex = which)
					.setPositiveButton(android.R.string.ok, (d, w) -> {
						ClearingDialog clearingDialog = new ClearingDialog(checkedIndex == 1);
						clearingDialog.setTargetFragment(getParentFragment(), 0);
						clearingDialog.show(getParentFragment().getParentFragmentManager(),
								ClearingDialog.class.getName());
					})
					.setNegativeButton(android.R.string.cancel, null)
					.create();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putInt(EXTRA_CHECKED_INDEX, checkedIndex);
		}
	}

	public static class ClearingDialog extends DialogFragment {
		private static final String EXTRA_ALL_PAGES = "allPages";

		public ClearingDialog() {}

		public ClearingDialog(boolean allPages) {
			Bundle args = new Bundle();
			args.putBoolean(EXTRA_ALL_PAGES, allPages);
			setArguments(args);
		}

		@NonNull
		@Override
		public ProgressDialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(requireContext(), null);
			dialog.setMessage(getString(R.string.clearing__ellipsis));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			ClearCacheViewModel viewModel = new ViewModelProvider(this).get(ClearCacheViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				Bundle args = requireArguments();
				boolean allPages = args.getBoolean(EXTRA_ALL_PAGES);
				Collection<PagesDatabase.ThreadKey> openThreads = Collections.emptyList();
				if (!allPages) {
					Collection<DrawerForm.Page> drawerPages =
							((FragmentHandler) requireActivity()).obtainDrawerPages();
					openThreads = new ArrayList<>(openThreads.size());
					for (DrawerForm.Page page : drawerPages) {
						if (page.threadNumber != null) {
							openThreads.add(new PagesDatabase.ThreadKey(page.chanName,
									page.boardName, page.threadNumber));
						}
					}
				}
				ClearCacheTask task = new ClearCacheTask(viewModel, allPages, openThreads);
				task.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(this, result -> {
				dismiss();
				sendUpdateCacheSize();
			});
		}

		private void sendUpdateCacheSize() {
			((ContentsFragment) getTargetFragment()).clearCachePreference.invalidate();
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			sendUpdateCacheSize();
		}
	}

	public static class ClearCacheViewModel extends TaskViewModel<ClearCacheTask, Object> {}

	private static class ClearCacheTask extends ExecutorTask<Void, Object> {
		private final ClearCacheViewModel viewModel;
		private final boolean allPages;
		private final Collection<PagesDatabase.ThreadKey> openThreads;

		public ClearCacheTask(ClearCacheViewModel viewModel, boolean allPages,
				Collection<PagesDatabase.ThreadKey> openThreads) {
			this.viewModel = viewModel;
			this.allPages = allPages;
			this.openThreads = openThreads;
		}

		@Override
		protected Void run() {
			if (allPages) {
				PagesDatabase.getInstance().eraseAll();
			} else {
				PagesDatabase.getInstance().erase(openThreads);
			}
			return null;
		}

		@Override
		protected void onComplete(Object o) {
			viewModel.handleResult(this);
		}
	}
}
