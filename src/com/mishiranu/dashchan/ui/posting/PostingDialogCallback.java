package com.mishiranu.dashchan.ui.posting;

import android.util.Pair;
import chan.content.ChanConfiguration;
import java.util.List;

public interface PostingDialogCallback {
	AttachmentHolder getAttachmentHolder(int index);
	List<Pair<String, String>> getAttachmentRatingItems();
	ChanConfiguration.Posting getPostingConfiguration();
}
