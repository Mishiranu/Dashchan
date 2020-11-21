package chan.content;

import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.graphics.ChanIconDrawable;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.Hasher;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.WeakObservable;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

public class ChanManager {
	public static final int MAX_VERSION = 1;
	public static final int MIN_VERSION = 1;

	public static final String EXTENSION_NAME_CLIENT = "client";
	public static final String EXTENSION_NAME_META = "meta";
	public static final String EXTENSION_NAME_LIB_WEBM = "webm";

	private static final Set<String> RESERVED_EXTENSION_NAMES;
	private static final Set<String> RESERVED_CHAN_NAMES;

	static {
		HashSet<String> reservedExtensionNames = new HashSet<>();
		reservedExtensionNames.add(EXTENSION_NAME_CLIENT);
		reservedExtensionNames.add(EXTENSION_NAME_META);
		Collections.addAll(reservedExtensionNames, Preferences.SPECIAL_EXTENSION_NAMES);
		RESERVED_EXTENSION_NAMES = Collections.unmodifiableSet(reservedExtensionNames);
		HashSet<String> reservedChanNames = new HashSet<>(reservedExtensionNames);
		reservedChanNames.add(EXTENSION_NAME_LIB_WEBM);
		RESERVED_CHAN_NAMES = Collections.unmodifiableSet(reservedChanNames);
	}

	private static final String FEATURE_CHAN_EXTENSION = "chan.extension";
	private static final String META_CHAN_EXTENSION_NAME = "chan.extension.name";
	private static final String META_CHAN_EXTENSION_TITLE = "chan.extension.title";
	private static final String META_CHAN_EXTENSION_VERSION = "chan.extension.version";
	private static final String META_CHAN_EXTENSION_ICON = "chan.extension.icon";
	private static final String META_CHAN_EXTENSION_SOURCE = "chan.extension.source";
	private static final String META_CHAN_EXTENSION_CLASS_CONFIGURATION = "chan.extension.class.configuration";
	private static final String META_CHAN_EXTENSION_CLASS_PERFORMER = "chan.extension.class.performer";
	private static final String META_CHAN_EXTENSION_CLASS_LOCATOR = "chan.extension.class.locator";
	private static final String META_CHAN_EXTENSION_CLASS_MARKUP = "chan.extension.class.markup";

	private static final String FEATURE_LIB_EXTENSION = "lib.extension";
	private static final String META_LIB_EXTENSION_NAME = "lib.extension.name";
	private static final String META_LIB_EXTENSION_TITLE = "lib.extension.title";
	private static final String META_LIB_EXTENSION_SOURCE = "lib.extension.source";

	@SuppressWarnings("deprecation")
	private static final int PACKAGE_MANAGER_SIGNATURE_FLAGS = PackageManager.GET_SIGNATURES |
			(C.API_PIE ? PackageManager.GET_SIGNING_CERTIFICATES : 0);

	private final Chan fallbackChan;
	private final Fingerprints applicationFingerprints;
	private Map<String, Extension> extensions;
	private List<String> sortedExtensionNames;
	private Map<String, List<String>> archiveMap = Collections.emptyMap();

	private static final Pattern VALID_EXTENSION_NAME = Pattern.compile("[a-z][a-z0-9]{3,14}");

	private static final ChanManager INSTANCE;

	static {
		INSTANCE = new ChanManager();
	}

	public static ChanManager getInstance() {
		return INSTANCE;
	}

	public static final class Fingerprints {
		public final Set<String> fingerprints;

		public Fingerprints(Set<String> fingerprints) {
			this.fingerprints = fingerprints;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Fingerprints && ((Fingerprints) o).fingerprints.equals(fingerprints);
		}

		@Override
		public int hashCode() {
			return fingerprints.hashCode();
		}

		@Override
		public String toString() {
			ArrayList<String> list = new ArrayList<>(fingerprints);
			Collections.sort(list);
			StringBuilder builder = new StringBuilder();
			for (String fingerprint : list) {
				if (builder.length() > 0) {
					builder.append('.');
				}
				builder.append(fingerprint);
			}
			return builder.toString();
		}
	}

