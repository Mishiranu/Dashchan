package com.mishiranu.dashchan.content.async;

import android.os.SystemClock;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;

public class TimedProgressHandler implements HttpRequest.OutputListener, MultipartEntity.OpenableOutputListener {
	private final long[] lastProgressUpdate = new long[3];
	private long progressMax = -1;

	private boolean checkNeedToUpdate(int index, long progress, long progressMax) {
		long time = SystemClock.elapsedRealtime();
		if (time - lastProgressUpdate[index] >= 200 || progress == 0 || progress == progressMax) {
			lastProgressUpdate[index] = time;
			return true;
		}
		return false;
	}

	public void updateProgress(long count) {
		if (checkNeedToUpdate(0, count, progressMax)) {
			onProgressChange(count, progressMax);
		}
	}

	public void setInputProgressMax(long progressMax) {
		this.progressMax = progressMax;
	}

	@Override
	public final void onOutputProgressChange(long progress, long progressMax) {
		if (checkNeedToUpdate(1, progress, progressMax)) {
			onProgressChange(progress, progressMax);
		}
	}

	@Override
	public final void onOutputProgressChange(MultipartEntity.Openable openable, long progress, long progressMax) {
		if (checkNeedToUpdate(2, progress, progressMax)) {
			onProgressChange(openable, progress, progressMax);
		}
	}

	public void onProgressChange(long progress, long progressMax) {}

	public void onProgressChange(MultipartEntity.Openable openable, long progress, long progressMax) {}
}
