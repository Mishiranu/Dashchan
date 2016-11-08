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
import java.util.ArrayList;
import java.util.HashSet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.preference.UpdaterActivity;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class UpdateFragment extends BaseListFragment {
	private static final String VERSION_TITLE_RELEASE = "Release";

	private static final String EXTRA_UPDATE_DATA_MAP = "update_data_map";
	private static final String EXTRA_TARGET_PREFIX = "target_";

	private ArrayAdapter<ListItem> mAdapter;
	private ReadUpdateTask.UpdateDataMap mUpdateDataMap;

	private static final class ListItem {
		public final String extensionName;
		public final String title;

		public String target;
		public int targetIndex;
		public String warning;

		public ListItem(String extensionName) {
			String title = ChanConfiguration.get(extensionName).getTitle();
			if (title == null) {
				title = extensionName;
			}
			this.extensionName = extensionName;
			this.title = title;
		}

		public ListItem(String extensionName, String title) {
			this.extensionName = extensionName;
			this.title = title;
		}

		public void setTarget(Context context, ArrayList<ReadUpdateTask.UpdateItem> updateItems, int targetIndex) {
			ReadUpdateTask.UpdateItem updateItem = updateItems.get(targetIndex);
			this.targetIndex = targetIndex;
			if (targetIndex > 0) {
				StringBuilder target = new StringBuilder(updateItem.title);
				target.append(", ").append(updateItem.name);
				if (updateItem.length > 0) {
					target.append(", ").append(updateItem.length / 1024).append(" KB");
				}
				this.target = target.toString();
			} else {
				target = context != null ? context.getString(R.string.text_without_updating) : null;
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		updateTitle();
	}

	private void updateTitle() {
		int count = 0;
		if (mUpdateDataMap != null) {
			for (int i = 0; i < mAdapter.getCount(); i++) {
				ListItem listItem = mAdapter.getItem(i);
				if (listItem.targetIndex > 0) {
					count++;
				}
			}
		}
		getActivity().setTitle(getString(R.string.text_updates_format, count));
		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
		mAdapter = new ArrayAdapter<ListItem>(getActivity(), 0) {
			private final CheckBoxPreference mCheckBoxViewGetter = new CheckBoxPreference(getContext());

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				ListItem listItem = getItem(position);
				mCheckBoxViewGetter.setTitle(listItem.title);
				if (listItem.warning != null) {
					SpannableString spannable = new SpannableString(listItem.target + "\n" + listItem.warning);
					int length = spannable.length();
					spannable.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(getContext(),
							R.attr.colorTextError)), length - listItem.warning.length(), length,
							SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
					mCheckBoxViewGetter.setSummary(spannable);
				} else {
					mCheckBoxViewGetter.setSummary(listItem.target);
				}
				mCheckBoxViewGetter.setChecked(listItem.targetIndex > 0);
				return mCheckBoxViewGetter.getView(convertView, parent);
			}
		};
		setListAdapter(mAdapter);
		mUpdateDataMap = (ReadUpdateTask.UpdateDataMap) getArguments().getSerializable(EXTRA_UPDATE_DATA_MAP);
		buildData(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		for (int i = 0; i < mAdapter.getCount(); i++) {
			ListItem listItem = mAdapter.getItem(i);
			outState.putInt(EXTRA_TARGET_PREFIX + listItem.extensionName, listItem.targetIndex);
		}
	}

	private static boolean checkVersionValid(ReadUpdateTask.UpdateItem updateItem, int minVersion, int maxVersion) {
		return updateItem.ignoreVersion || updateItem.version >= minVersion && updateItem.version <= maxVersion;
	}

	private static boolean compareForUpdates(ReadUpdateTask.UpdateItem installedUpdateItem,
			ReadUpdateTask.UpdateItem newUpdateItem) {
		return newUpdateItem.code > installedUpdateItem.code || !StringUtils.equals(newUpdateItem.name,
				installedUpdateItem.name);
	}

	private static ListItem handleAddListItem(Context context, ReadUpdateTask.UpdateDataMap updateDataMap,
			String extensionName, Bundle savedInstanceState, int minVersion, int maxVersion,
			String warningUnsupported) {
		ArrayList<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName);
		ListItem listItem = new ListItem(extensionName);
		int targetIndex = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_TARGET_PREFIX
				+ extensionName, -1) : -1;
		if (targetIndex == -1) {
			ReadUpdateTask.UpdateItem installedExtensionData = updateItems.get(0);
			if (checkVersionValid(installedExtensionData, minVersion, maxVersion)) {
				targetIndex = 0;
			}
			for (int i = 1; i < updateItems.size(); i++) {
				ReadUpdateTask.UpdateItem newUpdateItem = updateItems.get(i);
				if (checkVersionValid(newUpdateItem, minVersion, maxVersion)) {
					// targetIndex == -1 - means installed version is not supported
					if (targetIndex == -1 || VERSION_TITLE_RELEASE.equals(newUpdateItem.title)
							&& compareForUpdates(installedExtensionData, newUpdateItem)) {
						targetIndex = i;
						break;
					}
				}
			}
			if (targetIndex == -1) {
				targetIndex = 0;
				listItem.warning = warningUnsupported;
			}
		} else {
			// Restore state
			if (!checkVersionValid(updateItems.get(targetIndex), minVersion, maxVersion)) {
				listItem.warning = warningUnsupported;
			}
		}
		listItem.setTarget(context, updateItems, targetIndex);
		return listItem;
	}

	private static ArrayList<ListItem> buildData(Context context, ReadUpdateTask.UpdateDataMap updateDataMap,
			Bundle savedInstanceState) {
		ArrayList<ListItem> listItems = new ArrayList<>();
		String warningUnsupported = context != null ? context.getString(R.string.text_unsupported_version) : null;
		HashSet<String> handledExtensionNames = new HashSet<>();
		ArrayList<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(ChanManager.EXTENSION_NAME_CLIENT);
		ListItem listItem = new ListItem(ChanManager.EXTENSION_NAME_CLIENT, context != null
				? context.getString(R.string.const_app_name) : null);
		int targetIndex = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_TARGET_PREFIX
				+ listItem.extensionName, -1) : -1;
		if (targetIndex == -1) {
			targetIndex = 0;
			for (int i = 1; i < updateItems.size(); i++) {
				ReadUpdateTask.UpdateItem newUpdateItem = updateItems.get(i);
				if (VERSION_TITLE_RELEASE.equals(newUpdateItem.title) &&
						compareForUpdates(updateItems.get(0), newUpdateItem)) {
					targetIndex = 1;
					break;
				}
			}
		}
		listItem.setTarget(context, updateItems, targetIndex);
		ReadUpdateTask.UpdateItem currentAppUpdateItem = updateItems.get(targetIndex);
		int minVersion = currentAppUpdateItem.minVersion;
		int maxVersion = currentAppUpdateItem.version;
		listItems.add(listItem);
		handledExtensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
		ChanManager manager = ChanManager.getInstance();
		for (String chanName : manager.getAvailableChanNames()) {
			listItem = handleAddListItem(context, updateDataMap, chanName, savedInstanceState,
					minVersion, maxVersion, warningUnsupported);
			listItems.add(listItem);
			handledExtensionNames.add(chanName);
		}
		for (ChanManager.ExtensionItem libItem : manager.getLibItems()) {
			listItem = handleAddListItem(context, updateDataMap, libItem.extensionName, savedInstanceState,
					minVersion, maxVersion, warningUnsupported);
			listItems.add(listItem);
			handledExtensionNames.add(libItem.extensionName);
		}
		for (String extensionName : updateDataMap.extensionNames()) {
			if (!handledExtensionNames.contains(extensionName)) {
				listItem = handleAddListItem(context, updateDataMap, extensionName, savedInstanceState,
						minVersion, maxVersion, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(extensionName);
			}
		}
		return listItems;
	}

	private void buildData(Bundle savedInstanceState) {
		mAdapter.clear();
		mAdapter.addAll(buildData(getActivity(), mUpdateDataMap, savedInstanceState));
		updateTitle();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		ListItem listItem = mAdapter.getItem(position);
		ArrayList<ReadUpdateTask.UpdateItem> updateItems = mUpdateDataMap.get(listItem.extensionName);
		ArrayList<String> targets = new ArrayList<>();
		targets.add(getString(R.string.text_without_updating));
		for (int i = 1; i < updateItems.size(); i++) {
			targets.add(updateItems.get(i).title);
		}
		TargetDialog dialog = new TargetDialog(listItem.extensionName, targets, listItem.targetIndex);
		dialog.setTargetFragment(this, 0);
		dialog.show(getFragmentManager(), TargetDialog.TAG);
	}

	private static final int OPTIONS_MENU_DOWNLOAD = 0;
	private static final int OPTIONS_CHECK_ON_START = 1;

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		ActionIconSet set = new ActionIconSet(getActivity());
		long length = 0;
		if (mUpdateDataMap != null) {
			for (int i = 0; i < mAdapter.getCount(); i++) {
				ListItem listItem = mAdapter.getItem(i);
				if (listItem.targetIndex > 0) {
					length += mUpdateDataMap.get(listItem.extensionName).get(listItem.targetIndex).length;
				}
			}
		}
		String downloadTitle = getString(R.string.action_download_files);
		if (length > 0) {
			downloadTitle += ", " + length / 1024 + " KB";
		}
		menu.add(0, OPTIONS_MENU_DOWNLOAD, 0, downloadTitle).setIcon(set.getId(R.attr.actionDownload))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_CHECK_ON_START, 0, R.string.action_check_on_startup).setCheckable(true);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(OPTIONS_CHECK_ON_START).setChecked(Preferences.isCheckUpdatesOnStart());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_DOWNLOAD: {
				DownloadManager downloadManager = (DownloadManager) getActivity()
						.getSystemService(Context.DOWNLOAD_SERVICE);
				File directory = ReadUpdateTask.getDownloadDirectory(getActivity());
				boolean started = false;
				boolean downloadManagerError = false;
				long clientId = -1;
				ArrayList<Long> ids = new ArrayList<>();
				for (int i = 0; i < mAdapter.getCount(); i++) {
					ListItem listItem = mAdapter.getItem(i);
					ReadUpdateTask.UpdateItem updateItem = mUpdateDataMap.get(listItem.extensionName)
							.get(listItem.targetIndex);
					if (updateItem.source != null) {
						String name = listItem.extensionName + "_" + updateItem.name;
						String extension = ".apk";
						File file;
						for (int j = 0; true; j++) {
							file = new File(directory, name + (j == 0 ? "" : "_" + j) + extension);
							if (!file.exists()) {
								break;
							}
						}
						DownloadManager.Request request = new DownloadManager.Request(Uri.parse(updateItem.source));
						request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
						request.setDestinationUri(Uri.fromFile(file));
						request.setTitle(file.getName());
						request.setDescription(i == 0 ? getString(R.string.text_main_application)
								: getString(R.string.text_extension_name_format, listItem.title));
						request.setMimeType("application/vnd.android.package-archive");
						long id;
						try {
							id = downloadManager.enqueue(request);
						} catch (IllegalArgumentException e) {
							String message = e.getMessage();
							if (message.equals("Unknown URL content://downloads/my_downloads")) {
								downloadManagerError = true;
								break;
							}
							throw e;
						}
						started = true;
						if (ChanManager.EXTENSION_NAME_CLIENT.equals(listItem.extensionName)) {
							clientId = id;
						} else {
							ids.add(id);
						}
					}
				}
				if (started) {
					MessageDialog.create(MessageDialog.TYPE_UPDATE_REMINDER, this, false);
					UpdaterActivity.initUpdater(clientId, ids);
				} else if (downloadManagerError) {
					ToastUtils.show(getActivity(), R.string.message_download_manager_error);
				} else {
					ToastUtils.show(getActivity(), R.string.message_no_available_updates);
				}
				return true;
			}
			case OPTIONS_CHECK_ON_START: {
				Preferences.setCheckUpdatesOnStart(!item.isChecked());
				break;
			}
		}
		return false;
	}

	private void handleListItemValidity(ListItem listItem, int minVersion, int maxVersion, String warningUnsupported) {
		ReadUpdateTask.UpdateItem targetUpdateItem = mUpdateDataMap.get(listItem.extensionName)
				.get(listItem.targetIndex);
		boolean valid = checkVersionValid(targetUpdateItem, minVersion, maxVersion);
		if (valid) {
			listItem.warning = null;
		} else {
			listItem.warning = warningUnsupported;
		}
	}

	private void onTargetChanged(ListItem listItem) {
		String warningUnsupported = getString(R.string.text_unsupported_version);
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(listItem.extensionName)) {
			ReadUpdateTask.UpdateItem targetAppUpdateItem = mUpdateDataMap.get(listItem.extensionName)
					.get(listItem.targetIndex);
			int minVersion = targetAppUpdateItem.minVersion;
			int maxVersion = targetAppUpdateItem.version;
			for (int i = 1; i < mAdapter.getCount(); i++) {
				handleListItemValidity(mAdapter.getItem(i), minVersion, maxVersion, warningUnsupported);
			}
		} else {
			ListItem appItem = mAdapter.getItem(0);
			ReadUpdateTask.UpdateItem targetUpdateItem = mUpdateDataMap.get(appItem.extensionName)
					.get(appItem.targetIndex);
			handleListItemValidity(listItem, targetUpdateItem.minVersion, targetUpdateItem.version, warningUnsupported);
		}
		// Called method must invoke notifyDataSetChanged later
	}

	private void onTargetSelected(String extensionName, int targetIndex) {
		for (int i = 0; i < mAdapter.getCount(); i++) {
			ListItem listItem = mAdapter.getItem(i);
			if (extensionName.equals(listItem.extensionName)) {
				ArrayList<ReadUpdateTask.UpdateItem> updateItems = mUpdateDataMap.get(extensionName);
				if (listItem.targetIndex != targetIndex) {
					listItem.setTarget(getActivity(), updateItems, targetIndex);
					onTargetChanged(listItem);
					mAdapter.notifyDataSetChanged();
					updateTitle();
				}
				break;
			}
		}
	}

	public static class TargetDialog extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String TAG = TargetDialog.class.getName();

		private static final String EXTRA_EXTENSION_NAME = "extensionName";
		private static final String EXTRA_TARGETS = "targets";
		private static final String EXTRA_INDEX = "index";

		public TargetDialog() {}

		public TargetDialog(String extensionName, ArrayList<String> targets, int index) {
			Bundle args = new Bundle();
			args.putString(EXTRA_EXTENSION_NAME, extensionName);
			args.putStringArrayList(EXTRA_TARGETS, targets);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			int index = getArguments().getInt(EXTRA_INDEX);
			ArrayList<String> targets = getArguments().getStringArrayList(EXTRA_TARGETS);
			return new AlertDialog.Builder(getActivity()).setSingleChoiceItems(CommonUtils.toArray(targets,
					String.class), index, this).setNegativeButton(android.R.string.cancel, null).create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			dismiss();
			((UpdateFragment) getTargetFragment()).onTargetSelected(getArguments()
					.getString(EXTRA_EXTENSION_NAME), which);
		}
	}

	public static int checkNewVersions(ReadUpdateTask.UpdateDataMap updateDataMap) {
		int count = 0;
		ArrayList<ListItem> listItems = buildData(null, updateDataMap, null);
		for (ListItem listItem : listItems) {
			if (listItem.targetIndex > 0) {
				count++;
			}
		}
		return count;
	}

	public static Intent createUpdateIntent(Context context, ReadUpdateTask.UpdateDataMap updateDataMap) {
		Bundle args = new Bundle();
		args.putSerializable(EXTRA_UPDATE_DATA_MAP, updateDataMap);
		Intent intent = new Intent(context, PreferencesActivity.class);
		intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, UpdateFragment.class.getName());
		intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
		intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
		return intent;
	}
}