	public static class ExtensionItem {
		public enum Type {CHAN, LIBRARY}
		public enum TrustState {UNTRUSTED, TRUSTED, DISCARDED}

		public final Type type;
		public final String name;
		public final String title;
		public final TrustState trustState;
		public final String packageName;
		public final String versionName;
		public final long versionCode;
		private final ApplicationInfo applicationInfo;
		public final Fingerprints fingerprints;
		public final int apiVersion;
		public final boolean supported;
		public final int iconResId;
		public final Uri updateUri;

		public final String classConfiguration;
		public final String classPerformer;
		public final String classLocator;
		public final String classMarkup;

		public String getNativeLibraryDir() {
			return applicationInfo.nativeLibraryDir;
		}

		private ExtensionItem(Type type, String name, String title, TrustState trustState,
				String packageName, String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, int apiVersion, boolean supported, int iconResId, Uri updateUri,
				String classConfiguration, String classPerformer, String classLocator, String classMarkup) {
			this.type = type;
			this.name = name;
			this.title = title;
			this.trustState = trustState;
			this.packageName = packageName;
			this.versionName = versionName;
			this.versionCode = versionCode;
			this.applicationInfo = applicationInfo;
			this.fingerprints = fingerprints;
			this.apiVersion = apiVersion;
			this.supported = supported;
			this.iconResId = iconResId;
			this.updateUri = updateUri;

			this.classConfiguration = classConfiguration;
			this.classPerformer = classPerformer;
			this.classLocator = classLocator;
			this.classMarkup = classMarkup;
		}

		public ExtensionItem(String name, String title, String packageName,
				String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, int apiVersion, boolean supported, int iconResId, Uri updateUri,
				String classConfiguration, String classPerformer, String classLocator, String classMarkup) {
			this(Type.CHAN, name, title, TrustState.UNTRUSTED,
					packageName, versionName, versionCode, applicationInfo,
					fingerprints, apiVersion, supported, iconResId, updateUri,
					classConfiguration, classPerformer, classLocator, classMarkup);
		}

		public ExtensionItem(String name, String title, String packageName,
				String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, Uri updateUri) {
			this(Type.LIBRARY, name, title, TrustState.UNTRUSTED,
					packageName, versionName, versionCode, applicationInfo,
					fingerprints, 0, true, 0, updateUri, null, null, null, null);
		}

		public ExtensionItem changeTrustState(boolean trusted) {
			if (this.trustState != TrustState.UNTRUSTED) {
				throw new IllegalStateException();
			}
			TrustState trustState = trusted ? TrustState.TRUSTED : TrustState.DISCARDED;
			return new ExtensionItem(type, name, title, trustState,
					packageName, versionName, versionCode, applicationInfo,
					fingerprints, apiVersion, supported, iconResId, updateUri,
					classConfiguration, classPerformer, classLocator, classMarkup);
		}
	}

	public interface Callback {
		void onRestartRequiredChanged();
		void onUntrustedExtensionInstalled();
		void onChanInstalled(Chan chan);
		void onChanUninstalled(Chan chan);
	}

	private static class Extension {
		public final ExtensionItem item;
		public final Chan chan;

		private Extension(ExtensionItem item, Chan chan) {
			this.item = item;
			this.chan = chan;
		}
	}

	private static String extendClassName(String className, String packageName) {
		if (className.startsWith(".")) {
			className = packageName + className;
		}
		return className;
	}

	private static Map<String, Extension> extensionsMap(List<Extension> extensions) {
		LinkedHashMap<String, Extension> map = new LinkedHashMap<>();
		for (Extension extension : extensions) {
			map.put(extension.item.name, extension);
		}
		return Collections.unmodifiableMap(map);
	}

