package com.mishiranu.dashchan.content.async;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.core.content.pm.PackageInfoCompat;
import chan.content.ChanLocator;
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

public class ReadUpdateTask extends HttpHolderTask<Void, Long, Void> {
	private final Context context;
	private final Callback callback;

	private HashMap<String, List<UpdateItem>> updateDataMap;
	private HashMap<String, List<UpdateItem>> installDataMap;
	private ErrorItem errorItem;

	public static class UpdateDataMap implements Parcelable {
		private final Map<String, List<UpdateItem>> update;
		private final Map<String, List<UpdateItem>> install;

		private UpdateDataMap(Map<String, List<UpdateItem>> update,
				Map<String, List<UpdateItem>> install) {
			this.update = update;
			this.install = install;
		}

		public List<UpdateItem> get(String extensionName, boolean installed) {
			return (installed ? update : install).get(extensionName);
		}

		public Collection<String> extensionNames(boolean installed) {
			return (installed ? update: install).keySet();
		}

		private static <T extends Parcelable> void writeGroupMap(Parcel dest, Map<String, List<T>> map) {
			dest.writeInt(map.size());
			for (Map.Entry<String, List<T>> entry : map.entrySet()) {
				dest.writeString(entry.getKey());
				dest.writeTypedList(entry.getValue());
			}
		}

		private static <T extends Parcelable> Map<String, List<T>> readGroupMap(Parcel in,
				Parcelable.Creator<T> creator) {
			int count = in.readInt();
			Map<String, List<T>> map = new HashMap<>();
			for (int i = 0; i < count; i++) {
				String key = in.readString();
				List<T> values = in.createTypedArrayList(creator);
				map.put(key, values);
			}
			return map;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			writeGroupMap(dest, update);
			writeGroupMap(dest, install);
		}

		public static final Creator<UpdateDataMap> CREATOR = new Creator<UpdateDataMap>() {
			@Override
			public UpdateDataMap createFromParcel(Parcel in) {
				Map<String, List<UpdateItem>> update = readGroupMap(in, UpdateItem.CREATOR);
				Map<String, List<UpdateItem>> install = readGroupMap(in, UpdateItem.CREATOR);
				return new UpdateDataMap(update, install);
			}

			@Override
			public UpdateDataMap[] newArray(int size) {
				return new UpdateDataMap[size];
			}
		};
	}

	public static class UpdateItem implements Parcelable {
		public final String repository;
		public final String title;
		public final String name;
		public final long code;
		public final int minVersion;
		public final int version;
		public final long length;
		public final String source;
		public final boolean ignoreVersion;

		private UpdateItem(String repository, String title, String name, long code,
				int minVersion, int version, long length, String source, boolean ignoreVersion) {
			this.repository = repository;
			this.title = title;
			this.name = name;
			this.code = code;
			this.minVersion = minVersion;
			this.version = version;
			this.length = length;
			this.source = source;
			this.ignoreVersion = ignoreVersion;
		}

		public static UpdateItem createExtension(String repository, String title, String name, long code,
				int version, long length, String source, boolean ignoreVersion) {
			return new UpdateItem(repository, title, name, code, -1, version, length, source, ignoreVersion);
		}

		public static UpdateItem createClient(String repository, String title, String name, long code,
				int minVersion, int maxVersion, long length, String source) {
			return new UpdateItem(repository, title, name, code, minVersion, maxVersion, length, source, false);
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(repository);
			dest.writeString(title);
			dest.writeString(name);
			dest.writeLong(code);
			dest.writeInt(minVersion);
			dest.writeInt(version);
			dest.writeLong(length);
			dest.writeString(source);
			dest.writeByte((byte) (ignoreVersion ? 1 : 0));
		}

		public static final Creator<UpdateItem> CREATOR = new Creator<UpdateItem>() {
			@Override
			public UpdateItem createFromParcel(Parcel in) {
				String repository = in.readString();
				String title = in.readString();
				String name = in.readString();
				long code = in.readLong();
				int minVersion = in.readInt();
				int version = in.readInt();
				long length = in.readLong();
				String source = in.readString();
				boolean ignoreVersion = in.readByte() != 0;
				return new UpdateItem(repository, title, name, code,
						minVersion, version, length, source, ignoreVersion);
			}

			@Override
			public UpdateItem[] newArray(int size) {
				return new UpdateItem[size];
			}
		};
	}

	public interface Callback {
		public void onReadUpdateComplete(UpdateDataMap updateDataMap, ErrorItem errorItem);
	}

	public ReadUpdateTask(Context context, Callback callback) {
		this.context = context.getApplicationContext();
		this.callback = callback;
	}

