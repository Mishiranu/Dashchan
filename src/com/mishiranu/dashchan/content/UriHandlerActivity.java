package com.mishiranu.dashchan.content;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class UriHandlerActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean success = false;
		Uri uri = getIntent().getData();
		CONDITION: if (uri != null) {
			if (getIntent().getBooleanExtra(C.EXTRA_EXTERNAL_BROWSER, false)) {
				NavigationUtils.handleUri(this, null, uri, NavigationUtils.BrowserType.EXTERNAL);
				success = true;
				break CONDITION;
			}
			String chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
			if (chanName != null) {
				boolean fromClient = getIntent().getBooleanExtra(C.EXTRA_FROM_CLIENT, false);
				ChanLocator locator = ChanLocator.get(chanName);
				boolean boardUri = locator.safe(false).isBoardUri(uri);
				boolean threadUri = locator.safe(false).isThreadUri(uri);
				String boardName = boardUri || threadUri ? locator.safe(false).getBoardName(uri) : null;
				String threadNumber = threadUri ? locator.safe(false).getThreadNumber(uri) : null;
				String postNumber = threadUri ? locator.safe(false).getPostNumber(uri) : null;
				if (boardUri) {
					if (!fromClient) {
						startActivity(NavigationUtils.obtainThreadsIntent(this, chanName, boardName,
								NavigationUtils.FLAG_RETURNABLE));
					}
					success = true;
				} else if (threadUri) {
					if (!fromClient) {
						startActivity(NavigationUtils.obtainPostsIntent(this, chanName, boardName, threadNumber,
								postNumber, NavigationUtils.FLAG_RETURNABLE));
					}
					success = true;
				} else if (locator.isImageUri(uri)) {
					if (!fromClient) {
						NavigationUtils.openImageVideo(this, locator.convert(uri));
					}
					success = true;
				} else if (locator.isAudioUri(uri)) {
					if (!fromClient) {
						AudioPlayerService.start(this, chanName, uri, locator.createAttachmentFileName(uri));
					}
					success = true;
				} else if (locator.isVideoUri(uri)) {
					String fileName = locator.createAttachmentFileName(uri);
					if (NavigationUtils.isOpenableVideoPath(fileName)) {
						if (!fromClient) {
							NavigationUtils.openImageVideo(this, locator.convert(uri));
						}
						success = true;
					} else if (!fromClient) {
						NavigationUtils.handleUri(this, chanName, locator.convert(uri),
								NavigationUtils.BrowserType.EXTERNAL);
						success = true;
					}
				} else if (fromClient && Preferences.isUseInternalBrowser()) {
					NavigationUtils.handleUri(this, chanName, locator.convert(uri),
							NavigationUtils.BrowserType.INTERNAL);
					success = true;
				}
			}
		}
		if (!success) {
			ToastUtils.show(this, R.string.message_unknown_address);
		}
		finish();
	}
}
