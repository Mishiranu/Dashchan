package com.mishiranu.dashchan.ui.posting.dialog;

import android.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import chan.content.ChanConfiguration;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import java.util.List;

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

	public static void bindCallback(FragmentActivity activity, String tag, Callback callback) {
		PostingDialog dialog = (PostingDialog) activity.getSupportFragmentManager().findFragmentByTag(tag);
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
		return callback.getAttachmentHolder(requireArguments().getInt(key));
	}

	public final List<Pair<String, String>> getAttachmentRatingItems() {
		return callback.getAttachmentRatingItems();
	}

	public final ChanConfiguration.Posting getPostingConfiguration() {
		return callback.getPostingConfiguration();
	}
}
