package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserAgentProvider {
	private static final UserAgentProvider INSTANCE = new UserAgentProvider();

	public static UserAgentProvider getInstance() {
		return INSTANCE;
	}

	private UserAgentProvider() {
		String reference = Preferences.getUserAgentReference();
		Version3 latest = null;
		if (reference == null) {
			reference = "firefox:68.0";
		}
		if (reference.startsWith("firefox:")) {
			String version = reference.substring(8);
			String[] splitted = version.split("\\.");
			if (splitted != null && splitted.length >= 2) {
				try {
					latest = new Version3(Integer.parseInt(splitted[0]), Integer.parseInt(splitted[1]),
							splitted.length >= 3 ? Integer.parseInt(splitted[2]) : null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			userAgent = formatFirefoxUserAgent(version);
		}
		loadFirefoxUserAgentReference(latest);
	}

	private String userAgent = "Mozilla/5.0";

	private String formatFirefoxUserAgent(String version) {
		return String.format(Locale.US, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:%s) " +
				"Gecko/20100101 Firefox/%s", version, version);
	}

	private static final Pattern FIREFOX_RELEASE_LINK = Pattern
			.compile("<a href=\".*?\">(\\d+)\\.(\\d+)(?:\\.(\\d+))?/</a>");

	private static class Version3 implements Comparable<Version3> {
		public final int major;
		public final int minor;
		public final Integer patch;

		private Version3(int major, int minor, Integer patch) {
			this.major = major;
			this.minor = minor;
			this.patch = patch;
		}

		@Override
		public int compareTo(Version3 o) {
			int result = Integer.compare(major, o.major);
			if (result != 0) {
				return result;
			}
			result = Integer.compare(minor, o.minor);
			if (result != 0) {
				return result;
			}
			return Integer.compare(patch != null ? patch : 0, o.patch != null ? o.patch : 0);
		}

		@Override
		public String toString() {
			return patch != null ? major + "." + minor + "." + patch : major + "." + minor;
		}
	}

	private void loadFirefoxUserAgentReference(Version3 initialLatest) {
		new HttpHolderTask<Void, Void, String>() {
			@Override
			protected String doInBackground(HttpHolder holder, Void... params) {
				String response;
				try {
					Uri uri = ChanLocator.getDefault().buildPathWithHost("ftp.mozilla.org", "pub/firefox/releases/");
					response = new HttpRequest(uri, holder).read().getString();
				} catch (HttpException e) {
					return null;
				}
				Version3 latest = initialLatest;
				Matcher matcher = FIREFOX_RELEASE_LINK.matcher(response);
				while (matcher.find()) {
					String majorString = matcher.group(1);
					String minorString = matcher.group(2);
					String patchString = matcher.group(3);
					int major = Integer.parseInt(majorString);
					int minor = Integer.parseInt(minorString);
					Integer patch = StringUtils.isEmpty(patchString) ? null : Integer.parseInt(patchString);
					Version3 version = new Version3(major, minor, patch);
					if (latest == null || version.compareTo(latest) > 0) {
						latest = version;
					}
				}
				return latest != null && latest != initialLatest ? latest.toString() : null;
			}

			@Override
			protected void onPostExecute(String version) {
				if (version != null) {
					Preferences.setUserAgentReference("firefox:" + version);
					userAgent = formatFirefoxUserAgent(version);
				}
			}
		}.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	public String getUserAgent() {
		return userAgent;
	}
}
