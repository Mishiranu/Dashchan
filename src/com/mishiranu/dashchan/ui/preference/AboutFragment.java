package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.util.CommonUtils;
import chan.util.DataFile;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.BackupManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.SharedPreferences;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AboutFragment extends PreferenceFragment implements FragmentHandler.Callback {
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
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new TextFragment(TextFragment.Type.CHANGELOG)));
		addButton(R.string.check_for_updates, 0)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new UpdateFragment()));
		addButton(R.string.foss_licenses, R.string.foss_licenses__summary)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new TextFragment(TextFragment.Type.LICENSES)));
		String versionDate = TextFragment.formatChangelogDate
				(DateFormat.getDateFormat(requireContext()), BuildConfig.VERSION_DATE);
		addButton(getString(R.string.build_version), BuildConfig.VERSION_NAME +
				(versionDate != null ? " " + versionDate : ""));
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
			List<BackupManager.BackupFile> backupFiles = BackupManager.getAvailableBackups(requireContext());
			if (backupFiles != null && !backupFiles.isEmpty()) {
				RestoreListDialog dialog = new RestoreListDialog(backupFiles);
				dialog.show(getChildFragmentManager(), RestoreListDialog.class.getName());
			} else {
				ClickableToast.show(R.string.backups_not_found);
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

	public static class RestoreListDialog extends DialogFragment {
		private static final String EXTRA_FILES = "files";
		private static final String EXTRA_NAMES = "names";

		public RestoreListDialog() {}

		public RestoreListDialog(List<BackupManager.BackupFile> backupFiles) {
			Bundle args = new Bundle();
			ArrayList<String> files = new ArrayList<>(backupFiles.size());
			ArrayList<String> names = new ArrayList<>(backupFiles.size());
			for (BackupManager.BackupFile backupFile : backupFiles) {
				files.add(backupFile.file.getRelativePath());
				names.add(backupFile.name);
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
					.setTitle(R.string.restore_data)
					.setItems(items, (d, which) -> {
						String path = requireArguments().getStringArrayList(EXTRA_FILES).get(which);
						DataFile file = DataFile.obtain(requireContext(), DataFile.Target.DOWNLOADS, path);
						List<BackupManager.Entry> entries = BackupManager.readBackupEntries(file);
						if (entries.isEmpty()) {
							ClickableToast.show(R.string.invalid_data_format);
						} else {
							RestoreEntriesDialog dialog = new RestoreEntriesDialog(file, entries);
							dialog.show(getParentFragmentManager(), RestoreEntriesDialog.class.getName());
						}
					})
					.setNegativeButton(android.R.string.cancel, null)
					.create();
		}
	}

	public static class RestoreEntriesDialog extends DialogFragment {
		private static final String EXTRA_FILE = "file";
		private static final String EXTRA_ENTRIES = "entries";
		private static final String EXTRA_CHECKED = "checked";

		public RestoreEntriesDialog() {}

		public RestoreEntriesDialog(DataFile file, List<BackupManager.Entry> entries) {
			Bundle args = new Bundle();
			ArrayList<String> entryNames = new ArrayList<>();
			for (BackupManager.Entry entry : entries) {
				entryNames.add(entry.name());
			}
			args.putString(EXTRA_FILE, file.getRelativePath());
			args.putStringArrayList(EXTRA_ENTRIES, entryNames);
			setArguments(args);
		}

		private boolean[] checkedItems;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			ArrayList<String> entryNames = requireArguments().getStringArrayList(EXTRA_ENTRIES);
			String[] items = new String[entryNames.size()];
			for (int i = 0; i < items.length; i++) {
				items[i] = getString(BackupManager.Entry.valueOf(entryNames.get(i)).titleResId);
			}
			checkedItems = new boolean[items.length];
			ArrayList<String> checked = savedInstanceState != null
					? savedInstanceState.getStringArrayList(EXTRA_CHECKED) : null;
			for (int i = 0; i < checkedItems.length; i++) {
				checkedItems[i] = checked == null || checked.contains(entryNames.get(i));
			}
			return new AlertDialog.Builder(requireContext())
					.setTitle(R.string.restore_data)
					.setMultiChoiceItems(items, checkedItems,
							(d, which, isChecked) -> checkedItems[which] = isChecked)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, (d, w) -> loadBackup())
					.create();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);

			ArrayList<String> entryNames = requireArguments().getStringArrayList(EXTRA_ENTRIES);
			ArrayList<String> checked = new ArrayList<>();
			for (int i = 0; i < checkedItems.length; i++) {
				if (checkedItems[i]) {
					checked.add(entryNames.get(i));
				}
			}
			outState.putStringArrayList(EXTRA_CHECKED, checked);
		}

		private void loadBackup() {
			ArrayList<String> entryNames = requireArguments().getStringArrayList(EXTRA_ENTRIES);
			HashSet<BackupManager.Entry> checked = new HashSet<>();
			for (int i = 0; i < checkedItems.length; i++) {
				if (checkedItems[i]) {
					checked.add(BackupManager.Entry.valueOf(entryNames.get(i)));
				}
			}
			if (!checked.isEmpty()) {
				String path = requireArguments().getString(EXTRA_FILE);
				DataFile file = DataFile.obtain(requireContext(), DataFile.Target.DOWNLOADS, path);
				if (BackupManager.loadBackup(file, checked)) {
					NavigationUtils.restartApplication(requireContext());
				} else {
					ClickableToast.show(R.string.unknown_error);
				}
			}
		}
	}
}
