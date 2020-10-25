package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.BackupManager;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AboutFragment extends PreferenceFragment implements ActivityHandler {
	private static final String EXTRA_IN_STORAGE_REQUEST = "inStorageRequest";

	private boolean inStorageRequest = false;

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

		addButton(R.string.statistics, 0)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new StatisticsFragment()));
		addButton(R.string.backup_data, R.string.backup_data__summary)
				.setOnClickListener(p -> new BackupDialog()
						.show(getChildFragmentManager(), BackupDialog.class.getName()));
		addButton(R.string.changelog, 0)
				.setOnClickListener(p -> new ReadDialog(ReadDialog.Type.CHANGELOG)
						.show(getChildFragmentManager(), ReadDialog.class.getName()));
		addButton(R.string.check_for_updates, 0)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new UpdateFragment()));
		addButton(R.string.foss_licenses, R.string.foss_licenses__summary)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new TextFragment(TextFragment.Type.LICENSES, null)));
		addButton(getString(R.string.build_version), BuildConfig.VERSION_NAME);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.about), null);
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
			if (Preferences.getDownloadUriTree(requireContext()) != null) {
				restoreBackup();
			}
		}
	}

	private void restoreBackup() {
		if (C.USE_SAF && Preferences.getDownloadUriTree(requireContext()) == null) {
			if (((FragmentHandler) requireActivity()).requestStorage()) {
				inStorageRequest = true;
			}
		} else {
			LinkedHashMap<DataFile, String> filesMap = BackupManager.getAvailableBackups(requireContext());
			if (filesMap != null && filesMap.size() > 0) {
				new RestoreFragment(filesMap).show(getChildFragmentManager(), RestoreFragment.class.getName());
			} else {
				ToastUtils.show(requireContext(), R.string.backups_not_found);
			}
		}
	}

	public static class BackupDialog extends DialogFragment implements DialogInterface.OnClickListener {
		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			String[] items = {getString(R.string.save_data), getString(R.string.restore_data)};
			return new AlertDialog.Builder(requireContext())
					.setItems(items, this)
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

		public RestoreFragment(LinkedHashMap<DataFile, String> filesMap) {
			Bundle args = new Bundle();
			ArrayList<String> files = new ArrayList<>(filesMap.size());
			ArrayList<String> names = new ArrayList<>(filesMap.size());
			for (LinkedHashMap.Entry<DataFile, String> pair : filesMap.entrySet()) {
				files.add(pair.getKey().getRelativePath());
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
			String path = requireArguments().getStringArrayList(EXTRA_FILES).get(index);
			DataFile file = DataFile.obtain(requireContext(), DataFile.Target.DOWNLOADS, path);
			BackupManager.loadBackup(requireContext(), file);
		}
	}

	public static class ReadDialog extends DialogFragment implements AsyncManager.Callback {
		private static final String EXTRA_TYPE = "type";

		private enum Type {CHANGELOG}

		private static final String TASK_READ_CHANGELOG = "read_changelog";

		public ReadDialog() {}

		public ReadDialog(Type type) {
			Bundle args = new Bundle();
			args.putString(EXTRA_TYPE, type.name());
			setArguments(args);
		}

		@NonNull
		@Override
		public ProgressDialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(requireContext(), null);
			dialog.setMessage(getString(R.string.loading__ellipsis));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
				case CHANGELOG: {
					AsyncManager.get(this).startTask(TASK_READ_CHANGELOG, this, null, false);
					break;
				}
			}
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
				case CHANGELOG: {
					AsyncManager.get(this).cancelTask(TASK_READ_CHANGELOG, this);
					break;
				}
			}
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			switch (name) {
				case TASK_READ_CHANGELOG: {
					ReadChangelogTask task = new ReadChangelogTask(requireContext());
					task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
					return task.getHolder();
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
								.pushFragment(new TextFragment(TextFragment.Type.CHANGELOG, content));
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
			}
		}
	}

	private static class ReadChangelogTask extends AsyncManager.SimpleTask<Boolean> {
		private static final Pattern PATTERN_TITLE = Pattern.compile("<h1.*?>Changelog (.*)</h1>");

		private final HttpHolder holder = new HttpHolder(Chan.getFallback());

		private final Configuration configuration;

		private String result;
		private ErrorItem errorItem;

		public ReadChangelogTask(Context context) {
			configuration = context.getResources().getConfiguration();
		}

		private static String downloadChangelog(HttpHolder holder, String suffix) throws HttpException {
			Uri uri = Chan.getFallback().locator.buildPathWithHost("github.com",
					"Mishiranu", "Dashchan", "wiki", "Changelog-" + suffix);
			String response = new HttpRequest(uri, holder).setSuccessOnly(false).perform().readString();
			Matcher matcher = PATTERN_TITLE.matcher(StringUtils.emptyIfNull(response));
			if (matcher.find()) {
				String titleSuffix = matcher.group(1);
				if (titleSuffix.replace(' ', '-').toLowerCase(Locale.US).equals(suffix.toLowerCase(Locale.US))) {
					return response;
				}
			}
			return null;
		}

		@Override
		public Boolean run() {
			try {
				String result = null;
				try (HttpHolder.Use ignored = holder.use()) {
					for (Locale locale : LocaleManager.getInstance().getLocales(configuration)) {
						String language = locale.getLanguage();
						String country = locale.getCountry();
						if (!StringUtils.isEmpty(country)) {
							result = downloadChangelog(holder, language.toUpperCase(Locale.US) +
									"-" + country.toUpperCase(Locale.US));
							if (result != null) {
								break;
							}
						}
						result = downloadChangelog(holder, language.toUpperCase(Locale.US));
						if (result != null) {
							break;
						}
					}
					if (result == null) {
						result = downloadChangelog(holder, Locale.US.getLanguage().toUpperCase(Locale.US));
					}
				}
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
				return false;
			}
		}

		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Boolean result) {
			holder.storeResult(this.result, errorItem);
		}

		@Override
		public void cancel() {
			super.cancel();
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
				if (StringUtils.isEmpty(callback.result)) {
					Log.persistent().stack(e);
				}
			}
			return callback.result;
		}

		@Override
		public boolean onStartElement(GroupParser parser, String tagName, GroupParser.Attributes attributes) {
			return "div".equals(tagName) && "markdown-body".equals(attributes.get("class"));
		}

		@Deprecated
		@Override
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
			throw new IllegalStateException();
		}

		@Override
		public void onEndElement(GroupParser parser, String tagName) {}

		@Override
		public void onText(GroupParser parser, CharSequence text) {}

		@Deprecated
		@Override
		public void onText(GroupParser parser, String source, int start, int end) {
			throw new IllegalStateException();
		}

		@Override
		public void onGroupComplete(GroupParser parser, String text) throws ParseException {
			result = text;
			throw new ParseException();
		}
	}
}
