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

package com.mishiranu.dashchan.graphics;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.SparseIntArray;

import com.mishiranu.dashchan.R;

public class ActionIconSet {
	private final SparseIntArray mIds = new SparseIntArray();

	private static final int[] ATTRS = new int[] {R.attr.actionAttach, R.attr.actionAddPost, R.attr.actionAddRule,
		R.attr.actionAddToFavorites, R.attr.actionBack, R.attr.actionDelete, R.attr.actionDownload,
		R.attr.actionForward, R.attr.actionMakeThreadshot, R.attr.actionRefresh, R.attr.actionReport,
		R.attr.actionRemoveFromFavorites, R.attr.actionSave, R.attr.actionSearch, R.attr.actionSelect,
		R.attr.actionVolumeOff};

	public ActionIconSet(Context context) {
		TypedArray typedArray = context.obtainStyledAttributes(ATTRS);
		for (int i = 0; i < typedArray.length(); i++) {
			mIds.append(ATTRS[i], typedArray.getResourceId(i, 0));
		}
		typedArray.recycle();
	}

	public int getId(int attr) {
		return mIds.get(attr);
	}
}