package chan.content;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import androidx.annotation.NonNull;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.SinglePost;
import chan.content.model.ThreadSummary;
import chan.http.ChanFileOpenable;
import chan.http.FirewallResolver;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.GraphicsUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Extendable
public class ChanPerformer implements Chan.Linked {
	private final Chan.Provider chanProvider;
	private final ArrayList<FirewallResolver> firewallResolvers = new ArrayList<>(0);

	private boolean isInitialized = false;

	static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

	@Public
	public ChanPerformer() {
		this(null);
	}

	ChanPerformer(Chan.Provider chanProvider) {
		if (chanProvider == null) {
			ChanManager.Initializer.Holder holder = INITIALIZER.consume();
			this.chanProvider = holder.chanProvider;
		} else {
			this.chanProvider = chanProvider;
		}
	}

	@Override
	public final void init() {
		isInitialized = true;
	}

	@Override
	public Chan get() {
		return chanProvider.get();
	}

	@Public
	public static ChanPerformer get(Object object) {
		return ((Chan.Linked) object).get().performer;
	}

	private void checkInit() {
		if (isInitialized) {
			throw new IllegalStateException("This method available only from constructor");
		}
	}

	@Public
	protected final void registerFirewallResolver(FirewallResolver firewallResolver) {
		Objects.requireNonNull(firewallResolver);
		checkInit();
		firewallResolvers.add(firewallResolver);
	}

