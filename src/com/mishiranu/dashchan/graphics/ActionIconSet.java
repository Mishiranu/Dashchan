package com.mishiranu.dashchan.graphics;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.SparseIntArray;
import com.mishiranu.dashchan.R;

public class ActionIconSet {
	private final SparseIntArray ids = new SparseIntArray();

	private static final int[] ATTRS = new int[] {R.attr.iconActionAttach, R.attr.iconActionAddPost,
			R.attr.iconActionAddRule, R.attr.iconActionAddToFavorites, R.attr.iconActionBack, R.attr.iconActionDelete,
			R.attr.iconActionDownload, R.attr.iconActionForward, R.attr.iconActionMakeThreadshot,
			R.attr.iconActionRefresh, R.attr.iconActionReport, R.attr.iconActionRemoveFromFavorites,
			R.attr.iconActionSave, R.attr.iconActionSearch, R.attr.iconActionSelect, R.attr.iconActionVolumeOff};

	public ActionIconSet(Context context) {
		TypedArray typedArray = context.obtainStyledAttributes(ATTRS);
		for (int i = 0; i < typedArray.length(); i++) {
			ids.append(ATTRS[i], typedArray.getResourceId(i, 0));
		}
		typedArray.recycle();
	}

	public int getId(int attr) {
		return ids.get(attr);
	}
}
