package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.util.StringUtils;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReadChangelogTask extends HttpHolderTask<Void, Pair<ErrorItem, List<ReadChangelogTask.Entry>>> {
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
		super(Chan.getFallback());
		this.callback = callback;
		this.locales = locales;
	}

	private static String testLocale(Uri baseUri, HttpHolder holder, String locale, long code) throws HttpException {
		HttpResponse response = new HttpRequest(baseUri.buildUpon().appendPath(locale)
				.appendPath("changelogs").appendPath(code + ".txt").build(), holder)
				.setHeadMethod().setSuccessOnly(false).perform();
		try {
			if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
				return locale;
			} else {
				return null;
			}
		} finally {
			response.cleanupAndDisconnect();
		}
	}

	private static String testLocale(Uri baseUri, HttpHolder holder,
			String language, String country, long code) throws HttpException {
		if (country != null) {
			String locale = testLocale(baseUri, holder, language + "-" + country, code);
			if (locale != null) {
				return locale;
			}
		}
		return testLocale(baseUri, holder, language, code);
	}

	@Override
	protected Pair<ErrorItem, List<Entry>> run(HttpHolder holder) {
		try {
			Uri uri = Chan.getFallback().locator.setSchemeIfEmpty(Uri.parse(BuildConfig.URI_VERSIONS), null);
			JSONArray versionsArray = new JSONObject(new HttpRequest(uri, holder)
					.perform().readString()).getJSONArray("versions");
			long maxCode = 0;
			for (int i = 0; i < versionsArray.length(); i++) {
				JSONObject jsonObject = versionsArray.getJSONObject(i);
				if (jsonObject.optBoolean("changelog")) {
					maxCode = Math.max(maxCode, jsonObject.getLong("code"));
				}
			}

			Uri baseUri;
			String path = uri.getPath();
			int index = path.lastIndexOf('/');
			if (index >= 0) {
				baseUri = uri.buildUpon().path(path.substring(0, index)).build();
			} else {
				baseUri = uri.buildUpon().path("/").build();
			}
			String localePath = null;
			for (Locale locale : locales) {
				localePath = testLocale(baseUri, holder, locale.getLanguage(),
						StringUtils.emptyIfNull(locale.getCountry()), maxCode);
				if (localePath != null) {
					break;
				}
			}
			if (localePath == null) {
				localePath = testLocale(baseUri, holder, "en-US", maxCode);
			}
			if (localePath == null) {
				localePath = testLocale(baseUri, holder, "en", maxCode);
			}
			if (localePath == null) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null);
			}

			TreeMap<Long, Entry> entriesMap = new TreeMap<>();
			for (int i = 0; i < versionsArray.length(); i++) {
				JSONObject jsonObject = versionsArray.getJSONObject(i);
				long code = jsonObject.getLong("code");
				String name = jsonObject.getString("name");
				String date = jsonObject.getString("date");
				Entry entry = entriesMap.get(code);
				if ((entry == null || entry.text == null) && jsonObject.optBoolean("changelog")) {
					Uri changelogUri = baseUri.buildUpon().appendPath(localePath)
							.appendPath("changelogs").appendPath(code + ".txt").build();
					String changelog = null;
					try {
						changelog = new HttpRequest(changelogUri, holder).perform().readString();
					} catch (HttpException e) {
						if (e.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
							throw e;
						}
					}
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
		} catch (JSONException e) {
			Log.persistent().stack(e);
			return new Pair<>(new ErrorItem(ErrorItem.Type.INVALID_RESPONSE), null);
		} catch (HttpException e) {
			return new Pair<>(e.getErrorItemAndHandle(), null);
		}
	}

	@Override
	protected void onComplete(Pair<ErrorItem, List<Entry>> result) {
		callback.onReadChangelogComplete(result.second, result.first);
	}
}
