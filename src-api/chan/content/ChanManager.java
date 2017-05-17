/*
 * Copyright 2014-2017 Fukurou Mishiranu
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

package chan.content;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import dalvik.system.PathClassLoader;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.WeakObservable;

public class ChanManager {
	public static final int MAX_VERSION = 1;
	public static final int MIN_VERSION = 1;

	public static final String EXTENSION_NAME_CLIENT = "client";
	public static final String EXTENSION_NAME_LIB_WEBM = "webm";

	private static final String FEATURE_CHAN_EXTENSION = "chan.extension";
	private static final String META_CHAN_EXTENSION_NAME = "chan.extension.name";
	private static final String META_CHAN_EXTENSION_VERSION = "chan.extension.version";
	private static final String META_CHAN_EXTENSION_ICON = "chan.extension.icon";
	private static final String META_CHAN_EXTENSION_SOURCE = "chan.extension.source";
	private static final String META_CHAN_EXTENSION_CLASS_CONFIGURATION = "chan.extension.class.configuration";
	private static final String META_CHAN_EXTENSION_CLASS_PERFORMER = "chan.extension.class.performer";
	private static final String META_CHAN_EXTENSION_CLASS_LOCATOR = "chan.extension.class.locator";
	private static final String META_CHAN_EXTENSION_CLASS_MARKUP = "chan.extension.class.markup";

	private static final String FEATURE_LIB_EXTENSION = "lib.extension";
	private static final String META_LIB_EXTENSION_NAME = "lib.extension.name";
	private static final String META_LIB_EXTENSION_SOURCE = "lib.extension.source";

	private LinkedHashSet<String> availableChanNames;
	private ArrayList<String> sortedAvailableChanNames;

	private final ChanHolder defaultChanHolder;
	private final HashMap<String, ChanHolder> chanHolders = new HashMap<>();
	private final HashMap<String, ArrayList<String>> archiveMap = new HashMap<>();

	private final LinkedHashMap<String, ExtensionItem> extensionItems;
	private final LinkedHashMap<String, ExtensionItem> chanItems;
	private final LinkedHashMap<String, ExtensionItem> libItems;

	private final Set<String> applicationFingerprints;

	private static final Pattern VALID_EXTENSION_NAME = Pattern.compile("[a-z][a-z0-9]{3,14}");

	private static class ChanHolder {
		public final ChanConfiguration configuration;
		public final ChanPerformer performer;
		public final ChanLocator locator;
		public final ChanMarkup markup;
		public final Drawable icon;

		public ChanHolder(ChanConfiguration configuration, ChanPerformer performer, ChanLocator locator,
				ChanMarkup markup, Drawable icon) {
			this.configuration = configuration;
			this.performer = performer;
			this.locator = locator;
			this.markup = markup;
			this.icon = icon;
		}
	}

	private static final ChanManager INSTANCE;

	static {
		INSTANCE = new ChanManager();
	}

	public static ChanManager getInstance() {
		return INSTANCE;
	}

	public static class ExtensionItem {
		public final boolean isChanExtension;
		public final boolean isLibExtension;

		public final String extensionName;
		public final PackageInfo packageInfo;
		public final ApplicationInfo applicationInfo;
		public final Set<String> fingerprints;
		public final int version;
		public final boolean supported;
		public final int iconResId;
		public final Uri updateUri;

		public final String classConfiguration;
		public final String classPerformer;
		public final String classLocator;
		public final String classMarkup;

		public static final int TRUST_STATE_UNTRUSTED = 0;
		public static final int TRUST_STATE_TRUSTED = 1;
		public static final int TRUST_STATE_DISCARDED = 2;

		public int trustState = TRUST_STATE_UNTRUSTED;

		public ExtensionItem(String chanName, PackageInfo packageInfo, ApplicationInfo applicationInfo,
				Set<String> fingerprints, int version, boolean supported, int iconResId, Uri updateUri,
				String classConfiguration, String classPerformer, String classLocator, String classMarkup) {
			isChanExtension = true;
			isLibExtension = false;

			extensionName = chanName;
			this.packageInfo = packageInfo;
			this.applicationInfo = applicationInfo;
			this.fingerprints = fingerprints;
			this.version = version;
			this.supported = supported;
			this.iconResId = iconResId;
			this.updateUri = updateUri;

			this.classConfiguration = classConfiguration;
			this.classPerformer = classPerformer;
			this.classLocator = classLocator;
			this.classMarkup = classMarkup;
		}

		public ExtensionItem(String libName, PackageInfo packageInfo, ApplicationInfo applicationInfo,
				Set<String> fingerprints, Uri updateUri) {
			isChanExtension = false;
			isLibExtension = true;

			extensionName = libName;
			this.packageInfo = packageInfo;
			this.applicationInfo = applicationInfo;
			this.fingerprints = fingerprints;
			version = 0;
			supported = true;
			iconResId = 0;
			this.updateUri = updateUri;

			classConfiguration = null;
			classPerformer = null;
			classLocator = null;
			classMarkup = null;
		}
	}

	private static String extendClassName(String className, String packageName) {
		if (className.startsWith(".")) {
			className = packageName + className;
		}
		return className;
	}

	private static LinkedHashMap<String, ExtensionItem> mapExtensionsList(ArrayList<ExtensionItem> extensionItems) {
		LinkedHashMap<String, ExtensionItem> map = new LinkedHashMap<>();
		for (ExtensionItem extensionItem : extensionItems) {
			map.put(extensionItem.extensionName, extensionItem);
		}
		return map;
	}

	@SuppressLint("PackageManagerGetSignatures")
	private ChanManager() {
		ArrayList<String> busyExtensionNames = new ArrayList<>();
		busyExtensionNames.add(EXTENSION_NAME_CLIENT);
		Collections.addAll(busyExtensionNames, Preferences.SPECIAL_EXTENSION_NAMES);
		ArrayList<String> busyChanNames = new ArrayList<>(busyExtensionNames);
		busyChanNames.add(EXTENSION_NAME_LIB_WEBM);
		defaultChanHolder = new ChanHolder(new ChanConfiguration(false), new ChanPerformer(false),
				new ChanLocator(false), null, null);
		PackageManager packageManager = MainApplication.getInstance().getPackageManager();
		List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_CONFIGURATIONS
				| PackageManager.GET_SIGNATURES);
		ArrayList<ExtensionItem> extensionItems = new ArrayList<>();
		ArrayList<ExtensionItem> chanItems = new ArrayList<>();
		ArrayList<ExtensionItem> libItems = new ArrayList<>();
		HashSet<String> usedExtensionNames = new HashSet<>();
		try {
			applicationFingerprints = extractFingerprints(packageManager
					.getPackageInfo(MainApplication.getInstance().getPackageName(), PackageManager.GET_SIGNATURES));
		} catch (PackageManager.NameNotFoundException e) {
			throw new RuntimeException(e);
		}
		for (PackageInfo packageInfo : packages) {
			FeatureInfo[] features = packageInfo.reqFeatures;
			if (features != null) {
				for (FeatureInfo featureInfo : features) {
					boolean chanExtension = FEATURE_CHAN_EXTENSION.equals(featureInfo.name);
					boolean libExtension = FEATURE_LIB_EXTENSION.equals(featureInfo.name);
					if (chanExtension || libExtension) {
						ApplicationInfo applicationInfo;
						try {
							applicationInfo = packageManager.getApplicationInfo(packageInfo.packageName,
									PackageManager.GET_META_DATA);
						} catch (PackageManager.NameNotFoundException e) {
							throw new RuntimeException(e);
						}
						Bundle data = applicationInfo.metaData;
						String extensionName = data.getString(chanExtension ? META_CHAN_EXTENSION_NAME
								: libExtension ? META_LIB_EXTENSION_NAME : null);
						if (extensionName == null || !VALID_EXTENSION_NAME.matcher(extensionName).matches() ||
								(chanExtension ? busyChanNames : busyExtensionNames).contains(extensionName)) {
							Log.persistent().write("Invalid extension name: " + extensionName);
							break;
						}
						if (usedExtensionNames.contains(extensionName)) {
							Log.persistent().write("Extension names conflict: " + extensionName + " already exists");
							break;
						}
						Set<String> fingerprints = extractFingerprints(packageInfo);
						ExtensionItem extensionItem;
						if (chanExtension) {
							int invalidVersion = Integer.MIN_VALUE;
							int version = data.getInt(META_CHAN_EXTENSION_VERSION, invalidVersion);
							if (version == invalidVersion) {
								Log.persistent().write("Invalid extension version");
								break;
							}
							int iconResId = data.getInt(META_CHAN_EXTENSION_ICON);
							String source = data.getString(META_CHAN_EXTENSION_SOURCE);
							Uri updateUri = source != null ? Uri.parse(source) : null;
							String classConfiguration = data.getString(META_CHAN_EXTENSION_CLASS_CONFIGURATION);
							String classPerformer = data.getString(META_CHAN_EXTENSION_CLASS_PERFORMER);
							String classLocator = data.getString(META_CHAN_EXTENSION_CLASS_LOCATOR);
							String classMarkup = data.getString(META_CHAN_EXTENSION_CLASS_MARKUP);
							if (classConfiguration == null || classPerformer == null || classLocator == null
									|| classMarkup == null) {
								Log.persistent().write("Undefined extension class");
								break;
							}
							classConfiguration = extendClassName(classConfiguration, packageInfo.packageName);
							classPerformer = extendClassName(classPerformer, packageInfo.packageName);
							classLocator = extendClassName(classLocator, packageInfo.packageName);
							classMarkup = extendClassName(classMarkup, packageInfo.packageName);
							boolean supported = version >= MIN_VERSION && version <= MAX_VERSION;
							extensionItem = new ExtensionItem(extensionName, packageInfo, applicationInfo,
									fingerprints, version, supported, iconResId, updateUri,
									classConfiguration, classPerformer, classLocator, classMarkup);
							chanItems.add(extensionItem);
						} else if (libExtension) {
							String source = data.getString(META_LIB_EXTENSION_SOURCE);
							Uri updateUri = source != null ? Uri.parse(source) : null;
							extensionItem = new ExtensionItem(extensionName, packageInfo, applicationInfo,
									fingerprints, updateUri);
							libItems.add(extensionItem);
						} else {
							throw new RuntimeException();
						}
						extensionItems.add(extensionItem);
						usedExtensionNames.add(extensionName);
						if (Preferences.isExtensionTrusted(packageInfo.packageName)
								|| fingerprints.equals(applicationFingerprints)) {
							extensionItem.trustState = ExtensionItem.TRUST_STATE_TRUSTED;
						}
						break;
					}
				}
			}
		}

		this.extensionItems = mapExtensionsList(extensionItems);
		this.chanItems = mapExtensionsList(chanItems);
		this.libItems = mapExtensionsList(libItems);
		ArrayList<String> loadedChanNames = new ArrayList<>();
		if (!this.chanItems.isEmpty()) {
			for (ExtensionItem chanItem : this.chanItems.values()) {
				if (chanItem.trustState == ExtensionItem.TRUST_STATE_TRUSTED) {
					ChanHolder holder = loadChan(chanItem, MainApplication.getInstance().getPackageManager());
					if (holder != null) {
						chanHolders.put(chanItem.extensionName, holder);
						loadedChanNames.add(chanItem.extensionName);
					}
				}
			}
		}
		availableChanNames = new LinkedHashSet<>(loadedChanNames);
		invalidateChansOrder();

		IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addDataScheme("package");
		MainApplication.getInstance().registerReceiver(AndroidUtils.createReceiver((receiver, context, intent) -> {
			Uri uri = intent.getData();
			if (uri == null) {
				return;
			}
			String packageName = uri.getSchemeSpecificPart();
			if (packageName == null) {
				return;
			}
			PackageInfo packageInfo;
			try {
				packageInfo = context.getPackageManager().getPackageInfo(packageName,
						PackageManager.GET_CONFIGURATIONS);
			} catch (PackageManager.NameNotFoundException e) {
				return;
			}
			FeatureInfo[] features = packageInfo.reqFeatures;
			if (features != null) {
				for (FeatureInfo featureInfo : features) {
					if (FEATURE_CHAN_EXTENSION.equals(featureInfo.name) ||
							FEATURE_LIB_EXTENSION.equals(featureInfo.name)) {
						newExtensionsInstalled = true;
						for (Runnable runnable : installationObservable) {
							runnable.run();
						}
						break;
					}
				}
			}
		}), filter);
		updateArchiveMap();
	}

	private void invalidateChansOrder() {
		ArrayList<String> orderedChanNames = Preferences.getChansOrder();
		if (orderedChanNames != null) {
			Collection<String> oldAvailableChanNames = availableChanNames;
			availableChanNames = new LinkedHashSet<>();
			for (String chanName : orderedChanNames) {
				if (oldAvailableChanNames.contains(chanName)) {
					availableChanNames.add(chanName);
				}
			}
			availableChanNames.addAll(oldAvailableChanNames);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private ChanHolder loadChan(ExtensionItem chanItem, PackageManager packageManager) {
		if (chanItem.supported) {
			String chanName = chanItem.extensionName;
			try {
				String nativeLibraryDir = chanItem.applicationInfo.nativeLibraryDir;
				if (nativeLibraryDir != null && !new File(nativeLibraryDir).exists()) {
					nativeLibraryDir = null;
				}
				ClassLoader classLoader = new PathClassLoader(chanItem.applicationInfo.sourceDir, nativeLibraryDir,
						ChanManager.class.getClassLoader());
				Resources resources = packageManager.getResourcesForApplication(chanItem.applicationInfo);
				ChanConfiguration configuration = ChanConfiguration.INITIALIZER.initialize(classLoader,
						chanItem.classConfiguration, chanName, resources);
				ChanPerformer performer = ChanPerformer.INITIALIZER.initialize(classLoader,
						chanItem.classPerformer, chanName, resources);
				ChanLocator locator = ChanLocator.INITIALIZER.initialize(classLoader,
						chanItem.classLocator, chanName, resources);
				ChanMarkup markup = ChanMarkup.INITIALIZER.initialize(classLoader,
						chanItem.classMarkup, chanName, resources);
				Drawable icon = C.API_LOLLIPOP && chanItem.iconResId != 0
						? resources.getDrawable(chanItem.iconResId, null) : null;
				return new ChanHolder(configuration, performer, locator, markup, icon);
			} catch (Exception | LinkageError e) {
				Log.persistent().stack(e);
			}
		}
		return null;
	}

	private void loadLibrary(ExtensionItem libItem) {
		switch (libItem.extensionName) {
			case EXTENSION_NAME_LIB_WEBM: {
				if (Preferences.isUseVideoPlayer()) {
					VideoPlayer.loadLibraries(MainApplication.getInstance());
				}
				break;
			}
		}
	}

	public void loadLibraries() {
		if (!libItems.isEmpty()) {
			for (ExtensionItem libItem : libItems.values()) {
				if (libItem.trustState == ExtensionItem.TRUST_STATE_TRUSTED) {
					loadLibrary(libItem);
				}
			}
		}
	}

	public void changeUntrustedExtensionState(String extensionName, boolean trusted) {
		ExtensionItem extensionItem = extensionItems.get(extensionName);
		if (extensionItem == null || extensionItem.trustState != ExtensionItem.TRUST_STATE_UNTRUSTED) {
			return;
		}
		if (trusted) {
			extensionItem.trustState = ExtensionItem.TRUST_STATE_TRUSTED;
			if (libItems.containsKey(extensionName)) {
				loadLibrary(extensionItem);
			} else {
				ChanHolder holder = loadChan(extensionItem, MainApplication.getInstance().getPackageManager());
				if (holder != null) {
					chanHolders.put(extensionItem.extensionName, holder);
					availableChanNames.add(extensionItem.extensionName);
					invalidateChansOrder();
				}
			}
			Preferences.setExtensionTrusted(extensionItem.packageInfo.packageName);
		} else {
			extensionItem.trustState = ExtensionItem.TRUST_STATE_DISCARDED;
		}
	}

	private void updateArchiveMap() {
		archiveMap.clear();
		for (ChanHolder chanHolder : chanHolders.values()) {
			ChanConfiguration.Archivation archivation = chanHolder.configuration.obtainArchivationConfiguration();
			if (archivation != null) {
				for (String host : archivation.hosts) {
					String chanName = getChanNameByHost(host);
					if (chanName != null) {
						ArrayList<String> archiveChanNames = archiveMap.get(chanName);
						if (archiveChanNames == null) {
							archiveChanNames = new ArrayList<>();
							archiveMap.put(chanName, archiveChanNames);
						}
						archiveChanNames.add(chanHolder.configuration.getChanName());
					}
				}
			}
		}
	}

	public interface Linked {
		public String getChanName();
		public void init();
	}

	public static final class Initializer {
		public static class Holder {
			public final String chanName;
			public final Resources resources;

			private Holder(String chanName, Resources resources) {
				this.chanName = chanName;
				this.resources = resources;
			}
		}

		private Holder holder;

		@SuppressWarnings("unchecked")
		public <T extends Linked> T initialize(ClassLoader classLoader, String className,
				String chanName, Resources resources) throws LinkageError, Exception {
			synchronized (this) {
				holder = new Holder(chanName, resources);
				T result;
				try {
					result = (T) Class.forName(className, false, classLoader).newInstance();
				} finally {
					holder = null;
				}
				result.init();
				return result;
			}
		}

		public Holder consume() {
			if (holder != null) {
				Holder holder = this.holder;
				this.holder = null;
				return holder;
			} else {
				throw new IllegalStateException("You can't initiate instance of this object by yourself.");
			}
		}
	}

	private ChanHolder getChanHolder(String chanName, boolean defaultIfNotFound) {
		ChanHolder chanHolder = chanHolders.get(chanName);
		if (chanHolder == null) {
			if (defaultIfNotFound) {
				return defaultChanHolder;
			} else {
				throw new IllegalArgumentException("Unsupported operation for " + chanName);
			}
		}
		return chanHolder;
	}

	@SuppressWarnings("unchecked")
	<T extends ChanConfiguration> T getConfiguration(String chanName, boolean defaultIfNotFound) {
		return (T) getChanHolder(chanName, defaultIfNotFound).configuration;
	}

	@SuppressWarnings("unchecked")
	<T extends ChanPerformer> T getPerformer(String chanName, boolean defaultIfNotFound) {
		return (T) getChanHolder(chanName, defaultIfNotFound).performer;
	}

	@SuppressWarnings("unchecked")
	<T extends ChanLocator> T getLocator(String chanName, boolean defaultIfNotFound) {
		return (T) getChanHolder(chanName, defaultIfNotFound).locator;
	}

	@SuppressWarnings("unchecked")
	<T extends ChanMarkup> T getMarkup(String chanName) {
		return (T) getChanHolder(chanName, false).markup;
	}

	public Collection<ExtensionItem> getExtensionItems() {
		return extensionItems.values();
	}

	public Collection<ExtensionItem> getChanItems() {
		return chanItems.values();
	}

	public Collection<ExtensionItem> getLibItems() {
		return libItems.values();
	}

	public Collection<ExtensionItem> getUntrustedExtensionItems() {
		LinkedHashSet<ExtensionItem> extensionItems = new LinkedHashSet<>();
		for (ExtensionItem extensionItem : this.extensionItems.values()) {
			if (extensionItem.trustState == ExtensionItem.TRUST_STATE_UNTRUSTED) {
				extensionItems.add(extensionItem);
			}
		}
		return extensionItems;
	}

	public ExtensionItem getLibExtension(String libName) {
		return libItems.get(libName);
	}

	public HashMap<String, ArrayList<String>> getArhiveMap() {
		return archiveMap;
	}

	public boolean canBeArchived(String chanName) {
		return archiveMap.containsKey(chanName) || !ChanConfiguration.get(chanName)
				.getOption(ChanConfiguration.OPTION_HIDDEN_DISALLOW_ARCHIVATION);
	}

	public Collection<String> getAllChanNames() {
		return chanItems.keySet();
	}

	public Collection<String> getAvailableChanNames() {
		return availableChanNames;
	}

	public int compareChanNames(String lhs, String rhs) {
		if (sortedAvailableChanNames == null) {
			if (availableChanNames != null) {
				sortedAvailableChanNames = new ArrayList<>(availableChanNames);
			} else {
				return 0;
			}
		}
		return sortedAvailableChanNames.indexOf(lhs) - sortedAvailableChanNames.indexOf(rhs);
	}

	public String getDefaultChanName() {
		return availableChanNames.size() > 0 ? availableChanNames.iterator().next() : null;
	}

	public void setChansOrder(ArrayList<String> chanNames) {
		for (int i = chanNames.size() - 1; i >= 0; i--) {
			String chanName = chanNames.get(i);
			if (!availableChanNames.contains(chanName)) {
				chanNames.remove(i);
			}
		}
		for (String chanName : availableChanNames) {
			if (!chanNames.contains(chanName)) {
				chanNames.add(chanName);
			}
		}
		availableChanNames = new LinkedHashSet<>(chanNames);
		sortedAvailableChanNames = null;
		Preferences.setChansOrder(chanNames);
	}

	public String getChanNameByHost(String host) {
		if (host != null) {
			for (HashMap.Entry<String, ChanHolder> entry : chanHolders.entrySet()) {
				if (entry.getValue().locator.isChanHost(host)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public Drawable getIcon(String chanName, int tint) {
		if (C.API_LOLLIPOP) {
			Drawable drawable = chanHolders.get(chanName).icon;
			if (drawable == null) {
				drawable = MainApplication.getInstance().getDrawable(R.drawable.ic_extension_white);
			}
			drawable = drawable.getConstantState().newDrawable().mutate();
			drawable.setTint(tint);
			return drawable;
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public void updateConfiguration(Configuration newConfig, DisplayMetrics metrics) {
		for (ChanHolder chanHolder : chanHolders.values()) {
			chanHolder.configuration.getResources().updateConfiguration(newConfig, metrics);
		}
	}

	public boolean isExtensionPackage(String packageName) {
		for (ExtensionItem extensionItem : extensionItems.values()) {
			if (packageName.equals(extensionItem.packageInfo.packageName)) {
				return true;
			}
		}
		return false;
	}

	public static Set<String> extractFingerprints(PackageInfo packageInfo) {
		HashSet<String> fingerprints = new HashSet<>();
		android.content.pm.Signature[] signaturesArray = packageInfo.signatures;
		if (signaturesArray != null) {
			for (android.content.pm.Signature signature : signaturesArray) {
				if (signature != null) {
					fingerprints.add(IOUtils.calculateSha256(signature.toByteArray()));
				}
			}
		}
		return Collections.unmodifiableSet(fingerprints);
	}

	public Set<String> getApplicationFingerprints() {
		return applicationFingerprints;
	}

	String getLinkedChanName(Object object) {
		if (object instanceof Linked) {
			return ((Linked) object).getChanName();
		}
		throw new IllegalArgumentException("Object must be instance of ChanConfiguration, ChanPerformer, " +
				"ChanLocator or ChanMarkup.");
	}

	private boolean newExtensionsInstalled = false;

	public boolean hasNewExtensionsInstalled() {
		return newExtensionsInstalled;
	}

	private final WeakObservable<Runnable> installationObservable = new WeakObservable<>();

	public WeakObservable<Runnable> getInstallationObservable() {
		return installationObservable;
	}
}
