package com.mishiranu.dashchan.content;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.util.ToastUtils;

import java.util.ArrayList;

public class PostingShareActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		ArrayList<Uri> uris = null;
		Intent intent = getIntent();
		if (Intent.ACTION_SEND.equals(intent.getAction())) {
			Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
			if (uri != null) {
				uris = new ArrayList<>(1);
				uris.add(uri);
			}
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
			uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		}
		int success = 0;
		if (uris != null && !uris.isEmpty()) {
			for (Uri uri : uris) {
				FileHolder fileHolder = FileHolder.obtain(this, uri);
				if (fileHolder != null && draftsStorage.storeFuture(fileHolder)) {
					success++;
				}
			}
		}

		ToastUtils.show(this, success > 0 ? R.string.message_draft_saved : R.string.message_unknown_address);
		finish();
	}
}