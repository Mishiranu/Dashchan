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

package com.mishiranu.dashchan.ui.posting.dialog;

import java.util.List;

import android.app.Activity;
import android.app.DialogFragment;
import android.util.Pair;

import chan.content.ChanConfiguration;

import com.mishiranu.dashchan.ui.posting.AttachmentHolder;

public class PostingDialog extends DialogFragment {
	public interface Callback {
		public AttachmentHolder getAttachmentHolder(int index);
		public List<Pair<String, String>> getAttachmentRatingItems();
		public ChanConfiguration.Posting getPostingConfiguration();
	}

	private Callback callback;

	public PostingDialog bindCallback(Callback callback) {
		this.callback = callback;
		return this;
	}

	public PostingDialog bindCallback(PostingDialog dialog) {
		return bindCallback(dialog.callback);
	}

	public static void bindCallback(Activity activity, String tag, Callback callback) {
		PostingDialog dialog = (PostingDialog) activity.getFragmentManager().findFragmentByTag(tag);
		if (dialog != null) {
			dialog.bindCallback(callback);
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		callback = null;
	}

	public final AttachmentHolder getAttachmentHolder(String key) {
		return callback.getAttachmentHolder(getArguments().getInt(key));
	}

	public final List<Pair<String, String>> getAttachmentRatingItems() {
		return callback.getAttachmentRatingItems();
	}

	public final ChanConfiguration.Posting getPostingConfiguration() {
		return callback.getPostingConfiguration();
	}
}