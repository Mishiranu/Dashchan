package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.UpdaterActivity;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class UpdateFragment extends BaseListFragment {
	private static final String VERSION_TITLE_RELEASE = "Release";

	private static final String EXTRA_UPDATE_DATA_MAP = "updateDataMap";
	private static final String EXTRA_TARGET_PREFIX = "target_";

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private ReadUpdateTask.UpdateDataMap updateDataMap;

	private static final class ListItem {
		public final String extensionName;
		public final String title;
		public final boolean enabled;

		public String target;
		public int targetIndex;
		public String warning;

		public ListItem(String extensionName, boolean enabled) {
			String title = ChanConfiguration.get(extensionName).getTitle();
			if (title == null) {
				title = extensionName;
			}
			this.extensionName = extensionName;
			this.title = title;
			this.enabled = enabled;
		}

		public ListItem(String extensionName, String title, boolean enabled) {
			this.extensionName = extensionName;
			this.title = title;
			this.enabled = enabled;
		}

		public void setTarget(Context context, List<ReadUpdateTask.UpdateItem> updateItems, int targetIndex) {
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
				target = context != null ? context.getString(R.string.keep_current_version) : null;
			}
		}
	}

	public UpdateFragment() {}

	public UpdateFragment(ReadUpdateTask.UpdateDataMap updateDataMap) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_UPDATE_DATA_MAP, updateDataMap);
		setArguments(args);
	}

	private void updateTitle() {
		int count = 0;
		if (updateDataMap != null) {
			for (ListItem listItem : listItems) {
				if (listItem.targetIndex > 0) {
					count++;
				}
			}
		}
		((FragmentHandler) requireActivity()).setTitleSubtitle(ResourceUtils.getColonString(getResources(),
				R.string.updates__genitive, count), null);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		updateTitle();

		getRecyclerView().setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			private final CheckPreference checkBoxViewGetter = new CheckPreference(requireContext(),
					"", false, "title", "summary");

			@Override
			public int getItemCount() {
				return listItems.size();
			}

			@NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				CheckPreference.CheckViewHolder viewHolder = checkBoxViewGetter.createViewHolder(parent);
				viewHolder.view.setTag(viewHolder);
				return new RecyclerView.ViewHolder(viewHolder.view) {{
					ViewUtils.setSelectableItemBackground(itemView);
					itemView.setOnClickListener(v -> onItemClick(getAdapterPosition()));
				}};
			}

			@Override
			public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
				ListItem listItem = listItems.get(position);
				CheckPreference.CheckViewHolder viewHolder = (CheckPreference.CheckViewHolder) holder.itemView.getTag();
				checkBoxViewGetter.setValue(listItem.targetIndex > 0);
				checkBoxViewGetter.setEnabled(listItem.enabled);
				checkBoxViewGetter.bindViewHolder(viewHolder);
				viewHolder.title.setText(listItem.title);
				if (listItem.warning != null) {
					SpannableString spannable = new SpannableString(listItem.target + "\n" + listItem.warning);
					int length = spannable.length();
					spannable.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(getContext(),
							R.attr.colorTextError)), length - listItem.warning.length(), length,
							SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
					viewHolder.summary.setText(spannable);
				} else {
					viewHolder.summary.setText(listItem.target);
				}
			}
		});
		updateDataMap = requireArguments().getParcelable(EXTRA_UPDATE_DATA_MAP);
		buildData(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		for (ListItem listItem : listItems) {
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
		List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName);
		ListItem listItem = new ListItem(extensionName, updateItems.size() >= 2);
		int targetIndex = savedInstanceState != null ? savedInstanceState
				.getInt(EXTRA_TARGET_PREFIX + extensionName, -1) : -1;
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
		String warningUnsupported = context != null ? context.getString(R.string.unsupported_version) : null;
		HashSet<String> handledExtensionNames = new HashSet<>();
		List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(ChanManager.EXTENSION_NAME_CLIENT);
		ListItem listItem = new ListItem(ChanManager.EXTENSION_NAME_CLIENT,
				context != null ? AndroidUtils.getApplicationLabel(context) : null, updateItems.size() >= 2);
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
		for (ChanManager.ExtensionItem extensionItem : manager.getExtensionItems()) {
			if (extensionItem.type == ChanManager.ExtensionItem.Type.LIBRARY) {
				listItem = handleAddListItem(context, updateDataMap, extensionItem.extensionName, savedInstanceState,
						minVersion, maxVersion, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(extensionItem.extensionName);
			}
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
		listItems.clear();
		listItems.addAll(buildData(requireContext(), updateDataMap, savedInstanceState));
		getRecyclerView().getAdapter().notifyDataSetChanged();
		updateTitle();
	}

	private void onItemClick(int position) {
		ListItem listItem = listItems.get(position);
		List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(listItem.extensionName);
		ArrayList<String> targets = new ArrayList<>();
		targets.add(getString(R.string.keep_current_version));
		for (int i = 1; i < updateItems.size(); i++) {
			targets.add(updateItems.get(i).title);
		}
		TargetDialog dialog = new TargetDialog(listItem.extensionName, listItem.title, targets, listItem.targetIndex);
		dialog.show(getChildFragmentManager(), TargetDialog.TAG);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		long length = 0;
		if (updateDataMap != null) {
			for (ListItem listItem : listItems) {
				if (listItem.targetIndex > 0) {
					length += updateDataMap.get(listItem.extensionName).get(listItem.targetIndex).length;
				}
			}
		}
		String downloadTitle = getString(R.string.download_files);
		if (length > 0) {
			downloadTitle += ", " + length / 1024 + " KB";
		}
		menu.add(0, R.id.menu_download, 0, downloadTitle)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionDownload))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_check_on_start, 0, R.string.check_on_startup).setCheckable(true);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_check_on_start).setChecked(Preferences.isCheckUpdatesOnStart());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_download: {
				ArrayList<UpdaterActivity.Request> requests = new ArrayList<>();
				for (ListItem listItem : listItems) {
					ReadUpdateTask.UpdateItem updateItem = updateDataMap.get(listItem.extensionName)
							.get(listItem.targetIndex);
					if (updateItem.source != null) {
						requests.add(new UpdaterActivity.Request(listItem.extensionName, updateItem.name,
								Uri.parse(updateItem.source)));
					}
				}
				if (!requests.isEmpty()) {
					MessageDialog.create(this, getString(R.string.update_reminder__sentence), true);
					UpdaterActivity.startUpdater(requests);
				} else {
					ToastUtils.show(requireContext(), R.string.no_available_updates);
				}
				return true;
			}
			case R.id.menu_check_on_start: {
				Preferences.setCheckUpdatesOnStart(!item.isChecked());
				break;
			}
		}
		return false;
	}

	private void handleListItemValidity(ListItem listItem, int minVersion, int maxVersion, String warningUnsupported) {
		ReadUpdateTask.UpdateItem targetUpdateItem = updateDataMap.get(listItem.extensionName)
				.get(listItem.targetIndex);
		boolean valid = checkVersionValid(targetUpdateItem, minVersion, maxVersion);
		if (valid) {
			listItem.warning = null;
		} else {
			listItem.warning = warningUnsupported;
		}
	}

	private void onTargetChanged(ListItem listItem) {
		String warningUnsupported = getString(R.string.unsupported_version);
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(listItem.extensionName)) {
			ReadUpdateTask.UpdateItem targetAppUpdateItem = updateDataMap.get(listItem.extensionName)
					.get(listItem.targetIndex);
			int minVersion = targetAppUpdateItem.minVersion;
			int maxVersion = targetAppUpdateItem.version;
			for (ListItem invalidateListItem : listItems) {
				handleListItemValidity(invalidateListItem, minVersion, maxVersion, warningUnsupported);
			}
		} else {
			ListItem applicationListItem = listItems.get(0);
			ReadUpdateTask.UpdateItem targetUpdateItem = updateDataMap.get(applicationListItem.extensionName)
					.get(applicationListItem.targetIndex);
			handleListItemValidity(listItem, targetUpdateItem.minVersion, targetUpdateItem.version, warningUnsupported);
		}
		// Called method must invoke notifyDataSetChanged later
	}

	private void onTargetSelected(String extensionName, int targetIndex) {
		for (int i = 0; i < listItems.size(); i++) {
			ListItem listItem = listItems.get(i);
			if (extensionName.equals(listItem.extensionName)) {
				List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName);
				if (listItem.targetIndex != targetIndex) {
					listItem.setTarget(requireContext(), updateItems, targetIndex);
					onTargetChanged(listItem);
					getRecyclerView().getAdapter().notifyDataSetChanged();
					requireActivity().invalidateOptionsMenu();
					updateTitle();
				}
				break;
			}
		}
	}

	public static class TargetDialog extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String TAG = TargetDialog.class.getName();

		private static final String EXTRA_EXTENSION_NAME = "extensionName";
		private static final String EXTRA_TITLE = "title";
		private static final String EXTRA_TARGETS = "targets";
		private static final String EXTRA_INDEX = "index";

		public TargetDialog() {}

		public TargetDialog(String extensionName, String title, ArrayList<String> targets, int index) {
			Bundle args = new Bundle();
			args.putString(EXTRA_EXTENSION_NAME, extensionName);
			args.putString(EXTRA_TITLE, title);
			args.putStringArrayList(EXTRA_TARGETS, targets);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			int index = requireArguments().getInt(EXTRA_INDEX);
			ArrayList<String> targets = requireArguments().getStringArrayList(EXTRA_TARGETS);
			return new AlertDialog.Builder(requireContext())
					.setTitle(requireArguments().getString(EXTRA_TITLE))
					.setSingleChoiceItems(CommonUtils.toArray(targets, String.class), index, this)
					.setNegativeButton(android.R.string.cancel, null).create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			dismiss();
			((UpdateFragment) getParentFragment())
					.onTargetSelected(requireArguments().getString(EXTRA_EXTENSION_NAME), which);
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
}
