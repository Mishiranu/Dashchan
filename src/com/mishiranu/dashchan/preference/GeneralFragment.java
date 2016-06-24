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

package com.mishiranu.dashchan.preference;

import java.util.Collection;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import chan.content.ChanManager;

import com.mishiranu.dashchan.R;

public class GeneralFragment extends BasePreferenceFragment
{
	private ListPreference mLocalePreference;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		
		mLocalePreference = makeList(null, Preferences.KEY_LOCALE, Preferences.VALUES_LOCALE,
				Preferences.DEFAULT_LOCALE, R.string.preference_locale, Preferences.ENTRIES_LOCALE);
		
		PreferenceCategory navigationCategory = makeCategory(R.string.preference_category_navigation);
		makeCheckBox(navigationCategory, true, Preferences.KEY_CLOSE_ON_BACK, Preferences.DEFAULT_CLOSE_ON_BACK,
				R.string.preference_close_on_back, R.string.preference_close_on_back_summary);
		if (chanNames.size() > 1)
		{
			makeCheckBox(navigationCategory, true, Preferences.KEY_MERGE_CHANS, Preferences.DEFAULT_MERGE_CHANS,
					R.string.preference_merge_chans, R.string.preference_merge_chans_summary);
		}
		makeCheckBox(navigationCategory, true, Preferences.KEY_INTERNAL_BROWSER, Preferences.DEFAULT_INTERNAL_BROWSER,
				R.string.preference_internal_browser, R.string.preference_internal_browser_sumamry);
		
		PreferenceCategory servicesCategory = makeCategory(R.string.preference_category_services);
		String[] captchaTypes = Preferences.getCloudFlareCaptchaTypes();
		String[] values = Preferences.getCaptchaTypeValues(captchaTypes);
		makeList(servicesCategory, Preferences.KEY_CAPTCHA_CLOUDFLARE, values,
				Preferences.getCloudFlareCaptchaTypeDefaultValue(), R.string.preference_captcha_cloudflare,
				Preferences.getCaptchaTypeEntries(null, captchaTypes));
		
		PreferenceCategory connectionCategory = makeCategory(R.string.preference_category_connection);
		makeButton(connectionCategory, null, getString(R.string.preference_use_https_warning), true)
				.setSelectable(false);
		makeCheckBox(connectionCategory, true, Preferences.KEY_USE_HTTPS_GENERAL, Preferences.DEFAULT_USE_HTTPS,
				getString(R.string.preference_use_https), getString(R.string.preference_use_https_summary));
		makeCheckBox(connectionCategory, true, Preferences.KEY_VERIFY_CERTIFICATE,
				Preferences.DEFAULT_VERIFY_CERTIFICATE, R.string.preference_verify_certificate,
				R.string.preference_verify_certificate_summary);
	}
	
	@Override
	public void onPreferenceAfterChange(Preference preference)
	{
		super.onPreferenceAfterChange(preference);
		if (preference == mLocalePreference)
		{
			Preferences.applyLocale(getActivity());
		}
	}
}