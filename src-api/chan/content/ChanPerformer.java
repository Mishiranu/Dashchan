/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.graphics.Bitmap;
import android.net.Uri;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.ThreadSummary;
import chan.content.model.Threads;
import chan.http.ChanFileOpenable;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.CaptchaManager;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.net.CloudFlarePasser;

public class ChanPerformer implements ChanManager.Linked
{
	private String mChanName;
	
	public ChanPerformer()
	{
		this(false);
	}
	
	ChanPerformer(boolean defaultInstance)
	{
		if (defaultInstance) mChanName = null; else
		{
			ChanManager.checkInstancesAndThrow();
			mChanName = ChanManager.initializingChanName;
		}
	}
	
	@Override
	public final String getChanName()
	{
		return mChanName;
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends ChanPerformer> T get(String chanName)
	{
		return (T) ChanManager.getInstance().getPerformer(chanName, true);
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends ChanPerformer> T get(Object object)
	{
		ChanManager manager = ChanManager.getInstance();
		return (T) manager.getPerformer(manager.getLinkedChanName(object), false);
	}
	
	// Can be overridden
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws HttpException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		return new ReadContentResult(new HttpRequest(data.uri, data.holder, data).read());
	}
	
	// Can be overridden
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		if (mChanName == null && data.requirement != null &&
				data.requirement.startsWith(CloudFlarePasser.CAPTCHA_REQUIREMENT))
		{
			return CloudFlarePasser.readCaptcha(data);
		}
		return new ReadCaptchaResult(CaptchaState.SKIP, null);
	}
	
