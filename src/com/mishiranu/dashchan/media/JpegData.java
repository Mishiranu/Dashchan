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

package com.mishiranu.dashchan.media;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import android.util.Pair;

import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.IOUtils;

public class JpegData
{
	private static final String KEY_DESCRIPTION = "description";
	private static final String KEY_MAKE = "make";
	private static final String KEY_MODEL = "model";
	private static final String KEY_ORIENTATION = "orientation";
	private static final String KEY_SOFTWARE = "software";
	private static final String KEY_DATE_TIME = "dateTime";
	
	private static final String KEY_LATITUDE = "latitude";
	private static final String KEY_LATITUDE_REF = "latitudeRef";
	private static final String KEY_LONGITUDE = "longitude";
	private static final String KEY_LONGITUDE_REF = "longitudeRef";
	
	private static final String KEY_EXIF_OFFSET = "exifOffset";
	private static final String KEY_GPS_OFFSET = "gpsOffset";
	
	public final boolean hasExif;
	public final boolean forbidRegionDecoder;
	private final LinkedHashMap<String, String> mExif;
	
	private JpegData(boolean hasExif, boolean forbidRegionDecoder, LinkedHashMap<String, String> exif)
	{
		this.hasExif = hasExif;
		this.forbidRegionDecoder = forbidRegionDecoder;
		mExif = exif;
	}
	
	private List<Pair<String, String>> mUserMetadata = null;
	
	private void addUserMetadata(String key, String title)
	{
		String value = mExif.get(key);
		if (!StringUtils.isEmpty(value)) mUserMetadata.add(new Pair<>(title, value));
	}
	
	public List<Pair<String, String>> getUserMetadata()
	{
		if (mUserMetadata == null)
		{
			if (mExif != null)
			{
				mUserMetadata = new ArrayList<>();
				addUserMetadata(KEY_DESCRIPTION, "Description");
				addUserMetadata(KEY_MAKE, "Manufacturer");
				addUserMetadata(KEY_MODEL, "Model");
				addUserMetadata(KEY_SOFTWARE, "Software");
				addUserMetadata(KEY_DATE_TIME, "Date");
				int rotation = getRotation();
				if (rotation != 0) mUserMetadata.add(new Pair<>("Rotation", rotation + "°"));
				String geolocation = getGeolocation(true);
				if (geolocation != null) mUserMetadata.add(new Pair<>("Geolocation", geolocation));
				mUserMetadata = Collections.unmodifiableList(mUserMetadata);
			}
			else mUserMetadata = Collections.emptyList();
		}
		return mUserMetadata;
	}
	
	public int getRotation()
	{
		if (mExif != null)
		{
			String orientation = mExif.get(KEY_ORIENTATION);
			if (orientation != null)
			{
				switch (orientation)
				{
					case "8": return 90;
					case "3": return 180;
					case "6": return 270;
				}
			}
		}
		return 0;
	}
	
	private String formatLocationValue(double value)
	{
		int degrees = (int) value;
		value -= degrees;
		value *= 60;
		int minutes = (int) value;
		value -= minutes;
		value *= 60;
		int seconds = (int) value;
		return degrees + "°" + minutes + "'" + seconds + "\"";
	}
	
	public String getGeolocation(boolean userReadable)
	{
		if (mExif != null)
		{
			String latitude = mExif.get(KEY_LATITUDE);
			String longitude = mExif.get(KEY_LONGITUDE);
			if (latitude != null && longitude != null)
			{
				String latitudeRef = mExif.get(KEY_LATITUDE_REF);
				String longitudeRef = mExif.get(KEY_LONGITUDE_REF);
				if (StringUtils.isEmptyOrWhitespace(latitudeRef)) latitudeRef = "N";
				if (StringUtils.isEmptyOrWhitespace(longitudeRef)) longitudeRef = "E";
				if (userReadable)
				{
					double latitudeValue = Double.parseDouble(latitude);
					double longitudeValue = Double.parseDouble(longitude);
					return formatLocationValue(latitudeValue) + latitudeRef + " " + formatLocationValue(longitudeValue)
							+ longitudeRef;
				}
				else
				{
					return ("S".equals(latitudeRef) ? "-" : "") + latitude + ","
							+ ("W".equals(longitudeRef) ? "-" : "") + longitude;
				}
			}
		}
		return null;
	}
	
