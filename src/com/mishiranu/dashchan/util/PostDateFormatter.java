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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import android.content.Context;

import chan.util.StringUtils;

public class PostDateFormatter
{
	private final String mInstance;
	private final DateFormat mDateFormat, mDateFormatLong, mTimeFormat;

	public PostDateFormatter(Context context)
	{
		SimpleDateFormat systemFormat = (SimpleDateFormat) android.text.format.DateFormat.getDateFormat(context);
		String systemPattern = systemFormat.toPattern();
		String dayFormat = systemPattern.contains("dd") ? "dd" : "d";
		String monthFormat = systemPattern.contains("MM") ? "MM" : "M";
		int index = StringUtils.nearestIndexOf(systemPattern, '.', '/', '-');
		char divider = index >= 0 ? systemPattern.charAt(index) : '.';
		String shortDateFormat = systemPattern.indexOf('d') > systemPattern.indexOf('M')
				? monthFormat + divider + dayFormat : dayFormat + divider + monthFormat;
		String longDateFormat = systemPattern.indexOf('d') > systemPattern.indexOf('y')
				? "yy" + divider + shortDateFormat : shortDateFormat + divider + "yy";
		String timeFormat = android.text.format.DateFormat.is24HourFormat(context) ? "HH:mm:ss" : "hh:mm:ss aa";
		mInstance = longDateFormat + timeFormat + Locale.getDefault().toString();
		mDateFormat = new SimpleDateFormat(shortDateFormat, Locale.getDefault());
		mDateFormatLong = new SimpleDateFormat(longDateFormat, Locale.getDefault());
		mTimeFormat = new SimpleDateFormat(timeFormat, Locale.US);
	}

	public String format(long timestamp)
	{
		Calendar calendar = Calendar.getInstance();
		int year = calendar.get(Calendar.YEAR);
		calendar.setTimeInMillis(timestamp);
		return (calendar.get(Calendar.YEAR) != year ? mDateFormatLong : mDateFormat).format(timestamp)
				+ " " + mTimeFormat.format(timestamp);
	}

	public Holder format(long timestamp, Holder holder)
	{
		if (holder != null && holder.instance.equals(mInstance)) return holder;
		return new Holder(format(timestamp), mInstance);
	}

	public static class Holder
	{
		public final String text;
		private final String instance;

		private Holder(String text, String instance)
		{
			this.text = text;
			this.instance = instance;
		}
	}
}