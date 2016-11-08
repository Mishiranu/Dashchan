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

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.widget.ListView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.ExtendedEditTextPreference;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ToastUtils;

public class ContentsFragment extends BasePreferenceFragment implements DialogInterface.OnClickListener {
	private ExtendedEditTextPreference mDownloadPathPreference;
	private Preference mClearCachePreference;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		makeList(null, Preferences.KEY_LOAD_THUMBNAILS, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_THUMBNAILS, R.string.preference_load_thumbnails,
				R.array.preference_generic_network_choices);
		makeCheckBox(null, true, Preferences.KEY_DOWNLOAD_YOUTUBE_TITLES, Preferences.DEFAULT_DOWNLOAD_YOUTUBE_TITLES,
				R.string.preference_download_youtube_titles, R.string.preference_download_youtube_titles_summary);

		PreferenceCategory autoRefreshingCategory = makeCategory(R.string.preference_category_auto_refreshing);
		makeList(autoRefreshingCategory, Preferences.KEY_AUTO_REFRESH_MODE, Preferences.VALUES_AUTO_REFRESH_MODE,
				Preferences.DEFAULT_AUTO_REFRESH_MODE, R.string.preference_auto_refresh_mode,
				R.array.preference_auto_refresh_choices);
		makeSeekBar(autoRefreshingCategory, Preferences.KEY_AUTO_REFRESH_INTERVAL,
				Preferences.DEFAULT_AUTO_REFRESH_INTERVAL, R.string.preference_auto_refresh_interval,
				R.string.preference_auto_refresh_interval_summary_format, Preferences.MIN_AUTO_REFRESH_INTERVAL,
				Preferences.MAX_AUTO_REFRESH_INTERVAL, Preferences.STEP_AUTO_REFRESH_INTERVAL, 1f);

		PreferenceCategory downloadingCategory = makeCategory(R.string.preference_category_downloading);
		makeCheckBox(downloadingCategory, true, Preferences.KEY_DOWNLOAD_DETAIL_NAME,
				Preferences.DEFAULT_DOWNLOAD_DETAIL_NAME, R.string.preference_download_detail_name,
				R.string.preference_download_detail_name_summary);
		makeCheckBox(downloadingCategory, true, Preferences.KEY_DOWNLOAD_ORIGINAL_NAME,
				Preferences.DEFAULT_DOWNLOAD_ORIGINAL_NAME, R.string.preference_download_original_name,
				R.string.preference_download_original_name_summary);
		mDownloadPathPreference = makeEditText(downloadingCategory, Preferences.KEY_DOWNLOAD_PATH, null,
				R.string.preference_download_path, 0, C.DEFAULT_DOWNLOAD_PATH, InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_URI, true);
		if (C.API_LOLLIPOP) {
			mDownloadPathPreference.setNeutralButton(getString(R.string.action_choose), this, false);
		}
		makeList(downloadingCategory, Preferences.KEY_DOWNLOAD_SUBDIR, Preferences.VALUES_DOWNLOAD_SUBDIR,
				Preferences.DEFAULT_DOWNLOAD_SUBDIR, R.string.preference_download_subdir,
				R.array.preference_download_subdir_choices);
		makeEditText(downloadingCategory, Preferences.KEY_SUBDIR_PATTERN, Preferences.DEFAULT_SUBDIR_PATTERN,
				R.string.preference_subdirectory_pattern, 0, Preferences.DEFAULT_SUBDIR_PATTERN,
				InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true)
				.setDescription(makeSubdirDescrption());
		if (C.API_LOLLIPOP) {
			makeCheckBox(downloadingCategory, true, Preferences.KEY_NOTIFY_DOWNLOAD_COMPLETE,
					Preferences.DEFAULT_NOTIFY_DOWNLOAD_COMPLETE, R.string.preference_notify_download_complete,
					R.string.preference_notify_download_complete_summary);
		}

		PreferenceCategory imagesCategory = makeCategory(R.string.preference_category_images);
		makeList(imagesCategory, Preferences.KEY_LOAD_NEAREST_IMAGE, Preferences.GENERIC_VALUES_NETWORK,
				Preferences.DEFAULT_LOAD_NEAREST_IMAGE, R.string.preference_load_nearest_image,
				R.array.preference_generic_network_choices);

		PreferenceCategory videoPlayerCategory = makeCategory(R.string.preference_category_video_player);
		boolean playerAvailable = VideoPlayer.loadLibraries(getActivity());
		if (!playerAvailable) {
			makeButton(videoPlayerCategory, 0, R.string.preference_use_video_player_warning, true).setSelectable(false);
		}
		makeCheckBox(videoPlayerCategory, true, Preferences.KEY_USE_VIDEO_PLAYER, Preferences.DEFAULT_USE_VIDEO_PLAYER,
				R.string.preference_use_video_player, R.string.preference_use_video_player_summary)
				.setEnabled(playerAvailable);
		makeList(videoPlayerCategory, Preferences.KEY_VIDEO_COMPLETION, Preferences.VALUES_VIDEO_COMPLETION,
				Preferences.DEFAULT_VIDEO_COMPLETION, R.string.preference_video_completion,
				R.array.preference_video_completion_choices).setEnabled(playerAvailable);
		makeCheckBox(videoPlayerCategory, true, Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL,
				Preferences.DEFAULT_VIDEO_PLAY_AFTER_SCROLL, R.string.preference_video_play_after_scroll,
				R.string.preference_video_play_after_scroll_summary).setEnabled(playerAvailable);
		makeCheckBox(videoPlayerCategory, true, Preferences.KEY_VIDEO_SEEK_ANY_FRAME,
				Preferences.DEFAULT_VIDEO_SEEK_ANY_FRAME, R.string.preference_video_seek_any_frame,
				R.string.preference_video_seek_any_frame_summary).setEnabled(playerAvailable);
		if (playerAvailable) {
			addDependency(Preferences.KEY_VIDEO_COMPLETION, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.KEY_USE_VIDEO_PLAYER, true);
		}