	private static String extractIfdString(byte[] exifBytes, int offset, int format)
	{
		if (format == 2 && offset >= 0 && exifBytes.length > offset && exifBytes[offset] != 0x00)
		{
			for (int i = offset; i < exifBytes.length; i++)
			{
				if (exifBytes[i] == 0x00) return new String(exifBytes, offset, i - offset);
			}
		}
		return null;
	}
	
	private static double convertIfdRational(byte[] exifBytes, int offset, int format, boolean littleEndian)
	{
		if ((format == 5 || format == 10) && offset >= 0 && exifBytes.length >= offset + 8)
		{
			int numerator = IOUtils.bytesToInt(littleEndian, offset, 4, exifBytes);
			int denominator = IOUtils.bytesToInt(littleEndian, offset + 4, 4, exifBytes);
			return (double) numerator / denominator;
		}
		return Double.NaN;
	}
	
	private static String convertIfdGpsString(byte[] exifBytes, int offset, int format, boolean littleEndian,
			int count)
	{
		if ((format == 5 || format == 10) && offset >= 0 && exifBytes.length >= offset + 8)
		{
			double value = 0.0;
			int[] denominators = {1, 60, 3600};
			count = Math.max(Math.min(count, 3), 1);
			for (int i = 0; i < count; i++)
			{
				double itValue = convertIfdRational(exifBytes, offset + 8 * i, format, littleEndian);
				if (itValue == Double.NaN) break;
				value += itValue / denominators[i];
			}
			return String.format(Locale.US, "%.7f", value);
		}
		return null;
	}

	private static final int IFD_GENERAL = 0;
	private static final int IFD_GPS = 1;
	
	private static boolean extractIfd(LinkedHashMap<String, String> exif, int ifd, byte[] exifBytes, int offset,
			boolean littleEndian)
	{
		if (offset >= 0 && exifBytes.length >= offset + 2)
		{
			int entries = IOUtils.bytesToInt(littleEndian, offset, 2, exifBytes);
			if (exifBytes.length >= offset + 2 + 12 * entries)
			{
				for (int i = 0, position = offset + 2; i < entries; i++, position += 12)
				{
					int type = IOUtils.bytesToInt(littleEndian, position, 2, exifBytes);
					int format = IOUtils.bytesToInt(littleEndian, position + 2, 2, exifBytes);
					int count = IOUtils.bytesToInt(littleEndian, position + 4, 4, exifBytes);
					int value = IOUtils.bytesToInt(littleEndian, position + 8, 4, exifBytes);
					if (ifd == IFD_GENERAL)
					{
						switch (type)
						{
							case 0x010e:
							{
								exif.put(KEY_DESCRIPTION, extractIfdString(exifBytes, value, format));
								break;
							}
							case 0x010f:
							{
								exif.put(KEY_MAKE, extractIfdString(exifBytes, value, format));
								break;
							}
							case 0x0110:
							{
								exif.put(KEY_MODEL, extractIfdString(exifBytes, value, format));
								break;
							}
							case 0x0112:
							{
								exif.put(KEY_ORIENTATION, Integer.toString(value));
								break;
							}
							case 0x0131:
							{
								exif.put(KEY_SOFTWARE, extractIfdString(exifBytes, value, format));
								break;
							}
							case 0x0132:
							{
								exif.put(KEY_DATE_TIME, extractIfdString(exifBytes, value, format));
								break;
							}
							case 0x8769:
							{
								exif.put(KEY_EXIF_OFFSET, Integer.toString(value));
								break;
							}
							case 0x8825:
							{
								exif.put(KEY_GPS_OFFSET, Integer.toString(value));
								break;
							}
						}
					}
					else if (ifd == IFD_GPS)
					{
						switch (type)
						{
							case 0x0001:
							{
								exif.put(KEY_LATITUDE_REF, Character.toString((char) value));
								break;
							}
							case 0x0002:
							{
								exif.put(KEY_LATITUDE, convertIfdGpsString(exifBytes, value, format,
										littleEndian, count));
								break;
							}
							case 0x0003:
							{
								exif.put(KEY_LONGITUDE_REF, Character.toString((char) value));
								break;
							}
							case 0x0004:
							{
								exif.put(KEY_LONGITUDE, convertIfdGpsString(exifBytes, value, format,
										littleEndian, count));
								break;
							}
						}
					}
				}
				return true;
			}
			return false;
		}
		return false;
	}
	
