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

package com.mishiranu.dashchan.net;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;

import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;

public class CaptchaServiceReader
{
	private static final CaptchaServiceReader INSTANCE = new CaptchaServiceReader();
	
	public static CaptchaServiceReader getInstance()
	{
		return INSTANCE;
	}
	
	public static class Result
	{
		public final String challenge;
		public final Bitmap image;
		public final boolean blackAndWhite;
		
		private Result(String challenge, Bitmap image, boolean blackAndWhite)
		{
			this.challenge = challenge;
			this.image = image;
			this.blackAndWhite = blackAndWhite;
		}
	}
	
	private static final Pattern PATTERN_MAILRU = Pattern.compile("id: \"(.*?)\"[\\s\\S]*url: \"(.*?)\"");
	
	public Result readMailru(HttpHolder holder, String chanName, String apiKey) throws HttpException,
			InvalidResponseException
	{
		Thread thread = Thread.currentThread();
		ChanLocator locator = ChanLocator.get(chanName);
		Uri uri = locator.buildQueryWithSchemeHost(true, "api-nocaptcha.mail.ru", "captcha", "public_key", apiKey);
		String responseText = new HttpRequest(uri, holder).addHeader("Referer",
				locator.buildPath().toString()).read().getString();
		if (thread.isInterrupted()) return null;
		String mrcu = holder.getCookieValue("mrcu");
		Matcher matcher = PATTERN_MAILRU.matcher(responseText);
		if (matcher.find())
		{
			String challenge = matcher.group(1);
			String uriString = matcher.group(2);
			Bitmap image = new HttpRequest(Uri.parse(uriString), holder).addCookie("mrcu", mrcu).read().getBitmap();
			if (image == null) throw new InvalidResponseException();
			int[] pixels = new int[image.getWidth()];
			int top = 0;
			int bottom = image.getHeight();
			OUT: for (int i = 0; i < image.getHeight(); i++)
			{
				image.getPixels(pixels, 0, image.getWidth(), 0, i, image.getWidth(), 1);
				for (int pixel : pixels)
				{
					if (pixel != -1)
					{
						top = i;
						break OUT;
					}
				}
			}
			OUT: for (int i = image.getHeight() - 1; i >= 0; i--)
			{
				image.getPixels(pixels, 0, image.getWidth(), 0, i, image.getWidth(), 1);
				for (int pixel : pixels)
				{
					if (pixel != -1)
					{
						bottom = i + 1;
						break OUT;
					}
				}
			}
			if (bottom <= top) throw new InvalidResponseException();
			if (thread.isInterrupted()) return null;
			Bitmap newImage = Bitmap.createBitmap((int) (image.getWidth() * 1.5f), bottom - top,
					Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(newImage);
			canvas.drawBitmap(image, new Rect(0, top, image.getWidth(), bottom),
					new Rect(0, 0, newImage.getWidth(), newImage.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
			image.recycle();
			newImage = GraphicsUtils.handleBlackAndWhiteCaptchaImage(newImage).first;
			return new Result(challenge, newImage, true);
		}
		throw new InvalidResponseException();
	}
}