	public static Uri normalizeUri(Uri uri, Uri base) {
		boolean noScheme = uri.getScheme() == null;
		boolean noHost = uri.getHost() == null;
		if (noScheme || noHost) {
			Uri.Builder redirectUriBuilder = uri.buildUpon();
			if (noScheme) {
				redirectUriBuilder.scheme(base.getScheme());
			}
			if (noHost) {
				redirectUriBuilder.authority(base.getHost());
			}
			uri = redirectUriBuilder.build();
		}
		return uri;
	}

	private static UpdateItem extractUpdateItem(String extensionName, String repository, Uri uri, Long installedCode,
			ChanManager.Fingerprints fingerprints, boolean ignoreVersion, JSONObject chanObject) throws JSONException {
		int minSdk = chanObject.optInt("minSdk");
		int maxSdk = chanObject.optInt("maxSdk");
		if (minSdk > 0 && minSdk > Build.VERSION.SDK_INT ||
				maxSdk > 0 && maxSdk < Build.VERSION.SDK_INT) {
			return null;
		}
		ArrayList<String> rawFingerprints = new ArrayList<>();
		JSONArray fingerprintsArray = chanObject.optJSONArray("fingerprints");
		if (fingerprintsArray != null) {
			for (int j = 0; j < fingerprintsArray.length(); j++) {
				rawFingerprints.add(fingerprintsArray.optString(j));
			}
		} else {
			rawFingerprints.add(CommonUtils.optJsonString(chanObject, "fingerprint"));
		}
		if (fingerprints != null) {
			HashSet<String> fingerprintsSet = new HashSet<>();
			for (String rawFingerprint : rawFingerprints) {
				if (!StringUtils.isEmpty(rawFingerprint)) {
					rawFingerprint = rawFingerprint.replaceAll("[^a-fA-F0-9]", "")
							.toLowerCase(Locale.US);
					if (!StringUtils.isEmpty(rawFingerprint)) {
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
		String title = CommonUtils.getJsonString(chanObject, "title");
		String name = CommonUtils.getJsonString(chanObject, "name");
		int code = chanObject.getInt("code");
		if (installedCode != null && code < installedCode) {
			return null;
		}
		long length = chanObject.getLong("length");
		String source = CommonUtils.getJsonString(chanObject, "source");
		if (source == null) {
			return null;
		}
		Uri sourceUri = normalizeUri(Uri.parse(source), uri);
		source = sourceUri.toString();
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(extensionName)) {
			int minVersion = chanObject.getInt("minVersion");
			int maxVersion = chanObject.getInt("maxVersion");
			return UpdateItem.createClient(repository, title, name, code, minVersion, maxVersion, length, source);
		} else {
			int version = chanObject.optInt("version");
			return UpdateItem.createExtension(repository, title, name, code, version, length, source, ignoreVersion);
		}
	}

	private static class Response {
		public Uri uri;
		public JSONObject jsonObject;
		public HashSet<String> extensionNames;

		public Response(Uri uri, JSONObject jsonObject, HashSet<String> extensionNames) {
			this.uri = uri;
			this.jsonObject = jsonObject;
			this.extensionNames = extensionNames;
		}

		public String getRepositoryName() {
			JSONObject metaObject = jsonObject.optJSONObject(ChanManager.EXTENSION_NAME_META);
			String repository = null;
			if (metaObject != null) {
				repository = metaObject.optString("repository");
			}
			if (StringUtils.isEmpty(repository)) {
				repository = "Unknown repository";
			}
			return repository;
		}
	}

	private static Iterable<Response> readData(HttpHolder holder,
			Iterable<ChanManager.ExtensionItem> extensionItems) {
		ChanLocator locator = ChanLocator.getDefault();
		LinkedHashMap<Uri, HashSet<String>> targets = new LinkedHashMap<>();
		{
			Uri applicationUri = locator.setScheme(Uri.parse(BuildConfig.URI_UPDATES));
			HashSet<String> extensionNames = new HashSet<>();
			extensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
			targets.put(applicationUri, extensionNames);
		}
		for (ChanManager.ExtensionItem extensionItem : extensionItems) {
			if (extensionItem.updateUri != null) {
				Uri uri = locator.setScheme(extensionItem.updateUri);
				HashSet<String> extensionNames = targets.get(uri);
				if (extensionNames == null) {
					extensionNames = new HashSet<>();
					targets.put(uri, extensionNames);
				}
				extensionNames.add(extensionItem.extensionName);
			}
		}

		LinkedHashMap<String, Response> responses = new LinkedHashMap<>();
		for (Map.Entry<Uri, HashSet<String>> entry : targets.entrySet()) {
			try {
				Uri uri = entry.getKey();
				int redirects = 0;
				while (redirects++ < 5) {
					String uriKey = uri.buildUpon().scheme("").build().toString();
					Response response = responses.get(uriKey);
					if (response != null) {
						if ("http".equals(response.uri.getScheme()) && "https".equals(uri.getScheme())) {
							response.uri = uri;
						}
						response.extensionNames.addAll(entry.getValue());
						break;
					}
					JSONObject jsonObject = new HttpRequest(uri, holder).read().getJsonObject();
					if (jsonObject == null) {
						throw new JSONException("Not a JSON object");
					}
					String redirect = CommonUtils.optJsonString(jsonObject, "redirect");
					if (redirect != null) {
						uri = normalizeUri(Uri.parse(redirect), uri);
					} else {
						responses.put(uriKey, new Response(uri, jsonObject, new HashSet<>(entry.getValue())));
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
	protected Void doInBackground(HttpHolder holder, Void... params) {
		long timeThreshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000; // One week left
		File directory = FileProvider.getUpdatesDirectory();
		if (directory == null) {
			errorItem = new ErrorItem(ErrorItem.Type.NO_ACCESS_TO_MEMORY);
			return null;
		}
		File[] files = directory.listFiles();
		if (files != null) {
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
			fingerprintsMap.put(extensionItem.extensionName, extensionItem.fingerprints);
		}
		String applicationVersionName;
		long applicationVersionCode;
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			applicationVersionName = packageInfo.versionName;
			applicationVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		HashMap<String, List<UpdateItem>> updateDataMap = new HashMap<>();
		{
			List<UpdateItem> updateItems = new ArrayList<>();
			updateItems.add(UpdateItem.createClient(null, null, applicationVersionName, applicationVersionCode,
					ChanManager.MIN_VERSION, ChanManager.MAX_VERSION, -1, null));
			updateDataMap.put(ChanManager.EXTENSION_NAME_CLIENT, updateItems);
			for (ChanManager.ExtensionItem extensionItem : extensionItems) {
				updateItems = new ArrayList<>();
				updateItems.add(UpdateItem.createExtension(null, null, extensionItem.versionName,
						extensionItem.versionCode, extensionItem.version, -1, null,
						extensionItem.type == ChanManager.ExtensionItem.Type.LIBRARY));
				updateDataMap.put(extensionItem.extensionName, updateItems);
			}
		}
		if (isCancelled()) {
			return null;
		}

		Iterable<Response> responses = readData(holder, extensionItems);
		if (!responses.iterator().hasNext()) {
			errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
			return null;
		}
		if (isCancelled()) {
			return null;
		}

		for (Response response : responses) {
			try {
				Iterator<String> keys = response.jsonObject.keys();
				while (keys.hasNext()) {
					String extensionName = keys.next();
					if (ChanManager.EXTENSION_NAME_META.equals(extensionName)) {
						continue;
					}
					if (response.extensionNames.contains(extensionName)) {
						List<UpdateItem> updateItems = updateDataMap.get(extensionName);
						long installedCode = updateItems.get(0).code;
						ChanManager.Fingerprints fingerprints = fingerprintsMap.get(extensionName);
						boolean ignoreVersion = updateItems.get(0).ignoreVersion;
						JSONArray jsonArray = response.jsonObject.getJSONArray(extensionName);
						for (int i = 0; i < jsonArray.length(); i++) {
							UpdateItem updateItem = extractUpdateItem(extensionName, response.getRepositoryName(),
									response.uri, installedCode, fingerprints, ignoreVersion,
									jsonArray.getJSONObject(i));
							if (updateItem != null) {
								updateItems.add(updateItem);
							}
						}
					}
				}
			} catch (JSONException e) {
				Log.persistent().stack(e);
			}
			if (isCancelled()) {
				return null;
			}
		}

		HashMap<String, List<UpdateItem>> installDataMap = new HashMap<>();
		for (Response response : responses) {
			try {
				Iterator<String> keys = response.jsonObject.keys();
				while (keys.hasNext()) {
					String extensionName = keys.next();
					if (ChanManager.EXTENSION_NAME_META.equals(extensionName)) {
						continue;
					}
					if (!updateDataMap.containsKey(extensionName)) {
						JSONArray jsonArray = response.jsonObject.getJSONArray(extensionName);
						for (int i = 0; i < jsonArray.length(); i++) {
							UpdateItem updateItem = extractUpdateItem(extensionName, response.getRepositoryName(),
									response.uri, null, null, false, jsonArray.getJSONObject(i));
							if (updateItem != null) {
								List<UpdateItem> updateItems = installDataMap.get(extensionName);
								if (updateItems == null) {
									updateItems = new ArrayList<>();
									installDataMap.put(extensionName, updateItems);
								}
								updateItems.add(updateItem);
							}
						}
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
			this.updateDataMap = updateDataMap;
			this.installDataMap = installDataMap;
		} else {
			this.errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		callback.onReadUpdateComplete(updateDataMap != null && installDataMap != null
				? new UpdateDataMap(updateDataMap, installDataMap) : null, errorItem);
	}
}
