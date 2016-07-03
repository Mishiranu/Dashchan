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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.Gravity;

import com.mishiranu.dashchan.content.model.FileHolder;

public class GraphicsUtils
{
	public static final Typeface TYPEFACE_MEDIUM = newTypeface("sans-serif-medium");
	public static final Typeface TYPEFACE_LIGHT = newTypeface("sans-serif-light");
	
	private static final Random RANDOM = new Random(System.currentTimeMillis());
	
	private static Typeface newTypeface(String familyName)
	{
		return Typeface.create(familyName, Typeface.NORMAL);
	}
	
	private static final float CONTRAST_GAIN = 2.5f;
	
	private static final ColorMatrixColorFilter CONTRAST_FILTER = new ColorMatrixColorFilter(new float[]
	{
		CONTRAST_GAIN, 0f, 0f, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, CONTRAST_GAIN, 0f, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, 0f, CONTRAST_GAIN, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, 0f, 0f, 1f, 0f
	});
	
	private static final ColorMatrixColorFilter BLACK_CHROMA_KEY_FILTER = new ColorMatrixColorFilter(new float[]
	{
		0f, 0f, 0f, 0f, 0f,
		0f, 0f, 0f, 0f, 0f,
		0f, 0f, 0f, 0f, 0f,
		-1f/3f, -1f/3f, -1f/3f, 0f, 255f
	});
	
	public static final ColorMatrixColorFilter INVERT_FILTER = new ColorMatrixColorFilter(new float[]
	{
		-1f, 0f, 0f, 0f, 255f,
		0f, -1f, 0f, 0f, 255f,
		0f, 0f, -1f, 0f, 255f,
		0f, 0f, 0f, 1f, 0f
	});
	
	public static int modifyColorGain(int color, float gain)
	{
		int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
		return Color.argb(Color.alpha(color), Math.min((int) (r * gain), 0xff), Math.min((int) (g * gain), 0xff),
				Math.min((int) (b * gain), 0xff));
	}
	
	public static boolean isLight(int color)
	{
		return (Color.red(color) + Color.green(color) + Color.blue(color)) / 3 >= 0x80;
	}
	
	public static int mixColors(int background, int foreground)
	{
		int ba = Color.alpha(background), fa = Color.alpha(foreground);
		int a = fa + ba * (0xff - fa) / 0xff;
		int r = (Color.red(foreground) * fa + Color.red(background) * ba * (0xff - fa) / 0xff) / a;
		int g = (Color.green(foreground) * fa + Color.green(background) * ba * (0xff - fa) / 0xff) / a;
		int b = (Color.blue(foreground) * fa + Color.blue(background) * ba * (0xff - fa) / 0xff) / a;
		return Color.argb(Math.min(a, 0xff), Math.min(r, 0xff), Math.min(g, 0xff), Math.min(b, 0xff));
	}
	
