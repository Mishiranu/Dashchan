package com.mishiranu.dashchan.content.async;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import androidx.core.content.pm.PackageInfoCompat;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReadUpdateTask extends HttpHolderTask<Void, Pair<ErrorItem, ReadUpdateTask.UpdateDataMap>> {
	private final Context context;
	private final Callback callback;

	public static class UpdateDataMap implements Parcelable {
		private final Map<String, ApplicationItem> update;
		private final Map<String, ApplicationItem> install;

		private UpdateDataMap(Map<String, ApplicationItem> update, Map<String, ApplicationItem> install) {
			this.update = update;
			this.install = install;
		}

		public ApplicationItem get(String extensionName, boolean installed) {
			return (installed ? update : install).get(extensionName);
		}

		public Collection<String> extensionNames(boolean installed) {
			return (installed ? update: install).keySet();
		}

		private static <T extends Parcelable> void writeMap(Parcel dest, int flags, Map<String, T> map) {
			dest.writeInt(map.size());
			for (Map.Entry<String, T> entry : map.entrySet()) {
				dest.writeString(entry.getKey());
				entry.getValue().writeToParcel(dest, flags);
			}
		}

		private static <T extends Parcelable> Map<String, T> readMap(Parcel source, Parcelable.Creator<T> creator) {
			int count = source.readInt();
			Map<String, T> map = new HashMap<>();
			for (int i = 0; i < count; i++) {
				String key = source.readString();
				T value = creator.createFromParcel(source);
				map.put(key, value);
			}
			return map;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			writeMap(dest, flags, update);
			writeMap(dest, flags, install);
		}

		public static final Creator<UpdateDataMap> CREATOR = new Creator<UpdateDataMap>() {
			@Override
			public UpdateDataMap createFromParcel(Parcel source) {
				Map<String, ApplicationItem> update = readMap(source, ApplicationItem.CREATOR);
				Map<String, ApplicationItem> install = readMap(source, ApplicationItem.CREATOR);
				return new UpdateDataMap(update, install);
			}

			@Override
			public UpdateDataMap[] newArray(int size) {
				return new UpdateDataMap[size];
			}
		};
	}

	public static class ApplicationItem implements Parcelable {
		public enum Type {CLIENT, LIBRARY, CHAN}

		public final Type type;
		public final String name;
		public final String title;
		public final List<PackageItem> packageItems;

		public ApplicationItem(Type type, String name, String title, List<PackageItem> packageItems) {
			this.name = name;
			this.type = type;
			this.title = title;
			this.packageItems = packageItems;
		}

		private static ApplicationItem fromJsonV1(JSONObject jsonObject) throws JSONException {
			String name = jsonObject.getString("name");
			String typeString = jsonObject.getString("type");
			String title = jsonObject.getString("title");
			Type type;
			switch (typeString) {
				case "client": {
					type = Type.CLIENT;
					break;
				}
				case "chan": {
					type = Type.CHAN;
					break;
				}
				case "library": {
					type = Type.LIBRARY;
					break;
				}
				default: {
					throw new JSONException("Invalid type");
				}
			}
			return new ApplicationItem(type, name, title, Collections.emptyList());
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(type.name());
			dest.writeString(name);
			dest.writeString(title);
			dest.writeTypedList(packageItems);
		}

		public static final Creator<ApplicationItem> CREATOR = new Creator<ApplicationItem>() {
			@Override
			public ApplicationItem createFromParcel(Parcel source) {
				Type type = Type.valueOf(source.readString());
				String name = source.readString();
				String title = source.readString();
				List<PackageItem> packageItems = source.createTypedArrayList(PackageItem.CREATOR);
				return new ApplicationItem(type, name, title, packageItems);
			}

			@Override
			public ApplicationItem[] newArray(int size) {
				return new ApplicationItem[size];
			}
		};
	}

	public static class PackageItem implements Parcelable {
		public final String repository;
		public final String title;
		public final String versionName;
		public final long versionCode;
		public final int minApiVersion;
		public final int maxApiVersion;
		public final int apiVersion;
		public final long length;
		public final Uri source;
		public final byte[] sha256sum;

		public PackageItem(String repository, String title, String versionName, long versionCode,
				int minApiVersion, int maxApiVersion, int apiVersion, long length, Uri source, byte[] sha256sum) {
			this.repository = repository;
			this.title = title;
			this.versionName = versionName;
			this.versionCode = versionCode;
			this.minApiVersion = minApiVersion;
			this.maxApiVersion = maxApiVersion;
			this.apiVersion = apiVersion;
			this.length = length;
			this.source = source;
			this.sha256sum = sha256sum;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(repository);
			dest.writeString(title);
			dest.writeString(versionName);
			dest.writeLong(versionCode);
			dest.writeInt(minApiVersion);
			dest.writeInt(maxApiVersion);
			dest.writeInt(apiVersion);
			dest.writeLong(length);
			dest.writeString(source != null ? source.toString() : null);
			dest.writeByteArray(sha256sum);
		}

		public static final Creator<PackageItem> CREATOR = new Creator<PackageItem>() {
			@Override
			public PackageItem createFromParcel(Parcel source) {
				String repository = source.readString();
				String title = source.readString();
				String versionName = source.readString();
				long versionCode = source.readLong();
				int minVersion = source.readInt();
				int maxVersion = source.readInt();
				int version = source.readInt();
				long length = source.readLong();
				String sourceString = source.readString();
				Uri sourceUri = sourceString != null ? Uri.parse(sourceString) : null;
				byte[] sha256sum = source.createByteArray();
				return new PackageItem(repository, title, versionName, versionCode,
						minVersion, maxVersion, version, length, sourceUri, sha256sum);
			}

			@Override
			public PackageItem[] newArray(int size) {
				return new PackageItem[size];
			}
		};
	}

	public interface Callback {
		void onReadUpdateComplete(UpdateDataMap updateDataMap, ErrorItem errorItem);
	}

	public ReadUpdateTask(Context context, Callback callback) {
		super(Chan.getFallback());
		this.context = context.getApplicationContext();
		this.callback = callback;
	}

	public static Uri normalizeRelativeUri(Uri base, String uriOrPath) {
		Uri uri = Uri.parse(uriOrPath);
		boolean noScheme = StringUtils.isEmpty(uri.getScheme());
		boolean noHost = StringUtils.isEmpty(uri.getHost());
		boolean noPath = StringUtils.isEmpty(uri.getPath());
		if (noScheme || noHost || noPath) {
			Uri.Builder builder = uri.buildUpon();
			if (noScheme) {
				builder.scheme(base.getScheme());
			}
			if (noHost) {
				builder.authority(base.getHost());
			}
			if (noPath) {
				builder.path(base.getPath());
			} else if (noScheme && noHost && !uriOrPath.startsWith("/")) {
				String path = uri.getPath();
				String basePath = base.getPath();
				int index = basePath.lastIndexOf('/');
				basePath = index >= 0 ? basePath.substring(0, index + 1) : "/";
				builder.path(basePath + path);
			}
			uri = builder.build();
		}
		return uri;
	}

	private static PackageItem extractPackageItem(DataVersion dataVersion, JSONObject chanObject,
			String extensionName, String repository, Uri uri, Long installedCode,
			ChanManager.Fingerprints fingerprints) throws JSONException {
		String title;
		String versionName;
		long versionCode;
		int minSdk;
		int maxSdk;
		int minApiVersion;
		int maxApiVersion;
		int apiVersion;
		long length;
		String source;
		JSONArray fingerprintsArray;
		String fingerprint;
		String sha256sumString;
		boolean requireFingerprintChecksum;
		switch (dataVersion) {
			case LEGACY: {
				title = CommonUtils.getJsonString(chanObject, "title");
				versionName = CommonUtils.getJsonString(chanObject, "name");
				versionCode = chanObject.getInt("code");
				minApiVersion = chanObject.optInt("minVersion");
				maxApiVersion = chanObject.optInt("maxVersion");
				apiVersion = chanObject.optInt("version");
				minSdk = chanObject.optInt("minSdk");
				maxSdk = chanObject.optInt("maxSdk");
				length = chanObject.getLong("length");
				source = CommonUtils.getJsonString(chanObject, "source");
				fingerprintsArray = chanObject.optJSONArray("fingerprints");
				fingerprint = CommonUtils.optJsonString(chanObject, "fingerprint");
				sha256sumString = null;
				requireFingerprintChecksum = false;
				break;
			}
			case V1: {
				title = CommonUtils.getJsonString(chanObject, "title");
				versionName = CommonUtils.getJsonString(chanObject, "version_name");
				versionCode = chanObject.getInt("version_code");
				minApiVersion = chanObject.optInt("min_api_version");
				maxApiVersion = chanObject.optInt("max_api_version");
				apiVersion = chanObject.optInt("api_version");
				minSdk = chanObject.optInt("min_sdk");
				maxSdk = chanObject.optInt("max_sdk");
				length = chanObject.getLong("length");
				source = CommonUtils.getJsonString(chanObject, "source");
				fingerprintsArray = chanObject.optJSONArray("fingerprints");
				fingerprint = CommonUtils.optJsonString(chanObject, "fingerprint");
				sha256sumString = CommonUtils.getJsonString(chanObject, "sha256sum");
				requireFingerprintChecksum = true;
				break;
			}
			default: {
				throw new IllegalStateException();
			}
		}
		if (minSdk > 0 && minSdk > Build.VERSION.SDK_INT ||
				maxSdk > 0 && maxSdk < Build.VERSION.SDK_INT) {
			return null;
		}
		ArrayList<String> rawFingerprints = new ArrayList<>();
		if (fingerprintsArray != null) {
			for (int j = 0; j < fingerprintsArray.length(); j++) {
				rawFingerprints.add(fingerprintsArray.optString(j));
			}
		} else {
			rawFingerprints.add(fingerprint);
		}
		if (requireFingerprintChecksum && rawFingerprints.isEmpty()) {
			return null;
		}
		if (fingerprints != null) {
			HashSet<String> fingerprintsSet = new HashSet<>();
			for (String rawFingerprint : rawFingerprints) {
				if (!StringUtils.isEmpty(rawFingerprint)) {
					rawFingerprint = rawFingerprint.replaceAll("[^a-fA-F0-9]", "")
							.toLowerCase(Locale.US);
					if (rawFingerprint.length() == 64) {
						fingerprintsSet.add(rawFingerprint);
					}
				}
			}
			ChanManager.Fingerprints chanFingerprints = new ChanManager
					.Fingerprints(fingerprintsSet);
			if (!chanFingerprints.equals(fingerprints)) {
				return null;
			}
		}
		if (sha256sumString != null) {
			sha256sumString = sha256sumString.replaceAll("[^a-fA-F0-9]", "").toLowerCase(Locale.US);
		}
		if (installedCode != null && versionCode < installedCode || source == null) {
			return null;
		}
		byte[] sha256sum = null;
		if (sha256sumString != null && sha256sumString.length() == 64) {
			sha256sum = new byte[sha256sumString.length() / 2];
			for (int i = 0; i < sha256sum.length; i++) {
				int h = sha256sumString.charAt(2 * i);
				int l = sha256sumString.charAt(2 * i + 1);
				h = h >= 'a' ? h - 'a' + 10 : h - '0';
				l = l >= 'a' ? l - 'a' + 10 : l - '0';
				sha256sum[i] = (byte) ((h << 4) | l);
			}
		} else if (requireFingerprintChecksum) {
			return null;
		}
		Uri sourceUri = normalizeRelativeUri(uri, source);
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(extensionName)) {
			if (minApiVersion <= 0 || maxApiVersion <= 0) {
				return null;
			}
			return new PackageItem(repository, title, versionName, versionCode,
					minApiVersion, maxApiVersion, 0, length, sourceUri, sha256sum);
		} else {
			return new PackageItem(repository, title, versionName, versionCode,
					0, 0, apiVersion, length, sourceUri, sha256sum);
		}
	}

	private static void handleUpdateItems(Response response, String extensionName,
			HashMap<String, ApplicationItem> updateDataMap, HashMap<String, ChanManager.Fingerprints> fingerprintsMap,
			JSONArray packagesArray, DataVersion dataVersion,
			ApplicationItem updateApplicationItem) throws JSONException {
		ApplicationItem applicationItem = updateDataMap.get(extensionName);
		if (updateApplicationItem == null || applicationItem.type == updateApplicationItem.type) {
			long installedCode = applicationItem.packageItems.get(0).versionCode;
			ChanManager.Fingerprints fingerprints = fingerprintsMap.get(extensionName);
			for (int i = 0; i < packagesArray.length(); i++) {
				PackageItem packageItem = extractPackageItem(dataVersion, packagesArray.getJSONObject(i),
						extensionName, response.getRepositoryName(), response.uri, installedCode, fingerprints);
				if (packageItem != null) {
					applicationItem.packageItems.add(packageItem);
				}
			}
		}
	}

	private static void handleInstallItems(Response response, String extensionName,
			HashMap<String, ApplicationItem> installDataMap, JSONArray packagesArray, DataVersion dataVersion,
			ApplicationItem installApplicationItem) throws JSONException {
		for (int i = 0; i < packagesArray.length(); i++) {
			PackageItem packageItem = extractPackageItem(dataVersion, packagesArray.getJSONObject(i),
					extensionName, response.getRepositoryName(), response.uri, null, null);
			if (packageItem != null) {
				ApplicationItem applicationItem = installDataMap.get(extensionName);
				if (applicationItem == null) {
					if (installApplicationItem != null) {
						applicationItem = new ApplicationItem(installApplicationItem.type, installApplicationItem.name,
								installApplicationItem.title, new ArrayList<>());
					} else {
						applicationItem = new ApplicationItem(ApplicationItem.Type.CHAN, extensionName,
								extensionName, new ArrayList<>());
					}
					installDataMap.put(extensionName, applicationItem);
				} else if (installApplicationItem != null && applicationItem.type != installApplicationItem.type) {
					continue;
				}
				applicationItem.packageItems.add(packageItem);
			}
		}
	}

	private enum DataVersion {
		V1("data-v1.json"),
		LEGACY("data.json");

		public final String fileName;

		DataVersion(String fileName) {
			this.fileName = fileName;
		}
	}

	private static class TargetUri {
		public final Uri uri;
		public final boolean directory;

		public TargetUri(Uri uri) {
			Uri.Builder builder = uri.buildUpon();
			builder.scheme("");
			String name = uri.getLastPathSegment();
			boolean directory = false;
			for (DataVersion dataVersion : DataVersion.values()) {
				if (name.equals(dataVersion.fileName)) {
					directory = true;
					break;
				}
			}
			if (directory) {
				String path = uri.getPath();
				builder.path(path.substring(0, path.lastIndexOf('/')));
			}
			this.uri = builder.build();
			this.directory = directory;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof TargetUri) {
				TargetUri targetUri = (TargetUri) o;
				return uri.equals(targetUri.uri) && directory == targetUri.directory;
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + uri.hashCode();
			result = prime * result + (directory ? 1231 : 1237);
			return result;
		}
	}

	private static class Response {
		public Uri uri;
		public final DataVersion dataVersion;
		public final JSONObject jsonObject;
		public final HashSet<String> extensionNames;

		public Response(Uri uri, DataVersion dataVersion, JSONObject jsonObject, HashSet<String> extensionNames) {
			this.uri = uri;
			this.dataVersion = dataVersion;
			this.jsonObject = jsonObject;
			this.extensionNames = extensionNames;
		}

		public String getRepositoryName() {
			String repository = null;
			switch (dataVersion) {
				case LEGACY: {
					JSONObject metaObject = jsonObject.optJSONObject(ChanManager.EXTENSION_NAME_META);
					if (metaObject != null) {
						repository = metaObject.optString("repository");
					}
					break;
				}
				case V1: {
					repository = jsonObject.optString("title");
					break;
				}
				default: {
					throw new IllegalStateException();
				}
			}
			if (StringUtils.isEmpty(repository)) {
				repository = "Unknown repository";
			}
			return repository;
		}
	}

	private static Iterable<Response> readData(HttpHolder holder, Iterable<ChanManager.ExtensionItem> extensionItems) {
		Chan chan = Chan.getFallback();
		LinkedHashMap<TargetUri, HashSet<String>> targets = new LinkedHashMap<>();
		HashMap<TargetUri, String> requestedScheme = new HashMap<>();
		{
			Uri uri = Uri.parse(BuildConfig.URI_UPDATES);
			TargetUri targetUri = new TargetUri(uri);
			HashSet<String> extensionNames = new HashSet<>();
			extensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
			targets.put(targetUri, extensionNames);
			String scheme = uri.getScheme();
			if (!StringUtils.isEmpty(scheme)) {
				requestedScheme.put(targetUri, scheme);
			}
		}
		for (ChanManager.ExtensionItem extensionItem : extensionItems) {
			if (extensionItem.updateUri != null) {
				TargetUri targetUri = new TargetUri(extensionItem.updateUri);
				HashSet<String> extensionNames = targets.get(targetUri);
				if (extensionNames == null) {
					extensionNames = new HashSet<>();
					targets.put(targetUri, extensionNames);
				}
				extensionNames.add(extensionItem.name);
				String scheme = extensionItem.updateUri.getScheme();
				if (!StringUtils.isEmpty(scheme)) {
					requestedScheme.put(targetUri, scheme);
				}
			}
		}

		LinkedHashMap<TargetUri, Response> responses = new LinkedHashMap<>();
		for (Map.Entry<TargetUri, HashSet<String>> entry : targets.entrySet()) {
			try {
				TargetUri targetUri = entry.getKey();
				String targetScheme = requestedScheme.get(targetUri);
				int redirects = 0;
				while (redirects++ < 5) {
					Response response = responses.get(targetUri);
					if (response != null) {
						if ("http".equals(response.uri.getScheme()) && "https".equals(targetScheme)) {
							response.uri = response.uri.buildUpon().scheme("https").build();
						}
						response.extensionNames.addAll(entry.getValue());
						break;
					}
					Uri responseUri = null;
					String responseText = null;
					DataVersion responseDataVersion = null;
					if (targetUri.directory) {
						HttpException lastHttpException = null;
						Uri directoryUri = chan.locator.setSchemeIfEmpty(targetUri.uri, targetScheme);
						for (DataVersion dataVersion : DataVersion.values()) {
							Uri uri = directoryUri.buildUpon().appendPath(dataVersion.fileName).build();
							try {
								responseUri = uri;
								responseText = new HttpRequest(uri, holder).perform().readString();
								responseDataVersion = dataVersion;
								lastHttpException = null;
								break;
							} catch (HttpException e) {
								if (!e.isHttpException() || e.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
									throw e;
								} else {
									lastHttpException = e;
								}
							}
						}
						if (lastHttpException != null) {
							throw lastHttpException;
						}
					} else {
						Uri uri = chan.locator.setSchemeIfEmpty(targetUri.uri, targetScheme);
						responseUri = uri;
						responseText = new HttpRequest(uri, holder).perform().readString();
						responseDataVersion = DataVersion.LEGACY;
					}
					JSONObject jsonObject = new JSONObject(responseText);
					String redirect = CommonUtils.optJsonString(jsonObject, "redirect");
					if (redirect != null) {
						Uri uri = normalizeRelativeUri(responseUri, redirect);
						targetUri = new TargetUri(uri);
						targetScheme = uri.getScheme();
					} else {
						responses.put(targetUri, new Response(responseUri, responseDataVersion,
								jsonObject, new HashSet<>(entry.getValue())));
						break;
					}
				}
			} catch (HttpException | JSONException e) {
				Log.persistent().stack(e);
			}
			if (Thread.currentThread().isInterrupted()) {
				return Collections.emptyList();
			}
		}
		return responses.values();
	}

	@Override
	protected Pair<ErrorItem, UpdateDataMap> run(HttpHolder holder) {
		File directory = FileProvider.getUpdatesDirectory();
		if (directory == null) {
			return new Pair<>(new ErrorItem(ErrorItem.Type.NO_ACCESS_TO_MEMORY), null);
		}
		File[] files = directory.listFiles();
		if (files != null) {
			// One week
			long timeThreshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;
			for (File file : files) {
				if (file.lastModified() < timeThreshold) {
					file.delete();
				}
			}
		}

		Iterable<ChanManager.ExtensionItem> extensionItems = ChanManager.getInstance().getExtensionItems();
		HashMap<String, ChanManager.Fingerprints> fingerprintsMap = new HashMap<>();
		fingerprintsMap.put(ChanManager.EXTENSION_NAME_CLIENT,
				ChanManager.getInstance().getApplicationFingerprints());
		for (ChanManager.ExtensionItem extensionItem : extensionItems) {
			fingerprintsMap.put(extensionItem.name, extensionItem.fingerprints);
		}
		String applicationTitle;
		String applicationVersionName;
		long applicationVersionCode;
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			applicationTitle = packageInfo.applicationInfo.loadLabel(context.getPackageManager()).toString();
			applicationVersionName = packageInfo.versionName;
			applicationVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		HashMap<String, ApplicationItem> updateDataMap = new HashMap<>();
		{
			ApplicationItem applicationItem = new ApplicationItem(ApplicationItem.Type.CLIENT,
					ChanManager.EXTENSION_NAME_CLIENT, applicationTitle, new ArrayList<>());
			applicationItem.packageItems.add(new PackageItem(null, null,
					applicationVersionName, applicationVersionCode,
					ChanManager.MIN_VERSION, ChanManager.MAX_VERSION, 0, -1, null, null));
			updateDataMap.put(ChanManager.EXTENSION_NAME_CLIENT, applicationItem);
			for (ChanManager.ExtensionItem extensionItem : extensionItems) {
				applicationItem = new ApplicationItem(extensionItem.type == ChanManager.ExtensionItem.Type.LIBRARY
						? ApplicationItem.Type.LIBRARY : ApplicationItem.Type.CHAN,
						extensionItem.name, extensionItem.title, new ArrayList<>());
				applicationItem.packageItems.add(new PackageItem(null, null, extensionItem.versionName,
						extensionItem.versionCode, 0, 0, extensionItem.apiVersion, -1, null, null));
				updateDataMap.put(extensionItem.name, applicationItem);
			}
		}
		if (isCancelled()) {
			return null;
		}

		Iterable<Response> responses = readData(holder, extensionItems);
		if (!responses.iterator().hasNext()) {
			return new Pair<>(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE), null);
		}
		if (isCancelled()) {
			return null;
		}

		for (Response response : responses) {
			try {
				switch (response.dataVersion) {
					case LEGACY: {
						Iterator<String> keys = response.jsonObject.keys();
						while (keys.hasNext()) {
							String extensionName = keys.next();
							if (ChanManager.EXTENSION_NAME_META.equals(extensionName)) {
								continue;
							}
							if (response.extensionNames.contains(extensionName)) {
								JSONArray packagesArray = response.jsonObject.getJSONArray(extensionName);
								handleUpdateItems(response, extensionName, updateDataMap, fingerprintsMap,
										packagesArray, DataVersion.LEGACY, null);
							}
						}
						break;
					}
					case V1: {
						JSONArray jsonArray = response.jsonObject.optJSONArray("applications");
						if (jsonArray != null && jsonArray.length() > 0) {
							for (int i = 0; i < jsonArray.length(); i++) {
								JSONObject jsonObject = jsonArray.getJSONObject(i);
								ApplicationItem extractedItem = ApplicationItem.fromJsonV1(jsonObject);
								if (response.extensionNames.contains(extractedItem.name)) {
									JSONArray packagesArray = jsonObject.getJSONArray("packages");
									handleUpdateItems(response, extractedItem.name, updateDataMap, fingerprintsMap,
											packagesArray, DataVersion.V1, extractedItem);
								}
							}
						}
						break;
					}
				}
			} catch (JSONException e) {
				Log.persistent().stack(e);
			}
			if (isCancelled()) {
				return null;
			}
		}

		HashMap<String, ApplicationItem> installDataMap = new HashMap<>();
		for (Response response : responses) {
			try {
				switch (response.dataVersion) {
					case LEGACY: {
						Iterator<String> keys = response.jsonObject.keys();
						while (keys.hasNext()) {
							String extensionName = keys.next();
							if (ChanManager.EXTENSION_NAME_META.equals(extensionName)) {
								continue;
							}
							if (!updateDataMap.containsKey(extensionName)) {
								JSONArray packagesArray = response.jsonObject.getJSONArray(extensionName);
								handleInstallItems(response, extensionName, installDataMap,
										packagesArray, DataVersion.LEGACY, null);
							}
						}
						break;
					}
					case V1: {
						JSONArray jsonArray = response.jsonObject.optJSONArray("applications");
						if (jsonArray != null && jsonArray.length() > 0) {
							for (int i = 0; i < jsonArray.length(); i++) {
								JSONObject jsonObject = jsonArray.getJSONObject(i);
								ApplicationItem extractedItem = ApplicationItem.fromJsonV1(jsonObject);
								if (!updateDataMap.containsKey(extractedItem.name)) {
									JSONArray packagesArray = jsonObject.getJSONArray("packages");
									handleInstallItems(response, extractedItem.name, installDataMap,
											packagesArray, DataVersion.V1, extractedItem);
								}
							}
						}
						break;
					}
				}
			} catch (JSONException e) {
				Log.persistent().stack(e);
			}
			if (isCancelled()) {
				return null;
			}
		}

		if (updateDataMap.size() > 0) {
			return new Pair<>(null, new UpdateDataMap(updateDataMap, installDataMap));
		} else {
			return new Pair<>(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE), null);
		}
	}

	@Override
	protected void onComplete(Pair<ErrorItem, UpdateDataMap> result) {
		callback.onReadUpdateComplete(result.second, result.first);
	}
}
