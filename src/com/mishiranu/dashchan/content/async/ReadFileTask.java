package com.mishiranu.dashchan.content.async;

import android.content.Context;
import android.net.Uri;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;
import chan.util.DataFile;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ReadFileTask extends HttpHolderTask<long[], Boolean> {
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 15000;

	public interface Callback {
		void onStartDownloading();
		void onFinishDownloading(boolean success, Uri uri, DataFile file, ErrorItem errorItem);
		default void onCancelDownloading() {}
		void onUpdateProgress(long progress, long progressMax);
	}

	public interface FileCallback extends Callback {
		void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem);

		@Override
		default void onFinishDownloading(boolean success, Uri uri, DataFile file, ErrorItem errorItem) {
			File realFile = file.getFileOrUri().first;
			if (realFile == null) {
				throw new IllegalStateException();
			}
			onFinishDownloading(success, uri, realFile, errorItem);
		}
	}

	private final Callback callback;
	private final Chan chan;
	private final Uri fromUri;
	private final DataFile toFile;
	private final File cachedMediaFile;
	private final boolean overwrite;
	private final byte[] checkSha256;

	private ErrorItem errorItem;

	private boolean loadingStarted;

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			notifyProgress(new long[] {progress, progressMax});
		}
	};

	public static ReadFileTask createCachedMediaFile(Context context, FileCallback callback, Chan chan,
			Uri fromUri, File cachedMediaFile) {
		DataFile toFile = DataFile.obtain(context, DataFile.Target.CACHE, cachedMediaFile.getName());
		return new ReadFileTask(callback, chan, fromUri, toFile, null, true, null);
	}

	public static ReadFileTask createShared(Callback callback, Chan chan,
			Uri fromUri, DataFile toFile, boolean overwrite, byte[] checkSha256) {
		File cachedMediaFile = CacheManager.getInstance().getMediaFile(fromUri, true);
		if (cachedMediaFile == null || !cachedMediaFile.exists() ||
				CacheManager.getInstance().cancelCachedMediaBusy(cachedMediaFile)) {
			cachedMediaFile = null;
		}
		return new ReadFileTask(callback, chan, fromUri, toFile, cachedMediaFile, overwrite, checkSha256);
	}

	private ReadFileTask(Callback callback, Chan chan, Uri fromUri, DataFile toFile,
			File cachedMediaFile, boolean overwrite, byte[] checkSha256) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.fromUri = fromUri;
		this.toFile = toFile;
		this.cachedMediaFile = cachedMediaFile;
		this.overwrite = overwrite;
		this.checkSha256 = checkSha256;
	}

	@Override
	protected void onPrepare() {
		callback.onStartDownloading();
	}

	private static void copyStream(InputStream input, OutputStream output,
			TimedProgressHandler progressHandler, MessageDigest digest) throws IOException {
		byte[] data = new byte[8192];
		int count;
		long read = 0;
		while ((count = input.read(data)) != -1) {
			output.write(data, 0, count);
			read += count;
			progressHandler.updateProgress(read);
			if (digest != null) {
				digest.update(data, 0, count);
			}
		}
	}

	@Override
	protected Boolean run(HttpHolder holder) {
		boolean success = false;
		try {
			loadingStarted = true;
			MessageDigest digest = null;
			if (checkSha256 != null) {
				try {
					digest = MessageDigest.getInstance("SHA-256");
				} catch (NoSuchAlgorithmException e) {
					throw new RuntimeException(e);
				}
			}
			// noinspection StatementWithEmptyBody
			if (!overwrite && toFile.exists()) {
				// Do nothing
			} else if (cachedMediaFile != null) {
				progressHandler.setInputProgressMax(cachedMediaFile.length());
				try (FileInputStream input = new FileInputStream(cachedMediaFile);
						OutputStream output = toFile.openOutputStream()) {
					copyStream(input, output, progressHandler, digest);
				} catch (IOException e) {
					ErrorItem.Type type = getErrorTypeFromExceptionAndHandle(e);
					errorItem = new ErrorItem(type != null ? type : ErrorItem.Type.UNKNOWN);
					return false;
				}
			} else if (ChanConfiguration.SCHEME_CHAN.equals(fromUri.getScheme())) {
				try (OutputStream output = toFile.openOutputStream()) {
					if (!chan.configuration.readResourceUri(fromUri, output)) {
						throw HttpException.createNotFoundException();
					}
				} catch (IOException e) {
					ErrorItem.Type type = getErrorTypeFromExceptionAndHandle(e);
					errorItem = new ErrorItem(type != null ? type : ErrorItem.Type.UNKNOWN);
					return false;
				}
			} else {
				ChanPerformer.ReadContentResult result = chan.performer.safe()
						.onReadContent(new ChanPerformer.ReadContentData(fromUri,
								CONNECT_TIMEOUT, READ_TIMEOUT, holder, -1, -1));
				HttpResponse response = result != null ? result.response : null;
				if (response == null) {
					errorItem = new ErrorItem(ErrorItem.Type.DOWNLOAD);
					return false;
				}
				progressHandler.setInputProgressMax(response.getLength());
				try (InputStream input = response.open();
						OutputStream output = toFile.openOutputStream()) {
					copyStream(input, output, progressHandler, digest);
				} catch (IOException e) {
					ErrorItem.Type errorType = getErrorTypeFromExceptionAndHandle(e);
					if (errorType != null) {
						errorItem = new ErrorItem(errorType);
						return false;
					} else {
						throw response.fail(e);
					}
				} finally {
					response.cleanupAndDisconnect();
				}
			}
			if (digest != null) {
				byte[] sha256 = digest.digest();
				if (!Arrays.equals(sha256, checkSha256)) {
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
					return false;
				}
			}
			success = true;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			if (!success) {
				toFile.delete();
			}
			File file = toFile.getFileOrUri().first;
			if (file != null) {
				CacheManager.getInstance().handleDownloadedFile(file, success);
			}
			if (chan.name != null) {
				chan.configuration.commit();
			}
		}
	}

	public static ErrorItem.Type getErrorTypeFromExceptionAndHandle(IOException exception) {
		if (exception instanceof FileNotFoundException) {
			Log.persistent().stack(exception);
			return ErrorItem.Type.NO_ACCESS_TO_MEMORY;
		} else {
			String message = exception.getMessage();
			if (message != null && message.contains("ENOSPC")) {
				Log.persistent().stack(exception);
				return ErrorItem.Type.INSUFFICIENT_SPACE;
			}
		}
		return null;
	}

	@Override
	protected void onProgress(long[] values) {
		callback.onUpdateProgress(values[0], values[1]);
	}

	@Override
	protected void onComplete(Boolean success) {
		callback.onFinishDownloading(success, fromUri, toFile, errorItem);
	}

	public boolean isDownloadingFromCache() {
		return cachedMediaFile != null;
	}

	public String getFileName() {
		return toFile.getName();
	}

	@Override
	public void cancel() {
		super.cancel();

		if (loadingStarted) {
			toFile.delete();
			File file = toFile.getFileOrUri().first;
			if (file != null) {
				CacheManager.getInstance().handleDownloadedFile(file, false);
			}
		}
		callback.onCancelDownloading();
	}
}
