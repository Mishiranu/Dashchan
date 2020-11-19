package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import chan.content.ChanMarkup;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.preference.core.EditPreference;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;

public class MediaFragment extends PreferenceFragment implements FragmentHandler.Callback {
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

		addHeader(R.string.images);
		addList(Preferences.KEY_LOAD_THUMBNAILS, enumList(Preferences.NetworkMode.values(), v -> v.value),
				Preferences.DEFAULT_LOAD_THUMBNAILS.value, R.string.load_thumbnails,
				enumResList(Preferences.NetworkMode.values(), v -> v.titleResId));
		addList(Preferences.KEY_LOAD_NEAREST_IMAGE, enumList(Preferences.NetworkMode.values(), v -> v.value),
				Preferences.DEFAULT_LOAD_NEAREST_IMAGE.value, R.string.load_nearest_image,
				enumResList(Preferences.NetworkMode.values(), v -> v.titleResId));

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
		addList(Preferences.KEY_DOWNLOAD_SUBDIR, enumList(Preferences.DownloadSubdirMode.values(), v -> v.value),
				Preferences.DEFAULT_DOWNLOAD_SUBDIR.value, R.string.show_download_configuration_dialog,
				enumResList(Preferences.DownloadSubdirMode.values(), v -> v.titleResId));
		EditPreference subdirectoryPreference = addEdit(Preferences.KEY_SUBDIR_PATTERN,
				Preferences.DEFAULT_SUBDIR_PATTERN, R.string.subdirectory_pattern, Preferences.DEFAULT_SUBDIR_PATTERN,
				InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		String subdirectoryHtml = IOUtils.readRawResourceString(getResources(), R.raw.markup_subdirectory);
		subdirectoryPreference.setDescription(BUILDER_SUBDIRECTORY.fromHtmlReduced(subdirectoryHtml));
		subdirectoryPreference.setNeutralButton(getString(R.string.more_info),
				() -> showSubdirectoryInfoDialog(getChildFragmentManager()));
		if (C.API_LOLLIPOP) {
			addCheck(true, Preferences.KEY_NOTIFY_DOWNLOAD_COMPLETE, Preferences.DEFAULT_NOTIFY_DOWNLOAD_COMPLETE,
					R.string.notify_when_download_is_completed, R.string.notify_when_download_is_completed__summary);
		}

		addHeader(R.string.video_player);
		Pair<Boolean, String> playerLoadResult = VideoPlayer.loadLibraries(requireContext());
		if (!playerLoadResult.first) {
			if (playerLoadResult.second != null) {
				SpannableStringBuilder builder = new SpannableStringBuilder(playerLoadResult.second);
				if (builder.length() == 0) {
					builder.append(getString(R.string.unknown_error));
				}
				builder.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(requireContext(),
						R.attr.colorTextError)), 0, builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				addButton(null, builder).setSelectable(false);
			} else {
				addButton(0, R.string.requires_decoding_libraries__sentence).setSelectable(false);
			}
		}
		addCheck(true, Preferences.KEY_USE_VIDEO_PLAYER, Preferences.DEFAULT_USE_VIDEO_PLAYER,
				R.string.use_built_in_video_player, R.string.use_built_in_video_player__summary)
				.setEnabled(playerLoadResult.first);
		addList(Preferences.KEY_VIDEO_COMPLETION, enumList(Preferences.VideoCompletionMode.values(), o -> o.value),
				Preferences.DEFAULT_VIDEO_COMPLETION.value, R.string.action_on_playback_completion,
				enumResList(Preferences.VideoCompletionMode.values(), o -> o.titleResId))
				.setEnabled(playerLoadResult.first);
		addCheck(true, Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.DEFAULT_VIDEO_PLAY_AFTER_SCROLL,
				R.string.play_after_scroll, R.string.play_after_scroll__summary).setEnabled(playerLoadResult.first);
		addCheck(true, Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.DEFAULT_VIDEO_SEEK_ANY_FRAME,
				R.string.seek_any_frame, R.string.seek_any_frame__summary).setEnabled(playerLoadResult.first);
		if (playerLoadResult.first) {
			addDependency(Preferences.KEY_VIDEO_COMPLETION, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_PLAY_AFTER_SCROLL, Preferences.KEY_USE_VIDEO_PLAYER, true);
			addDependency(Preferences.KEY_VIDEO_SEEK_ANY_FRAME, Preferences.KEY_USE_VIDEO_PLAYER, true);
		}

