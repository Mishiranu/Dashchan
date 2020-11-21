package chan.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extendable
public class ChanLocator implements Chan.Linked {
	private final Chan.Provider chanProvider;

	private static final int HOST_TYPE_CONFIGURABLE = 0;
	private static final int HOST_TYPE_CONVERTABLE = 1;
	private static final int HOST_TYPE_SPECIAL = 2;

	private final LinkedHashMap<String, Integer> hosts = new LinkedHashMap<>();
	private HttpsMode httpsMode = HttpsMode.NO_HTTPS;

	@Public
	public enum HttpsMode {
		@Public NO_HTTPS,
		@Public HTTPS_ONLY,
		@Public CONFIGURABLE
	}

	@Public
	public static final class NavigationData implements Parcelable {
		@Public public static final int TARGET_THREADS = 0;
		@Public public static final int TARGET_POSTS = 1;
		@Public public static final int TARGET_SEARCH = 2;

		public enum Target {THREADS, POSTS, SEARCH}

		public final Target target;
		public final String boardName;
		public final String threadNumber;
		public final PostNumber postNumber;
		public final String searchQuery;

		@Public
		public NavigationData(int target, String boardName, String threadNumber, String postNumber,
				String searchQuery) {
			this(transformTarget(target), boardName, threadNumber, postNumber != null
					? PostNumber.parseOrThrow(postNumber) : null, searchQuery);
			PostNumber.validateThreadNumber(threadNumber, true);
		}

		public NavigationData(Target target, String boardName, String threadNumber, PostNumber postNumber,
				String searchQuery) {
			this.target = target;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			this.searchQuery = searchQuery;
			if (target == Target.POSTS && StringUtils.isEmpty(threadNumber)) {
				throw new IllegalArgumentException("threadNumber must not be empty!");
			}
		}

		private static Target transformTarget(int target) {
			switch (target) {
				case TARGET_THREADS: {
					return Target.THREADS;
				}
				case TARGET_POSTS: {
					return Target.POSTS;
				}
				case TARGET_SEARCH: {
					return Target.SEARCH;
				}
				default: {
					throw new IllegalArgumentException();
				}
			}
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(target.name());
			dest.writeString(boardName);
			dest.writeString(threadNumber);
			dest.writeByte((byte) (postNumber != null ? 1 : 0));
			if (postNumber != null) {
				postNumber.writeToParcel(dest, flags);
			}
			dest.writeString(searchQuery);
		}

		public static final Creator<NavigationData> CREATOR = new Creator<NavigationData>() {
			@Override
			public NavigationData createFromParcel(Parcel source) {
				Target target = Target.valueOf(source.readString());
				String boardName = source.readString();
				String threadNumber = source.readString();
				PostNumber postNumber = source.readByte() != 0 ? PostNumber.CREATOR.createFromParcel(source) : null;
				String searchQuery = source.readString();
				return new NavigationData(target, boardName, threadNumber, postNumber, searchQuery);
			}

			@Override
			public NavigationData[] newArray(int size) {
				return new NavigationData[size];
			}
		};
	}

	static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

	@Public
	public ChanLocator() {
		this(null);
	}

	ChanLocator(Chan.Provider chanProvider) {
		if (chanProvider == null) {
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			this.chanProvider = holder.chanProvider;
		} else {
			this.chanProvider = chanProvider;
			setHttpsMode(HttpsMode.CONFIGURABLE);
		}
	}

	@Override
	public final void init() {
		if (getChanHosts(true).size() == 0) {
			throw new RuntimeException("Chan hosts not defined");
		}
	}

	@Override
	public Chan get() {
		return chanProvider.get();
	}

	@Public
	public static ChanLocator get(Object object) {
		return ((Chan.Linked) object).get().locator;
	}

	@Public
	public final void addChanHost(String host) {
		hosts.put(host, HOST_TYPE_CONFIGURABLE);
	}

	@Public
	public final void addConvertableChanHost(String host) {
		hosts.put(host, HOST_TYPE_CONVERTABLE);
	}

	@Public
	public final void addSpecialChanHost(String host) {
		hosts.put(host, HOST_TYPE_SPECIAL);
	}

	@Public
	public final void setHttpsMode(HttpsMode httpsMode) {
		if (httpsMode == null) {
			throw new NullPointerException();
		}
		this.httpsMode = httpsMode;
	}