	public static int getDrawableColor(Context context, Drawable drawable, int gravity)
	{
		float density = ResourceUtils.obtainDensity(context);
		int size = Math.max(drawable.getMinimumWidth(), drawable.getMinimumHeight());
		if (size == 0) size = (int) (64f * density);
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		try
		{
			drawable.setBounds(0, 0, size, size);
			drawable.draw(new Canvas(bitmap));
			int x, y;
			switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK)
			{
				case Gravity.LEFT:
				{
					x = 0;
					break;
				}
				case Gravity.RIGHT:
				{
					x = size - 1;
					break;
				}
				default:
				{
					x = size / 2;
					break;
				}
			}
			switch (gravity & Gravity.VERTICAL_GRAVITY_MASK)
			{
				case Gravity.TOP:
				{
					y = 0;
					break;
				}
				case Gravity.BOTTOM:
				{
					y = size - 1;
					break;
				}
				default:
				{
					y = size / 2;
					break;
				}
			}
			return bitmap.getPixel(x, y);
		}
		finally
		{
			bitmap.recycle();
		}
	}
	
	public static Bitmap reduceThumbnailSize(Resources resources, Bitmap bitmap)
	{
		int newSize = (int) (72f * ResourceUtils.obtainDensity(resources));
		return reduceBitmapSize(bitmap, newSize, true);
	}
	
	public static Bitmap reduceBitmapSize(Bitmap bitmap, int newSize, boolean recycleOld)
	{
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int oldSize = Math.min(width, height);
		float scale = newSize / (float) oldSize;
		if (scale >= 1.0) return bitmap;
		Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, (int) (width * scale), (int) (height * scale), true);
		if (recycleOld && resizedBitmap != bitmap) bitmap.recycle();
		return resizedBitmap;
	}
	
	public static boolean canRemoveMetadata(FileHolder fileHolder)
	{
		return fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG
				|| fileHolder.getImageType() == FileHolder.ImageType.IMAGE_PNG;
	}
	
	public static class SkipRange
	{
		public final int start;
		public final int count;
		
		private SkipRange(int start, int count)
		{
			this.start = start;
			this.count = count;
		}
	}
	
	public static class TransformationData
	{
		public final ArrayList<SkipRange> skipRanges;
		public final byte[] decodedBytes;
		public final String newFileName;
		
		private TransformationData(ArrayList<SkipRange> skipRanges, byte[] decodedBytes, String newFileName)
		{
			this.skipRanges = skipRanges;
			this.decodedBytes = decodedBytes;
			this.newFileName = newFileName;
		}
	}
	
	public static TransformationData transformImageForPosting(FileHolder fileHolder, String fileName,
			boolean removeMetadata, boolean reencodeImage)
	{
		ArrayList<SkipRange> skipRanges = null;
		byte[] decodedBytes = null;
		String newFileName = null;
		InputStream input = null;
		try
		{
			if (reencodeImage && fileHolder.isImage())
			{
				Bitmap bitmap;
				try
				{
					bitmap = fileHolder.readImageBitmap(Integer.MAX_VALUE, true, true);
				}
				catch (Exception | OutOfMemoryError e)
				{
					bitmap = null;
				}
				if (bitmap != null)
				{
					try
					{
						boolean jpeg = fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG;
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						bitmap.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 100, output);
						decodedBytes = output.toByteArray();
						int index = fileName.lastIndexOf('.');
						newFileName = (index >= 0 ? fileName.substring(0, index) : fileName) + (jpeg ? ".jpg" : ".png");
					}
					finally
					{
						bitmap.recycle();
					}
				}
			}
			else if (removeMetadata)
			{
				if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG)
				{
					input = new BufferedInputStream(fileHolder.openInputStream(), 16 * 1024);
					int position = 0;
					byte[] buffer = new byte[2];
					while (true)
					{
						int oneByte = input.read();
						position++;
						if (oneByte == 0xff)
						{
							oneByte = input.read();
							position++;
							if ((oneByte & 0xe0) == 0xe0 || oneByte == 0xfe)
							{
								// Application data (0xe0 for JFIF, 0xe1 for EXIF) or comment (0xfe)
								if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) break;
								int size = IOUtils.bytesToInt(false, 0, 2, buffer);
								if (!IOUtils.skipExactlyCheck(input, size - 2)) break;
								if (skipRanges == null) skipRanges = new ArrayList<>();
								skipRanges.add(new SkipRange(position - 2, size + 2));
								position += size;
							}
						}
						if (oneByte == -1) break;
					}
				}
				else if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_PNG)
				{
					input = fileHolder.openInputStream();
					if (IOUtils.skipExactlyCheck(input, 8))
					{
						int position = 8;
						byte[] buffer = new byte[8];
						while (true)
						{
							if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) break;
							int size = IOUtils.bytesToInt(false, 0, 4, buffer);
							String name = new String(buffer, 4, 4);
							if (!IOUtils.skipExactlyCheck(input, size + 4)) break;
							if (isUselessPngChunk(name))
							{
								if (skipRanges == null) skipRanges = new ArrayList<>();
								skipRanges.add(new SkipRange(position, size + 12));
							}
							position += size + 12;
							if ("IEND".equals(name))
							{
								int fileSize = fileHolder.getSize();
								if (fileSize > position)
								{
									if (skipRanges == null) skipRanges = new ArrayList<>();
									skipRanges.add(new SkipRange(position, fileSize - position));
								}
								break;
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			
		}
		finally
		{
			try
			{
				input.close();
			}
			catch (Exception e)
			{
				
			}
		}
		return skipRanges != null || decodedBytes != null || newFileName != null
				? new TransformationData(skipRanges, decodedBytes, newFileName) : null;
	}
	
	public static boolean isUselessPngChunk(String name)
	{
		return "iTXt".equals(name) || "tEXt".equals(name) || "tIME".equals(name)
				|| "zTXt".equals(name);
	}
	
	public static boolean isWhite(int color)
	{
		return Color.alpha(color) == 0xff && Color.red(color) >= 0xe0 && Color.green(color) >= 0xe0
				&& Color.blue(color) >= 0xe0;
	}
	
	public static boolean isBlackAndWhiteCaptchaImage(Bitmap image)
	{
		if (image != null)
		{
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = new int[width];
			for (int i = 0; i < height; i++)
			{
				image.getPixels(pixels, 0, width, 0, i, width, 1);
				for (int j = 0; j < width; j++)
				{
					int color = pixels[j];
					int a = Color.alpha(color);
					int r = Color.red(color);
					int g = Color.green(color);
					int b = Color.blue(color);
					if (a >= 0x20)
					{
						int max = Math.max(r, Math.max(g, b));
						int min = Math.min(r, Math.min(g, b));
						if (max - min >= 0x1a) return false; // 10%
					}
				}
			}
			return true;
		}
		return false;
	}
	
	public static Pair<Bitmap, Boolean> handleBlackAndWhiteCaptchaImage(Bitmap image)
	{
		return handleBlackAndWhiteCaptchaImage(image, null, 0, 0);
	}
	
	public static Pair<Bitmap, Boolean> handleBlackAndWhiteCaptchaImage(Bitmap image, Bitmap overlay,
			int overlayX, int overlayY)
	{
		if (image != null)
		{
			int width = image.getWidth(), height = image.getHeight();
			Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(mask);
			canvas.drawColor(Color.WHITE);
			Paint paint = new Paint();
			paint.setColorFilter(CONTRAST_FILTER);
			canvas.drawBitmap(image, 0, 0, paint);
			if (overlay != null) canvas.drawBitmap(overlay, overlayX, overlayY, paint);
			image.recycle();
			paint.reset();
			Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			canvas = new Canvas(result);
			paint.setColorFilter(BLACK_CHROMA_KEY_FILTER);
			canvas.drawBitmap(mask, 0, 0, paint);
			mask.recycle();
			image = result;
			return new Pair<Bitmap, Boolean>(image, true);
		}
		return new Pair<Bitmap, Boolean>(null, false);
	}
	
	public static Bitmap generateNoise(int size, int scale, int colorFrom, int colorTo)
	{
		int aFrom = Color.alpha(colorFrom);
		int rFrom = Color.red(colorFrom);
		int gFrom = Color.green(colorFrom);
		int bFrom = Color.blue(colorFrom);
		int aTo = Color.alpha(colorTo);
		int rTo = Color.red(colorTo);
		int gTo = Color.green(colorTo);
		int bTo = Color.blue(colorTo);
		Random random = RANDOM;
		scale = Math.max(scale, 1);
		int realSize = size * scale;
		int[] pixels = new int[realSize * realSize];
		for (int i = 0; i < pixels.length; i += realSize * scale)
		{
			for (int j = 0; j < realSize; j += scale)
			{
				int a = random.nextInt(aTo - aFrom + 1) + aFrom;
				int r = random.nextInt(rTo - rFrom + 1) + rFrom;
				int g = random.nextInt(gTo - gFrom + 1) + gFrom;
				int b = random.nextInt(bTo - bFrom + 1) + bFrom;
				for (int k = 0; k < scale; k++) pixels[i + j + k] = Color.argb(a, r, g, b);
			}
			for (int j = 1; j < scale; j++)
			{
				System.arraycopy(pixels, i, pixels, i + j * realSize, realSize);
			}
		}
		return Bitmap.createBitmap(pixels, realSize, realSize, Bitmap.Config.ARGB_8888);
	}
}