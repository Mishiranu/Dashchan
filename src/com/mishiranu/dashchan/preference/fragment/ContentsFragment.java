package com.mishiranu.dashchan.preference.fragment;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.core.EditPreference;
import com.mishiranu.dashchan.preference.core.Preference;
import com.mishiranu.dashchan.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;

public class ContentsFragment extends PreferenceFragment {
	private EditPreference downloadPathPreference;
	private Preference<?> clearCachePreference;

	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_LOAD_THUMBNAILS, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_THUMBNAILS, R.string.preference_load_thumbnails,
				R.array.preference_generic_network_choices);

		addHeader(R.string.preference_category_auto_refreshing);
		addList(Preferences.KEY_AUTO_REFRESH_MODE, Preferences.VALUES_AUTO_REFRESH_MODE,
				Preferences.DEFAULT_AUTO_REFRESH_MODE, R.string.preference_auto_refresh_mode,
				R.array.preference_auto_refresh_choices);
		addSeek(Preferences.KEY_AUTO_REFRESH_INTERVAL,
				Preferences.DEFAULT_AUTO_REFRESH_INTERVAL, R.string.preference_auto_refresh_interval,
				R.string.preference_auto_refresh_interval_summary_format, Preferences.MIN_AUTO_REFRESH_INTERVAL,
				Preferences.MAX_AUTO_REFRESH_INTERVAL, Preferences.STEP_AUTO_REFRESH_INTERVAL, 1f);

		addHeader(R.string.preference_category_downloading);
		addCheck(true, Preferences.KEY_DOWNLOAD_DETAIL_NAME,
				Preferences.DEFAULT_DOWNLOAD_DETAIL_NAME, R.string.preference_download_detail_name,
				R.string.preference_download_detail_name_summary);
		addCheck(true, Preferences.KEY_DOWNLOAD_ORIGINAL_NAME,
				Preferences.DEFAULT_DOWNLOAD_ORIGINAL_NAME, R.string.preference_download_original_name,
				R.string.preference_download_original_name_summary);
		downloadPathPreference = addEdit(Preferences.KEY_DOWNLOAD_PATH, null,
				R.string.preference_download_path, C.DEFAULT_DOWNLOAD_PATH, InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_URI);
		if (C.API_LOLLIPOP) {
			downloadPathPreference.setNeutralButton(getString(R.string.action_choose),
					() -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
							.putExtra("android.content.extra.SHOW_ADVANCED", true), C.REQUEST_CODE_OPEN_PATH));
		}
		addList(Preferences.KEY_DOWNLOAD_SUBDIR, Preferences.VALUES_DOWNLOAD_SUBDIR,
				Preferences.DEFAULT_DOWNLOAD_SUBDIR, R.string.preference_download_subdir,
				R.array.preference_download_subdir_choices);
		addEdit(Preferences.KEY_SUBDIR_PATTERN, Preferences.DEFAULT_SUBDIR_PATTERN,
				R.string.preference_subdirectory_pattern, Preferences.DEFAULT_SUBDIR_PATTERN,
				InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
				.setDescription(makeSubdirDescription());
		if (C.API_LOLLIPOP) {
			addCheck(true, Preferences.KEY_NOTIFY_DOWNLOAD_COMPLETE,
					Preferences.DEFAULT_NOTIFY_DOWNLOAD_COMPLETE, R.string.preference_notify_download_complete,
					R.string.preference_notify_download_complete_summary);
		}

		addHeader(R.string.preference_category_images);
		addList(Preferences.KEY_LOAD_NEAREST_IMAGE, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_NEAREST_IMAGE, R.string.preference_load_nearest_image,
				R.array.preference_generic_network_choices);

		addHeader(R.string.preference_category_video_player);
		boolean playerAvailable = VideoPlayer.loadLibraries(requireContext());
		if (!playerAvailable) {
			addButton(0, R.string.preference_use_video_player_warning).setSelectable(false);
		}
		addCheck(true, Preferences.KEY_USE_VIDEO_PLAYER, Preferences.DEFAULT_USE_VIDEO_PLAYER,
				R.string.preference_use_video_player, R.string.preference_use_video_player_summary)
				.setEnabled(playerAvailable);
		addList(Preferences.KEY_VIDEO_COMPLETION, Preferences.VALUES_VIDEO_COMPLETION,
				Preferences.DEFAULT_VIDEO_COMPLETION, R.string.preference_video_completion,
				R.array.preference_video_completion_choices).setEnabled(playerAvailable);
		addCheck(true, Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL,
				Preferences.DEFAULT_VIDEO_PLAY_AFTER_SCROLL, R.string.preference_video_play_after_scroll,
				R.string.preference_video_play_after_scroll_summary).setEnabled(playerAvailable);
		addCheck(true, Preferences.KEY_VIDEO_SEEK_ANY_FRAME,
				Preferences.DEFAULT_VIDEO_SEEK_ANY_FRAME, R.string.preference_video_seek_any_frame,
				R.string.preference_video_seek_any_frame_summary).setEnabled(playerAvailable);
		if (playerAvailable) {
			addDependency(Preferences.KEY_VIDEO_COMPLETION, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.KEY_USE_VIDEO_PLAYER, true);
		}

		addHeader(R.string.preference_category_additional);
		addSeek(Preferences.KEY_CACHE_SIZE, Preferences.DEFAULT_CACHE_SIZE,
				getString(R.string.preference_cache_size), "%d MB", 50, 400, 10, Preferences.MULTIPLIER_CACHE_SIZE);
		clearCachePreference = addButton(getString(R.string.preference_clear_cache), p -> {
			long cacheSize = CacheManager.getInstance().getCacheSize();
			return String.format(Locale.US, "%.2f", cacheSize / 1024. / 1024.) + " MB";
		});
		clearCachePreference.setOnClickListener(p -> {
			ClearCacheFragment fragment = new ClearCacheFragment();
			fragment.setTargetFragment(this, 0);
			fragment.show(getParentFragmentManager(), ClearCacheFragment.class.getName());
		});

		addDependency(Preferences.KEY_AUTO_REFRESH_INTERVAL, Preferences.KEY_AUTO_REFRESH_MODE, true,
				Preferences.VALUE_AUTO_REFRESH_MODE_ENABLED);
		addDependency(Preferences.KEY_SUBDIR_PATTERN, Preferences.KEY_DOWNLOAD_SUBDIR, false,
				Preferences.VALUE_DOWNLOAD_SUBDIR_DISABLED);
		updateCacheSize();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		downloadPathPreference = null;
		clearCachePreference = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		requireActivity().setTitle(R.string.preference_header_contents);
		requireActivity().getActionBar().setSubtitle(null);
	}

	private CharSequence makeSubdirDescription() {
		String[] formats = {"\\c", "\\d", "\\b", "\\t", "\\e", "<\u2026>"};
		String[] descriptions = getResources().getStringArray(R.array.preference_subdirectory_pattern_descriptions);
		SpannableStringBuilder builder = new SpannableStringBuilder();
		for (int i = 0; i < formats.length; i++) {
			if (builder.length() > 0) {
				builder.append('\n');
			}
			StringUtils.appendSpan(builder, formats[i], new TypefaceSpan("sans-serif-medium"));
			builder.append(" — ");
			builder.append(descriptions[i]);
		}
		return builder;
	}

	private void updateCacheSize() {
		long cacheSize = CacheManager.getInstance().getCacheSize();
		clearCachePreference.setEnabled(cacheSize > 0L);
		clearCachePreference.invalidate();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == C.REQUEST_CODE_OPEN_PATH && resultCode == Activity.RESULT_OK) {
			boolean success = false;
			Uri uri = data.getData();
			ContentResolver contentResolver = requireContext().getContentResolver();
			for (UriPermission uriPermission : contentResolver.getPersistedUriPermissions()) {
				if (!uri.equals(uriPermission.getUri())) {
					contentResolver.releasePersistableUriPermission(uriPermission.getUri(),
							(uriPermission.isReadPermission() ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0) |
							(uriPermission.isWritePermission() ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0));
				}
			}
			contentResolver.takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
					| Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
			String id = DocumentsContract.getTreeDocumentId(uri);
			if (id != null) {
				// TODO Handle deprecation
				String[] splitted = id.split(":", -1);
				if (splitted.length == 2) {
					String volumeName = splitted[0];
					String path = splitted[1];
					File storageDirectory = null;
					if ("primary".equals(volumeName)) {
						storageDirectory = Environment.getExternalStorageDirectory();
					} else {
						StorageManager storageManager = (StorageManager) requireContext()
								.getSystemService(Context.STORAGE_SERVICE);
						try {
							Object[] list = (Object[]) StorageManager.class.getMethod("getVolumeList")
									.invoke(storageManager);
							if (list != null) {
								for (Object volume : list) {
									Class<?> volumeClass = volume.getClass();
									String uuid = (String) volumeClass.getMethod("getUuid").invoke(volume);
									if (volumeName.equals(uuid)) {
										storageDirectory = (File) volumeClass.getMethod("getPathFile").invoke(volume);
										break;
									}
								}
							}
						} catch (Exception e) {
							// Reflective operation, ignore exception
						}
					}
					if (storageDirectory != null) {
						if (storageDirectory.equals(Environment.getExternalStorageDirectory())) {
							path = "/" + path;
						} else {
							path = new File(storageDirectory, path).getAbsolutePath();
						}
						if (!path.endsWith("/")) {
							path += "/";
						}
						AlertDialog dialog = getDialog(downloadPathPreference);
						if (dialog != null) {
							downloadPathPreference.setInput(dialog, path);
						}
						success = true;
					}
				}
			}
			if (!success) {
				ToastUtils.show(requireContext(), R.string.message_unknown_error);
			}
		}
	}

	public static class ClearCacheFragment extends DialogFragment implements DialogInterface.OnMultiChoiceClickListener,
			DialogInterface.OnClickListener, DialogInterface.OnShowListener {
		private static final String EXTRA_CHECKED_ITEMS = "checkedItems";

		private boolean[] checkedItems;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			checkedItems = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_CHECKED_ITEMS) : null;
			if (checkedItems == null) {
				checkedItems = new boolean[] {true, true, true, false};
			}
			String[] items = getResources().getStringArray(R.array.preference_clear_cache_choices);
			AlertDialog dialog = new AlertDialog.Builder(requireContext())
					.setTitle(getString(R.string.preference_clear_cache))
					.setMultiChoiceItems(items, checkedItems, this)
					.setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, this)
					.create();
			dialog.setOnShowListener(this);
			return dialog;
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED_ITEMS, checkedItems);
		}

		@Override
		public void onShow(DialogInterface dialog) {
			((AlertDialog) dialog).getListView().getChildAt(2).setEnabled(!checkedItems[3]);
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
					ListView listView = ((AlertDialog) dialog).getListView();
					listView.getChildAt(2).setEnabled(!isChecked);
					break;
				}
			}
			checkedItems[which] = isChecked;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			ClearingDialog clearingDialog = new ClearingDialog(checkedItems[0], checkedItems[1],
					checkedItems[2], checkedItems[3]);
			clearingDialog.setTargetFragment(getTargetFragment(), 0);
			clearingDialog.show(getTargetFragment().getParentFragmentManager(), ClearingDialog.class.getName());
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
			dialog.setMessage(getString(R.string.message_clearing));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			AsyncManager.get(this).startTask(TASK_CLEAR_CACHE, this, null, false);
		}

		private void sendUpdateCacheSize() {
			((ContentsFragment) getTargetFragment()).updateCacheSize();
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
			ClearCacheTask task = new ClearCacheTask(args.getBoolean(EXTRA_THUMBNAILS), args.getBoolean(EXTRA_MEDIA),
					args.getBoolean(EXTRA_OLD_PAGES), args.getBoolean(EXTRA_ALL_PAGES));
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

	private static class ClearCacheTask extends AsyncManager.SimpleTask<Void, Void, Void> {
		private final boolean thumbnails;
		private final boolean media;
		private final boolean oldPages;
		private final boolean allPages;

		public ClearCacheTask(boolean thumbnails, boolean media, boolean oldPages, boolean allPages) {
			this.thumbnails = thumbnails;
			this.media = media;
			this.oldPages = oldPages;
			this.allPages = allPages;
		}

		@Override
		protected Void doInBackground(Void... params) {
			CacheManager cacheManager = CacheManager.getInstance();
			try {
				if (thumbnails) {
					cacheManager.eraseThumbnailsCache();
				}
				if (media) {
					cacheManager.eraseMediaCache();
				}
				if (oldPages && !allPages) {
					cacheManager.erasePagesCache(true);
				}
				if (allPages) {
					cacheManager.erasePagesCache(false);
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
