package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.BackupManager;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public class AboutFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addButton(R.string.preference_statistics, 0)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new StatisticsFragment()));
		addButton(R.string.preference_backup_data, R.string.preference_backup_data_summary)
				.setOnClickListener(p -> new BackupDialog()
						.show(getChildFragmentManager(), BackupDialog.class.getName()));
		addButton(R.string.preference_changelog, 0)
				.setOnClickListener(p -> new ReadDialog(ReadDialog.TYPE_CHANGELOG)
						.show(getChildFragmentManager(), ReadDialog.class.getName()));
		addButton(R.string.preference_check_for_updates, 0)
				.setOnClickListener(p -> new ReadDialog(ReadDialog.TYPE_UPDATE)
						.show(getChildFragmentManager(), ReadDialog.class.getName()));
		addButton(R.string.preference_licenses, R.string.preference_licenses_summary)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new TextFragment(TextFragment.TYPE_LICENSES, null)));
		addButton(getString(R.string.preference_version), C.BUILD_VERSION +
				" (" + DateFormat.getDateFormat(requireContext()).format(C.BUILD_TIMESTAMP) + ")");
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		requireActivity().setTitle(R.string.preference_header_about);
		requireActivity().getActionBar().setSubtitle(null);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == C.REQUEST_CODE_STORAGE_PERMISSION && grantResults.length > 0) {
			boolean granted = true;
			for (int grantResult : grantResults) {
				granted &= grantResult == PackageManager.PERMISSION_GRANTED;
			}
			if (granted) {
				restoreBackup();
			} else {
				ToastUtils.show(requireContext(), R.string.message_no_access_to_memory);
			}
		}
	}

	private void restoreBackup() {
		if (C.API_MARSHMALLOW && requireContext()
				.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
					C.REQUEST_CODE_STORAGE_PERMISSION);
		} else {
			LinkedHashMap<File, String> filesMap = BackupManager.getAvailableBackups(requireContext());
			if (filesMap != null && filesMap.size() > 0) {
				new RestoreFragment(filesMap).show(getChildFragmentManager(), RestoreFragment.class.getName());
			} else {
				ToastUtils.show(requireContext(), R.string.message_no_backups);
			}
		}
	}

	public static class BackupDialog extends DialogFragment implements DialogInterface.OnClickListener {
		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			String[] items = getResources().getStringArray(R.array.preference_backup_data_choices);
			return new AlertDialog.Builder(requireContext()).setItems(items, this)
					.setNegativeButton(android.R.string.cancel, null)
					.create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == 0) {
				DownloadService.Binder binder = ((FragmentHandler) requireActivity()).getDownloadBinder();
				if (binder != null) {
					BackupManager.makeBackup(binder, requireContext());
				}
			} else if (which == 1) {
				((AboutFragment) getParentFragment()).restoreBackup();
			}
		}
	}

	public static class RestoreFragment extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String EXTRA_FILES = "files";
		private static final String EXTRA_NAMES = "names";

		public RestoreFragment() {}

		public RestoreFragment(LinkedHashMap<File, String> filesMap) {
			Bundle args = new Bundle();
			ArrayList<String> files = new ArrayList<>(filesMap.size());
			ArrayList<String> names = new ArrayList<>(filesMap.size());
			for (LinkedHashMap.Entry<File, String> pair : filesMap.entrySet()) {
				files.add(pair.getKey().getAbsolutePath());
				names.add(pair.getValue());
			}
			args.putStringArrayList(EXTRA_FILES, files);
			args.putStringArrayList(EXTRA_NAMES, names);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			ArrayList<String> names = requireArguments().getStringArrayList(EXTRA_NAMES);
			String[] items = CommonUtils.toArray(names, String.class);
			return new AlertDialog.Builder(requireContext())
					.setSingleChoiceItems(items, 0, null)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, this)
					.create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
			File file = new File(requireArguments().getStringArrayList(EXTRA_FILES).get(index));
			BackupManager.loadBackup(requireContext(), file);
		}
	}

	public static class ReadDialog extends DialogFragment implements AsyncManager.Callback {
		private static final String EXTRA_TYPE = "type";

		private static final int TYPE_CHANGELOG = 0;
		private static final int TYPE_UPDATE = 1;

		private static final String TASK_READ_CHANGELOG = "read_changelog";
		private static final String TASK_READ_UPDATE = "read_update";

		public ReadDialog() {}

		public ReadDialog(int type) {
			Bundle args = new Bundle();
			args.putInt(EXTRA_TYPE, type);
			setArguments(args);
		}

		@NonNull
		@Override
		public ProgressDialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(requireContext(), null);
			dialog.setMessage(getString(R.string.message_loading));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			switch (requireArguments().getInt(EXTRA_TYPE)) {
				case TYPE_CHANGELOG: {
					AsyncManager.get(this).startTask(TASK_READ_CHANGELOG, this, null, false);
					break;
				}
				case TYPE_UPDATE: {
					AsyncManager.get(this).startTask(TASK_READ_UPDATE, this, null, false);
					break;
				}
			}
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			switch (requireArguments().getInt(EXTRA_TYPE)) {
				case TYPE_CHANGELOG: {
					AsyncManager.get(this).cancelTask(TASK_READ_CHANGELOG, this);
					break;
				}
				case TYPE_UPDATE: {
					AsyncManager.get(this).cancelTask(TASK_READ_UPDATE, this);
					break;
				}
			}
		}

		private static class ReadUpdateHolder extends AsyncManager.Holder implements ReadUpdateTask.Callback {
			@Override
			public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap, ErrorItem errorItem) {
				storeResult(updateDataMap, errorItem);
			}
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			switch (name) {
				case TASK_READ_CHANGELOG: {
					ReadChangelogTask task = new ReadChangelogTask(requireContext());
					task.executeOnExecutor(ReadChangelogTask.THREAD_POOL_EXECUTOR);
					return task.getHolder();
				}
				case TASK_READ_UPDATE: {
					ReadUpdateHolder holder = new ReadUpdateHolder();
					ReadUpdateTask task = new ReadUpdateTask(requireContext(), holder);
					task.executeOnExecutor(ReadChangelogTask.THREAD_POOL_EXECUTOR);
					return holder.attach(task);
				}
			}
			return null;
		}

		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
			dismissAllowingStateLoss();
			switch (name) {
				case TASK_READ_CHANGELOG: {
					String content = holder.nextArgument();
					ErrorItem errorItem = holder.nextArgument();
					if (errorItem == null) {
						((FragmentHandler) requireActivity())
								.pushFragment(new TextFragment(TextFragment.TYPE_CHANGELOG, content));
					} else {
						ToastUtils.show(requireContext(), errorItem);
					}
					break;
				}
				case TASK_READ_UPDATE: {
					ReadUpdateTask.UpdateDataMap updateDataMap = holder.nextArgument();
					ErrorItem errorItem = holder.nextArgument();
					if (updateDataMap != null) {
						((FragmentHandler) requireActivity())
								.pushFragment(new UpdateFragment(updateDataMap));
					} else {
						ToastUtils.show(requireContext(), errorItem);
					}
					break;
				}
			}
		}

		@Override
		public void onRequestTaskCancel(String name, Object task) {
			switch (name) {
				case TASK_READ_CHANGELOG: {
					((ReadChangelogTask) task).cancel();
					break;
				}
				case TASK_READ_UPDATE: {
					((ReadUpdateTask) task).cancel();
					break;
				}
			}
		}
	}

	private static class ReadChangelogTask extends AsyncManager.SimpleTask<Void, Void, Boolean> {
		private final HttpHolder holder = new HttpHolder();

		private final Context context;

		private String result;
		private ErrorItem errorItem;

		public ReadChangelogTask(Context context) {
			this.context = context.getApplicationContext();
		}

		@Override
		public Boolean doInBackground(Void... params) {
			String page = "Changelog-EN";
			for (Locale locale : LocaleManager.getInstance().list(context)) {
				String language = locale.getLanguage();
				if ("ru".equals(language)) {
					page = "Changelog-RU";
					break;
				}
			}
			Uri uri = ChanLocator.getDefault().buildPathWithHost("github.com", "Mishiranu", "Dashchan", "wiki", page);
			try {
				String result = new HttpRequest(uri, holder).read().getString();
				if (result != null) {
					result = ChangelogGroupCallback.parse(result);
				}
				if (result == null) {
					errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
					return false;
				} else {
					this.result = result;
					return true;
				}
			} catch (HttpException e) {
				errorItem = e.getErrorItemAndHandle();
				holder.disconnect();
				return false;
			} finally {
				holder.cleanup();
			}
		}

		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Boolean result) {
			holder.storeResult(this.result, errorItem);
		}

		@Override
		public void cancel() {
			cancel(true);
			holder.interrupt();
		}
	}

	private static class ChangelogGroupCallback implements GroupParser.Callback {
		private String result;

		public static String parse(String source) {
			ChangelogGroupCallback callback = new ChangelogGroupCallback();
			try {
				GroupParser.parse(source, callback);
			} catch (ParseException e) {
				Log.persistent().stack(e);
			}
			return callback.result;
		}

		@Override
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
			return "div".equals(tagName) && "markdown-body".equals(parser.getAttr(attrs, "class"));
		}

		@Override
		public void onEndElement(GroupParser parser, String tagName) {}

		@Override
		public void onText(GroupParser parser, String source, int start, int end) {}

		@Override
		public void onGroupComplete(GroupParser parser, String text) throws ParseException {
			result = text;
			throw new ParseException();
		}
	}
}
