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

package chan.util;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.mishiranu.dashchan.util.Log;

public class CommonUtils
{
	/*
	 * Returns true if thread was interrupted.
	 */
	public static boolean sleepMaxTime(long startTime, long interval)
	{
		long time = interval - (System.currentTimeMillis() - startTime);
		if (time <= 0) return false;
		try
		{
			Thread.sleep(time);
			return false;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return true;
		}
	}
	
	public static String optJsonString(JSONObject jsonObject, String name)
	{
		return optJsonString(jsonObject, name, null);
	}
	
	public static String optJsonString(JSONObject jsonObject, String name, String fallback)
	{
		try
		{
			return getJsonString(jsonObject, name);
		}
		catch (JSONException e)
		{
			return fallback;
		}
	}
	
	public static String getJsonString(JSONObject jsonObject, String name) throws JSONException
	{
		if (jsonObject.has(name) && jsonObject.isNull(name)) return null; else return jsonObject.getString(name);
	}
	
	public static String restoreCloudFlareProtectedEmails(String string)
	{
		int index = 0;
		StringBuilder builder = null;
		while (true)
		{
			index = builder != null ? builder.indexOf("/cdn-cgi/l/email-protection", index)
					: string.indexOf("/cdn-cgi/l/email-protection");
			if (index >= 0)
			{
				if (builder == null) builder = new StringBuilder(string);
				int index1 = builder.lastIndexOf("<", index);
				int index2 = builder.lastIndexOf("<a", index);
				int index3 = builder.indexOf(">", index);
				int index4 = builder.indexOf("</a>", index);
				if (index1 == index2 && index3 > index)
				{
					int index5 = builder.indexOf("\"", index);
					String url = builder.substring(index, index5);
					int index6 = url.indexOf('#');
					String hash = null;
					boolean replaceTag = false;
					if (index6 >= 0) hash = url.substring(index6 + 1); else
					{
						index6 = builder.indexOf("data-cfemail=", index1);
						if (index6 >= 0 && index6 < index3)
						{
							index6 += 14;
							index5 = builder.indexOf("\"", index6);
							hash = builder.substring(index6, index5);
							replaceTag = true;
						}
					}
					if (hash != null && hash.length() % 2 == 0)
					{
						int x = Integer.parseInt(hash.substring(0, 2), 16);
						StringBuilder email = new StringBuilder(hash.length() / 2 - 1);
						for (int i = 2; i < hash.length(); i += 2)
						{
							int b = Integer.parseInt(hash.substring(i, i + 2), 16);
							email.append((char) (b ^ x));
						}
						if (replaceTag)
						{
							builder.replace(index1, index4 + 4, email.toString());
							index = index1 + email.length();
							continue;
						}
						else
						{
							builder.replace(index, index5, "mailto:" + email.toString());
							index = builder.indexOf(">", index) + 1;
							continue;
						}
					}
				}
				if (index4 == -1) break;
				index = index4 + 4;
			}
			else break;
		}
		if (builder != null)
		{
			while (true)
			{
				int start = builder.indexOf("<script data-cfhash");
				if (start >= 0)
				{
					int end = builder.indexOf("</script>");
					if (end > start)
					{
						end += 9;
						builder.delete(start, end);
						continue;
					}
				}
				break;
			}
			string = builder.toString();
		}
		return string;
	}
	
	public static Bitmap trimBitmap(Bitmap bitmap, int backgroundColor)
	{
		if (bitmap == null) return null;
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int[] pixels = new int[Math.max(width, height)];
		int actualLeft = 0;
		int actualRight = width;
		int actualTop = 0;
		int actualBottom = height;
		OUT: for (int i = 0; i < width; i++)
		{
			bitmap.getPixels(pixels, 0, 1, i, 0, 1, height);
			for (int j = 0; j < height; j++)
			{
				if (pixels[j] != backgroundColor)
				{
					actualLeft = i;
					break OUT;
				}
			}
		}
		OUT: for (int i = width - 1; i >= 0; i--)
		{
			bitmap.getPixels(pixels, 0, 1, i, 0, 1, height);
			for (int j = 0; j < height; j++)
			{
				if (pixels[j] != backgroundColor)
				{
					actualRight = i + 1;
					break OUT;
				}
			}
		}
		OUT: for (int i = 0; i < height; i++)
		{
			bitmap.getPixels(pixels, 0, width, 0, i, width, 1);
			for (int j = 0; j < width; j++)
			{
				if (pixels[j] != backgroundColor)
				{
					actualTop = i;
					break OUT;
				}
			}
		}
		OUT: for (int i = height - 1; i >= 0; i--)
		{
			bitmap.getPixels(pixels, 0, width, 0, i, width, 1);
			for (int j = 0; j < width; j++)
			{
				if (pixels[j] != backgroundColor)
				{
					actualBottom = i + 1;
					break OUT;
				}
			}
		}
		if (actualLeft != 0 || actualTop != 0 || actualRight != width || actualBottom != height)
		{
			if (actualRight > actualLeft && actualBottom > actualTop)
			{
				Bitmap newBitmap = Bitmap.createBitmap(actualRight - actualLeft, actualBottom - actualTop,
						Bitmap.Config.ARGB_8888);
				new Canvas(newBitmap).drawBitmap(bitmap, -actualLeft, -actualTop, null);
				return newBitmap;
			}
			return null;
		}
		return bitmap;
	}
	
	public static void writeLog(Object... data)
	{
		Log.persistent().write(data);
	}
}