package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import chan.content.Chan;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.InetSocket;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.SubversionProtocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReadChangelogTask extends ExecutorTask<Void, Pair<ErrorItem, List<ReadChangelogTask.Entry>>> {
	public interface Callback {
		void onReadChangelogComplete(List<Entry> entries, ErrorItem errorItem);
	}

	public static class Entry implements Parcelable {
		public static class Version {
			public final String name;
			public final String date;

			public Version(String name, String date) {
				this.name = name;
				this.date = date;
			}
		}

		public final List<Version> versions;
		public final String text;

		public Entry(List<Version> versions, String text) {
			this.versions = versions;
			this.text = text;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(versions.size());
			for (Version version : versions) {
				dest.writeString(version.name);
				dest.writeString(version.date);
			}
			dest.writeString(text);
		}

		public static final Creator<Entry> CREATOR = new Creator<Entry>() {
			@Override
			public Entry createFromParcel(Parcel source) {
				int versionsSize = source.readInt();
				ArrayList<Version> versions = new ArrayList<>(versionsSize);
				for (int i = 0; i < versionsSize; i++) {
					String name = source.readString();
					String date = source.readString();
					versions.add(new Version(name, date));
				}
				String text = source.readString();
				return new Entry(versions, text);
			}

			@Override
			public Entry[] newArray(int size) {
				return new Entry[size];
			}
		};
	}

	private final Callback callback;
	private final List<Locale> locales;

	public ReadChangelogTask(Callback callback, List<Locale> locales) {
		this.callback = callback;
		this.locales = locales;
	}

	private static String decodeDiffToString(byte[] svnDiff) throws IOException {
		byte[] decoded;
		try {
			decoded = SubversionProtocol.applyDiff(null, svnDiff);
		} catch (IllegalArgumentException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			} else {
				throw new IOException(e);
			}
		}
		return decoded != null ? new String(decoded) : null;
	}

	@Override
	protected Pair<ErrorItem, List<Entry>> run() throws InterruptedException {
		Uri githubUri = Chan.getFallback().locator.setSchemeIfEmpty(Uri.parse(BuildConfig.GITHUB_URI_METADATA), null);
		String metadataPath = BuildConfig.GITHUB_PATH_METADATA;
		try {
			HashMap<String, byte[]> metadataFiles = SubversionProtocol.listGithubFiles(githubUri, metadataPath);
			if (isCancelled()) {
				return null;
			}
			HashSet<String> metadataDirs = new HashSet<>();
			for (HashMap.Entry<String, byte[]> entry : metadataFiles.entrySet()) {
				if (entry.getValue() == null) {
					metadataDirs.add(entry.getKey());
				}
			}
			if (metadataDirs.isEmpty()) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null);
			}
			byte[] versionsFile = metadataFiles.get("versions.json");
			if (versionsFile == null) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null);
			}
			JSONArray versionsArray = new JSONObject(decodeDiffToString(versionsFile)).getJSONArray("versions");

			ArrayList<String> downloadLocales = new ArrayList<>();
			for (Locale locale : locales) {
				String language = locale.getLanguage();
				String country = locale.getCountry();
				String languageCountry = country != null ? language + "-" + country : language;
				if (metadataDirs.contains(languageCountry)) {
					downloadLocales.add(languageCountry);
				} else {
					if (country == null) {
						for (String metadataDir : metadataDirs) {
							if (metadataDir.startsWith(language + "-")) {
								downloadLocales.add(metadataDir);
							}
						}
					} else if (metadataDirs.contains(language)) {
						downloadLocales.add(language);
					}
				}
			}
			for (String fallbackLocaleDir : Arrays.asList("en-US", "en")) {
				if (metadataDirs.contains(fallbackLocaleDir)) {
					downloadLocales.add(fallbackLocaleDir);
				}
			}
			if (downloadLocales.isEmpty()) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null);
			}

			HashSet<String> checkedLocales = new HashSet<>();
			HashMap<String, byte[]> changelogFiles = null;
			for (String localeDir : downloadLocales) {
				if (!checkedLocales.contains(localeDir)) {
					checkedLocales.add(localeDir);
					try {
						changelogFiles = SubversionProtocol.listGithubFiles(githubUri,
								metadataPath + "/" + localeDir + "/changelogs");
						if (isCancelled()) {
							return null;
						}
						if (changelogFiles != null && !changelogFiles.isEmpty()) {
							break;
						}
					} catch (HttpException e) {
						if (!e.isHttpException()) {
							throw e;
						}
					}
				}
			}
			if (changelogFiles == null || changelogFiles.isEmpty()) {
				throw HttpException.createNotFoundException();
			}

			TreeMap<Long, Entry> entriesMap = new TreeMap<>();
			for (int i = 0; i < versionsArray.length(); i++) {
				JSONObject jsonObject = versionsArray.getJSONObject(i);
				long code = jsonObject.getLong("code");
				String name = jsonObject.getString("name");
				String date = jsonObject.getString("date");
				Entry entry = entriesMap.get(code);
				if ((entry == null || entry.text == null) && jsonObject.optBoolean("changelog")) {
					byte[] file = changelogFiles.get(code + ".txt");
					String changelog = decodeDiffToString(file);
					if (changelog != null) {
						entry = new Entry(entry == null ? new ArrayList<>() : entry.versions, changelog);
						entriesMap.put(code, entry);
					}
				}
				if (entry == null) {
					entry = new Entry(new ArrayList<>(), null);
					entriesMap.put(code, entry);
				}
				entry.versions.add(new Entry.Version(name, date));
			}

			ArrayList<Entry> entries = new ArrayList<>(entriesMap.size());
			for (Entry entry : entriesMap.values()) {
				if (entry.text != null) {
					entries.add(entry);
				} else if (!entries.isEmpty()) {
					entries.get(entries.size() - 1).versions.addAll(entry.versions);
				}
			}
			Collections.reverse(entries);
			return new Pair<>(null, entries);
		} catch (HttpException e) {
			return new Pair<>(e.getErrorItemAndHandle(), null);
		} catch (InetSocket.InvalidCertificateException e) {
			e.printStackTrace();
			return new Pair<>(new ErrorItem(ErrorItem.Type.INVALID_CERTIFICATE), null);
		} catch (IOException e) {
			e.printStackTrace();
			return new Pair<>(HttpClient.transformIOException(e).getErrorItemAndHandle(), null);
		} catch (JSONException e) {
			e.printStackTrace();
			return new Pair<>(new ErrorItem(ErrorItem.Type.INVALID_RESPONSE), null);
		}
	}

	@Override
	protected void onComplete(Pair<ErrorItem, List<Entry>> result) {
		callback.onReadChangelogComplete(result.second, result.first);
	}
}
