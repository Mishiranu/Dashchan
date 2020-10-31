package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChanMultiChoiceDialog extends DialogFragment implements DialogInterface.OnMultiChoiceClickListener {
	public interface Callback {
		void onChansSelected(Collection<String> chanNames);
	}

	private static final String EXTRA_SELECTED = "selected";
	private static final String EXTRA_CHECKED = "checked";

	public ChanMultiChoiceDialog() {}

	public ChanMultiChoiceDialog(Collection<String> selected) {
		Bundle args = new Bundle();
		args.putStringArrayList(EXTRA_SELECTED, new ArrayList<>(selected));
		setArguments(args);
	}

	public void show(Fragment fragment) {
		show(fragment.getChildFragmentManager(), ChanMultiChoiceDialog.class.getName());
	}

	private String[] chanNames;
	private boolean[] checkedItems;

	@NonNull
	@Override
	public AlertDialog onCreateDialog(Bundle savedInstanceState) {
		ArrayList<Chan> chans = new ArrayList<>();
		for (Chan chan : ChanManager.getInstance().getAvailableChans()) {
			chans.add(chan);
		}
		chanNames = new String[chans.size()];
		String[] items = new String[chans.size()];
		for (int i = 0; i < chans.size(); i++) {
			Chan chan = chans.get(i);
			chanNames[i] = chan.name;
			items[i] = chan.configuration.getTitle();
		}
		checkedItems = new boolean[chans.size()];
		Collection<String> selected = savedInstanceState != null
				? savedInstanceState.getStringArrayList(EXTRA_CHECKED)
				: requireArguments().getStringArrayList(EXTRA_SELECTED);
		Set<String> selectedSet = selected != null ? new HashSet<>(selected) : Collections.emptySet();
		for (int i = 0; i < chans.size(); i++) {
			checkedItems[i] = selectedSet.contains(chanNames[i]);
		}
		AlertDialog dialog = new AlertDialog.Builder(requireContext())
				.setMultiChoiceItems(items, checkedItems, this)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, (d, which) -> ((Callback) getParentFragment())
						.onChansSelected(collectSelected()))
				.create();
		updateTitle(dialog);
		return dialog;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putStringArrayList(EXTRA_CHECKED, collectSelected());
	}

	@Override
	public void onClick(DialogInterface dialog, int which, boolean isChecked) {
		checkedItems[which] = isChecked;
		updateTitle((AlertDialog) dialog);
	}

	private void updateTitle(AlertDialog dialog) {
		String singleChanName = null;
		int checkedCount = 0;
		for (int i = 0; i < chanNames.length; i++) {
			if (checkedItems[i]) {
				singleChanName = chanNames[i];
				checkedCount++;
			}
		}
		dialog.setTitle(checkedCount >= 2 ? getString(R.string.multiple_forums) : checkedCount == 1
				? getString(R.string.forum_only__format, StringUtils.emptyIfNull(Chan.get(singleChanName)
				.configuration.getTitle())) : getString(R.string.all_forums));
	}

	private ArrayList<String> collectSelected() {
		ArrayList<String> result = new ArrayList<>();
		for (int i = 0; i < chanNames.length; i++) {
			if (checkedItems[i]) {
				result.add(chanNames[i]);
			}
		}
		return result;
	}
}