	@SuppressLint("PackageManagerGetSignatures")
	private ChanManager() {
		String packageName = MainApplication.getInstance().getPackageName();
		Chan.Provider fallbackChanProvider = new Chan.Provider(null);
		Chan fallbackChan = new Chan(null, packageName, new ChanConfiguration(fallbackChanProvider),
				new ChanPerformer(fallbackChanProvider), new ChanLocator(fallbackChanProvider),
				new ChanMarkup(fallbackChanProvider), null);
		fallbackChanProvider.set(fallbackChan);
		this.fallbackChan = fallbackChan;

		if (MainApplication.getInstance().isMainProcess()) {
			PackageManager packageManager = MainApplication.getInstance().getPackageManager();
			try {
				applicationFingerprints = extractFingerprints(packageManager
						.getPackageInfo(packageName, PACKAGE_MANAGER_SIGNATURE_FLAGS));
				if (applicationFingerprints.fingerprints.isEmpty()) {
					throw new RuntimeException();
				}
			} catch (PackageManager.NameNotFoundException e) {
				throw new RuntimeException(e);
			}
			List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_CONFIGURATIONS
				| PACKAGE_MANAGER_SIGNATURE_FLAGS);

			ArrayList<Extension> extensions = new ArrayList<>();
			HashSet<String> usedExtensionNames = new HashSet<>();
			for (PackageInfo packageInfo : packages) {
				boolean chanExtension = isExtension(packageInfo, FEATURE_CHAN_EXTENSION);
				boolean libExtension = isExtension(packageInfo, FEATURE_LIB_EXTENSION);
				if (chanExtension || libExtension) {
					Extension extension = loadExtension(packageInfo, chanExtension, libExtension,
							applicationFingerprints, usedExtensionNames, Collections.emptyMap());
					if (extension != null) {
						extensions.add(extension);
						usedExtensionNames.add(extension.item.name);
					}
				}
			}

			this.extensions = extensionsMap(extensions);
			updateExtensions(null, null, true);
			registerReceiver();
		} else {
			this.applicationFingerprints = new Fingerprints(Collections.emptySet());
			this.extensions = Collections.emptyMap();
		}
	}

	@SuppressLint("PackageManagerGetSignatures")
	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
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
			if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
				PackageInfo packageInfo;
				try {
					packageInfo = context.getPackageManager().getPackageInfo(packageName,
							PackageManager.GET_CONFIGURATIONS | PACKAGE_MANAGER_SIGNATURE_FLAGS);
				} catch (PackageManager.NameNotFoundException e) {
					return;
				}
				boolean chanExtension = isExtension(packageInfo, FEATURE_CHAN_EXTENSION);
				boolean libExtension = isExtension(packageInfo, FEATURE_LIB_EXTENSION);
				if (chanExtension) {
					Map<String, Extension> extensions = this.extensions;
					Extension newExtension = loadExtension(packageInfo, true, false,
							applicationFingerprints, Collections.emptySet(), extensions);
					if (newExtension != null) {
						boolean newTrusted = newExtension.item.trustState == ExtensionItem.TrustState.TRUSTED;
						Extension oldExtension = extensions.get(newExtension.item.name);
						if (oldExtension == null) {
							updateExtensions(newExtension, null, true);
							for (Callback callback : observable) {
								if (newTrusted) {
									callback.onChanInstalled(newExtension.chan);
								} else {
									callback.onUntrustedExtensionInstalled();
								}
							}
						} else {
							boolean oldTrusted = oldExtension.item.trustState == ExtensionItem.TrustState.TRUSTED;
							updateExtensions(newExtension, null, newTrusted || oldTrusted);
							for (Callback callback : observable) {
								if (newTrusted) {
									callback.onChanInstalled(newExtension.chan);
								} else {
									if (oldTrusted) {
										callback.onChanUninstalled(oldExtension.chan);
									}
									callback.onUntrustedExtensionInstalled();
								}
							}
						}
					}
				} else if (libExtension && !restartRequired) {
					restartRequired = true;
					for (Callback callback : observable) {
						callback.onRestartRequiredChanged();
					}
				}
			} else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
				for (Extension extension : extensions.values()) {
					if (packageName.equals(extension.item.packageName)) {
						if (extension.item.type == ExtensionItem.Type.CHAN) {
							if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
								updateExtensions(null, extension.item.name,
										extension.item.trustState == ExtensionItem.TrustState.TRUSTED);
								if (extension.chan != null) {
									for (Callback callback : observable) {
										callback.onChanUninstalled(extension.chan);
									}
								}
							}
						} else if (extension.item.type == ExtensionItem.Type.LIBRARY && !restartRequired) {
							restartRequired = true;
							for (Callback callback : observable) {
								callback.onRestartRequiredChanged();
							}
						}
						break;
					}
				}
			}
		}), filter);
	}

	private void updateExtensions(Extension newExtension, String deleteExtensionName, boolean updateArchiveMap) {
		List<String> orderedChanNames = Preferences.getChansOrder();
		if (orderedChanNames == null) {
			orderedChanNames = Collections.emptyList();
		}
		LinkedHashMap<String, Extension> extensions = new LinkedHashMap<>(this.extensions);
		if (newExtension != null) {
			extensions.put(newExtension.item.name, newExtension);
		}
		if (deleteExtensionName != null) {
			extensions.remove(deleteExtensionName);
		}
		LinkedHashMap<String, Extension> ordered = new LinkedHashMap<>();
		for (String chanName : orderedChanNames) {
			Extension extension = extensions.get(chanName);
			if (extension != null) {
				ordered.put(extension.item.name, extension);
			}
		}
		extensions.keySet().removeAll(orderedChanNames);
		ordered.putAll(extensions);
		this.extensions = Collections.unmodifiableMap(ordered);
		sortedExtensionNames = null;

		if (updateArchiveMap) {
			Map<String, List<String>> archiveMap = new HashMap<>();
			for (Extension extension : ordered.values()) {
				ChanConfiguration.Archivation archivation = extension.chan != null ?
						extension.chan.configuration.obtainArchivationConfiguration() : null;
				if (archivation != null) {
					for (String host : archivation.hosts) {
						String chanName = getChanNameByHost(host);
						if (chanName != null) {
							List<String> archiveChanNames = archiveMap.get(chanName);
							if (archiveChanNames == null) {
								archiveChanNames = new ArrayList<>();
								archiveMap.put(chanName, archiveChanNames);
							}
							archiveChanNames.add(extension.item.name);
						}
					}
				}
			}
			this.archiveMap = Collections.unmodifiableMap(archiveMap);
		}
	}

	private static class CompatPathClassLoader extends PathClassLoader {
		public CompatPathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
			super(dexPath, librarySearchPath, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if ("chan.text.TemplateParser".equals(name) ||
					name != null && name.startsWith("chan.text.TemplateParser$")) {
				// TemplateParser is moved to the library, which resolution should have higher priority
				try {
					return findClass(name);
				} catch (ClassNotFoundException e) {
					// Extension still uses API class
				}
			}
			return super.loadClass(name, resolve);
		}
	}

	private boolean isExtension(PackageInfo packageInfo, String feature) {
		FeatureInfo[] features = packageInfo.reqFeatures;
		if (features != null) {
			for (FeatureInfo featureInfo : features) {
				if (feature.equals(featureInfo.name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Extension loadExtension(PackageInfo packageInfo,
			boolean chanExtension, boolean libExtension, Fingerprints applicationFingerprints,
			Collection<String> usedExtensionNames, Map<String, Extension> extensions) {
		if (!chanExtension && !libExtension) {
			throw new IllegalArgumentException();
		}
		PackageManager packageManager = MainApplication.getInstance().getPackageManager();
		ApplicationInfo applicationInfo;
		try {
			applicationInfo = packageManager.getApplicationInfo(packageInfo.packageName,
					PackageManager.GET_META_DATA);
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
		long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
		Bundle data = applicationInfo.metaData;
		String name = data.getString(chanExtension ? META_CHAN_EXTENSION_NAME
				: libExtension ? META_LIB_EXTENSION_NAME : null);
		if (name == null || !VALID_EXTENSION_NAME.matcher(name).matches() ||
				(chanExtension ? RESERVED_CHAN_NAMES : RESERVED_EXTENSION_NAMES).contains(name)) {
			Log.persistent().write("Invalid extension name: " + name);
			return null;
		}
		boolean nameConflict;
		if (usedExtensionNames.contains(name)) {
			nameConflict = true;
		} else {
			Extension extension = extensions.get(name);
			nameConflict = extension != null && !extension.item.packageName.equals(packageInfo.packageName);
		}
		if (nameConflict) {
			Log.persistent().write("Extension name conflict: " + name + " already exists");
			return null;
		}
		String title = data.getString(chanExtension ? META_CHAN_EXTENSION_TITLE
				: libExtension ? META_LIB_EXTENSION_TITLE : null);
		if (title == null) {
			title = name;
		}
		Fingerprints fingerprints = extractFingerprints(packageInfo);
		ExtensionItem extensionItem;
		if (chanExtension) {
			int invalidVersion = Integer.MIN_VALUE;
			int apiVersion = data.getInt(META_CHAN_EXTENSION_VERSION, invalidVersion);
			if (apiVersion == invalidVersion) {
				Log.persistent().write("Invalid extension version");
				return null;
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
				return null;
			}
			classConfiguration = extendClassName(classConfiguration, packageInfo.packageName);
			classPerformer = extendClassName(classPerformer, packageInfo.packageName);
			classLocator = extendClassName(classLocator, packageInfo.packageName);
			classMarkup = extendClassName(classMarkup, packageInfo.packageName);
			boolean supported = apiVersion >= MIN_VERSION && apiVersion <= MAX_VERSION;
			extensionItem = new ExtensionItem(name, title, packageInfo.packageName,
					packageInfo.versionName, versionCode, applicationInfo,
					fingerprints, apiVersion, supported, iconResId, updateUri,
					classConfiguration, classPerformer, classLocator, classMarkup);
		} else if (libExtension) {
			String source = data.getString(META_LIB_EXTENSION_SOURCE);
			Uri updateUri = source != null ? Uri.parse(source) : null;
			extensionItem = new ExtensionItem(name, title, packageInfo.packageName,
					packageInfo.versionName, versionCode, applicationInfo, fingerprints, updateUri);
		} else {
			throw new RuntimeException();
		}
		Chan chan = null;
		if (fingerprints.equals(applicationFingerprints) ||
				Preferences.isExtensionTrusted(packageInfo.packageName, fingerprints.toString())) {
			if (extensionItem.type == ExtensionItem.Type.CHAN) {
				chan = loadChan(extensionItem, packageManager);
				extensionItem = extensionItem.changeTrustState(chan != null);
			} else {
				extensionItem = extensionItem.changeTrustState(true);
			}
		}
		return new Extension(extensionItem, chan);
	}

	private static Chan loadChan(ExtensionItem chanItem, PackageManager packageManager) {
		if (chanItem.supported) {
			String chanName = chanItem.name;
			try {
				String nativeLibraryDir = chanItem.applicationInfo.nativeLibraryDir;
				if (nativeLibraryDir != null && !new File(nativeLibraryDir).exists()) {
					nativeLibraryDir = null;
				}
				ClassLoader classLoader = new CompatPathClassLoader(chanItem.applicationInfo.sourceDir,
						nativeLibraryDir, ChanManager.class.getClassLoader());
				Resources resources = packageManager.getResourcesForApplication(chanItem.applicationInfo);
				Chan.Provider chanProvider = new Chan.Provider(null);
				ChanConfiguration configuration = ChanConfiguration.INITIALIZER.initialize(classLoader,
						chanItem.classConfiguration, chanName, chanProvider, resources);
				ChanPerformer performer = ChanPerformer.INITIALIZER.initialize(classLoader,
						chanItem.classPerformer, chanName, chanProvider, resources);
				ChanLocator locator = ChanLocator.INITIALIZER.initialize(classLoader,
						chanItem.classLocator, chanName, chanProvider, resources);
				ChanMarkup markup = ChanMarkup.INITIALIZER.initialize(classLoader,
						chanItem.classMarkup, chanName, chanProvider, resources);
				Drawable icon = C.API_LOLLIPOP && chanItem.iconResId != 0
						? resources.getDrawable(chanItem.iconResId, null) : null;
				Chan chan = new Chan(chanName, chanItem.packageName, configuration, performer, locator, markup, icon);
				chanProvider.set(chan);
				return chan;
			} catch (Exception | LinkageError e) {
				Log.persistent().stack(e);
			}
		}
		return null;
	}

	private void loadLibrary(ExtensionItem libraryItem) {
		switch (libraryItem.name) {
			case EXTENSION_NAME_LIB_WEBM: {
				if (Preferences.isUseVideoPlayer()) {
					VideoPlayer.loadLibraries(MainApplication.getInstance());
				}
				break;
			}
		}
	}

	public void loadLibraries() {
		for (Extension extension : extensions.values()) {
			if (extension.item.type == ExtensionItem.Type.LIBRARY &&
					extension.item.trustState == ExtensionItem.TrustState.TRUSTED) {
				loadLibrary(extension.item);
			}
		}
	}

	public void changeUntrustedExtensionState(String extensionName, boolean trusted) {
		Extension extension = extensions.get(extensionName);
		if (extension == null || extension.item.trustState != ExtensionItem.TrustState.UNTRUSTED) {
			return;
		}
		if (trusted) {
			Preferences.setExtensionTrusted(extension.item.packageName, extension.item.fingerprints.toString());
			if (extension.item.type == ExtensionItem.Type.CHAN) {
				Chan chan = loadChan(extension.item, MainApplication.getInstance().getPackageManager());
				Extension newExtension = new Extension(extension.item.changeTrustState(chan != null), chan);
				updateExtensions(newExtension, null, chan != null);
				if (chan != null) {
					for (Callback callback : observable) {
						callback.onChanInstalled(chan);
					}
				}
			} else if (extension.item.type == ExtensionItem.Type.LIBRARY) {
				Extension newExtension = new Extension(extension.item.changeTrustState(true), null);
				updateExtensions(newExtension, null, false);
				loadLibrary(newExtension.item);
			}
		} else {
			Extension newExtension = new Extension(extension.item.changeTrustState(false), null);
			updateExtensions(newExtension, null, false);
		}
	}

	public static final class Initializer {
		public static class Holder {
			public final String chanName;
			public final Chan.Provider chanProvider;
			public final Resources resources;

			private Holder(String chanName, Chan.Provider chanProvider, Resources resources) {
				this.chanName = chanName;
				this.resources = resources;
				this.chanProvider = chanProvider;
			}
		}

		private Holder holder;

		@SuppressWarnings("unchecked")
		public <T extends Chan.Linked> T initialize(ClassLoader classLoader, String className, String chanName,
				Chan.Provider chanProvider, Resources resources) throws LinkageError, Exception {
			synchronized (this) {
				holder = new Holder(chanName, chanProvider, resources);
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

	private static class ExtensionsIterable<T> implements Iterable<T> {
		public interface FilterMap<T> {
			T filterMap(Extension extension);
		}

		private final Collection<Extension> extensions;
		private final FilterMap<T> filterMap;

		private ExtensionsIterable(Collection<Extension> extensions, FilterMap<T> filterMap) {
			this.extensions = extensions;
			this.filterMap = filterMap;
		}

		@NonNull
		@Override
		public Iterator<T> iterator() {
			Iterator<Extension> iterator = extensions.iterator();
			return new Iterator<T>() {
				private T next;

				private boolean findNext() {
					while (iterator.hasNext()) {
						T next = filterMap.filterMap(iterator.next());
						if (next != null) {
							this.next = next;
							return true;
						}
					}
					return false;
				}

				@Override
				public boolean hasNext() {
					return next != null || findNext();
				}

				@Override
				public T next() {
					if (next == null && !findNext()) {
						throw new NoSuchElementException();
					}
					T next = this.next;
					this.next = null;
					return next;
				}
			};
		}
	}

	private static final ExtensionsIterable.FilterMap<ExtensionItem> FILTER_MAP_EXTENSION_ITEMS
			= extension -> extension.item;
	private static final ExtensionsIterable.FilterMap<Chan> FILTER_MAP_AVAILABLE_CHAN_NAMES
			= extension -> extension.chan != null ? extension.chan : null;

	public Iterable<ExtensionItem> getExtensionItems() {
		return new ExtensionsIterable<>(extensions.values(), FILTER_MAP_EXTENSION_ITEMS);
	}

	public ExtensionItem getFirstUntrustedExtension() {
		for (Extension extension : extensions.values()) {
			if (extension.item.trustState == ExtensionItem.TrustState.UNTRUSTED) {
				return extension.item;
			}
		}
		return null;
	}

	public ExtensionItem getLibraryExtension(String libraryName) {
		Extension extension = extensions.get(libraryName);
		return extension != null && extension.item.type == ExtensionItem.Type.LIBRARY ? extension.item : null;
	}

	public List<String> getArchiveChanNames(String chanName) {
		List<String> list = archiveMap.get(chanName);
		return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
	}

	public boolean isExistingChanName(String chanName) {
		Extension extension = extensions.get(chanName);
		return extension != null && extension.item.type == ExtensionItem.Type.CHAN;
	}

	public Iterable<Chan> getAvailableChans() {
		return new ExtensionsIterable<>(extensions.values(), FILTER_MAP_AVAILABLE_CHAN_NAMES);
	}

	public boolean hasMultipleAvailableChans() {
		int count = 0;
		for (Extension extension : extensions.values()) {
			if (extension.chan != null && ++count >= 2) {
				return true;
			}
		}
		return false;
	}

	public int compareChanNames(String lhs, String rhs) {
		if (sortedExtensionNames == null) {
			sortedExtensionNames = Collections.unmodifiableList(new ArrayList<>(extensions.keySet()));
		}
		return sortedExtensionNames.indexOf(lhs) - sortedExtensionNames.indexOf(rhs);
	}

	public Chan getDefaultChan() {
		for (Extension extension : extensions.values()) {
			if (extension.chan != null) {
				return extension.chan;
			}
		}
		return null;
	}

	public void setChansOrder(List<String> chanNames) {
		Preferences.setChansOrder(chanNames);
		updateExtensions(null, null, true);
	}

	String getChanNameByHost(String host) {
		if (host != null) {
			for (Extension extension : extensions.values()) {
				if (extension.chan != null && extension.chan.locator.isChanHost(host)) {
					return extension.item.name;
				}
			}
		}
		return null;
	}

	public ChanIconDrawable getIcon(Chan chan) {
		if (chan != null && C.API_LOLLIPOP) {
			Drawable drawable = chan.icon;
			if (drawable == null) {
				drawable = MainApplication.getInstance().getDrawable(R.drawable.ic_extension);
			}
			return new ChanIconDrawable(drawable.getConstantState().newDrawable().mutate());
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public void updateConfiguration(Configuration newConfig, DisplayMetrics metrics) {
		for (Extension extension : extensions.values()) {
			if (extension.chan != null) {
				extension.chan.configuration.getResources().updateConfiguration(newConfig, metrics);
			}
		}
	}

	public boolean isExtensionPackage(String packageName) {
		for (Extension extension : extensions.values()) {
			if (packageName.equals(extension.item.packageName)) {
				return true;
			}
		}
		return false;
	}

	private static Fingerprints extractFingerprints(PackageInfo packageInfo) {
		HashSet<String> fingerprints = new HashSet<>();
		List<android.content.pm.Signature> signatures;
		if (C.API_PIE) {
			android.content.pm.Signature[] signaturesArray = packageInfo.signingInfo != null
					? packageInfo.signingInfo.getApkContentsSigners() : null;
			signatures = signaturesArray != null ? Arrays.asList(signaturesArray) : Collections.emptyList();
		} else {
			@SuppressWarnings("deprecation")
			android.content.pm.Signature[] signaturesArray = packageInfo.signatures;
			signatures = signaturesArray != null ? Arrays.asList(signaturesArray) : Collections.emptyList();
		}
		for (android.content.pm.Signature signature : signatures) {
			if (signature != null) {
				fingerprints.add(StringUtils.formatHex(Hasher
						.getInstanceSha256().calculate(signature.toByteArray())));
			}
		}
		return new Fingerprints(Collections.unmodifiableSet(fingerprints));
	}

	public Fingerprints getApplicationFingerprints() {
		return applicationFingerprints;
	}

	Chan getFallbackChan() {
		return fallbackChan;
	}

	Chan getChan(String chanName) {
		Extension extension = extensions.get(chanName);
		Chan chan = extension != null ? extension.chan : null;
		return chan != null ? chan : fallbackChan;
	}

	private boolean restartRequired = false;

	public boolean isRestartRequired() {
		return restartRequired;
	}

	public final WeakObservable<Callback> observable = new WeakObservable<>();
}
