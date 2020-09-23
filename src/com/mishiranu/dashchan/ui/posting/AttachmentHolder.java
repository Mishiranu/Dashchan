package com.mishiranu.dashchan.ui.posting;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.mishiranu.dashchan.util.GraphicsUtils;

public class AttachmentHolder {
	public final View view;
	public final TextView fileName;
	public final TextView fileSize;
	public final View options;
	public final ImageView imageView;
	public final View warningButton;
	public final View ratingButton;
	public final View removeButton;

	public String hash;
	public String name;
	public String rating;
	public boolean optionUniqueHash = false;
	public boolean optionRemoveMetadata = false;
	public boolean optionRemoveFileName = false;
	public boolean optionSpoiler = false;
	public GraphicsUtils.Reencoding reencoding;

	public AttachmentHolder(View view, TextView fileName, TextView fileSize, View options, ImageView imageView,
			View warningButton, View ratingButton, View removeButton) {
		this.view = view;
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.options = options;
		this.imageView = imageView;
		this.warningButton = warningButton;
		this.ratingButton = ratingButton;
		this.removeButton = removeButton;
	}
}
