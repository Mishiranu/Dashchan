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

package com.mishiranu.dashchan.widget;

import android.graphics.Rect;
import android.os.Parcel;
import android.view.View;
import android.widget.ListView;

public class ListPosition
{
	public ListPosition(int position, int y)
	{
		this.position = position;
		this.y = y;
	}
	
	public final int position;
	public final int y;
	
	public static ListPosition obtain(ListView listView)
	{
		int position = listView.getFirstVisiblePosition();
		int y = 0;
		Rect rect = new Rect();
		int paddingTop = listView.getPaddingTop(), paddingLeft = listView.getPaddingLeft();
		for (int i = 0, count = listView.getChildCount(); i < count; i++)
		{
			View view = listView.getChildAt(i);
			view.getHitRect(rect);
			if (rect.contains(paddingLeft, paddingTop))
			{
				position += i;
				y = rect.top - paddingTop;
				break;
			}
		}
		return new ListPosition(position, y);
	}
	
	public void apply(final ListView listView)
	{
		if (listView.getHeight() == 0) listView.post(() -> listView.setSelectionFromTop(position, y));
		else listView.setSelectionFromTop(position, y);
	}
	
	public static void writeToParcel(Parcel dest, ListPosition position)
	{
		if (position != null)
		{
			dest.writeInt(position.position);
			dest.writeInt(position.y);
		}
		else
		{
			dest.writeInt(-1);
			dest.writeInt(0);
		}
	}
	
	public static ListPosition readFromParcel(Parcel source)
	{
		int position = source.readInt();
		int y = source.readInt();
		return position >= 0 ? new ListPosition(position, y) : null;
	}
}