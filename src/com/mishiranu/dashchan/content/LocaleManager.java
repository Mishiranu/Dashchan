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

package com.mishiranu.dashchan.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import chan.content.ChanManager;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;

public class LocaleManager
{
	public static final String[] ENTRIES_LOCALE;
	public static final String[] VALUES_LOCALE;
	public static final String DEFAULT_LOCALE = "";
	private static final HashMap<String, Locale> VALUES_LOCALE_OBJECTS;

	static
	{
		String[] codes = {DEFAULT_LOCALE, "ru", "en", "pt_BR"};
		String[] names = new String[codes.length];
		Locale[] locales = new Locale[codes.length];
		names[0] = "System";
		for (int i = 1; i < names.length; i++)
		{
			String[] splitted = codes[i].split("_");
			String language = splitted[0];
			String country = splitted.length > 1 ? splitted[1] : null;
			Locale locale = country != null ? new Locale(language, country) : new Locale(language);
			String displayName = locale.getDisplayName(locale);
			names[i] = displayName.substring(0, 1).toUpperCase(locale) + displayName.substring(1);
			locales[i] = locale;
		}
		ENTRIES_LOCALE = names;
		VALUES_LOCALE = codes;
		VALUES_LOCALE_OBJECTS = new HashMap<>();
		for (int i = 0; i < codes.length; i++) VALUES_LOCALE_OBJECTS.put(codes[i], locales[i]);
	}

	private static final LocaleManager INSTANCE = new LocaleManager();

	public static LocaleManager getInstance()
	{
		return INSTANCE;
	}

	private LocaleManager()
	{

	}

	private ArrayList<Locale> mSystemLocales;
	private ArrayList<Locale> mPreviousLocales;

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.N)
	public void apply(Context context, boolean configChanged)
	{
		if (!ConcurrentUtils.isMain()) return;
		Resources resources = context.getApplicationContext().getResources();
		Configuration configuration = resources.getConfiguration();
		if (mSystemLocales == null) mSystemLocales = list(configuration);
		if (configChanged)
		{
			ArrayList<Locale> locales = list(configuration);
			if (mPreviousLocales != null)
			{
				if (locales.equals(mPreviousLocales)) return;
				mSystemLocales = locales;
			}
		}
		Locale locale = VALUES_LOCALE_OBJECTS.get(Preferences.getLocale());
		boolean applySystem = false;
		if (locale == null)
		{
			locale = mSystemLocales.isEmpty() ? Locale.getDefault() : mSystemLocales.get(0);
			applySystem = true;
		}
		if (C.API_NOUGAT)
		{
			if (applySystem)
			{
				configuration.setLocales(new LocaleList(CommonUtils.toArray(mSystemLocales, Locale.class)));
				mPreviousLocales = mSystemLocales;
			}
			else
			{
				ArrayList<Locale> locales = new ArrayList<>();
				if (!locale.equals(Locale.US)) locales.add(locale);
				locales.add(Locale.US);
				configuration.setLocales(new LocaleList(CommonUtils.toArray(locales, Locale.class)));
				mPreviousLocales = locales;
			}
		}
		else
		{
			configuration.locale = locale;
			mPreviousLocales = list(configuration);
		}
		resources.updateConfiguration(configuration, resources.getDisplayMetrics());
		ChanManager.getInstance().updateConfiguration(configuration, resources.getDisplayMetrics());
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.N)
	private ArrayList<Locale> list(Configuration configuration)
	{
		ArrayList<Locale> arrayList = new ArrayList<>();
		if (C.API_NOUGAT)
		{
			LocaleList localeList = configuration.getLocales();
			for (int i = 0; i < localeList.size(); i++) arrayList.add(localeList.get(i));
		}
		else arrayList.add(configuration.locale);
		return arrayList;
	}

	public ArrayList<Locale> list(Context context)
	{
		return list(context.getApplicationContext().getResources().getConfiguration());
	}
}