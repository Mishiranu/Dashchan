package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class ContentsFragment extends PreferenceFragment implements ActivityHandler {
	private static final String EXTRA_IN_STORAGE_REQUEST = "inStorageRequest";

	private Preference<?> downloadUriTreePreference;
	private Preference<?> clearCachePreference;

	private boolean inStorageRequest;

	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		inStorageRequest = savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_IN_STORAGE_REQUEST);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_LOAD_THUMBNAILS, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_THUMBNAILS, R.string.load_thumbnails, Preferences.GENERIC_ENTRIES_NETWORK);

		addHeader(R.string.threads_auto_refreshing);
		addList(Preferences.KEY_AUTO_REFRESH_MODE, Preferences.VALUES_AUTO_REFRESH_MODE,
				Preferences.DEFAULT_AUTO_REFRESH_MODE, R.string.auto_refreshing_mode,
				Preferences.ENTRIES_AUTO_REFRESH_MODE);
		addSeek(Preferences.KEY_AUTO_REFRESH_INTERVAL, Preferences.DEFAULT_AUTO_REFRESH_INTERVAL,
				R.string.refresh_interval, R.string.every_number_sec__format, Preferences.MIN_AUTO_REFRESH_INTERVAL,
				Preferences.MAX_AUTO_REFRESH_INTERVAL, Preferences.STEP_AUTO_REFRESH_INTERVAL, 1f);

		addHeader(R.string.downloads);
		addCheck(true, Preferences.KEY_DOWNLOAD_DETAIL_NAME, Preferences.DEFAULT_DOWNLOAD_DETAIL_NAME,
				R.string.detailed_file_name, R.string.detailed_file_name__summary);
		addCheck(true, Preferences.KEY_DOWNLOAD_ORIGINAL_NAME, Preferences.DEFAULT_DOWNLOAD_ORIGINAL_NAME,
				R.string.original_file_name, R.string.original_file_name__summary);
		if (C.USE_SAF) {
			downloadUriTreePreference = addButton(getString(R.string.download_directory),
					p -> DataFile.obtain(requireContext(), DataFile.Target.DOWNLOADS, null).getName());
			downloadUriTreePreference.setOnClickListener(p -> {
				if (((FragmentHandler) requireActivity()).requestStorage()) {
					inStorageRequest = true;
				}
			});
		} else {
			addEdit(Preferences.KEY_DOWNLOAD_PATH, null, R.string.download_path, C.DEFAULT_DOWNLOAD_PATH,
					InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		}
		addList(Preferences.KEY_DOWNLOAD_SUBDIR, Preferences.VALUES_DOWNLOAD_SUBDIR,
				Preferences.DEFAULT_DOWNLOAD_SUBDIR, R.string.show_download_configuration_dialog,
				Preferences.ENTRIES_DOWNLOAD_SUBDIR);
		addEdit(Preferences.KEY_SUBDIR_PATTERN, Preferences.DEFAULT_SUBDIR_PATTERN,
				R.string.subdirectory_pattern, Preferences.DEFAULT_SUBDIR_PATTERN,
				InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
				.setDescription(makeSubdirDescription());
		if (C.API_LOLLIPOP) {
			addCheck(true, Preferences.KEY_NOTIFY_DOWNLOAD_COMPLETE, Preferences.DEFAULT_NOTIFY_DOWNLOAD_COMPLETE,
					R.string.notify_when_download_is_completed, R.string.notify_when_download_is_completed__summary);
		}

		addHeader(R.string.images);
		addList(Preferences.KEY_LOAD_NEAREST_IMAGE, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_NEAREST_IMAGE, R.string.load_nearest_image,
				Preferences.GENERIC_ENTRIES_NETWORK);

		addHeader(R.string.video_player);
		boolean playerAvailable = VideoPlayer.loadLibraries(requireContext());
		if (!playerAvailable) {
			addButton(0, R.string.requires_decoding_libraries__sentence).setSelectable(false);
		}
		addCheck(true, Preferences.KEY_USE_VIDEO_PLAYER, Preferences.DEFAULT_USE_VIDEO_PLAYER,
				R.string.use_built_in_video_player, R.string.use_built_in_video_player__summary)
				.setEnabled(playerAvailable);
		addList(Preferences.KEY_VIDEO_COMPLETION, Preferences.VALUES_VIDEO_COMPLETION,
				Preferences.DEFAULT_VIDEO_COMPLETION, R.string.action_on_playback_completion,
				Preferences.ENTRIES_VIDEO_COMPLETION).setEnabled(playerAvailable);
		addCheck(true, Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.DEFAULT_VIDEO_PLAY_AFTER_SCROLL,
				R.string.play_after_scroll, R.string.play_after_scroll__summary).setEnabled(playerAvailable);
		addCheck(true, Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.DEFAULT_VIDEO_SEEK_ANY_FRAME,
				R.string.seek_any_frame, R.string.seek_any_frame__summary).setEnabled(playerAvailable);
		if (playerAvailable) {
			addDependency(Preferences.KEY_VIDEO_COMPLETION, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.KEY_USE_VIDEO_PLAYER, true);
		}

		addHeader(R.string.additional);
		addSeek(Preferences.KEY_CACHE_SIZE, Preferences.DEFAULT_CACHE_SIZE,
				getString(R.string.cache_size), "%d MB", 50, 750, 10, Preferences.MULTIPLIER_CACHE_SIZE);
		clearCachePreference = addButton(getString(R.string.clear_cache), p -> {
			long cacheSize = CacheManager.getInstance().getCacheSize();
			long pagesSize = PagesDatabase.getInstance().getSize();
			return formatSize(cacheSize) + " + " + formatSize(pagesSize);
		});
		clearCachePreference.setOnClickListener(p -> {
			ClearCacheDialog dialog = new ClearCacheDialog();
			dialog.show(getChildFragmentManager(), ClearCacheDialog.class.getName());
		});

		addDependency(Preferences.KEY_AUTO_REFRESH_INTERVAL, Preferences.KEY_AUTO_REFRESH_MODE, true,
				Preferences.VALUE_AUTO_REFRESH_MODE_ENABLED);
		addDependency(Preferences.KEY_SUBDIR_PATTERN, Preferences.KEY_DOWNLOAD_SUBDIR, false,
				Preferences.VALUE_DOWNLOAD_SUBDIR_DISABLED);
		clearCachePreference.invalidate();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		downloadUriTreePreference = null;
		clearCachePreference = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.contents), null);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(EXTRA_IN_STORAGE_REQUEST, inStorageRequest);
	}

	@Override
	public void onStorageRequestResult() {
		if (inStorageRequest) {
			inStorageRequest = false;
			downloadUriTreePreference.invalidate();
		}
	}

	private static String formatSize(long size) {
		return String.format(Locale.US, "%.2f", size / 1000f / 1000f) + " MB";
	}

	private CharSequence makeSubdirDescription() {
		String[] formats = {"\\c", "\\d", "\\b", "\\t", "\\e", "<\u2026>"};
		int[] descriptions = {R.string.forum_code, R.string.forum_title, R.string.board_code, R.string.thread_number,
				R.string.thread_title, R.string.optional_part};
		SpannableStringBuilder builder = new SpannableStringBuilder();
		for (int i = 0; i < formats.length; i++) {
			if (builder.length() > 0) {
				builder.append('\n');
			}
			StringUtils.appendSpan(builder, formats[i], new TypefaceSpan("sans-serif-medium"));
			builder.append(" — ");
			builder.append(getString(descriptions[i]));
		}
		return builder;
	}

	public static class ClearCacheDialog extends DialogFragment
			implements DialogInterface.OnMultiChoiceClickListener, DialogInterface.OnClickListener {
		private static final String EXTRA_CHECKED_ITEMS = "checkedItems";

		private boolean[] checkedItems;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			checkedItems = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_CHECKED_ITEMS) : null;
			if (checkedItems == null) {
				checkedItems = new boolean[] {true, true, true, false};
			}
			String[] items = {getString(R.string.thumbnails), getString(R.string.cached_files),
					getString(R.string.old_threads), getString(R.string.all_threads)};
			AlertDialog dialog = new AlertDialog.Builder(requireContext())
					.setTitle(getString(R.string.clear_cache))
					.setMultiChoiceItems(items, checkedItems, this)
					.setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, this)
					.create();
			dialog.setOnShowListener(d -> dialog.getListView().getChildAt(2).setEnabled(!checkedItems[3]));
			return dialog;
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED_ITEMS, checkedItems);
		}

		@Override
		public void onClick(DialogInterface dialog, int which, boolean isChecked) {
			switch (which) {
				case 2: {
					if (checkedItems[3]) {
						isChecked = !isChecked;
						((AlertDialog) dialog).getListView().setItemChecked(which, isChecked);
					}
					break;
				}
				case 3: {
					((AlertDialog) dialog).getListView().getChildAt(2).setEnabled(!isChecked);
					break;
				}
			}
			checkedItems[which] = isChecked;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			ClearingDialog clearingDialog = new ClearingDialog(checkedItems[0], checkedItems[1],
					checkedItems[2], checkedItems[3]);
			clearingDialog.setTargetFragment(getParentFragment(), 0);
			clearingDialog.show(getParentFragment().getParentFragmentManager(), ClearingDialog.class.getName());
		}
	}

	public static class ClearingDialog extends DialogFragment implements AsyncManager.Callback {
		private static final String EXTRA_THUMBNAILS = "thumbnails";
		private static final String EXTRA_MEDIA = "media";
		private static final String EXTRA_OLD_PAGES = "oldPages";
		private static final String EXTRA_ALL_PAGES = "allPages";

		private static final String TASK_CLEAR_CACHE = "clear_cache";

		public ClearingDialog() {}

		public ClearingDialog(boolean thumbnails, boolean media, boolean oldPages, boolean allPages) {
			Bundle args = new Bundle();
			args.putBoolean(EXTRA_THUMBNAILS, thumbnails);
			args.putBoolean(EXTRA_MEDIA, media);
			args.putBoolean(EXTRA_OLD_PAGES, oldPages);
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
			AsyncManager.get(this).startTask(TASK_CLEAR_CACHE, this, null, false);
		}

		private void sendUpdateCacheSize() {
			((ContentsFragment) getTargetFragment()).clearCachePreference.invalidate();
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CLEAR_CACHE, this);
			sendUpdateCacheSize();
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			Bundle args = requireArguments();
			boolean thumbnails = args.getBoolean(EXTRA_THUMBNAILS);
			boolean media = args.getBoolean(EXTRA_MEDIA);
			boolean oldPages = args.getBoolean(EXTRA_OLD_PAGES);
			boolean allPages = args.getBoolean(EXTRA_ALL_PAGES);
			Collection<PagesDatabase.ThreadKey> openThreads = Collections.emptyList();
			if (oldPages && !allPages) {
				Collection<DrawerForm.Page> drawerPages = ((FragmentHandler) requireActivity()).obtainDrawerPages();
				openThreads = new ArrayList<>(openThreads.size());
				for (DrawerForm.Page page : drawerPages) {
					if (page.threadNumber != null) {
						openThreads.add(new PagesDatabase.ThreadKey(page.chanName, page.boardName, page.threadNumber));
					}
				}
			}
			ClearCacheTask task = new ClearCacheTask(thumbnails, media, oldPages, allPages, openThreads);
			task.executeOnExecutor(ClearCacheTask.THREAD_POOL_EXECUTOR);
			return task.getHolder();
		}

		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
			dismiss();
			sendUpdateCacheSize();
		}

		@Override
		public void onRequestTaskCancel(String name, Object task) {
			((ClearCacheTask) task).cancel();
		}
	}

	private static class ClearCacheTask extends AsyncManager.SimpleTask<Void> {
		private final boolean thumbnails;
		private final boolean media;
		private final boolean oldPages;
		private final boolean allPages;
		private final Collection<PagesDatabase.ThreadKey> openThreads;

		public ClearCacheTask(boolean thumbnails, boolean media, boolean oldPages, boolean allPages,
				Collection<PagesDatabase.ThreadKey> openThreads) {
			this.thumbnails = thumbnails;
			this.media = media;
			this.oldPages = oldPages;
			this.allPages = allPages;
			this.openThreads = openThreads;
		}

		@Override
		protected Void doInBackground() {
			try {
				if (thumbnails) {
					CacheManager.getInstance().eraseThumbnailsCache();
				}
				if (media) {
					CacheManager.getInstance().eraseMediaCache();
				}
				if (oldPages && !allPages) {
					PagesDatabase.getInstance().erase(openThreads);
				}
				if (allPages) {
					PagesDatabase.getInstance().eraseAll();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null;
		}

		@Override
		public void cancel() {
			cancel(true);
		}
	}
}
