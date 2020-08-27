package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;

public class GeneralFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_LOCALE, LocaleManager.VALUES_LOCALE,
				LocaleManager.DEFAULT_LOCALE, R.string.preference_locale, LocaleManager.ENTRIES_LOCALE)
				.setOnAfterChangeListener(p -> {
					LocaleManager.getInstance().apply(requireContext(), false);
					requireActivity().recreate();
				});

		addHeader(R.string.preference_category_navigation);
		addCheck(true, Preferences.KEY_CLOSE_ON_BACK, Preferences.DEFAULT_CLOSE_ON_BACK,
				R.string.preference_close_on_back, R.string.preference_close_on_back_summary);
		addCheck(true, Preferences.KEY_REMEMBER_HISTORY, Preferences.DEFAULT_REMEMBER_HISTORY,
				R.string.preference_remember_history, 0);
		if (ChanManager.getInstance().hasMultipleAvailableChans()) {
			addCheck(true, Preferences.KEY_MERGE_CHANS, Preferences.DEFAULT_MERGE_CHANS,
					R.string.preference_merge_chans, R.string.preference_merge_chans_summary);
		}
		addCheck(true, Preferences.KEY_INTERNAL_BROWSER, Preferences.DEFAULT_INTERNAL_BROWSER,
				R.string.preference_internal_browser, R.string.preference_internal_browser_sumamry);

		if (C.API_KITKAT) {
			addHeader(R.string.preference_category_services);
			addCheck(true, Preferences.KEY_RECAPTCHA_JAVASCRIPT,
					Preferences.DEFAULT_RECAPTCHA_JAVASCRIPT, R.string.preference_recaptcha_javascript,
					R.string.preference_recaptcha_javascript_summary);
		}

		addHeader(R.string.preference_category_connection);
		addButton(0, R.string.preference_use_https_warning).setSelectable(false);
		addCheck(true, Preferences.KEY_USE_HTTPS_GENERAL, Preferences.DEFAULT_USE_HTTPS,
				R.string.preference_use_https, R.string.preference_use_https_summary);
		addCheck(true, Preferences.KEY_VERIFY_CERTIFICATE,
				Preferences.DEFAULT_VERIFY_CERTIFICATE, R.string.preference_verify_certificate,
				R.string.preference_verify_certificate_summary);
		addCheck(true, Preferences.KEY_USE_GMS_PROVIDER, Preferences.DEFAULT_USE_GMS_PROVIDER,
				R.string.preference_use_gms_provider, R.string.preference_use_gms_provider_summary);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.preference_header_general), null);
	}
}
