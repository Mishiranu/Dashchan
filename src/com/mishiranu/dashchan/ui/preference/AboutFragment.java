package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
import com.mishiranu.dashchan.widget.ClickableToast;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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
			LinkedHashMap<DataFile, String> filesMap = BackupManager.getAvailableBackups(requireContext());
			if (filesMap != null && filesMap.size() > 0) {
				RestoreDialog dialog = new RestoreDialog(filesMap);
				dialog.show(getChildFragmentManager(), RestoreDialog.class.getName());
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

	public static class RestoreDialog extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String EXTRA_FILES = "files";
		private static final String EXTRA_NAMES = "names";

		public RestoreDialog() {}

		public RestoreDialog(LinkedHashMap<DataFile, String> filesMap) {
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
}