		PreferenceCategory additionalCategory = makeCategory(R.string.preference_category_additional);
		makeSeekBar(additionalCategory, Preferences.KEY_CACHE_SIZE, Preferences.DEFAULT_CACHE_SIZE,
				getString(R.string.preference_cache_size), "%d MB", 50, 400, 10, Preferences.MULTIPLIER_CACHE_SIZE);
		mClearCachePreference = makeButton(additionalCategory, R.string.preference_clear_cache, 0, false);

		addDependency(Preferences.KEY_AUTO_REFRESH_INTERVAL, Preferences.KEY_AUTO_REFRESH_MODE, true,
				Preferences.VALUE_AUTO_REFRESH_MODE_ENABLED);
		addDependency(Preferences.KEY_SUBDIR_PATTERN, Preferences.KEY_DOWNLOAD_SUBDIR, false,
				Preferences.VALUE_DOWNLOAD_SUBDIR_DISABLED);
		updateCacheSize();
	}

	private CharSequence makeSubdirDescrption() {
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

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mClearCachePreference) {
			ClearCacheFragment fragment = new ClearCacheFragment();
			fragment.setTargetFragment(this, 0);
			fragment.show(getFragmentManager(), ClearCacheFragment.class.getName());
		}
		return super.onPreferenceClick(preference);
	}

	private void updateCacheSize() {
		long cacheSize = CacheManager.getInstance().getCacheSize();
		String summary = String.format(Locale.US, "%.2f", cacheSize / 1024. / 1024.) + " MB";
		mClearCachePreference.setSummary(summary);
		mClearCachePreference.setEnabled(cacheSize > 0L);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onClick(DialogInterface dialog, int which) {
		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
				.putExtra("android.content.extra.SHOW_ADVANCED", true), C.REQUEST_CODE_OPEN_PATH);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == C.REQUEST_CODE_OPEN_PATH && resultCode == Activity.RESULT_OK) {
			boolean success = false;
			Uri uri = data.getData();
			ContentResolver contentResolver = getActivity().getContentResolver();
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
				String[] splitted = id.split(":", -1);
				if (splitted.length == 2) {
					String volumeName = splitted[0];
					String path = splitted[1];
					File storageDirectory = null;
					if ("primary".equals(volumeName)) {
						storageDirectory = Environment.getExternalStorageDirectory();
					} else {
						StorageManager storageManager = (StorageManager) getActivity()
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
							// Ignore
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
						mDownloadPathPreference.getEditText().setText(path);
						success = true;
					}
				}
			}
			if (!success) {
				ToastUtils.show(getActivity(), R.string.message_unknown_error);
			}
		}
	}

	public static class ClearCacheFragment extends DialogFragment implements DialogInterface.OnMultiChoiceClickListener,
			DialogInterface.OnClickListener, DialogInterface.OnShowListener {
		private static final String EXTRA_CHECKED_ITEMS = "checkedItems";

		private boolean[] mCheckedItems;

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			mCheckedItems = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_CHECKED_ITEMS) : null;
			if (mCheckedItems == null) {
				mCheckedItems = new boolean[] {true, true, true, false};
			}
			String[] items = getResources().getStringArray(R.array.preference_clear_cache_choices);
			AlertDialog dialog = new AlertDialog.Builder(getActivity())
					.setTitle(getString(R.string.preference_clear_cache))
					.setMultiChoiceItems(items, mCheckedItems, this)
					.setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, this)
					.create();
			dialog.setOnShowListener(this);
			return dialog;
		}

		@Override
		public void onSaveInstanceState(Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED_ITEMS, mCheckedItems);
		}

		@Override
		public void onShow(DialogInterface dialog) {
			((AlertDialog) dialog).getListView().getChildAt(2).setEnabled(!mCheckedItems[3]);
		}

		@Override
		public void onClick(DialogInterface dialog, int which, boolean isChecked) {
			switch (which) {
				case 2: {
					if (mCheckedItems[3]) {
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
			mCheckedItems[which] = isChecked;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			ClearingDialog clearingDialog = new ClearingDialog(mCheckedItems[0], mCheckedItems[1],
					mCheckedItems[2], mCheckedItems[3]);
			clearingDialog.setTargetFragment(getTargetFragment(), 0);
			clearingDialog.show(getTargetFragment().getFragmentManager(), ClearingDialog.class.getName());
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

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setMessage(getString(R.string.message_clearing));
			dialog.setCanceledOnTouchOutside(false);
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
		public void onCancel(DialogInterface dialog) {
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CLEAR_CACHE, this);
			sendUpdateCacheSize();
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			Bundle args = getArguments();
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
		private final boolean mThumbnails;
		private final boolean mMedia;
		private final boolean mOldPages;
		private final boolean mAllPages;

		public ClearCacheTask(boolean thumbnails, boolean media, boolean oldPages, boolean allPages) {
			mThumbnails = thumbnails;
			mMedia = media;
			mOldPages = oldPages;
			mAllPages = allPages;
		}

		@Override
		protected Void doInBackground(Void... params) {
			CacheManager cacheManager = CacheManager.getInstance();
			try {
				if (mThumbnails) {
					cacheManager.eraseThumbnailsCache();
				}
				if (mMedia) {
					cacheManager.eraseMediaCache();
				}
				if (mOldPages && !mAllPages) {
					cacheManager.erasePagesCache(true);
				}
				if (mAllPages) {
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