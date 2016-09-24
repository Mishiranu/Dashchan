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

package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.widget.ClickableToast;

public class ToastUtils implements Runnable
{
	private static Toast sToast;

	@SuppressLint("ShowToast")
	private static void makeNewToast(Context context)
	{
		synchronized (ToastUtils.class)
		{
			if (sToast == null) sToast = Toast.makeText(context, "", Toast.LENGTH_LONG);
			ToastUtils.class.notifyAll();
		}
	}

	private final Context mContext;

	private ToastUtils(Context context)
	{
		mContext = context;
	}

	@Override
	public void run()
	{
		makeNewToast(mContext);
	}

	public static void show(Context context, String message)
	{
		synchronized (ToastUtils.class)
		{
			ClickableToast.cancel(context);
			if (sToast == null)
			{
				if (Looper.myLooper() == Looper.getMainLooper()) makeNewToast(context.getApplicationContext()); else
				{
					new Handler(Looper.getMainLooper()).post(new ToastUtils(context.getApplicationContext()));
					try
					{
						while (sToast == null) ToastUtils.class.wait();
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
			sToast.setText(message);
			sToast.show();
		}
	}

	public static void show(Context context, int resId)
	{
		show(context, context.getString(resId));
	}

	public static void show(Context context, ErrorItem errorItem)
	{
		show(context, errorItem.toString());
	}

	public static void cancel()
	{
		synchronized (ToastUtils.class)
		{
			if (sToast != null)
			{
				sToast.cancel();
				// Toast can't be recycled, so I reset reference and create new toast later
				sToast = null;
			}
		}
	}
}