	public final boolean isHttpsConfigurable() {
		return httpsMode == HttpsMode.CONFIGURABLE;
	}

	@Public
	public final boolean isUseHttps() {
		HttpsMode httpsMode = this.httpsMode;
		if (httpsMode == HttpsMode.CONFIGURABLE) {
			Chan chan = get();
			return chan.name != null ? Preferences.isUseHttps(chan) : Preferences.isUseHttpsGeneral();
		}
		return httpsMode == HttpsMode.HTTPS_ONLY;
	}

	public final ArrayList<String> getChanHosts(boolean configurableOnly) {
		if (configurableOnly) {
			ArrayList<String> hosts = new ArrayList<>();
			for (LinkedHashMap.Entry<String, Integer> entry : this.hosts.entrySet()) {
				if (entry.getValue() == HOST_TYPE_CONFIGURABLE) {
					hosts.add(entry.getKey());
				}
			}
			return hosts;
		} else {
			return new ArrayList<>(hosts.keySet());
		}
	}

	public final boolean isChanHost(String host) {
		if (StringUtils.isEmpty(host)) {
			return false;
		}
		return hosts.containsKey(host) || host.equals(Preferences.getDomainUnhandled(get()))
				|| getHostTransition(getPreferredHost(), host) != null;
	}

	@Public
	public final boolean isChanHostOrRelative(Uri uri) {
		if (uri != null) {
			if (uri.isRelative()) {
				return true;
			}
			String host = uri.getHost();
			return host != null && isChanHost(host);
		}
		return false;
	}

	public final boolean isConvertableChanHost(String host) {
		if (StringUtils.isEmpty(host)) {
			return false;
		}
		if (host.equals(Preferences.getDomainUnhandled(get()))) {
			return true;
		}
		Integer hostType = hosts.get(host);
		return hostType != null && (hostType == HOST_TYPE_CONFIGURABLE || hostType == HOST_TYPE_CONVERTABLE);
	}

	public final Uri convert(Uri uri) {
		if (uri != null) {
			String preferredScheme = getPreferredScheme();
			String host = uri.getHost();
			String preferredHost = getPreferredHost();
			boolean relative = uri.isRelative();
			boolean webScheme = isWebScheme(uri);
			Uri.Builder builder = null;
			if (relative || webScheme && isConvertableChanHost(host)) {
				if (!CommonUtils.equals(host, preferredHost)) {
					if (builder == null) {
						builder = uri.buildUpon().scheme(preferredScheme);
					}
					builder.authority(preferredHost);
				}
			} else if (webScheme) {
				String hostTransition = getHostTransition(preferredHost, host);
				if (hostTransition != null) {
					if (builder == null) {
						builder = uri.buildUpon().scheme(preferredScheme);
					}
					builder.authority(hostTransition);
				}
			}
			if (StringUtils.isEmpty(uri.getScheme()) ||
					webScheme && !preferredScheme.equals(uri.getScheme()) && isChanHost(host)) {
				if (builder == null) {
					builder = uri.buildUpon().scheme(preferredScheme);
				}
				builder.scheme(preferredScheme);
			}
			if (builder != null) {
				return builder.build();
			}
		}
		return uri;
	}

	public final Uri makeRelative(Uri uri) {
		if (isWebScheme(uri)) {
			String host = uri.getHost();
			if (isConvertableChanHost(host)) {
				uri = uri.buildUpon().scheme(null).authority(null).build();
			}
		}
		return uri;
	}

	public final Uri fixRelativeFileUri(Uri uri) {
		if (uri == null) {
			return null;
		}
		String uriString = uri.toString();
		int index = uriString.indexOf("//");
		if (index >= 0) {
			index = uriString.indexOf('/', index + 2);
			if (index >= 0) {
				index++;
			} else {
				return uri;
			}
		}
		if (index < 0) {
			index = 0;
		}
		if (uriString.indexOf(':', index) >= 0) {
			uriString = uriString.substring(0, index) + uriString.substring(index).replace(":", "%3A");
		}
		return Uri.parse(uriString);
	}

	@Extendable
	protected String getHostTransition(String chanHost, String requiredHost) {
		return null;
	}

