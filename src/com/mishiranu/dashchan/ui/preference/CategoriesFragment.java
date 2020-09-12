package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import java.util.Iterator;

public class CategoriesFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Iterator<String> chanNames = ChanManager.getInstance().getAvailableChanNames().iterator();
		boolean hasChan = chanNames.hasNext();
		String singleChanName = hasChan ? chanNames.next() : null;
		boolean hasMultipleChans = hasChan && chanNames.hasNext();
		addCategory(R.string.general, R.drawable.ic_map)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new GeneralFragment()));
		if (hasMultipleChans) {
			addCategory(R.string.forums, R.drawable.ic_public)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChansFragment()));
		} else if (hasChan) {
			addCategory(R.string.forum, R.drawable.ic_public)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChanFragment(singleChanName)));
		}
		addCategory(R.string.user_interface, R.drawable.ic_color_lens)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new InterfaceFragment()));
		addCategory(R.string.contents, R.drawable.ic_local_library)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new ContentsFragment()));
		addCategory(R.string.favorites, R.drawable.ic_star)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new FavoritesFragment()));
		addCategory(R.string.autohide, R.drawable.ic_custom_fork)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new AutohideFragment()));
		addCategory(R.string.about, R.drawable.ic_info)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new AboutFragment()));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.preferences), null);
	}
}
