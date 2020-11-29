package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.UpdaterActivity;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class UpdateFragment extends BaseListFragment {
	private static final String VERSION_TITLE_RELEASE = "Release";

	private static final String EXTRA_UPDATE_DATA_MAP = "updateDataMap";
	private static final String EXTRA_UPDATE_ERROR_ITEM = "updateErrorItem";
	private static final String EXTRA_TARGET_PREFIX = "target_";

	private ReadUpdateTask.UpdateDataMap updateDataMap;
	private ErrorItem updateErrorItem;

	private View progressView;

	private static final class ListItem {
		public final String extensionName;
		public final String title;
		public final boolean enabled;
		public final boolean installed;

		public String target;
		public int targetIndex;
		public String warning;

		public static ListItem create(String extensionName, String extensionTitle, boolean enabled, boolean installed) {
			String title = Chan.get(extensionName).configuration.getTitle();
			if (title == null) {
				title = extensionTitle;
			}
			return new ListItem(extensionName, title, enabled, installed);
		}

		public ListItem(String extensionName, String title, boolean enabled, boolean installed) {
			this.extensionName = extensionName;
			this.title = title;
			this.enabled = enabled;
			this.installed = installed;
		}

		public boolean isHeader() {
			return StringUtils.isEmpty(extensionName);
		}

		public boolean willBeInstalled() {
			return !isHeader() && (installed && targetIndex > 0 || !installed && targetIndex >= 0);
		}

		public void setTarget(Context context, ReadUpdateTask.ApplicationItem applicationItem, int targetIndex) {
			this.targetIndex = targetIndex;
			if (installed && targetIndex > 0 || !installed && targetIndex >= 0) {
				ReadUpdateTask.PackageItem packageItem = applicationItem.packageItems.get(targetIndex);
				String target = packageItem.title;
				if (context != null) {
					target = context.getString(R.string.__enumeration_format, target, packageItem.versionName);
					if (packageItem.length > 0) {
						target = context.getString(R.string.__enumeration_format, target,
								StringUtils.formatFileSize(packageItem.length, false));
					}
				}
				this.target = target;
			} else if (targetIndex == 0) {
				target = context != null ? context.getString(R.string.keep_current_version) : null;
			} else {
				target = context != null ? context.getString(R.string.dont_install) : null;
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
			Adapter adapter = (Adapter) getRecyclerView().getAdapter();
			for (ListItem listItem : adapter.listItems) {
				if (listItem.willBeInstalled()) {
					count++;
				}
			}
		}
		((FragmentHandler) requireActivity()).setTitleSubtitle(count <= 0 ? getString(R.string.updates__genitive)
				: ResourceUtils.getColonString(getResources(), R.string.updates__genitive, count), null);
	}

	private boolean isUpdateDataProvided() {
		Bundle args = getArguments();
		return args != null && args.containsKey(EXTRA_UPDATE_DATA_MAP);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ExpandedLayout layout = (ExpandedLayout) super.onCreateView(inflater, container, savedInstanceState);
		FrameLayout progress = new FrameLayout(layout.getContext());
		layout.addView(progress, ExpandedLayout.LayoutParams.MATCH_PARENT, ExpandedLayout.LayoutParams.MATCH_PARENT);
		progress.setVisibility(View.GONE);
		progressView = progress;
		ProgressBar progressBar = new ProgressBar(progress.getContext());
		progress.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		((FrameLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER;
		ThemeEngine.applyStyle(progressBar);
		return layout;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		if (isUpdateDataProvided()) {
			updateDataMap = requireArguments().getParcelable(EXTRA_UPDATE_DATA_MAP);
		} else {
			updateDataMap = savedInstanceState != null
					? savedInstanceState.getParcelable(EXTRA_UPDATE_DATA_MAP) : null;
			updateErrorItem = savedInstanceState != null
					? savedInstanceState.getParcelable(EXTRA_UPDATE_ERROR_ITEM) : null;
			if (updateErrorItem != null) {
				setErrorText(updateErrorItem.toString());
			} else if (updateDataMap == null) {
				progressView.setVisibility(View.VISIBLE);
				UpdateViewModel viewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
				if (!viewModel.hasTaskOrValue()) {
					ReadUpdateTask task = new ReadUpdateTask(requireContext(), viewModel.callback);
					task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
					viewModel.attach(task);
				}
				viewModel.observe(getViewLifecycleOwner(), (updateDataMap, errorItem) -> {
					progressView.setVisibility(View.GONE);
					if (updateDataMap != null) {
						this.updateDataMap = updateDataMap;
						Adapter adapter = (Adapter) getRecyclerView().getAdapter();
						adapter.listItems = buildData(requireContext(), updateDataMap, null);
						adapter.notifyDataSetChanged();
						updateTitle();
					} else {
						updateErrorItem = errorItem;
						setErrorText(errorItem.toString());
					}
				});
			}
		}
		Adapter adapter = new Adapter(getRecyclerView().getContext(), this::onItemClick);
		getRecyclerView().setAdapter(adapter);
		if (updateDataMap != null) {
			adapter.listItems = buildData(requireContext(), updateDataMap, savedInstanceState);
		}
		updateTitle();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		RecyclerView recyclerView = getRecyclerView();
		if (recyclerView != null) {
			Adapter adapter = (Adapter) recyclerView.getAdapter();
			for (ListItem listItem : adapter.listItems) {
				outState.putInt(EXTRA_TARGET_PREFIX + listItem.extensionName, listItem.targetIndex);
			}
		}
		if (!isUpdateDataProvided()) {
			outState.putParcelable(EXTRA_UPDATE_DATA_MAP, updateDataMap);
			outState.putParcelable(EXTRA_UPDATE_ERROR_ITEM, updateErrorItem);
		}
	}

	@Override
	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return ((Adapter) getRecyclerView().getAdapter()).configureDivider(configuration, position);
	}

	private static boolean checkVersionValid(ReadUpdateTask.ApplicationItem applicationItem,
			ReadUpdateTask.PackageItem packageItem, int minApiVersion, int maxApiVersion) {
		return applicationItem.type != ReadUpdateTask.ApplicationItem.Type.CHAN ||
				packageItem.apiVersion >= minApiVersion && packageItem.apiVersion <= maxApiVersion;
	}

	private static boolean compareForUpdates(ReadUpdateTask.PackageItem installed, ReadUpdateTask.PackageItem update) {
		return update.versionCode > installed.versionCode ||
				!CommonUtils.equals(installed.versionName, update.versionName);
	}

	private static ListItem handleAddListItem(Context context, ReadUpdateTask.ApplicationItem applicationItem,
			Bundle savedInstanceState, int minApiVersion, int maxApiVersion,
			boolean installed, String warningUnsupported) {
		ListItem listItem = ListItem.create(applicationItem.name, applicationItem.title,
				applicationItem.packageItems.size() >= (installed ? 2 : 1), installed);
		int targetIndex = savedInstanceState != null ? savedInstanceState
				.getInt(EXTRA_TARGET_PREFIX + applicationItem.name, -1) : -1;
		if (targetIndex < 0) {
			if (installed) {
				ReadUpdateTask.PackageItem installedExtensionData = applicationItem.packageItems.get(0);
				if (checkVersionValid(applicationItem, installedExtensionData, minApiVersion, maxApiVersion)) {
					targetIndex = 0;
				}
				for (int i = 1; i < applicationItem.packageItems.size(); i++) {
					ReadUpdateTask.PackageItem updatePackageItem = applicationItem.packageItems.get(i);
					if (checkVersionValid(applicationItem, updatePackageItem, minApiVersion, maxApiVersion)) {
						// targetIndex < 0 - means installed version is not supported
						if (targetIndex < 0 || VERSION_TITLE_RELEASE.equals(updatePackageItem.title)
								&& compareForUpdates(installedExtensionData, updatePackageItem)) {
							targetIndex = i;
							break;
						}
					}
				}
				if (targetIndex < 0) {
					targetIndex = 0;
					listItem.warning = warningUnsupported;
				}
			}
		} else {
			// Restore state
			ReadUpdateTask.PackageItem packageItem = applicationItem.packageItems.get(targetIndex);
			if (!checkVersionValid(applicationItem, packageItem, minApiVersion, maxApiVersion)) {
				listItem.warning = warningUnsupported;
			}
		}
		listItem.setTarget(context, applicationItem, targetIndex);
		return listItem;
	}

	@SuppressWarnings("ComparatorCombinators")
	private static final Comparator<ReadUpdateTask.ApplicationItem> UPDATE_DATA_COMPARATOR = (lhs, rhs) -> {
		int result = lhs.type.compareTo(rhs.type);
		if (result != 0) {
			return result;
		}
		result = lhs.title.compareTo(rhs.title);
		if (result != 0) {
			return result;
		}
		return lhs.name.compareTo(rhs.name);
	};

	private static ArrayList<ReadUpdateTask.ApplicationItem> collectSorted
			(ReadUpdateTask.UpdateDataMap updateDataMap, boolean installed) {
		ArrayList<ReadUpdateTask.ApplicationItem> applicationItems = new ArrayList<>();
		for (String extensionName : updateDataMap.extensionNames(installed)) {
			applicationItems.add(updateDataMap.get(extensionName, installed));
		}
		Collections.sort(applicationItems, UPDATE_DATA_COMPARATOR);
		return applicationItems;
	}

	private static List<ListItem> buildData(Context context,
			ReadUpdateTask.UpdateDataMap updateDataMap, Bundle savedInstanceState) {
		ArrayList<ListItem> listItems = new ArrayList<>();
		String warningUnsupported = context != null ? context.getString(R.string.unsupported_version) : null;
		HashSet<String> handledExtensionNames = new HashSet<>();
		int minApiVersion;
		int maxApiVersion;
		{
			ReadUpdateTask.ApplicationItem applicationItem = updateDataMap.get(ChanManager.EXTENSION_NAME_CLIENT, true);
			ListItem listItem = new ListItem(ChanManager.EXTENSION_NAME_CLIENT,
					context != null ? AndroidUtils.getApplicationLabel(context) : null,
					applicationItem.packageItems.size() >= 2, true);
			int targetIndex = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_TARGET_PREFIX
					+ listItem.extensionName, -1) : -1;
			if (targetIndex < 0) {
				targetIndex = 0;
				for (int i = 1; i < applicationItem.packageItems.size(); i++) {
					ReadUpdateTask.PackageItem updatePackageItem = applicationItem.packageItems.get(i);
					if (VERSION_TITLE_RELEASE.equals(updatePackageItem.title) &&
							compareForUpdates(applicationItem.packageItems.get(0), updatePackageItem)) {
						targetIndex = 1;
						break;
					}
				}
			}
			listItem.setTarget(context, applicationItem, targetIndex);
			ReadUpdateTask.PackageItem currentApplicationPackageItem = applicationItem.packageItems.get(targetIndex);
			minApiVersion = currentApplicationPackageItem.minApiVersion;
			maxApiVersion = currentApplicationPackageItem.maxApiVersion;
			listItems.add(listItem);
		}
		handledExtensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
		ChanManager manager = ChanManager.getInstance();
		for (ChanManager.ExtensionItem extensionItem : manager.getExtensionItems()) {
			if (extensionItem.type == ChanManager.ExtensionItem.Type.LIBRARY) {
				ReadUpdateTask.ApplicationItem applicationItem = updateDataMap.get(extensionItem.name, true);
				if (applicationItem != null) {
					ListItem listItem = handleAddListItem(context, applicationItem,
							savedInstanceState, minApiVersion, maxApiVersion, true, warningUnsupported);
					listItems.add(listItem);
					handledExtensionNames.add(extensionItem.name);
				}
			}
		}
		for (Chan chan : manager.getAvailableChans()) {
			ReadUpdateTask.ApplicationItem applicationItem = updateDataMap.get(chan.name, true);
			if (applicationItem != null) {
				ListItem listItem = handleAddListItem(context, applicationItem,
						savedInstanceState, minApiVersion, maxApiVersion, true, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(chan.name);
			}
		}
		for (ReadUpdateTask.ApplicationItem applicationItem : collectSorted(updateDataMap, true)) {
			if (!handledExtensionNames.contains(applicationItem.name)) {
				ListItem listItem = handleAddListItem(context, applicationItem, savedInstanceState,
						minApiVersion, maxApiVersion, true, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(applicationItem.name);
			}
		}
		boolean availableHeaderAdded = false;
		for (ReadUpdateTask.ApplicationItem applicationItem : collectSorted(updateDataMap, false)) {
			if (!handledExtensionNames.contains(applicationItem.name)) {
				if (!availableHeaderAdded) {
					if (context != null) {
						listItems.add(new ListItem("", context.getString(R.string.available__plural), false, false));
					}
					availableHeaderAdded = true;
				}
				ListItem listItem = handleAddListItem(context, applicationItem, savedInstanceState,
						minApiVersion, maxApiVersion, false, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(applicationItem.name);
			}
		}
		return listItems;
	}

	private void onItemClick(ListItem listItem) {
		ArrayList<String> targets = new ArrayList<>();
		ArrayList<String> repositories = new ArrayList<>();
		int targetIndex;
		ReadUpdateTask.ApplicationItem applicationItem = updateDataMap.get(listItem.extensionName, listItem.installed);
		if (listItem.installed) {
			targets.add(getString(R.string.keep_current_version));
			repositories.add(null);
			for (ReadUpdateTask.PackageItem packageItem : applicationItem.packageItems
					.subList(1, applicationItem.packageItems.size())) {
				targets.add(packageItem.title);
				repositories.add(packageItem.repository);
			}
			targetIndex = listItem.targetIndex;
		} else {
			targets.add(getString(R.string.dont_install));
			repositories.add(null);
			for (ReadUpdateTask.PackageItem packageItem : applicationItem.packageItems) {
				targets.add(packageItem.title);
				repositories.add(packageItem.repository);
			}
			targetIndex = listItem.targetIndex + 1;
		}
		TargetDialog dialog = new TargetDialog(listItem.extensionName, listItem.title,
				targets, repositories, targetIndex);
		dialog.show(getChildFragmentManager(), TargetDialog.TAG);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_download, 0, R.string.download_files)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionDownload))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_check_on_start, 0, R.string.check_on_startup).setCheckable(true);
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		long length = 0;
		RecyclerView recyclerView = getRecyclerView();
		if (updateDataMap != null && recyclerView != null) {
			Adapter adapter = (Adapter) recyclerView.getAdapter();
			for (ListItem listItem : adapter.listItems) {
				if (listItem.willBeInstalled()) {
					length += updateDataMap.get(listItem.extensionName, listItem.installed)
							.packageItems.get(listItem.targetIndex).length;
				}
			}
		}
		String downloadTitle = getString(R.string.download_files);
		if (length > 0) {
			downloadTitle = getString(R.string.__enumeration_format, downloadTitle,
					StringUtils.formatFileSize(length, false));
		}
		menu.findItem(R.id.menu_download).setTitle(downloadTitle);
		menu.findItem(R.id.menu_check_on_start).setChecked(Preferences.isCheckUpdatesOnStart());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_download: {
				ArrayList<UpdaterActivity.Request> requests = new ArrayList<>();
				if (updateDataMap != null) {
					Adapter adapter = (Adapter) getRecyclerView().getAdapter();
					for (ListItem listItem : adapter.listItems) {
						if (listItem.willBeInstalled()) {
							ReadUpdateTask.PackageItem packageItem = updateDataMap
									.get(listItem.extensionName, listItem.installed)
									.packageItems.get(listItem.targetIndex);
							if (packageItem.source != null) {
								requests.add(new UpdaterActivity.Request(listItem.extensionName,
										packageItem.versionName, packageItem.source, packageItem.sha256sum));
							}
						}
					}
				}
				if (!requests.isEmpty()) {
					displayUpdateReminderDialog(getChildFragmentManager());
					UpdaterActivity.startUpdater(requests);
				} else {
					ClickableToast.show(R.string.no_available_updates);
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

	private static void displayUpdateReminderDialog(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, null, provider -> new AlertDialog
				.Builder(provider.getContext())
				.setMessage(R.string.update_reminder__sentence)
				.setPositiveButton(android.R.string.ok, (d, w) -> ((FragmentHandler) provider
						.getActivity()).removeFragment())
				.setOnCancelListener(d -> ((FragmentHandler) provider.getActivity()).removeFragment())
				.create());
	}

	private static void handleListItemValidity(ReadUpdateTask.UpdateDataMap updateDataMap,
			ListItem listItem, int minApiVersion, int maxApiVersion, String warningUnsupported) {
		boolean valid = true;
		if (listItem.targetIndex >= 0) {
			ReadUpdateTask.ApplicationItem applicationItem = updateDataMap
					.get(listItem.extensionName, listItem.installed);
			ReadUpdateTask.PackageItem packageItem = applicationItem.packageItems.get(listItem.targetIndex);
			valid = checkVersionValid(applicationItem, packageItem, minApiVersion, maxApiVersion);
		}
		listItem.warning = valid ? null : warningUnsupported;
	}

	private static void onTargetChanged(Context context, Adapter adapter,
			ReadUpdateTask.UpdateDataMap updateDataMap, ListItem listItem) {
		String warningUnsupported = context.getString(R.string.unsupported_version);
		ListItem applicationListItem = adapter.listItems.get(0);
		if (!ChanManager.EXTENSION_NAME_CLIENT.equals(applicationListItem.extensionName)) {
			throw new IllegalStateException();
		}
		ReadUpdateTask.PackageItem applicationPackageItem = updateDataMap
				.get(ChanManager.EXTENSION_NAME_CLIENT, true).packageItems.get(applicationListItem.targetIndex);
		int minApiVersion = applicationPackageItem.minApiVersion;
		int maxApiVersion = applicationPackageItem.maxApiVersion;
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(listItem.extensionName)) {
			for (ListItem invalidateListItem : adapter.listItems) {
				if (!invalidateListItem.isHeader()) {
					handleListItemValidity(updateDataMap, invalidateListItem,
							minApiVersion, maxApiVersion, warningUnsupported);
				}
			}
		} else {
			handleListItemValidity(updateDataMap, listItem, minApiVersion, maxApiVersion, warningUnsupported);
		}
	}

	private void onTargetSelected(String extensionName, int targetIndex) {
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		for (int i = 0; i < adapter.listItems.size(); i++) {
			ListItem listItem = adapter.listItems.get(i);
			if (extensionName.equals(listItem.extensionName)) {
				ReadUpdateTask.ApplicationItem applicationItem = updateDataMap.get(extensionName, listItem.installed);
				if (!listItem.installed) {
					targetIndex--;
				}
				if (listItem.targetIndex != targetIndex) {
					listItem.setTarget(requireContext(), applicationItem, targetIndex);
					onTargetChanged(requireContext(), adapter, updateDataMap, listItem);
					adapter.notifyDataSetChanged();
					requireActivity().invalidateOptionsMenu();
					updateTitle();
				}
				break;
			}
		}
	}

	public static class UpdateViewModel extends TaskViewModel.Proxy<ReadUpdateTask, ReadUpdateTask.Callback> {}

	private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private enum ViewType {ITEM, HEADER}

		private interface Callback extends ListViewUtils.ClickCallback<ListItem, RecyclerView.ViewHolder> {
			void onItemClick(ListItem listItem);

			@Override
			default boolean onItemClick(RecyclerView.ViewHolder holder,
					int position, ListItem listItem, boolean longClick) {
				onItemClick(listItem);
				return true;
			}
		}

		private final Callback callback;
		private final CheckPreference checkPreference;

		public List<ListItem> listItems = Collections.emptyList();

		public Adapter(Context context, Callback callback) {
			this.callback = callback;
			checkPreference = new CheckPreference(context, "", false, "title", "summary");
		}

		public DividerItemDecoration.Configuration configureDivider
				(DividerItemDecoration.Configuration configuration, int position) {
			ListItem current = listItems.get(position);
			ListItem next = listItems.size() > position + 1 ? listItems.get(position + 1) : null;
			return configuration.need(!current.isHeader() && (next == null || !next.isHeader() || C.API_LOLLIPOP));
		}

		@Override
		public int getItemCount() {
			return listItems.size();
		}

		@Override
		public int getItemViewType(int position) {
			return (listItems.get(position).isHeader() ? ViewType.HEADER : ViewType.ITEM).ordinal();
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (ViewType.values()[viewType]) {
				case ITEM: {
					CheckPreference.CheckViewHolder viewHolder = checkPreference.createViewHolder(parent);
					ViewUtils.setSelectableItemBackground(viewHolder.view);
					viewHolder.view.setTag(viewHolder);
					return ListViewUtils.bind(new SimpleViewHolder(viewHolder.view), false, listItems::get, callback);
				}
				case HEADER: {
					return new SimpleViewHolder(ViewFactory.makeListTextHeader(parent));
				}
				default: {
					throw new IllegalStateException();
				}
			}
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ListItem listItem = listItems.get(position);
			switch (ViewType.values()[holder.getItemViewType()]) {
				case ITEM: {
					CheckPreference.CheckViewHolder viewHolder = (CheckPreference.CheckViewHolder)
							holder.itemView.getTag();
					checkPreference.setValue(listItem.willBeInstalled());
					checkPreference.setEnabled(listItem.enabled);
					checkPreference.bindViewHolder(viewHolder);
					viewHolder.title.setText(listItem.title);
					if (listItem.warning != null) {
						SpannableString spannable = new SpannableString(listItem.target + "\n" + listItem.warning);
						int length = spannable.length();
						spannable.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(holder.itemView.getContext(),
								R.attr.colorTextError)), length - listItem.warning.length(), length,
								SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
						viewHolder.summary.setText(spannable);
					} else {
						viewHolder.summary.setText(listItem.target);
					}
					break;
				}
				case HEADER: {
					((TextView) holder.itemView).setText(listItem.title);
					break;
				}
			}
		}
	}

	public static class TargetDialog extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String TAG = TargetDialog.class.getName();

		private static final String EXTRA_EXTENSION_NAME = "extensionName";
		private static final String EXTRA_TITLE = "title";
		private static final String EXTRA_TARGETS = "targets";
		private static final String EXTRA_REPOSITORIES = "repositories";
		private static final String EXTRA_INDEX = "index";

		public TargetDialog() {}

		public TargetDialog(String extensionName, String title, ArrayList<String> targets,
				ArrayList<String> repositories, int index) {
			Bundle args = new Bundle();
			args.putString(EXTRA_EXTENSION_NAME, extensionName);
			args.putString(EXTRA_TITLE, title);
			args.putStringArrayList(EXTRA_TARGETS, targets);
			args.putStringArrayList(EXTRA_REPOSITORIES, repositories);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			int index = requireArguments().getInt(EXTRA_INDEX);
			List<String> targets = requireArguments().getStringArrayList(EXTRA_TARGETS);
			List<String> repositories = requireArguments().getStringArrayList(EXTRA_REPOSITORIES);
			CharSequence[] titles = new CharSequence[targets.size()];
			FrameLayout referenceParent = new FrameLayout(requireContext());
			TextView referenceSubtitle = ViewFactory.makeTwoLinesListItem(referenceParent, 0).text2;
			for (int i = 0; i < titles.length; i++) {
				String target = targets.get(i);
				String repository = repositories.get(i);
				if (StringUtils.isEmpty(repository)) {
					titles[i] = target;
				} else {
					SpannableStringBuilder builder = new SpannableStringBuilder(target);
					builder.append('\n');
					builder.append(repository);
					int from = builder.length() - repository.length();
					int to = builder.length();
					builder.setSpan(new ForegroundColorSpan(referenceSubtitle.getTextColors().getDefaultColor()),
							from, to, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new AbsoluteSizeSpan((int) (referenceSubtitle.getTextSize() + 0.5f)),
							from, to, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					titles[i] = builder;
				}
			}
			return new AlertDialog.Builder(requireContext())
					.setTitle(requireArguments().getString(EXTRA_TITLE))
					.setSingleChoiceItems(titles, index, this)
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
		List<ListItem> listItems = buildData(null, updateDataMap, null);
		for (ListItem listItem : listItems) {
			if (listItem.willBeInstalled()) {
				count++;
			}
		}
		return count;
	}
}