		addHeader(R.string.additional);
		addSeek(Preferences.KEY_CACHE_SIZE, Preferences.DEFAULT_CACHE_SIZE, getString(R.string.cache_size), "%d MB",
				null, Preferences.MIN_CACHE_SIZE, Preferences.MAX_CACHE_SIZE, Preferences.STEP_CACHE_SIZE);
		clearCachePreference = addButton(getString(R.string.clear_cache),
				p -> StringUtils.formatFileSizeMegabytes(CacheManager.getInstance().getCacheSize()));
		clearCachePreference.setOnClickListener(p -> {
			ClearCacheDialog dialog = new ClearCacheDialog();
			dialog.show(getChildFragmentManager(), ClearCacheDialog.class.getName());
		});
		clearCachePreference.invalidate();

		addDependency(Preferences.KEY_SUBDIR_PATTERN, Preferences.KEY_DOWNLOAD_SUBDIR, false,
				Preferences.DownloadSubdirMode.DISABLED.value);
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
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.media), null);
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

	private static void showSubdirectoryInfoDialog(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			String html = IOUtils.readRawResourceString(context.getResources(), R.raw.markup_subdirectory_info);
			return new AlertDialog.Builder(context)
					.setTitle(R.string.subdirectory_pattern)
					.setMessage(BUILDER_SUBDIRECTORY.fromHtmlReduced(html))
					.setPositiveButton(android.R.string.ok, null)
					.create();
		});
	}

	private static final ChanMarkup.MarkupBuilder BUILDER_SUBDIRECTORY = new ChanMarkup
			.MarkupBuilder(markup -> markup.addTag("b", ChanMarkup.TAG_BOLD));

	public static class ClearCacheDialog extends DialogFragment {
		private static final String EXTRA_CHECKED_ITEMS = "checkedItems";

		private boolean[] checkedItems;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			checkedItems = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_CHECKED_ITEMS) : null;
			if (checkedItems == null) {
				checkedItems = new boolean[] {true, true};
			}
			String[] items = {getString(R.string.thumbnails), getString(R.string.cached_files)};
			return new AlertDialog.Builder(requireContext())
					.setTitle(getString(R.string.clear_cache))
					.setMultiChoiceItems(items, checkedItems, (d, which, isChecked) -> checkedItems[which] = isChecked)
					.setPositiveButton(android.R.string.ok, (d, w) -> {
						ClearingDialog clearingDialog = new ClearingDialog(checkedItems[0], checkedItems[1]);
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
			outState.putBooleanArray(EXTRA_CHECKED_ITEMS, checkedItems);
		}
	}

	public static class ClearingDialog extends DialogFragment {
		private static final String EXTRA_THUMBNAILS = "thumbnails";
		private static final String EXTRA_MEDIA = "media";

		public ClearingDialog() {}

		public ClearingDialog(boolean thumbnails, boolean media) {
			Bundle args = new Bundle();
			args.putBoolean(EXTRA_THUMBNAILS, thumbnails);
			args.putBoolean(EXTRA_MEDIA, media);
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
				boolean thumbnails = args.getBoolean(EXTRA_THUMBNAILS);
				boolean media = args.getBoolean(EXTRA_MEDIA);
				ClearCacheTask task = new ClearCacheTask(viewModel, thumbnails, media);
				task.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(this, result -> {
				dismiss();
				sendUpdateCacheSize();
			});
		}

		private void sendUpdateCacheSize() {
			((MediaFragment) getTargetFragment()).clearCachePreference.invalidate();
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
		private final boolean thumbnails;
		private final boolean media;

		public ClearCacheTask(ClearCacheViewModel viewModel, boolean thumbnails, boolean media) {
			this.viewModel = viewModel;
			this.thumbnails = thumbnails;
			this.media = media;
		}

		@Override
		protected Void run() throws InterruptedException {
			if (thumbnails) {
				CacheManager.getInstance().eraseThumbnailsCache();
			}
			if (isCancelled()) {
				return null;
			}
			if (media) {
				CacheManager.getInstance().eraseMediaCache();
			}
			return null;
		}

		@Override
		protected void onComplete(Object result) {
			viewModel.handleResult(this);
		}
	}
}