	// Can be overridden
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	// Can be overridden
	public SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		throw new UnsupportedOperationException();
	}
	
	public static class ReadThreadsData implements HttpRequest.Preset
	{
		public static final int PAGE_NUMBER_CATALOG = -1;
		
		public final String boardName;
		public final int pageNumber;
		public final HttpHolder holder;
		public final HttpValidator validator;
		
		public ReadThreadsData(String boardName, int pageNumber, HttpHolder holder, HttpValidator validator)
		{
			this.boardName = boardName;
			this.pageNumber = pageNumber;
			this.holder = holder;
			this.validator = validator;
		}
		
		public boolean isCatalog()
		{
			return pageNumber == PAGE_NUMBER_CATALOG;
		}
	}
	
	public static class ReadThreadsResult
	{
		public final Threads threads;
		public HttpValidator validator;
		
		public ReadThreadsResult(Threads threads)
		{
			this.threads = threads;
		}
		
		public ReadThreadsResult(Posts... threads)
		{
			this(threads != null ? new Threads(threads) : null);
		}
		
		public ReadThreadsResult(Collection<Posts> threads)
		{
			this(threads != null && !threads.isEmpty() ? threads.toArray(new Posts[threads.size()]) : null);
		}
		
		public ReadThreadsResult setValidator(HttpValidator validator)
		{
			this.validator = validator;
			return this;
		}
	}
	
	public static class ReadPostsData implements HttpRequest.Preset
	{
		public final String boardName;
		public final String threadNumber;
		public final String lastPostNumber;
		public final boolean partialThreadLoading;
		public final Posts cachedPosts;
		public final HttpHolder holder;
		public final HttpValidator validator;
		
		public ReadPostsData(String boardName, String threadNumber, String lastPostNumber, boolean partialThreadLoading,
				Posts cachedPosts, HttpHolder holder, HttpValidator validator)
		{
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.lastPostNumber = lastPostNumber;
			this.partialThreadLoading = partialThreadLoading;
			this.cachedPosts = cachedPosts;
			this.holder = holder;
			this.validator = validator;
		}
	}
	
	public static class ReadPostsResult
	{
		public final Posts posts;
		public HttpValidator validator;
		public boolean fullThread;
		
		public ReadPostsResult(Posts posts)
		{
			this.posts = posts;
		}
		
		public ReadPostsResult(Post... posts)
		{
			this(posts != null ? new Posts(posts) : null);
		}
		
		public ReadPostsResult(Collection<Post> posts)
		{
			this(posts != null && !posts.isEmpty() ? posts.toArray(new Post[posts.size()]) : null);
		}
		
		public ReadPostsResult setValidator(HttpValidator validator)
		{
			this.validator = validator;
			return this;
		}
		
		public ReadPostsResult setFullThread(boolean fullThread)
		{
			this.fullThread = fullThread;
			return this;
		}
	}
	
	public static class ReadSinglePostData implements HttpRequest.Preset
	{
		public final String boardName;
		public final String postNumber;
		public final HttpHolder holder;
		
		public ReadSinglePostData(String boardName, String postNumber, HttpHolder holder)
		{
			this.boardName = boardName;
			this.postNumber = postNumber;
			this.holder = holder;
		}
	}
	
	public static class ReadSinglePostResult
	{
		public final Post post;
		
		public ReadSinglePostResult(Post post)
		{
			this.post = post;
		}
	}
	
	public static class ReadSearchPostsData implements HttpRequest.Preset
	{
		public final String boardName;
		public final String searchQuery;
		public final HttpHolder holder;
		
		public ReadSearchPostsData(String boardName, String searchQuery, HttpHolder holder)
		{
			this.boardName = boardName;
			this.searchQuery = searchQuery;
			this.holder = holder;
		}
	}
	
	public static class ReadSearchPostsResult
	{
		public final Post[] posts;
		
		public ReadSearchPostsResult(Post... posts)
		{
			this.posts = posts;
		}
		
		public ReadSearchPostsResult(Collection<Post> posts)
		{
			this(posts != null && !posts.isEmpty() ? posts.toArray(new Post[posts.size()]) : null);
		}
	}
	
	public static class ReadArchivedThreadsData implements HttpRequest.Preset
	{
		public final String boardName;
		public final HttpHolder holder;
		
		public ReadArchivedThreadsData(String boardName, HttpHolder holder)
		{
			this.boardName = boardName;
			this.holder = holder;
		}
	}
	
	public static class ReadBoardsData implements HttpRequest.Preset
	{
		public final HttpHolder holder;
		
		public ReadBoardsData(HttpHolder holder)
		{
			this.holder = holder;
		}
	}
	
	public static class ReadBoardsResult
	{
		public final BoardCategory[] boardCategories;
		
		public ReadBoardsResult(BoardCategory... boardCategories)
		{
			this.boardCategories = boardCategories;
		}
		
		public ReadBoardsResult(Collection<BoardCategory> boardCategories)
		{
			this(boardCategories != null && !boardCategories.isEmpty() ? boardCategories
					.toArray(new BoardCategory[boardCategories.size()]) : null);
		}
	}
	
	public static class ReadUserBoardsData implements HttpRequest.Preset
	{
		public final HttpHolder holder;
		
		public ReadUserBoardsData(HttpHolder holder)
		{
			this.holder = holder;
		}
	}
	
	public static class ReadUserBoardsResult
	{
		public final Board[] boards;
		
		public ReadUserBoardsResult(Board... boards)
		{
			this.boards = boards;
		}
		
		public ReadUserBoardsResult(Collection<Board> boards)
		{
			this(boards != null && !boards.isEmpty() ? boards.toArray(new Board[boards.size()]) : null);
		}
	}
	
	public static class ReadThreadSummariesData implements HttpRequest.Preset
	{
		public static final int TYPE_ARCHIVED_THREADS = 0;
		public static final int TYPE_POPULAR_THREADS = 1;

		public final String boardName;
		public final int type;
		public final HttpHolder holder;
		
		public ReadThreadSummariesData(String boardName, int type, HttpHolder holder)
		{
			this.boardName = boardName;
			this.type = type;
			this.holder = holder;
		}
	}
	
	public static class ReadThreadSummariesResult
	{
		public final ThreadSummary[] threadSummaries;
		
		public ReadThreadSummariesResult(ThreadSummary... threadSummaries)
		{
			this.threadSummaries = threadSummaries;
		}
		
		public ReadThreadSummariesResult(Collection<ThreadSummary> threadSummaries)
		{
			this(threadSummaries != null && !threadSummaries.isEmpty() ? threadSummaries
					.toArray(new ThreadSummary[threadSummaries.size()]) : null);
		}
	}
	
	public static class ReadPostsCountData implements HttpRequest.TimeoutsPreset
	{
		public final String boardName;
		public final String threadNumber;
		public final int connectTimeout;
		public final int readTimeout;
		public final HttpHolder holder;
		public final HttpValidator validator;
		
		public ReadPostsCountData(String boardName, String threadNumber, int connectTimeout, int readTimeout,
				HttpHolder holder, HttpValidator validator)
		{
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			this.holder = holder;
			this.validator = validator;
		}
		
		@Override
		public int getConnectTimeout()
		{
			return connectTimeout;
		}
		
		@Override
		public int getReadTimeout()
		{
			return readTimeout;
		}
	}
	
	public static class ReadPostsCountResult
	{
		public final int postsCount;
		public HttpValidator validator;
		
		public ReadPostsCountResult(int postsCount)
		{
			this.postsCount = postsCount;
		}
		
		public ReadPostsCountResult setValidator(HttpValidator validator)
		{
			this.validator = validator;
			return this;
		}
	}
	
	public static class ReadContentData implements HttpRequest.TimeoutsPreset, HttpRequest.InputListenerPreset,
			HttpRequest.OutputStreamPreset
	{
		public final Uri uri;
		public final int connectTimeout;
		public final int readTimeout;
		public final HttpHolder holder;
		public final HttpHolder.InputListener listener;
		public final OutputStream outputStream;
		
		public ReadContentData(Uri uri, int connectTimeout, int readTimeout, HttpHolder holder,
				HttpHolder.InputListener listener, OutputStream outputStream)
		{
			this.uri = uri;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			this.holder = holder;
			this.listener = listener;
			this.outputStream = outputStream;
		}
		
		@Override
		public int getConnectTimeout()
		{
			return connectTimeout;
		}
		
		@Override
		public int getReadTimeout()
		{
			return readTimeout;
		}
		
		@Override
		public HttpHolder.InputListener getInputListener()
		{
			return listener;
		}
		
		@Override
		public OutputStream getOutputStream()
		{
			return outputStream;
		}
	}
	
	public static class ReadContentResult
	{
		public final HttpResponse response;
		
		public ReadContentResult(HttpResponse response)
		{
			this.response = response;
		}
	}
	
	public static class CheckAuthorizationData implements HttpRequest.Preset
	{
		public static final int TYPE_CAPTCHA_PASS = 0;
		public static final int TYPE_USER_AUTHORIZATION = 1;
		
		public final int type;
		public final String[] authorizationData;
		public final HttpHolder holder;
		
		public CheckAuthorizationData(int type, String[] authorizationData, HttpHolder holder)
		{
			this.type = type;
			this.authorizationData = authorizationData;
			this.holder = holder;
		}
	}
	
	public static class CheckAuthorizationResult
	{
		public final boolean success;
		
		public CheckAuthorizationResult(boolean success)
		{
			this.success = success;
		}
	}
	
	public static class ReadCaptchaData implements HttpRequest.Preset
	{
		public static final String REQUIREMENT_NEW_THREAD = "new_thread";
		public static final String REQUIREMENT_REPLY_TO_THREAD = "reply_to_thread";
		
		public final String captchaType;
		public final String[] captchaPass;
		public final boolean mayShowLoadButton;
		public final String requirement;
		public final String boardName;
		public final String threadNumber;
		public final HttpHolder holder;
		
		public ReadCaptchaData(String captchaType, String[] captchaPass, boolean mayShowLoadButton,
				String requirement, String boardName, String threadNumber, HttpHolder holder)
		{
			this.captchaType = captchaType;
			this.captchaPass = captchaPass;
			this.mayShowLoadButton = mayShowLoadButton;
			this.requirement = requirement;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.holder = holder;
		}
	}
	
	public static enum CaptchaState {CAPTCHA, SKIP, PASS, NEED_LOAD};
	
	public static class ReadCaptchaResult
	{
		public final CaptchaState captchaState;
		public final CaptchaData captchaData;
		
		public String captchaType;
		public ChanConfiguration.Captcha.Input input;
		public ChanConfiguration.Captcha.Validity validity;
		public Bitmap image;
		public boolean large;
		
		public ReadCaptchaResult(CaptchaState captchaState, CaptchaData captchaData)
		{
			this.captchaState = captchaState;
			this.captchaData = captchaData;
		}
		
		public ReadCaptchaResult setCaptchaType(String captchaType)
		{
			this.captchaType = captchaType;
			return this;
		}
		
		public ReadCaptchaResult setInput(ChanConfiguration.Captcha.Input input)
		{
			this.input = input;
			return this;
		}
		
		public ReadCaptchaResult setValidity(ChanConfiguration.Captcha.Validity validity)
		{
			this.validity = validity;
			return this;
		}
		
		public ReadCaptchaResult setImage(Bitmap image)
		{
			this.image = image;
			return this;
		}
		
		public ReadCaptchaResult setLarge(boolean large)
		{
			this.large = large;
			return this;
		}
		
		// TODO CHAN
		// Remove this constructor after updating
		// infinite
		// Added: 17.03.16 03:28
		public ReadCaptchaResult(CaptchaState captchaState, CaptchaData captchaData, Bitmap image)
		{
			this(captchaState, captchaData);
			this.image = image;
		}
	}
	
	public static class CaptchaData implements Serializable
	{
		private static final long serialVersionUID = 1L;

		public static final String CHALLENGE = "challenge";
		public static final String INPUT = "input";
		public static final String API_KEY = "api_key";
		
		private final HashMap<String, String> mData = new HashMap<>();
		
		public void put(String key, String value)
		{
			mData.put(key, value);
		}
		
		public String get(String key)
		{
			return mData.get(key);
		}
	}
	
	public static class SendPostData implements HttpRequest.TimeoutsPreset, HttpRequest.OutputListenerPreset
	{
		public final String boardName;
		public final String threadNumber;
		
		public final String subject;
		public final String comment;
		public final String name;
		public final String email;
		public final String password;
		
		public final Attachment[] attachments;
		
		public final boolean optionSage;
		public final boolean optionSpoiler;
		public final boolean optionOriginalPoster;
		public final String userIcon;
		
		public String captchaType;
		public final CaptchaData captchaData;
		
		public final int connectTimeout;
		public final int readTimeout;
		public HttpHolder holder;
		public HttpRequest.OutputListener listener;
		
		public static class Attachment
		{
			public final FileHolder fileHolder;
			public final String rating;
			public final boolean optionUniqueHash, optionRemoveMetadata, optionRemoveFileName, optionSpoiler;
			
			public MultipartEntity.OpenableOutputListener listener;
			
			private ChanFileOpenable mOpenable;
			
			public Attachment(FileHolder fileHolder, String rating, boolean optionUniqueHash,
					boolean optionRemoveMetadata, boolean optionRemoveFilename, boolean optionSpoiler)
			{
				this.fileHolder = fileHolder;
				this.rating = rating;
				this.optionUniqueHash = optionUniqueHash;
				this.optionRemoveMetadata = optionRemoveMetadata;
				this.optionRemoveFileName = optionRemoveFilename;
				this.optionSpoiler = optionSpoiler;
			}
			
			private void ensureOpenable()
			{
				if (mOpenable == null)
				{
					mOpenable = new ChanFileOpenable(fileHolder, optionUniqueHash, optionRemoveMetadata,
							optionRemoveFileName);
				}
			}
			
			public void addToEntity(MultipartEntity entity, String name)
			{
				ensureOpenable();
				entity.add(name, mOpenable, listener);
			}
			
			public String getFileName()
			{
				ensureOpenable();
				return mOpenable.getFileName();
			}
			
			public String getMimeType()
			{
				ensureOpenable();
				return mOpenable.getMimeType();
			}
			
			public InputStream openInputSteam() throws IOException
			{
				ensureOpenable();
				return mOpenable.openInputStream();
			}
			
			public long getSize()
			{
				ensureOpenable();
				return mOpenable.getSize();
			}
			
			@Override
			public boolean equals(Object o)
			{
				if (o == this) return true;
				if (o instanceof Attachment)
				{
					Attachment attachment = (Attachment) o;
					return attachment.fileHolder.equals(fileHolder) && StringUtils.equals(attachment.rating, rating) &&
							attachment.optionUniqueHash == optionUniqueHash && attachment.optionRemoveMetadata ==
							optionRemoveMetadata && attachment.optionRemoveFileName == optionRemoveFileName &&
							attachment.optionSpoiler == optionSpoiler;
				}
				return false; 
			}
			
			@Override
			public int hashCode()
			{
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
				CaptchaData captchaData, int connectTimeout, int readTimeout)
		{
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
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
		}
		
		@Override
		public int getConnectTimeout()
		{
			return connectTimeout;
		}
		
		@Override
		public int getReadTimeout()
		{
			return readTimeout;
		}
		
		@Override
		public HttpRequest.OutputListener getOutputListener()
		{
			return listener;
		}
	}
	
	public static class SendPostResult
	{
		public final String threadNumber;
		public final String postNumber;
		
		public SendPostResult(String threadNumber, String postNumber)
		{
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
		}
	}
	
	public static class SendDeletePostsData implements HttpRequest.Preset
	{
		public final String boardName;
		public final String threadNumber;
		public final List<String> postNumbers;
		public final String password;
		public final boolean optionFilesOnly;
		public final HttpHolder holder;
		
		public SendDeletePostsData(String boardName, String threadNumber, List<String> postNumbers, String password,
				boolean optionFilesOnly, HttpHolder holder)
		{
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumbers = postNumbers;
			this.password = password;
			this.optionFilesOnly = optionFilesOnly;
			this.holder = holder;
		}
	}
	
	public static class SendDeletePostsResult
	{
		
	}
	
	public static class SendReportPostsData implements HttpRequest.Preset
	{
		public final String boardName;
		public final String threadNumber;
		public final List<String> postNumbers;
		public final String type;
		public final List<String> options;
		public final String comment;
		public final HttpHolder holder;
		
		public SendReportPostsData(String boardName, String threadNumber, List<String> postNumbers, String type,
				List<String> options, String comment, HttpHolder holder)
		{
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumbers = postNumbers;
			this.type = type;
			this.options = options != null ? Collections.unmodifiableList(options) : null;
			this.comment = comment;
			this.holder = holder;
		}
	}
	
	public static class SendReportPostsResult
	{
		
	}
	
	public static class SendAddToArchiveData implements HttpRequest.Preset
	{
		public final Uri uri;
		public final String boardName;
		public final String threadNumber;
		public final List<String> options;
		public final HttpHolder holder;
		
		public SendAddToArchiveData(Uri uri, String boardName, String threadNumber, List<String> options,
				HttpHolder holder)
		{
			this.uri = uri;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.options = options != null ? Collections.unmodifiableList(options) : null;
			this.holder = holder;
		}
	}
	
	public static class SendAddToArchiveResult
	{
		public final String boardName;
		public final String threadNumber;
		
		public SendAddToArchiveResult(String boardName, String threadNumber)
		{
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}
	}
	
	public final CaptchaData requireUserCaptcha(String requirement, String boardName, String threadNumber,
			boolean retry)
	{
		return CaptchaManager.getInstance().requireUserCaptcha(this, requirement, boardName, threadNumber, retry);
	}
	
	public final Integer requireUserImageSingleChoice(int selected, Bitmap[] images, String descriptionText,
			Bitmap descriptionImage)
	{
		return CaptchaManager.getInstance().requireUserImageSingleChoice(3, selected, images,
				descriptionText, descriptionImage);
	}
	
	public final boolean[] requireUserImageMultipleChoice(boolean[] selected, Bitmap[] images, String descriptionText,
			Bitmap descriptionImage)
	{
		return CaptchaManager.getInstance().requireUserImageMultipleChoice(3, selected, images,
				descriptionText, descriptionImage);
	}
}