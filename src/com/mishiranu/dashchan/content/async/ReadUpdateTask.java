package com.mishiranu.dashchan.content.async;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReadUpdateTask extends HttpHolderTask<Void, Long, Void> {
	private final Context context;
	private final Callback callback;

	private UpdateDataMap updateDataMap;
	private ErrorItem errorItem;

	public static class UpdateDataMap implements Serializable {
		private static final long serialVersionUID = 1L;

		private final HashMap<String, ArrayList<ReadUpdateTask.UpdateItem>> map = new HashMap<>();

		public ArrayList<ReadUpdateTask.UpdateItem> get(String extensionName) {
			return map.get(extensionName);
		}

		public Iterable<String> extensionNames() {
			return map.keySet();
		}
	}

	public static class UpdateItem implements Serializable {
		private static final long serialVersionUID = 1L;

		public final String title;
		public final String name;
		public final int code;
		public final int minVersion;
		public final int version;
		public final long length;
		public final String source;
		public final boolean ignoreVersion;

		public UpdateItem(String title, String name, int code, int version, long length, String source,
				boolean ignoreVersion) {
			this.title = title;
			this.name = name;
			this.code = code;
			minVersion = -1;
			this.version = version;
			this.length = length;
			this.source = source;
			this.ignoreVersion = ignoreVersion;
		}

		public UpdateItem(String title, String name, int code, int minVersion, int maxVersion, long length,
				String source) {
			this.title = title;
			this.name = name;
			this.code = code;
			this.minVersion = minVersion;
			version = maxVersion;
			this.length = length;
			this.source = source;
			ignoreVersion = false;
		}
	}

	public interface Callback {
		public void onReadUpdateComplete(UpdateDataMap updateDataMap, ErrorItem errorItem);
	}

	public ReadUpdateTask(Context context, Callback callback) {
		this.context = context.getApplicationContext();
		this.callback = callback;
	}

	private static Uri normalizeUri(Uri uri, Uri base) {
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

	@Override
	protected Void doInBackground(HttpHolder holder, Void... params) {
		long timeThreshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000; // One week left
		File directory = FileProvider.getUpdatesDirectory(context);
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
		ChanLocator locator = ChanLocator.getDefault();
		LinkedHashMap<Uri, ArrayList<String>> targets = new LinkedHashMap<>();
		{
			Uri appUri = locator.setScheme(Uri.parse(C.UPDATE_SOURCE_URI_STRING));
			ArrayList<String> extensionNames = new ArrayList<>();
			extensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
			targets.put(appUri, extensionNames);
		}
		HashMap<String, Set<String>> fingerprintsMap = new HashMap<>();
		fingerprintsMap.put(ChanManager.EXTENSION_NAME_CLIENT,
				ChanManager.getInstance().getApplicationFingerprints());
		Collection<ChanManager.ExtensionItem> extensionItems = ChanManager.getInstance().getExtensionItems();
		for (ChanManager.ExtensionItem extensionItem : extensionItems) {
			if (extensionItem.updateUri != null) {
				Uri uri = locator.setScheme(extensionItem.updateUri);
				ArrayList<String> workExtensionNames = targets.get(uri);
				if (workExtensionNames == null) {
					workExtensionNames = new ArrayList<>();
					targets.put(uri, workExtensionNames);
				}
				workExtensionNames.add(extensionItem.extensionName);
			}
			fingerprintsMap.put(extensionItem.extensionName, extensionItem.fingerprints);
		}
		String appVersionName;
		int appVersionCode;
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			appVersionName = packageInfo.versionName;
			appVersionCode = packageInfo.versionCode;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		UpdateDataMap updateDataMap = new UpdateDataMap();
		ArrayList<UpdateItem> updateItems = new ArrayList<>();
		updateItems.add(new UpdateItem(null, appVersionName, appVersionCode, ChanManager.MIN_VERSION,
				ChanManager.MAX_VERSION, -1, null));
		updateDataMap.map.put(ChanManager.EXTENSION_NAME_CLIENT, updateItems);
		for (ChanManager.ExtensionItem extensionItem : extensionItems) {
			updateItems = new ArrayList<>();
			updateItems.add(new UpdateItem(null, extensionItem.packageInfo.versionName,
					extensionItem.packageInfo.versionCode, extensionItem.version, -1, null,
					extensionItem.isLibExtension));
			updateDataMap.map.put(extensionItem.extensionName, updateItems);
		}
		if (isCancelled()) {
			return null;
		}
		for (LinkedHashMap.Entry<Uri, ArrayList<String>> target : targets.entrySet()) {
			try {
				Uri uri = target.getKey();
				int redirects = 0;
				while (redirects++ < 5) {
					JSONObject jsonObject = new HttpRequest(uri, holder).read().getJsonObject();
					if (jsonObject != null) {
						String redirect = CommonUtils.optJsonString(jsonObject, "redirect");
						if (redirect != null) {
							uri = normalizeUri(Uri.parse(redirect), uri);
							continue;
						}
						Iterator<String> keys = jsonObject.keys();
						while (keys.hasNext()) {
							String extensionName = keys.next();
							if (target.getValue().contains(extensionName)) {
								updateItems = updateDataMap.get(extensionName);
								boolean ignoreVersion = updateItems.get(0).ignoreVersion;
								JSONArray jsonArray = jsonObject.getJSONArray(extensionName);
								for (int i = 0; i < jsonArray.length(); i++) {
									JSONObject chanObject = jsonArray.getJSONObject(i);
									int minSdk = chanObject.optInt("minSdk");
									if (minSdk > Build.VERSION.SDK_INT) {
										continue;
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
									HashSet<String> fingerprints = new HashSet<>();
									for (String rawFingerprint : rawFingerprints) {
										if (!StringUtils.isEmpty(rawFingerprint)) {
											rawFingerprint = rawFingerprint.replaceAll("[^a-fA-F0-9]", "")
													.toLowerCase(Locale.US);
											if (!StringUtils.isEmpty(rawFingerprint)) {
												fingerprints.add(rawFingerprint);
											}
										}
									}
									if (!fingerprints.equals(fingerprintsMap.get(extensionName))) {
										continue;
									}
									String title = CommonUtils.getJsonString(chanObject, "title");
									String name = CommonUtils.getJsonString(chanObject, "name");
									int code = chanObject.getInt("code");
									if (code < updateItems.get(0).code) {
										continue;
									}
									long length = chanObject.getLong("length");
									String source = CommonUtils.getJsonString(chanObject, "source");
									if (source == null) {
										break;
									}
									Uri sourceUri = normalizeUri(Uri.parse(source), uri);
									source = sourceUri.toString();
									UpdateItem updateItem;
									if (ChanManager.EXTENSION_NAME_CLIENT.equals(extensionName)) {
										int minVersion = chanObject.getInt("minVersion");
										int maxVersion = chanObject.getInt("maxVersion");
										updateItem = new UpdateItem(title, name, code,
												minVersion, maxVersion, length, source);
									} else {
										int version = chanObject.optInt("version");
										updateItem = new UpdateItem(title, name, code, version, length,
												source, ignoreVersion);
									}
									updateItems.add(updateItem);
								}
							}
						}
					}
					break;
				}
			} catch (HttpException | JSONException e) {
				Log.persistent().stack(e);
			}
			if (isCancelled()) {
				return null;
			}
		}
		if (updateDataMap.map.size() > 0) {
			this.updateDataMap = updateDataMap;
		} else {
			this.errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		callback.onReadUpdateComplete(updateDataMap, errorItem);
	}
}
