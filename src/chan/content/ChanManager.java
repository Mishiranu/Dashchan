package chan.content;

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

	private final ChanHolder defaultChanHolder;
	private final Fingerprints applicationFingerprints;
	private Map<String, Extension> extensions;
	private List<String> sortedExtensionNames;
	private Map<String, List<String>> archiveMap = Collections.emptyMap();

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
		public final String extensionName;
		public final TrustState trustState;
		public final String packageName;
		public final String versionName;
		public final long versionCode;
		private final ApplicationInfo applicationInfo;
		public final Fingerprints fingerprints;
		public final int version;
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

		private ExtensionItem(Type type, String extensionName, TrustState trustState,
				String packageName, String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, int version, boolean supported, int iconResId, Uri updateUri,
				String classConfiguration, String classPerformer, String classLocator, String classMarkup) {
			this.type = type;
			this.extensionName = extensionName;
			this.trustState = trustState;
			this.packageName = packageName;
			this.versionName = versionName;
			this.versionCode = versionCode;
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

		public ExtensionItem(String chanName, String packageName,
				String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, int version, boolean supported, int iconResId, Uri updateUri,
				String classConfiguration, String classPerformer, String classLocator, String classMarkup) {
			this(Type.CHAN, chanName, TrustState.UNTRUSTED, packageName, versionName, versionCode, applicationInfo,
					fingerprints, version, supported, iconResId, updateUri,
					classConfiguration, classPerformer, classLocator, classMarkup);
		}

		public ExtensionItem(String libName, String packageName,
				String versionName, long versionCode, ApplicationInfo applicationInfo,
				Fingerprints fingerprints, Uri updateUri) {
			this(Type.LIBRARY, libName, TrustState.UNTRUSTED, packageName, versionName, versionCode, applicationInfo,
					fingerprints, 0, true, 0, updateUri, null, null, null, null);
		}

		public ExtensionItem changeTrustState(boolean trusted) {
			if (this.trustState != TrustState.UNTRUSTED) {
				throw new IllegalStateException();
			}
			TrustState trustState = trusted ? TrustState.TRUSTED : TrustState.DISCARDED;
			return new ExtensionItem(type, extensionName, trustState,
					packageName, versionName, versionCode, applicationInfo,
					fingerprints, version, supported, iconResId, updateUri,
					classConfiguration, classPerformer, classLocator, classMarkup);
		}
	}

	public interface Callback {
		void onExtensionInstalled();
		void onExtensionsChanged();
	}

	private static class Extension {
		public final ExtensionItem item;
		public final ChanHolder chanHolder;

		private Extension(ExtensionItem item, ChanHolder chanHolder) {
			this.item = item;
			this.chanHolder = chanHolder;
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
			map.put(extension.item.extensionName, extension);
		}
		return Collections.unmodifiableMap(map);
	}

	@SuppressLint("PackageManagerGetSignatures")
	private ChanManager() {
		ArrayList<String> reservedExtensionNames = new ArrayList<>();
		reservedExtensionNames.add(EXTENSION_NAME_CLIENT);
		reservedExtensionNames.add(EXTENSION_NAME_META);
		Collections.addAll(reservedExtensionNames, Preferences.SPECIAL_EXTENSION_NAMES);
		ArrayList<String> reservedChanNames = new ArrayList<>(reservedExtensionNames);
		reservedChanNames.add(EXTENSION_NAME_LIB_WEBM);
		defaultChanHolder = new ChanHolder(new ChanConfiguration(false), new ChanPerformer(false),
				new ChanLocator(false), null, null);
		PackageManager packageManager = MainApplication.getInstance().getPackageManager();
		List<PackageInfo> packages;
		if (MainApplication.getInstance().isMainProcess()) {
			// TODO Handle deprecation
			packages = packageManager.getInstalledPackages(PackageManager.GET_CONFIGURATIONS
					| PackageManager.GET_SIGNATURES);
		} else {
			packages = Collections.emptyList();
		}

		ArrayList<Extension> extensions = new ArrayList<>();
		HashSet<String> usedExtensionNames = new HashSet<>();
		try {
			// TODO Handle deprecation
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
						long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
						Bundle data = applicationInfo.metaData;
						String extensionName = data.getString(chanExtension ? META_CHAN_EXTENSION_NAME
								: libExtension ? META_LIB_EXTENSION_NAME : null);
						if (extensionName == null || !VALID_EXTENSION_NAME.matcher(extensionName).matches() ||
								(chanExtension ? reservedChanNames : reservedExtensionNames).contains(extensionName)) {
							Log.persistent().write("Invalid extension name: " + extensionName);
							break;
						}
						if (usedExtensionNames.contains(extensionName)) {
							Log.persistent().write("Extension names conflict: " + extensionName + " already exists");
							break;
						}
						Fingerprints fingerprints = extractFingerprints(packageInfo);
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
							extensionItem = new ExtensionItem(extensionName, packageInfo.packageName,
									packageInfo.versionName, versionCode, applicationInfo,
									fingerprints, version, supported, iconResId, updateUri,
									classConfiguration, classPerformer, classLocator, classMarkup);
						} else if (libExtension) {
							String source = data.getString(META_LIB_EXTENSION_SOURCE);
							Uri updateUri = source != null ? Uri.parse(source) : null;
							extensionItem = new ExtensionItem(extensionName, packageInfo.packageName,
									packageInfo.versionName, versionCode, applicationInfo,
									fingerprints, updateUri);
						} else {
							throw new RuntimeException();
						}
						ChanHolder chanHolder = null;
						if (fingerprints.equals(applicationFingerprints) ||
								Preferences.isExtensionTrusted(packageInfo.packageName, fingerprints.toString())) {
							if (extensionItem.type == ExtensionItem.Type.CHAN) {
								chanHolder = loadChan(extensionItem, MainApplication.getInstance().getPackageManager());
								if (chanHolder != null) {
									extensionItem = extensionItem.changeTrustState(true);
								} else {
									extensionItem = extensionItem.changeTrustState(false);
								}
							} else {
								extensionItem = extensionItem.changeTrustState(true);
							}
						}
						extensions.add(new Extension(extensionItem, chanHolder));
						usedExtensionNames.add(extensionName);
						break;
					}
				}
			}
		}

		this.extensions = extensionsMap(extensions);
		updateExtensions(null);
		updateArchiveMap();

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
							for (Callback callback : observable) {
								callback.onExtensionInstalled();
							}
							break;
						}
					}
				}
			} else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
				for (Extension extension : extensions) {
					if (packageName.equals(extension.item.packageName)) {
						newExtensionsInstalled = true;
						for (Callback callback : observable) {
							callback.onExtensionInstalled();
						}
						break;
					}
				}
			}
		}), filter);
	}

	private void updateExtensions(Extension newExtension) {
		List<String> orderedChanNames = Preferences.getChansOrder();
		if (orderedChanNames == null) {
			orderedChanNames = Collections.emptyList();
		}
		LinkedHashMap<String, Extension> extensions = new LinkedHashMap<>(this.extensions);
		if (newExtension != null) {
			extensions.put(newExtension.item.extensionName, newExtension);
		}
		LinkedHashMap<String, Extension> ordered = new LinkedHashMap<>();
		for (String chanName : orderedChanNames) {
			Extension extension = extensions.get(chanName);
			if (extension != null) {
				ordered.put(extension.item.extensionName, extension);
			}
		}
		extensions.keySet().removeAll(orderedChanNames);
		ordered.putAll(extensions);
		this.extensions = Collections.unmodifiableMap(ordered);
		sortedExtensionNames = null;
	}

	private static class LegacyPathClassLoader extends PathClassLoader {
		public LegacyPathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
			super(dexPath, librarySearchPath, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if ("chan.text.TemplateParser".equals(name) ||
					name != null && name.startsWith("chan.text.TemplateParser$")) {
				// TemplateParser is moved to the library, workaround is required for 4.4 or lower
				try {
					return findClass(name);
				} catch (ClassNotFoundException e) {
					// Extension still uses API class
				}
			}
			return super.loadClass(name, resolve);
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
				ClassLoader classLoader;
				if (C.API_LOLLIPOP) {
					// Don't use LegacyPathClassLoader on Lollipop,
					// overriding loadClass method leads to incomprehensible failures
					classLoader = new PathClassLoader(chanItem.applicationInfo.sourceDir, nativeLibraryDir,
							ChanManager.class.getClassLoader());
				} else {
					classLoader = new LegacyPathClassLoader(chanItem.applicationInfo.sourceDir, nativeLibraryDir,
							ChanManager.class.getClassLoader());
				}
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
			if (extension.item.type == ExtensionItem.Type.LIBRARY) {
				Extension newExtension = new Extension(extension.item.changeTrustState(true), null);
				updateExtensions(newExtension);
				loadLibrary(newExtension.item);
			} else {
				ChanHolder chanHolder = loadChan(extension.item, MainApplication.getInstance().getPackageManager());
				Extension newExtension;
				if (chanHolder != null) {
					newExtension = new Extension(extension.item.changeTrustState(true), chanHolder);
				} else {
					newExtension = new Extension(extension.item.changeTrustState(false), null);
				}
				updateExtensions(newExtension);
				updateArchiveMap();
				for (Callback callback : observable) {
					callback.onExtensionsChanged();
				}
			}
		} else {
			Extension newExtension = new Extension(extension.item.changeTrustState(false), null);
			updateExtensions(newExtension);
		}
	}

	private void updateArchiveMap() {
		Map<String, List<String>> archiveMap = new HashMap<>();
		for (Extension extension : extensions.values()) {
			ChanConfiguration.Archivation archivation = extension.chanHolder != null ?
					extension.chanHolder.configuration.obtainArchivationConfiguration() : null;
			if (archivation != null) {
				for (String host : archivation.hosts) {
					String chanName = getChanNameByHost(host);
					if (chanName != null) {
						List<String> archiveChanNames = archiveMap.get(chanName);
						if (archiveChanNames == null) {
							archiveChanNames = new ArrayList<>();
							archiveMap.put(chanName, archiveChanNames);
						}
						archiveChanNames.add(extension.item.extensionName);
					}
				}
			}
		}
		this.archiveMap = Collections.unmodifiableMap(archiveMap);
	}

	public interface Linked {
		String getChanName();
		void init();
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
		Extension extension = extensions.get(chanName);
		ChanHolder chanHolder = extension != null ? extension.chanHolder : null;
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
	private static final ExtensionsIterable.FilterMap<String> FILTER_MAP_AVAILABLE_CHAN_NAMES
			= extension -> extension.chanHolder != null ? extension.item.extensionName : null;

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

	public ExtensionItem getLibExtension(String libName) {
		Extension extension = extensions.get(libName);
		return extension != null && extension.item.type == ExtensionItem.Type.LIBRARY ? extension.item : null;
	}

	public List<String> getArchiveChanNames(String chanName) {
		List<String> list = archiveMap.get(chanName);
		return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
	}

	public boolean canBeArchived(String chanName) {
		return archiveMap.containsKey(chanName) || !ChanConfiguration.get(chanName)
				.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
	}

	public boolean isExistingChanName(String chanName) {
		Extension extension = extensions.get(chanName);
		return extension != null && extension.item.type == ExtensionItem.Type.CHAN;
	}

	public boolean isAvailableChanName(String chanName) {
		Extension extension = extensions.get(chanName);
		return extension != null && extension.chanHolder != null;
	}

	public Iterable<String> getAvailableChanNames() {
		return new ExtensionsIterable<>(extensions.values(), FILTER_MAP_AVAILABLE_CHAN_NAMES);
	}

	public boolean hasMultipleAvailableChans() {
		int count = 0;
		for (Extension extension : extensions.values()) {
			if (extension.chanHolder != null && ++count >= 2) {
				return true;
			}
		}
		return false;
	}

	public String getExtensionPackageName(String extensionName) {
		Extension extension = extensions.get(extensionName);
		return extension != null ? extension.item.packageName : null;
	}

	public int compareChanNames(String lhs, String rhs) {
		if (sortedExtensionNames == null) {
			sortedExtensionNames = Collections.unmodifiableList(new ArrayList<>(extensions.keySet()));
		}
		return sortedExtensionNames.indexOf(lhs) - sortedExtensionNames.indexOf(rhs);
	}

	public String getDefaultChanName() {
		for (Extension extension : extensions.values()) {
			if (extension.chanHolder != null) {
				return extension.item.extensionName;
			}
		}
		return null;
	}

	public void setChansOrder(List<String> chanNames) {
		Preferences.setChansOrder(chanNames);
		updateExtensions(null);
	}

	public String getChanNameByHost(String host) {
		if (host != null) {
			for (Extension extension : extensions.values()) {
				if (extension.chanHolder != null && extension.chanHolder.locator.isChanHost(host)) {
					return extension.item.extensionName;
				}
			}
		}
		return null;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public ChanIconDrawable getIcon(String chanName) {
		if (C.API_LOLLIPOP) {
			Extension extension = extensions.get(chanName);
			Drawable drawable = extension != null && extension.chanHolder != null ? extension.chanHolder.icon : null;
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
			if (extension.chanHolder != null) {
				extension.chanHolder.configuration.getResources().updateConfiguration(newConfig, metrics);
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

	public static Fingerprints extractFingerprints(PackageInfo packageInfo) {
		HashSet<String> fingerprints = new HashSet<>();
		// TODO Handle deprecation
		android.content.pm.Signature[] signaturesArray = packageInfo.signatures;
		if (signaturesArray != null) {
			for (android.content.pm.Signature signature : signaturesArray) {
				if (signature != null) {
					fingerprints.add(StringUtils.formatHex(Hasher
							.getInstanceSha256().calculate(signature.toByteArray())));
				}
			}
		}
		return new Fingerprints(Collections.unmodifiableSet(fingerprints));
	}

	public Fingerprints getApplicationFingerprints() {
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

	public final WeakObservable<Callback> observable = new WeakObservable<>();
}