	@Extendable
	protected boolean isBoardUri(Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected boolean isThreadUri(Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected boolean isAttachmentUri(Uri uri) {
		throw new UnsupportedOperationException();
	}

	public final boolean isImageUri(Uri uri) {
		return uri != null && isImageExtension(uri.getPath()) && safe.isAttachmentUri(uri);
	}

	public final boolean isAudioUri(Uri uri) {
		return uri != null && isAudioExtension(uri.getPath()) && safe.isAttachmentUri(uri);
	}

	public final boolean isVideoUri(Uri uri) {
		return uri != null && isVideoExtension(uri.getPath()) && safe.isAttachmentUri(uri);
	}

	@Extendable
	protected String getBoardName(Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected String getThreadNumber(Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected String getPostNumber(Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected Uri createBoardUri(String boardName, int pageNumber) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected Uri createThreadUri(String boardName, String threadNumber) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected Uri createPostUri(String boardName, String threadNumber, String postNumber) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	protected String createAttachmentForcedName(Uri fileUri) {
		return null;
	}

	public final String createAttachmentFileName(Uri fileUri) {
		return createAttachmentFileName(fileUri, safe.createAttachmentForcedName(fileUri));
	}

	public final String createAttachmentFileName(Uri fileUri, String forcedName) {
		String fileName;
		if (StringUtils.isEmpty(forcedName)) {
			String fileUriString = fileUri.getPath();
			int start = fileUriString.lastIndexOf('/') + 1;
			fileName = fileUriString.substring(start);
		} else {
			fileName = forcedName;
		}
		return StringUtils.isEmpty(fileName) ? "" : StringUtils.emptyIfNull(StringUtils.escapeFile(fileName, false));
	}

	public final Uri validateClickedUriString(String uriString, String boardName, String threadNumber) {
		Uri uri = uriString != null ? Uri.parse(uriString) : null;
		if (uri != null && uri.isRelative()) {
			Uri baseUri = safe.createThreadUri(boardName, threadNumber);
			if (baseUri != null) {
				String query = StringUtils.nullIfEmpty(uri.getQuery());
				String fragment = StringUtils.nullIfEmpty(uri.getFragment());
				Uri.Builder builder = baseUri.buildUpon().encodedQuery(query).encodedFragment(fragment);
				String path = uri.getPath();
				if (!StringUtils.isEmpty(path)) {
					builder.encodedPath(path);
				}
				return builder.build();
			}
		}
		return uri;
	}

	@Extendable
	protected NavigationData handleUriClickSpecial(Uri uri) {
		return null;
	}

	public final boolean isWebScheme(Uri uri) {
		String scheme = uri.getScheme();
		return "http".equals(scheme) || "https".equals(scheme);
	}

	@Public
	public final boolean isImageExtension(String path) {
		return C.IMAGE_EXTENSIONS.contains(getFileExtension(path));
	}

	@Public
	public final boolean isAudioExtension(String path) {
		return C.AUDIO_EXTENSIONS.contains(getFileExtension(path));
	}

	@Public
	public final boolean isVideoExtension(String path) {
		return C.VIDEO_EXTENSIONS.contains(getFileExtension(path));
	}

	@Public
	public final String getFileExtension(String path) {
		return StringUtils.getFileExtension(path);
	}

	public final String getPreferredHost() {
		String host = Preferences.getDomainUnhandled(get());
		if (StringUtils.isEmpty(host)) {
			for (LinkedHashMap.Entry<String, Integer> entry : hosts.entrySet()) {
				if (entry.getValue() == HOST_TYPE_CONFIGURABLE) {
					host = entry.getKey();
					break;
				}
			}
		}
		return host;
	}

	public final void setPreferredHost(String host) {
		if (host == null || getChanHosts(true).get(0).equals(host)) {
			host = "";
		}
		Preferences.setDomainUnhandled(get(), host);
	}

	private static String getPreferredScheme(boolean useHttps) {
		return useHttps ? "https" : "http";
	}

	private String getPreferredScheme() {
		return getPreferredScheme(isUseHttps());
	}

	@Public
	public final Uri buildPath(String... segments) {
		return buildPathWithHost(getPreferredHost(), segments);
	}

	@Public
	public final Uri buildPathWithHost(String host, String... segments) {
		return buildPathWithSchemeHost(isUseHttps(), host, segments);
	}

	@Public
	public final Uri buildPathWithSchemeHost(boolean useHttps, String host, String... segments) {
		Uri.Builder builder = new Uri.Builder().scheme(getPreferredScheme(useHttps)).authority(host);
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment != null) {
				builder.appendEncodedPath(segment.replaceFirst("^/+", ""));
			}
		}
		return builder.build();
	}

	@Public
	public final Uri buildQuery(String path, String... alternation) {
		return buildQueryWithHost(getPreferredHost(), path, alternation);
	}

	@Public
	public final Uri buildQueryWithHost(String host, String path, String... alternation) {
		return buildQueryWithSchemeHost(isUseHttps(), host, path, alternation);
	}

	@Public
	public final Uri buildQueryWithSchemeHost(boolean useHttps, String host, String path, String... alternation) {
		Uri.Builder builder = new Uri.Builder().scheme(getPreferredScheme(useHttps)).authority(host);
		if (path != null) {
			builder.appendEncodedPath(path.replaceFirst("^/+", ""));
		}
		if (alternation.length % 2 != 0) {
			throw new IllegalArgumentException("Length of alternation must be a multiple of 2.");
		}
		for (int i = 0; i < alternation.length; i += 2) {
			builder.appendQueryParameter(alternation[i], alternation[i + 1]);
		}
		return builder.build();
	}

	public final Uri setSchemeIfEmpty(Uri uri, String fallback) {
		if (uri != null && StringUtils.isEmpty(uri.getScheme())) {
			return uri.buildUpon().scheme(fallback != null ? fallback : getPreferredScheme()).build();
		}
		return uri;
	}

	@Public
	public final boolean isPathMatches(Uri uri, Pattern pattern) {
		if (uri != null) {
			String path = uri.getPath();
			if (path != null) {
				return pattern.matcher(path).matches();
			}
		}
		return false;
	}

	@Public
	public final String getGroupValue(String from, Pattern pattern, int groupIndex) {
		if (from == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(from);
		if (matcher.find() && matcher.groupCount() > 0) {
			return matcher.group(groupIndex);
		}
		return null;
	}

	public final String[] getUniqueGroupValues(String from, Pattern pattern, int groupIndex) {
		if (from == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(from);
		LinkedHashSet<String> data = new LinkedHashSet<>();
		while (matcher.find() && matcher.groupCount() > 0) {
			data.add(matcher.group(groupIndex));
		}
		return CommonUtils.toArray(data, String.class);
	}

	public static final class Safe {
		private final ChanLocator locator;
		private final boolean showToastOnError;

		private Safe(ChanLocator locator, boolean showToastOnError) {
			this.locator = locator;
			this.showToastOnError = showToastOnError;
		}

		public boolean isBoardUri(Uri uri) {
			try {
				return locator.isBoardUri(uri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return false;
			}
		}

		public boolean isThreadUri(Uri uri) {
			try {
				return locator.isThreadUri(uri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return false;
			}
		}

		public boolean isAttachmentUri(Uri uri) {
			try {
				return locator.isAttachmentUri(uri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return false;
			}
		}

		public String getBoardName(Uri uri) {
			try {
				return locator.getBoardName(uri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public String getThreadNumber(Uri uri) {
			try {
				String threadNumber = locator.getThreadNumber(uri);
				PostNumber.validateThreadNumber(threadNumber, true);
				return threadNumber;
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public PostNumber getPostNumber(Uri uri) {
			try {
				String postNumber = locator.getPostNumber(uri);
				return postNumber != null ? PostNumber.parseOrThrow(postNumber) : null;
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public Uri createBoardUri(String boardName, int pageNumber) {
			try {
				return locator.createBoardUri(boardName, pageNumber);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public Uri createThreadUri(String boardName, String threadNumber) {
			try {
				return locator.createThreadUri(boardName, threadNumber);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public Uri createPostUri(String boardName, String threadNumber, PostNumber postNumber) {
			try {
				return locator.createPostUri(boardName, threadNumber,
						postNumber != null ? postNumber.toString() : null);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public String createAttachmentForcedName(Uri fileUri) {
			try {
				return locator.createAttachmentForcedName(fileUri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}

		public NavigationData handleUriClickSpecial(Uri uri) {
			try {
				return locator.handleUriClickSpecial(uri);
			} catch (LinkageError | RuntimeException e) {
				ExtensionException.logException(e, showToastOnError);
				return null;
			}
		}
	}

	private final Safe safeToast = new Safe(this, true);
	private final Safe safe = new Safe(this, false);

	public final Safe safe(boolean showToastOnError) {
		return showToastOnError ? safeToast : safe;
	}
}
