package com.mishiranu.dashchan.content;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import chan.content.ChanManager;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.C;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LocaleManager {
	public static final String[] ENTRIES_LOCALE;
	public static final String[] VALUES_LOCALE;
	public static final String DEFAULT_LOCALE = "";
	private static final HashMap<String, Locale> VALUES_LOCALE_OBJECTS;

	static {
		int total = BuildConfig.LOCALES.length + 2;
		String[] codes = new String[total];
		String[] names = new String[total];
		Locale[] locales = new Locale[total];
		codes[0] = DEFAULT_LOCALE;
		codes[1] = "en";
		for (int i = 2; i < total; i++) {
			String locale = BuildConfig.LOCALES[i - 2];
			int index = locale.indexOf("-r");
			if (index >= 0) {
				codes[i] = locale.substring(0, index) + "_" + locale.substring(index + 2);
			} else {
				codes[i] = locale;
			}
		}
		names[0] = "System";
		for (int i = 1; i < names.length; i++) {
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
		for (int i = 0; i < codes.length; i++) {
			VALUES_LOCALE_OBJECTS.put(codes[i], locales[i]);
		}
	}

	private static final LocaleManager INSTANCE = new LocaleManager();

	public static LocaleManager getInstance() {
		return INSTANCE;
	}

	private LocaleManager() {}

	private Locale systemLocaleJellyBean;

	@SuppressWarnings("deprecation")
	public void updateConfiguration(Configuration configuration) {
		if (!C.API_JELLY_BEAN_MR1) {
			systemLocaleJellyBean = configuration.locale;
		}
	}

	private List<Locale> lastLocales = Collections.emptyList();
	private Context applicationContext;

	@SuppressWarnings("deprecation")
	public Context apply(Context context) {
		Resources resources = context.getResources();
		Configuration configuration = resources.getConfiguration();
		Locale locale = VALUES_LOCALE_OBJECTS.get(Preferences.getLocale());
		if (locale != null) {
			configuration = new Configuration(configuration);
			if (C.API_NOUGAT) {
				configuration.setLocales(locale != Locale.US
						? new LocaleList(locale, Locale.US) : new LocaleList(Locale.US));
			}
			configuration.locale = locale;
			Locale.setDefault(locale);
			if (C.API_JELLY_BEAN_MR1) {
				context = context.createConfigurationContext(configuration);
			} else {
				resources.updateConfiguration(configuration, resources.getDisplayMetrics());
			}
		} else {
			if (C.API_NOUGAT_MR1) {
				LocaleList localeList = configuration.getLocales();
				locale = localeList.size() > 0 ? localeList.get(0) : null;
			} else if (C.API_JELLY_BEAN_MR1) {
				locale = configuration.locale;
			} else {
				locale = systemLocaleJellyBean;
			}
			if (locale == null) {
				locale = Locale.US;
			}
			Locale.setDefault(locale);
			if (!C.API_JELLY_BEAN_MR1) {
				configuration = new Configuration(configuration);
				configuration.locale = locale;
				resources.updateConfiguration(configuration, resources.getDisplayMetrics());
			}
		}
		List<Locale> lastLocales;
		if (C.API_NOUGAT_MR1) {
			LocaleList localeList = configuration.getLocales();
			lastLocales = new ArrayList<>(localeList.size());
			for (int i = 0; i < localeList.size(); i++) {
				lastLocales.add(localeList.get(i));
			}
		} else {
			lastLocales = Collections.singletonList(configuration.locale);
		}
		if (!this.lastLocales.equals(lastLocales)) {
			this.lastLocales = lastLocales;
			applicationContext = null;
			ChanManager.getInstance().updateConfiguration(configuration, resources.getDisplayMetrics());
		}
		return context;
	}

	public Context applyApplication(Context context) {
		if (applicationContext == null) {
			applicationContext = apply(context.getApplicationContext());
		}
		return applicationContext;
	}

	public List<Locale> getLocales(Configuration configuration) {
		ArrayList<Locale> locales = new ArrayList<>();
		LocaleListCompat localeList = ConfigurationCompat.getLocales(configuration);
		for (int i = 0; i < localeList.size(); i++) {
			locales.add(localeList.get(i));
		}
		return locales;
	}
}
