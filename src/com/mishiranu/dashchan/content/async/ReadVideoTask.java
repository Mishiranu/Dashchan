package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadVideoTask extends HttpHolderTask<long[], Boolean> {
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 15000;

	private static final Pattern PATTERN_BYTES = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

	private final Callback callback;
	private final Chan chan;
	private final Uri uri;
	private final long start;
	private final File file;
	private final File partialFile;

	private ErrorItem errorItem;
	private boolean disallowRangeRequests;

	public interface Callback {
		void onReadVideoInit(File partialFile);
		void onReadVideoProgressUpdate(long progress, long progressMax);
		void onReadVideoRangeUpdate(long start, long end);
		void onReadVideoSuccess(boolean partial, File file);
		void onReadVideoFail(boolean partial, ErrorItem errorItem, boolean disallowRangeRequests);
	}

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			notifyProgress(new long[] {progress, progressMax});
		}
	};

	public ReadVideoTask(Callback callback, Chan chan, Uri uri, long start) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.uri = uri;
		this.start = start;
		file = CacheManager.getInstance().getMediaFile(uri, false);
		partialFile = CacheManager.getInstance().getPartialMediaFile(uri);
	}

	@Override
	protected Boolean run(HttpHolder holder) {
		if (file == null || partialFile == null) {
			errorItem = new ErrorItem(ErrorItem.Type.NO_ACCESS_TO_MEMORY);
			return false;
		}
		boolean success = false;
		try {
			TimedProgressHandler progressHandler = start > 0 ? null : this.progressHandler;
			ChanPerformer.ReadContentResult result = chan.performer.safe()
					.onReadContent(new ChanPerformer.ReadContentData(uri,
							CONNECT_TIMEOUT, READ_TIMEOUT, holder, start > 0 ? start : -1, -1));
			HttpResponse response = result != null ? result.response : null;
			if (response == null) {
				errorItem = new ErrorItem(ErrorItem.Type.DOWNLOAD);
				return false;
			}
			if (start > 0) {
				List<String> headers = response.getHeaderFields().get("Content-Range");
				if (headers == null || headers.size() != 1) {
					Log.persistent().write("Not a partial response");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
					disallowRangeRequests = true;
					return false;
				}
				String contentRange = headers.get(0);
				Matcher matcher = PATTERN_BYTES.matcher(contentRange);
				if (!matcher.matches()) {
					Log.persistent().write("Invalid header: " + contentRange);
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
					disallowRangeRequests = true;
					return false;
				}
				long responseStart = Long.parseLong(matcher.group(1));
				long responseEnd = Long.parseLong(matcher.group(2)) + 1;
				long responseTotal = Long.parseLong(matcher.group(3));
				if (responseEnd <= responseStart || responseTotal != responseEnd) {
					Log.persistent().write("Invalid header data range");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
					return false;
				}
				if (Math.max(1, responseStart - 100000) > start) {
					Log.persistent().write("Invalid data range start");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
					return false;
				}
			}
			if (start <= 0) {
				progressHandler.setInputProgressMax(response.getLength());
			}
			try (InputStream input = response.open();
					RandomAccessFile file = new RandomAccessFile(partialFile, "rw")) {
				if (start <= 0) {
					ConcurrentUtils.mainGet(() -> {
						callback.onReadVideoInit(partialFile);
						return null;
					});
				} else {
					file.seek(start);
				}
				int count;
				long read = 0;
				byte[] buffer = new byte[8192];
				while ((count = input.read(buffer)) > 0) {
					file.write(buffer, 0, count);
					read += count;
					if (start <= 0) {
						progressHandler.updateProgress(read);
					} else {
						notifyProgress(new long[] {read});
					}
				}
			} catch (IOException e) {
				ErrorItem.Type errorType = ReadFileTask.getErrorTypeFromExceptionAndHandle(e);
				if (errorType != null) {
					errorItem = new ErrorItem(errorType);
					return false;
				} else {
					throw response.fail(e);
				}
			} finally {
				response.cleanupAndDisconnect();
			}
			success = true;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			if (start <= 0) {
				file.delete();
				if (success) {
					partialFile.renameTo(file);
				} else {
					partialFile.delete();
				}
				CacheManager.getInstance().handleDownloadedFile(partialFile, false);
				CacheManager.getInstance().handleDownloadedFile(file, success);
				if (chan.name != null) {
					chan.configuration.commit();
				}
			}
		}
	}

	@Override
	protected void onProgress(long[] values) {
		if (start > 0) {
			callback.onReadVideoRangeUpdate(start, start + values[0]);
		} else {
			callback.onReadVideoProgressUpdate(values[0], values[1]);
		}
	}

	@Override
	protected void onComplete(Boolean success) {
		if (success) {
			callback.onReadVideoSuccess(start > 0, file);
		} else {
			callback.onReadVideoFail(start > 0, errorItem, disallowRangeRequests);
		}
	}

	public boolean isError() {
		return errorItem != null;
	}
}