	private static boolean extractIfd(LinkedHashMap<String, String> exif, int ifd, byte[] exifBytes,
			String offsetStringKey, boolean littleEndian)
	{
		String offsetString = exif.get(offsetStringKey);
		if (offsetString != null)
		{
			int offset = Integer.parseInt(offsetString);
			return extractIfd(exif, ifd, exifBytes, offset, littleEndian);
		}
		return false;
	}
	
	public static JpegData extract(FileHolder fileHolder)
	{
		if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG)
		{
			byte[] exifBytes = null;
			byte[] sofBytes = null;
			InputStream input = null;
			try
			{
				input = new BufferedInputStream(fileHolder.openInputStream(), 327680);
				byte[] buffer = new byte[2];
				while (true)
				{
					int oneByte = input.read();
					if (oneByte == 0xff)
					{
						oneByte = input.read();
						if ((oneByte & 0xe0) == 0xe0)
						{
							// Application data (0xe0 for JFIF, 0xe1 for EXIF) or comment (0xfe)
							if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) break;
							int size = IOUtils.bytesToInt(false, 0, 2, buffer);
							if (oneByte == 0xe1 && size > 14)
							{
								byte[] data = new byte[size - 8];
								if (!IOUtils.readExactlyCheck(input, data, 0, 6)) break;
								boolean isExif = new String(data).startsWith("Exif");
								if (!IOUtils.readExactlyCheck(input, data, 0, data.length)) break;
								if (isExif) exifBytes = data;
							}
							else if (!IOUtils.skipExactlyCheck(input, size - 2)) break;
						}
						else if (oneByte == 0xc0 || oneByte == 0xc1 || oneByte == 0xc2)
						{
							if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) break;
							int size = IOUtils.bytesToInt(false, 0, 2, buffer) - 2;
							byte[] data = new byte[size];
							if (!IOUtils.readExactlyCheck(input, data, 0, size)) break;
							sofBytes = data;
						}
						else if (oneByte == 0xda) break;
					}
					if (oneByte == -1) break;
				}
			}
			catch (IOException e)
			{
				
			}
			finally
			{
				IOUtils.close(input);
			}
			boolean forbidRegionDecoder = sofBytes != null && sofBytes.length > 7 && (sofBytes[5] & 0xff) == 1
					&& (sofBytes[7] & 0xff) != 0x11;
			LinkedHashMap<String, String> exif = null;
			if (exifBytes != null && exifBytes.length > 8)
			{
				int tiffHeader = IOUtils.bytesToInt(false, 0, 4, exifBytes);
				boolean littleEndian = false;
				boolean valid = true;
				if (tiffHeader == 0x49492a00) littleEndian = true;
				else if (tiffHeader != 0x4d4d002a) valid = false;
				if (valid)
				{
					exif = new LinkedHashMap<>();
					int ifdOffset = IOUtils.bytesToInt(littleEndian, 4, 4, exifBytes);
					extractIfd(exif, IFD_GENERAL, exifBytes, ifdOffset, littleEndian);
					extractIfd(exif, IFD_GENERAL, exifBytes, KEY_EXIF_OFFSET, littleEndian);
					extractIfd(exif, IFD_GPS, exifBytes, KEY_GPS_OFFSET, littleEndian);
				}
			}
			return new JpegData(exifBytes != null, forbidRegionDecoder, exif);
		}
		return null;
	}
}