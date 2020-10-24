package com.mishiranu.dashchan.util;

import android.content.Context;
import chan.util.StringUtils;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PostDateFormatter {
	private final String instance;
	private final DateFormat dateFormat;
	private final DateFormat dateFormatLong;
	private final DateFormat timeFormat;

	public PostDateFormatter(Context context) {
		SimpleDateFormat systemFormat = (SimpleDateFormat) android.text.format.DateFormat.getDateFormat(context);
		String systemPattern = systemFormat.toPattern();
		String dayFormat = systemPattern.contains("dd") ? "dd" : "d";
		String monthFormat = systemPattern.contains("MM") ? "MM" : "M";
		int index = StringUtils.nearestIndexOf(systemPattern, 0, '.', '/', '-');
		char divider = index >= 0 ? systemPattern.charAt(index) : '.';
		String shortDateFormat = systemPattern.indexOf('d') > systemPattern.indexOf('M')
				? monthFormat + divider + dayFormat : dayFormat + divider + monthFormat;
		String longDateFormat = systemPattern.indexOf('d') > systemPattern.indexOf('y')
				? "yy" + divider + shortDateFormat : shortDateFormat + divider + "yy";
		String timeFormat = android.text.format.DateFormat.is24HourFormat(context) ? "HH:mm:ss" : "hh:mm:ss aa";
		this.instance = longDateFormat + timeFormat + Locale.getDefault().toString();
		this.dateFormat = new SimpleDateFormat(shortDateFormat, Locale.getDefault());
		this.dateFormatLong = new SimpleDateFormat(longDateFormat, Locale.getDefault());
		this.timeFormat = new SimpleDateFormat(timeFormat, Locale.US);
	}

	public String formatDate(long timestamp) {
		Calendar calendar = Calendar.getInstance();
		int year = calendar.get(Calendar.YEAR);
		calendar.setTimeInMillis(timestamp);
		return (calendar.get(Calendar.YEAR) != year ? dateFormatLong : dateFormat).format(timestamp);
	}

	public String formatDateTime(long timestamp) {
		Calendar calendar = Calendar.getInstance();
		int year = calendar.get(Calendar.YEAR);
		calendar.setTimeInMillis(timestamp);
		return (calendar.get(Calendar.YEAR) != year ? dateFormatLong : dateFormat).format(timestamp)
				+ " " + timeFormat.format(timestamp);
	}

	public Holder formatDateTime(long timestamp, Holder holder) {
		if (holder != null && holder.instance.equals(instance)) {
			return holder;
		}
		return new Holder(formatDateTime(timestamp), instance);
	}

	public static class Holder {
		public final String text;
		private final String instance;

		private Holder(String text, String instance) {
			this.text = text;
			this.instance = instance;
		}
	}
}