	public List<FirewallResolver> getFirewallResolvers() {
		return firewallResolvers;
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException,
			RedirectException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException,
			RedirectException, ThreadRedirectException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
		return new ReadContentResult(new HttpRequest(data.uri, data.direct).perform());
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		return new ReadCaptchaResult(CaptchaState.SKIP, null);
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("RedundantThrows")
	@Extendable
	protected SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws HttpException, ApiException,
			InvalidResponseException {
		throw new UnsupportedOperationException();
	}

	@Public
	public static class ReadThreadsData implements HttpRequest.Preset {
		@Public public static final int PAGE_NUMBER_CATALOG = -1;

		@Public public final String boardName;
		@Public public final int pageNumber;
		public final HttpHolder holder;
		@Public public final HttpValidator validator;

		public ReadThreadsData(String boardName, int pageNumber, HttpHolder holder, HttpValidator validator) {
			this.boardName = boardName;
			this.pageNumber = pageNumber;
			this.holder = holder;
			this.validator = validator;
		}

		@Public
		public boolean isCatalog() {
			return pageNumber == PAGE_NUMBER_CATALOG;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadThreadsResult {
		public static class Thread {
			public final List<com.mishiranu.dashchan.content.model.Post> posts;
			public final String threadNumber;
			public final int postsCount;
			public final int filesCount;
			public final int postsWithFilesCount;

			public Thread(List<com.mishiranu.dashchan.content.model.Post> posts, String threadNumber,
					int postsCount, int filesCount, int postsWithFilesCount) {
				this.posts = posts;
				this.threadNumber = threadNumber;
				this.postsCount = postsCount;
				this.filesCount = filesCount;
				this.postsWithFilesCount = postsWithFilesCount;
			}
		}

		public final List<Thread> threads;

		public int boardSpeed;
		public HttpValidator validator;

		@Public
		public ReadThreadsResult(Posts... threads) {
			this(threads != null ? Arrays.asList(threads) : null);
		}

		@Public
		public ReadThreadsResult(Collection<Posts> threads) {
			List<Thread> list = Collections.emptyList();
			if (threads != null) {
				list = new ArrayList<>(threads.size());
				for (Posts thread : threads) {
					if (thread != null) {
						Post[] postsArray = thread.getPosts();
						if (postsArray != null) {
							String threadNumber = null;
							List<com.mishiranu.dashchan.content.model.Post> posts = new ArrayList<>();
							for (Post post : postsArray) {
								if (post != null) {
									if (posts.isEmpty()) {
										threadNumber = post.getThreadNumberOrOriginalPostNumber();
									}
									posts.add(post.build());
								}
							}
							if (!posts.isEmpty()) {
								if (StringUtils.isEmpty(threadNumber)) {
									throw new IllegalArgumentException("Thread number is not defined");
								}
								list.add(new Thread(posts, threadNumber, thread.getPostsCount(),
										thread.getFilesCount(), thread.getPostsWithFilesCount()));
							}
						}
					}
				}
			}
			this.threads = list;
		}

		@Public
		public ReadThreadsResult setBoardSpeed(int boardSpeed) {
			this.boardSpeed = boardSpeed;
			return this;
		}

		@Public
		public ReadThreadsResult setValidator(HttpValidator validator) {
			this.validator = validator;
			return this;
		}
	}

	@Public
	public static class ReadPostsData implements HttpRequest.Preset {
		@Public public final String boardName;
		@Public public final String threadNumber;
		@Public public final String lastPostNumber;
		@Public public final boolean partialThreadLoading;
		@Public public final Posts cachedPosts;
		public final HttpHolder holder;
		@Public public final HttpValidator validator;

		public ReadPostsData(String chanName, String boardName, String threadNumber,
				String lastPostNumber, boolean partialThreadLoading,
				boolean hasCachedPosts, HttpHolder holder, HttpValidator validator) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.lastPostNumber = lastPostNumber;
			this.partialThreadLoading = partialThreadLoading;
			this.cachedPosts = hasCachedPosts ? new Posts(chanName, boardName, threadNumber) : null;
			this.holder = holder;
			this.validator = validator;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadPostsResult {
		public final List<com.mishiranu.dashchan.content.model.Post> posts;
		public final Uri archivedThreadUri;
		public final int uniquePosters;

		public HttpValidator validator;
		public boolean fullThread;

		@Public
		public ReadPostsResult(Posts posts) {
			this(posts != null && posts.getPosts() != null ? Arrays.asList(posts.getPosts()) : null,
					posts != null ? posts.getArchivedThreadUri() : null, posts != null ? posts.getUniquePosters() : 0);
		}

		@Public
		public ReadPostsResult(Post... posts) {
			this(posts != null ? Arrays.asList(posts) : null, null, 0);
		}

		@Public
		public ReadPostsResult(Collection<Post> posts) {
			this(posts, null, 0);
		}

		private ReadPostsResult(Collection<Post> posts, Uri archivedThreadUri, int uniquePosters) {
			List<com.mishiranu.dashchan.content.model.Post> list = Collections.emptyList();
			if (posts != null) {
				list = new ArrayList<>(posts.size());
				for (Post post : posts) {
					list.add(post.build());
				}
			}
			this.posts = list;
			this.archivedThreadUri = archivedThreadUri;
			this.uniquePosters = uniquePosters;
		}

		@Public
		public ReadPostsResult setValidator(HttpValidator validator) {
			this.validator = validator;
			return this;
		}

		@Public
		public ReadPostsResult setFullThread(boolean fullThread) {
			this.fullThread = fullThread;
			return this;
		}
	}

	@Public
	public static class ReadSinglePostData implements HttpRequest.Preset {
		@Public public final String boardName;
		@Public public final String postNumber;
		public final HttpHolder holder;

		public ReadSinglePostData(String boardName, String postNumber, HttpHolder holder) {
			this.boardName = boardName;
			this.postNumber = postNumber;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadSinglePostResult {
		public final SinglePost post;

		@Public
		public ReadSinglePostResult(Post post) {
			this.post = post != null ? new SinglePost(post) : null;
		}
	}

	@Public
	public static class ReadSearchPostsData implements HttpRequest.Preset {
		@Public public final String boardName;
		@Public public final String searchQuery;
		@Public public final int pageNumber;
		public final HttpHolder holder;

		public ReadSearchPostsData(String boardName, String searchQuery, int pageNumber, HttpHolder holder) {
			this.boardName = boardName;
			this.searchQuery = searchQuery;
			this.pageNumber = pageNumber;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadSearchPostsResult {
		public final List<SinglePost> posts;

		@Public
		public ReadSearchPostsResult(Post... posts) {
			this(posts != null ? Arrays.asList(posts) : null);
		}

		@Public
		public ReadSearchPostsResult(Collection<Post> posts) {
			List<SinglePost> list = Collections.emptyList();
			if (posts != null) {
				list = new ArrayList<>(posts.size());
				for (Post post : posts) {
					if (post != null) {
						list.add(new SinglePost(post));
					}
				}
			}
			this.posts = list;
		}
	}

	@Public
	public static final class ReadBoardsData implements HttpRequest.Preset {
		public final HttpHolder holder;

		public ReadBoardsData(HttpHolder holder) {
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadBoardsResult {
		public final BoardCategory[] boardCategories;

		@Public
		public ReadBoardsResult(BoardCategory... boardCategories) {
			if (boardCategories != null) {
				for (int i = 0; i < boardCategories.length; i++) {
					if (boardCategories[i] != null) {
						Board[] boards = boardCategories[i].getBoards();
						if (boards == null || boards.length == 0) {
							boardCategories[i] = null;
						}
					}
				}
			}
			this.boardCategories = CommonUtils.removeNullItems(boardCategories, BoardCategory.class);
		}

		@Public
		public ReadBoardsResult(Collection<BoardCategory> boardCategories) {
			this(CommonUtils.toArray(boardCategories, BoardCategory.class));
		}
	}

	@Public
	public static class ReadUserBoardsData implements HttpRequest.Preset {
		public final HttpHolder holder;

		public ReadUserBoardsData(HttpHolder holder) {
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadUserBoardsResult {
		public final Board[] boards;

		@Public
		public ReadUserBoardsResult(Board... boards) {
			this.boards = CommonUtils.removeNullItems(boards, Board.class);
		}

		@Public
		public ReadUserBoardsResult(Collection<Board> boards) {
			this(CommonUtils.toArray(boards, Board.class));
		}
	}

	@Public
	public static class ReadThreadSummariesData implements HttpRequest.Preset {
		@Public public static final int TYPE_ARCHIVED_THREADS = 0;

		@Public public final String boardName;
		@Public public final int pageNumber;
		@Public public final int type;
		public final HttpHolder holder;

		public ReadThreadSummariesData(String boardName, int pageNumber, int type, HttpHolder holder) {
			this.boardName = boardName;
			this.pageNumber = pageNumber;
			this.type = type;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class ReadThreadSummariesResult {
		public final ThreadSummary[] threadSummaries;

		@Public
		public ReadThreadSummariesResult(ThreadSummary... threadSummaries) {
			this.threadSummaries = CommonUtils.removeNullItems(threadSummaries, ThreadSummary.class);
		}

		@Public
		public ReadThreadSummariesResult(Collection<ThreadSummary> threadSummaries) {
			this(CommonUtils.toArray(threadSummaries, ThreadSummary.class));
		}
	}

	@Public
	public static class ReadPostsCountData implements HttpRequest.TimeoutsPreset {
		@Public public final String boardName;
		@Public public final String threadNumber;
		public final int connectTimeout;
		public final int readTimeout;
		public final HttpHolder holder;
		@Public public final HttpValidator validator;

		public ReadPostsCountData(String boardName, String threadNumber, int connectTimeout, int readTimeout,
				HttpHolder holder, HttpValidator validator) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			this.holder = holder;
			this.validator = validator;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}

		@Override
		public int getConnectTimeout() {
			return connectTimeout;
		}

		@Override
		public int getReadTimeout() {
			return readTimeout;
		}
	}

	@Public
	public static final class ReadPostsCountResult {
		public final int postsCount;
		public HttpValidator validator;

		@Public
		public ReadPostsCountResult(int postsCount) {
			this.postsCount = postsCount;
		}

		@Public
		public ReadPostsCountResult setValidator(HttpValidator validator) {
			this.validator = validator;
			return this;
		}
	}

	private static class ReadContentDirectPreset implements HttpRequest.TimeoutsPreset, HttpRequest.RangePreset {
		public final int connectTimeout;
		public final int readTimeout;
		public final HttpHolder holder;
		public final long rangeStart;
		public final long rangeEnd;

		private ReadContentDirectPreset(int connectTimeout, int readTimeout, HttpHolder holder,
				long rangeStart, long rangeEnd) {
			this.holder = holder;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			this.rangeStart = rangeStart;
			this.rangeEnd = rangeEnd;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}

		@Override
		public int getConnectTimeout() {
			return connectTimeout;
		}

		@Override
		public int getReadTimeout() {
			return readTimeout;
		}

		@Override
		public long getRangeStart() {
			return rangeStart;
		}

		@Override
		public long getRangeEnd() {
			return rangeEnd;
		}
	}

	@Public
	public static class ReadContentData implements HttpRequest.TimeoutsPreset {
		@Public public final Uri uri;
		// TODO CHAN
		// Remove this field after updating
		// fourplebs
		// Added: 18.10.20 19:08
		public final HttpHolder holder;
		@Public public final HttpRequest.Preset direct;

		public ReadContentData(Uri uri, int connectTimeout, int readTimeout, HttpHolder holder,
				long rangeStart, long rangeEnd) {
			this.uri = uri;
			this.holder = holder;
			direct = new ReadContentDirectPreset(connectTimeout, readTimeout, holder, rangeStart, rangeEnd);
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}

		@Override
		public int getConnectTimeout() {
			return ((ReadContentDirectPreset) direct).getConnectTimeout();
		}

		@Override
		public int getReadTimeout() {
			return ((ReadContentDirectPreset) direct).getReadTimeout();
		}
	}

	@Public
	public static final class ReadContentResult {
		public final HttpResponse response;

		@Public
		public ReadContentResult(HttpResponse response) {
			this.response = response;
		}
	}

	@Public
	public static class CheckAuthorizationData implements HttpRequest.Preset {
		@Public public static final int TYPE_CAPTCHA_PASS = 0;
		@Public public static final int TYPE_USER_AUTHORIZATION = 1;

		@Public public final int type;
		@Public public final String[] authorizationData;
		public final HttpHolder holder;

		public CheckAuthorizationData(int type, String[] authorizationData, HttpHolder holder) {
			this.type = type;
			this.authorizationData = authorizationData;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class CheckAuthorizationResult {
		public final boolean success;

		@Public
		public CheckAuthorizationResult(boolean success) {
			this.success = success;
		}
	}

	@Public
	public static class ReadCaptchaData implements HttpRequest.Preset {
		@Public public final String captchaType;
		@Public public final String[] captchaPass;
		@Public public final boolean mayShowLoadButton;
		@Public public final String requirement;
		@Public public final String boardName;
		@Public public final String threadNumber;
		public final HttpHolder holder;

		public ReadCaptchaData(String captchaType, String[] captchaPass, boolean mayShowLoadButton,
				String requirement, String boardName, String threadNumber, HttpHolder holder) {
			this.captchaType = captchaType;
			this.captchaPass = captchaPass;
			this.mayShowLoadButton = mayShowLoadButton;
			this.requirement = requirement;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public enum CaptchaState {
		@Public CAPTCHA,
		@Public SKIP,
		@Public PASS,
		@Public NEED_LOAD
	}

	@Public
	public static final class ReadCaptchaResult {
		public final CaptchaState captchaState;
		public final CaptchaData captchaData;

		public String captchaType;
		public ChanConfiguration.Captcha.Input input;
		public ChanConfiguration.Captcha.Validity validity;
		public Bitmap image;
		public boolean large;

		@Public
		public ReadCaptchaResult(CaptchaState captchaState, CaptchaData captchaData) {
			this.captchaState = captchaState;
			this.captchaData = captchaData;
		}

		@Public
		public ReadCaptchaResult setCaptchaType(String captchaType) {
			this.captchaType = captchaType;
			return this;
		}

		@Public
		public ReadCaptchaResult setInput(ChanConfiguration.Captcha.Input input) {
			this.input = input;
			return this;
		}

		@Public
		public ReadCaptchaResult setValidity(ChanConfiguration.Captcha.Validity validity) {
			this.validity = validity;
			return this;
		}

		@Public
		public ReadCaptchaResult setImage(Bitmap image) {
			this.image = image;
			return this;
		}

		@Public
		public ReadCaptchaResult setLarge(boolean large) {
			this.large = large;
			return this;
		}
	}

	@Public
	public static class CaptchaData implements Parcelable {
		@Public public static final String CHALLENGE = "challenge";
		@Public public static final String INPUT = "input";
		@Public public static final String API_KEY = "api_key";
		@Public public static final String REFERER = "referer";

		private final Map<String, String> data;

		@Public
		public CaptchaData() {
			this(new HashMap<>());
		}

		private CaptchaData(Map<String, String> data) {
			this.data = data;
		}

		@Public
		public void put(String key, String value) {
			data.put(key, value);
		}

		@Public
		public String get(String key) {
			return data.get(key);
		}

		public CaptchaData copy() {
			return new CaptchaData(new HashMap<>(data));
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(data.size());
			for (HashMap.Entry<String, String> entry : data.entrySet()) {
				dest.writeString(entry.getKey());
				dest.writeString(entry.getValue());
			}
		}

		public static final Creator<CaptchaData> CREATOR = new Creator<CaptchaData>() {
			@Override
			public CaptchaData createFromParcel(Parcel source) {
				int count = source.readInt();
				HashMap<String, String> data = new HashMap<>(count);
				for (int i = 0; i < count; i++) {
					data.put(source.readString(), source.readString());
				}
				return new CaptchaData(data);
			}

			@Override
			public CaptchaData[] newArray(int size) {
				return new CaptchaData[size];
			}
		};
	}

	@Public
	public static class SendPostData implements HttpRequest.TimeoutsPreset, HttpRequest.OutputListenerPreset {
		@Public public final String boardName;
		@Public public final String threadNumber;

		@Public public final String subject;
		@Public public final String comment;
		@Public public final String name;
		@Public public final String email;
		@Public public final String password;

		@Public public final Attachment[] attachments;

		@Public public final boolean optionSage;
		@Public public final boolean optionSpoiler;
		@Public public final boolean optionOriginalPoster;
		@Public public final String userIcon;

		@Public public final String captchaType;
		@Public public final CaptchaData captchaData;
		public final boolean captchaNeedLoad;

		public final int connectTimeout;
		public final int readTimeout;
		public HttpHolder holder;
		public HttpRequest.OutputListener listener;

		@Public
		public static class Attachment {
			public final FileHolder fileHolder;
			public final String fileName;
			@Public public final String rating;

			public final boolean optionUniqueHash;
			public final boolean optionRemoveMetadata;
			public final boolean optionRemoveFileName;
			@Public public final boolean optionSpoiler;
			public final GraphicsUtils.Reencoding reencoding;

			public MultipartEntity.OpenableOutputListener listener;

			private ChanFileOpenable openable;

			public Attachment(FileHolder fileHolder, String fileName, String rating, boolean optionUniqueHash,
					boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler,
					GraphicsUtils.Reencoding reencoding) {
				this.fileHolder = fileHolder;
				this.fileName = fileName;
				this.rating = rating;
				this.optionUniqueHash = optionUniqueHash;
				this.optionRemoveMetadata = optionRemoveMetadata;
				this.optionRemoveFileName = optionRemoveFileName;
				this.optionSpoiler = optionSpoiler;
				this.reencoding = reencoding;
			}

			private void ensureOpenable() {
				if (openable == null) {
					openable = new ChanFileOpenable(fileHolder, fileName, optionUniqueHash, optionRemoveMetadata,
							optionRemoveFileName, reencoding);
				}
			}

			@Public
			public void addToEntity(MultipartEntity entity, String name) {
				ensureOpenable();
				entity.add(name, openable, listener);
			}

			@Public
			public String getFileName() {
				ensureOpenable();
				return openable.getFileName();
			}

			@Public
			public String getMimeType() {
				ensureOpenable();
				return openable.getMimeType();
			}

			@Public
			public InputStream openInputSteam() throws IOException {
				ensureOpenable();
				return openable.openInputStream();
			}

			@Public
			public InputStream openInputSteamForSending() throws IOException {
				InputStream inputStream = openInputSteam();
				if (listener != null) {
					return new InputStreamForSending(inputStream, getSize());
				} else {
					return inputStream;
				}
			}

			@Public
			public long getSize() {
				ensureOpenable();
				return openable.getSize();
			}

			@Public
			public Pair<Integer, Integer> getImageSize() {
				ensureOpenable();
				int width = openable.getImageWidth();
				int height = openable.getImageHeight();
				if (width > 0 && height > 0) {
					return new Pair<>(width, height);
				} else {
					return null;
				}
			}

			private class InputStreamForSending extends InputStream {
				private final InputStream inputStream;
				private final long progressMax;

				public InputStreamForSending(InputStream inputStream, long progressMax) {
					this.inputStream = inputStream;
					this.progressMax = progressMax;
				}

				private long progress = 0;

				private void notify(int count) {
					progress += count;
					listener.onOutputProgressChange(openable, progress, progressMax);
				}

				@Override
				public int read() throws IOException {
					int result = inputStream.read();
					if (result != -1) {
						notify(1);
					}
					return result;
				}

				@Override
				public int read(@NonNull byte[] buffer) throws IOException {
					return read(buffer, 0, buffer.length);
				}

				@Override
				public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
					int result = inputStream.read(buffer, byteOffset, byteCount);
					if (result > 0) {
						notify(result);
					}
					return result;
				}

				@Override
				public void close() throws IOException {
					inputStream.close();
				}
			}

			@Override
			public boolean equals(Object o) {
				if (o == this) {
					return true;
				}
				if (o instanceof Attachment) {
					Attachment attachment = (Attachment) o;
					return attachment.fileHolder.equals(fileHolder) && CommonUtils.equals(attachment.rating, rating) &&
							attachment.optionUniqueHash == optionUniqueHash && attachment.optionRemoveMetadata ==
							optionRemoveMetadata && attachment.optionRemoveFileName == optionRemoveFileName &&
							attachment.optionSpoiler == optionSpoiler;
				}
				return false;
			}

			@Override
			public int hashCode() {
				int prime = 31;
				int result = 1;
				result = prime * result + fileHolder.hashCode();
				result = prime * result + (rating != null ? rating.hashCode() : 0);
				result = prime * result + (optionUniqueHash ? 1 : 0);
				result = prime * result + (optionRemoveMetadata ? 1 : 0);
				result = prime * result + (optionRemoveFileName ? 1 : 0);
				result = prime * result + (optionSpoiler ? 1 : 0);
				return result;
			}
		}

		public SendPostData(String boardName, String threadNumber, String subject, String comment,
				String name, String email, String password, Attachment[] attachments, boolean optionSage,
				boolean optionSpoiler, boolean optionOriginalPoster, String userIcon, String captchaType,
				CaptchaData captchaData, boolean captchaNeedLoad, int connectTimeout, int readTimeout) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.subject = subject;
			this.comment = comment;
			this.name = name;
			this.email = email;
			this.password = password;
			this.attachments = attachments;
			this.optionSage = optionSage;
			this.optionSpoiler = optionSpoiler;
			this.optionOriginalPoster = optionOriginalPoster;
			this.userIcon = userIcon;
			this.captchaType = captchaType;
			this.captchaData = captchaData;
			this.captchaNeedLoad = captchaNeedLoad;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}

		@Override
		public int getConnectTimeout() {
			return connectTimeout;
		}

		@Override
		public int getReadTimeout() {
			return readTimeout;
		}

		@Override
		public HttpRequest.OutputListener getOutputListener() {
			return listener;
		}
	}

	@Public
	public static final class SendPostResult {
		public final String threadNumber;
		public final PostNumber postNumber;

		@Public
		public SendPostResult(String threadNumber, String postNumber) {
			this.threadNumber = threadNumber;
			this.postNumber = postNumber != null ? PostNumber.parseOrThrow(postNumber) : null;
			PostNumber.validateThreadNumber(threadNumber, true);
		}
	}

	@Public
	public static class SendDeletePostsData implements HttpRequest.Preset {
		@Public public final String boardName;
		@Public public final String threadNumber;
		@Public public final List<String> postNumbers;
		@Public public final String password;
		@Public public final boolean optionFilesOnly;
		public final HttpHolder holder;

		public SendDeletePostsData(String boardName, String threadNumber, List<String> postNumbers, String password,
				boolean optionFilesOnly, HttpHolder holder) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumbers = postNumbers;
			this.password = password;
			this.optionFilesOnly = optionFilesOnly;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class SendDeletePostsResult {
		@Public
		public SendDeletePostsResult() {}
	}

	@Public
	public static class SendReportPostsData implements HttpRequest.Preset {
		@Public public final String boardName;
		@Public public final String threadNumber;
		@Public public final List<String> postNumbers;
		@Public public final String type;
		@Public public final List<String> options;
		@Public public final String comment;
		public final HttpHolder holder;

		public SendReportPostsData(String boardName, String threadNumber, List<String> postNumbers, String type,
				List<String> options, String comment, HttpHolder holder) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumbers = postNumbers;
			this.type = type;
			this.options = options != null ? Collections.unmodifiableList(options) : null;
			this.comment = comment;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class SendReportPostsResult {
		@Public
		public SendReportPostsResult() {}
	}

	@Public
	public static class SendAddToArchiveData implements HttpRequest.Preset {
		@Public public final Uri uri;
		@Public public final String boardName;
		@Public public final String threadNumber;
		@Public public final List<String> options;
		public final HttpHolder holder;

		public SendAddToArchiveData(Uri uri, String boardName, String threadNumber, List<String> options,
				HttpHolder holder) {
			this.uri = uri;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.options = options != null ? Collections.unmodifiableList(options) : null;
			this.holder = holder;
		}

		@Override
		public HttpHolder getHolder() {
			return holder;
		}
	}

	@Public
	public static final class SendAddToArchiveResult {
		public final String boardName;
		public final String threadNumber;

		@Public
		public SendAddToArchiveResult(String boardName, String threadNumber) {
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			PostNumber.validateThreadNumber(threadNumber, true);
		}
	}

	private final ThreadLocal<Boolean> requireCallState = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		}
	};

	private static class PerformerContext {
		public final boolean requireCallState;

		public PerformerContext(boolean requireCallState) {
			this.requireCallState = requireCallState;
		}
	}

	private PerformerContext enterContext() {
		boolean requireCallState = this.requireCallState.get();
		this.requireCallState.set(true);
		return new PerformerContext(requireCallState);
	}

	private void exitContext(PerformerContext context) {
		requireCallState.set(context.requireCallState);
	}

	private void checkPerformerRequireCall() {
		if (!requireCallState.get()) {
			throw new IllegalStateException("Invalid call state");
		}
	}

	@Public
	public final CaptchaData requireUserCaptcha(String requirement, String boardName, String threadNumber,
			boolean retry) throws HttpException {
		checkPerformerRequireCall();
		try {
			return ForegroundManager.getInstance().requireUserCaptcha(get(),
					requirement, boardName, threadNumber, retry);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	@Public
	public final Integer requireUserItemSingleChoice(int selected, CharSequence[] item, String descriptionText,
			Bitmap descriptionImage) throws HttpException {
		checkPerformerRequireCall();
		try {
			return ForegroundManager.getInstance().requireUserItemSingleChoice(selected, item,
					descriptionText, descriptionImage);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	@Public
	public final boolean[] requireUserItemMultipleChoice(boolean[] selected, CharSequence[] item,
			String descriptionText, Bitmap descriptionImage) throws HttpException {
		checkPerformerRequireCall();
		try {
			return ForegroundManager.getInstance().requireUserItemMultipleChoice(selected, item,
					descriptionText, descriptionImage);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	@Public
	public final Integer requireUserImageSingleChoice(int selected, Bitmap[] images, String descriptionText,
			Bitmap descriptionImage) throws HttpException {
		checkPerformerRequireCall();
		try {
			return ForegroundManager.getInstance().requireUserImageSingleChoice(3, selected, images,
					descriptionText, descriptionImage);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	@Public
	public final boolean[] requireUserImageMultipleChoice(boolean[] selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage) throws HttpException {
		checkPerformerRequireCall();
		try {
			return ForegroundManager.getInstance().requireUserImageMultipleChoice(3, selected, images,
					descriptionText, descriptionImage);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	public static final class Safe {
		private final ChanPerformer performer;

		private Safe(ChanPerformer performer) {
			this.performer = performer;
		}

		public ReadThreadsResult onReadThreads(ReadThreadsData data) throws ExtensionException, HttpException,
				InvalidResponseException, RedirectException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadThreads(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadPostsResult onReadPosts(ReadPostsData data) throws ExtensionException, HttpException,
				InvalidResponseException, RedirectException, ThreadRedirectException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadPosts(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadSinglePost(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws ExtensionException,
				HttpException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadSearchPosts(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadBoardsResult onReadBoards(ReadBoardsData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadBoards(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadUserBoards(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws ExtensionException,
				HttpException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadThreadSummaries(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadPostsCount(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadContentResult onReadContent(ReadContentData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadContent(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws ExtensionException,
				HttpException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onCheckAuthorization(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws ExtensionException, HttpException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onReadCaptcha(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public SendPostResult onSendPost(SendPostData data) throws ExtensionException, HttpException, ApiException,
				InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onSendPost(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws ExtensionException,
				HttpException, ApiException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onSendDeletePosts(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws ExtensionException,
				HttpException, ApiException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onSendReportPosts(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}

		public SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws ExtensionException,
				HttpException, ApiException, InvalidResponseException {
			PerformerContext context = performer.enterContext();
			try {
				return performer.onSendAddToArchive(data);
			} catch (LinkageError | RuntimeException e) {
				throw new ExtensionException(e);
			} finally {
				performer.exitContext(context);
			}
		}
	}

	private final Safe safe = new Safe(this);

	public final Safe safe() {
		return safe;
	}
}
