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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;

public class AnimatedPngDecoder implements Runnable
{
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());
	private static final byte[] IEND_CHUNK = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82};

	private final Bitmap mBitmap;
	private final Canvas mCanvas;
	private final Frame[] mFrames;
	private final int mDuration;
	
	private long mStartTime;
	
	private static class Frame
	{
		public byte[] bytes;
		public Bitmap bitmap;

		public final int width, height;
		public final int x, y;
		public final int startTime;

		public final boolean restoreBackground;
		public final boolean restorePrevious;
		public final boolean blendOver;
		
		public Frame(int width, int height, int x, int y, int startTime, boolean restoreBackground,
				boolean restorePrevious, boolean blendOver)
		{
			this.width = width;
			this.height = height;
			this.x = x;
			this.y = y;
			this.startTime = startTime;
			this.restoreBackground = restoreBackground;
			this.restorePrevious = restorePrevious;
			this.blendOver = blendOver;
		}
	}
	
	public AnimatedPngDecoder(FileHolder fileHolder) throws IOException
	{
		// Transform APNG to multiple PNG images and decode them
		byte[] buffer = new byte[8192];
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		InputStream input = null;
		boolean validFile = false;
		byte[] head = null;
		Frame currentFrame = null;
		Frame[] frames = null;
		int framesCount = 0;
		int totalTime = 0;
		CRC32 crc32 = new CRC32();
		try
		{
			input = new BufferedInputStream(fileHolder.openInputStream());
			if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) throw new IOException();
			output.write(buffer, 0, 8);
			while (true)
			{
				if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) throw new IOException();
				int length = IOUtils.bytesToInt(false, 0, 4, buffer);
				if (length < 0) break;
				length += 4;
				String name = new String(buffer, 4, 4);
				if (GraphicsUtils.isUselessPngChunk(name))
				{
					if (!IOUtils.skipExactlyCheck(input, length)) throw new IOException();
					continue;
				}
				if ("acTL".equals(name))
				{
					if (length != 12) throw new IOException();
					if (!IOUtils.readExactlyCheck(input, buffer, 0, 12)) throw new IOException();
					int totalFramesCount = IOUtils.bytesToInt(false, 0, 4, buffer);
					if (totalFramesCount <= 0) throw new IOException();
					frames = new Frame[totalFramesCount];
					continue;
				}
				else if ("fcTL".equals(name))
				{
					if (frames == null) throw new IOException();
					if (framesCount == frames.length) throw new IOException();
					if (length != 30) throw new IOException();
					if (!IOUtils.readExactlyCheck(input, buffer, 0, 30)) throw new IOException();
					if (currentFrame == null) head = output.toByteArray();
					else currentFrame.bytes = output.toByteArray();
					output.reset();
					int width = IOUtils.bytesToInt(false, 4, 4, buffer);
					int height = IOUtils.bytesToInt(false, 8, 4, buffer);
					int x = IOUtils.bytesToInt(false, 12, 4, buffer);
					int y = IOUtils.bytesToInt(false, 16, 4, buffer);
					int delayNumerator = IOUtils.bytesToInt(false, 20, 2, buffer);
					int delayDenominator = IOUtils.bytesToInt(false, 22, 2, buffer);
					if (delayDenominator <= 0) delayDenominator = 100;
					int delay = delayNumerator * 1000 / delayDenominator;
					if (delay <= 10) delay = 100; // Like in Firefox
					int dispose = buffer[24];
					int blend = buffer[25];
					boolean restoreBackground = dispose == 1;
					boolean restorePrevious = dispose == 2;
					boolean blendOver = blend == 1;
					currentFrame = new Frame(width, height, x, y, totalTime,
							restoreBackground, restorePrevious, blendOver);
					totalTime += delay;
					frames[framesCount++] = currentFrame;
					continue;
				}
				else if ("fdAT".equals(name))
				{
					if (frames == null) throw new IOException();
					IOUtils.intToBytes(length - 8, false, 0, 4, buffer);
					output.write(buffer, 0, 4);
					byte[] nameBytes = "IDAT".getBytes("ISO-8859-1");
					output.write(nameBytes);
					crc32.update(nameBytes);
					if (!IOUtils.skipExactlyCheck(input, 4)) throw new IOException();
					length -= 8;
					while (length > 0)
					{
						int count = input.read(buffer, 0, Math.min(buffer.length, length));
						if (count == -1) throw new IOException();
						output.write(buffer, 0, count);
						crc32.update(buffer, 0, count);
						length -= count;
					}
					if (!IOUtils.skipExactlyCheck(input, 4)) throw new IOException();
					IOUtils.intToBytes((int) crc32.getValue(), false, 0, 4, buffer);
					output.write(buffer, 0, 4);
					crc32.reset();
					continue;
				}
				else if ("IDAT".equals(name))
				{
					if (frames == null) throw new IOException();
				}
				output.write(buffer, 0, 8);
				while (length > 0)
				{
					int count = input.read(buffer, 0, Math.min(buffer.length, length));
					output.write(buffer, 0, count);
					length -= count;
				}
				if ("IEND".equals(name))
				{
					if (head == null) throw new IOException();
					currentFrame.bytes = output.toByteArray();
					validFile = true;
					break;
				}
			}
		}
		finally
		{
			IOUtils.close(input);
		}
		if (!validFile) throw new IOException();
		if (framesCount != frames.length) throw new IOException();
		for (Frame frame : frames)
		{
			if (frame.bytes == null) throw new IOException();
		}
		try
		{
			for (Frame frame : frames)
			{
				IOUtils.intToBytes(frame.width, false, 16, 4, head);
				IOUtils.intToBytes(frame.height, false, 20, 4, head);
				crc32.update(head, 12, 17);
				IOUtils.intToBytes((int) crc32.getValue(), false, 29, 4, head);
				crc32.reset();
				frame.bitmap = BitmapFactory.decodeStream(new FrameInputStream(head, frame));
				if (frame.bitmap == null)
				{
					recycleFrames(frames);
					throw new IOException();
				}
			}
			mBitmap = Bitmap.createBitmap(frames[0].width, frames[0].height, Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBitmap);
		}
		catch (OutOfMemoryError e)
		{
			recycleFrames(frames);
			throw new IOException(e);
		}
		mFrames = frames;
		mDuration = totalTime;
		mStartTime = System.currentTimeMillis();
	}
	
	private static void recycleFrames(Frame[] frames)
	{
		for (Frame frame : frames)
		{
			if (frame.bitmap != null)
			{
				frame.bitmap.recycle();
				frame.bitmap = null;
			}
		}
	}
	
	private static class FrameInputStream extends InputStream
	{
		private final byte[] mHead;
		private final Frame mFrame;
		
		private int mPosition;
		
		private int getTotalCount()
		{
			return mHead.length + mFrame.bytes.length + IEND_CHUNK.length;
		}
		
		public FrameInputStream(byte[] head, Frame frame)
		{
			mHead = head;
			mFrame = frame;
		}
		
		@Override
		public int read()
		{
			int position = mPosition;
			if (position < mHead.length)
			{
				mPosition++;
				return mHead[position] & 0xff;
			}
			position -= mHead.length;
			if (position < mFrame.bytes.length)
			{
				mPosition++;
				return mFrame.bytes[position] & 0xff;
			}
			position -= mFrame.bytes.length;
			if (position < IEND_CHUNK.length)
			{
				mPosition++;
				return IEND_CHUNK[position] & 0xff;
			}
			return -1;
		}
		
		@Override
		public int read(byte[] buffer)
		{
			return read(buffer, 0, buffer.length);
		}
		
		@Override
		public int read(byte[] buffer, int byteOffset, int byteCount)
		{
			int position = mPosition;
			if (position < mHead.length) return copy(buffer, byteOffset, byteCount, mHead, position);
			position -= mHead.length;
			if (position < mFrame.bytes.length) return copy(buffer, byteOffset, byteCount, mFrame.bytes, position);
			position -= mFrame.bytes.length;
			if (position < IEND_CHUNK.length) return copy(buffer, byteOffset, byteCount, IEND_CHUNK, position);
			return -1;
		}
		
		private int copy(byte[] buffer, int byteOffset, int byteCount, byte[] from, int position)
		{
			int count = Math.min(from.length - position, byteCount);
			System.arraycopy(from, position, buffer, byteOffset, count);
			mPosition += count;
			return count;
		}
		
		@Override
		public long skip(long byteCount)
		{
			int totalCount = getTotalCount();
			int left = totalCount - mPosition;
			if (left < byteCount)
			{
				mPosition = totalCount;
				return left;
			}
			else
			{
				mPosition += byteCount;
				return byteCount;
			}
		}
		
		@Override
		public int available()
		{
			return getTotalCount() - mPosition;
		}
	}
	
	public void recycle()
	{
		recycleFrames(mFrames);
	}

	private int mLastIndex = -1;
	private boolean mHasPrevious = false;
	private int[] mPreviousColors;
	
	private final Paint mClearPaint = new Paint();
	
	{
		mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
	}
	
	private final Paint mDrawPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	
	private void drawImage(int index)
	{
		if (index > 0)
		{
			Frame frame = mFrames[index - 1];
			if (frame.restoreBackground)
			{
				mCanvas.drawRect(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height, mClearPaint);
			}
			else if (frame.restorePrevious)
			{
				mBitmap.setPixels(mPreviousColors, 0, mBitmap.getWidth(), 0, 0,
						mBitmap.getWidth(), mBitmap.getHeight());
			}
		}
		Frame frame = mFrames[index];
		if (frame.restorePrevious)
		{
			if (!mHasPrevious)
			{
				if (mPreviousColors == null) mPreviousColors = new int[mBitmap.getWidth() * mBitmap.getHeight()];
				mBitmap.getPixels(mPreviousColors, 0, mBitmap.getWidth(), 0, 0,
						mBitmap.getWidth(), mBitmap.getHeight());
				mHasPrevious = true;
			}
		}
		else mHasPrevious = false;
		if (!frame.blendOver)
		{
			mCanvas.drawRect(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height, mClearPaint);
		}
		mCanvas.drawBitmap(frame.bitmap, frame.x, frame.y, mDrawPaint);
	}
	
	private int draw()
	{
		int position = 0;
		if (mDuration > 0)
		{
			long time = System.currentTimeMillis();
			if (mStartTime == 0) mStartTime = time;
			position = (int) ((time - mStartTime) % mDuration);
		}
		int index = 0;
		int delay = -1;
		int count = mFrames.length;
		for (int i = 0; i < count; i++)
		{
			if (position >= mFrames[i].startTime) index = i; else break;
		}
		if (count > 1) delay = (index + 1 < count ? mFrames[index + 1].startTime : mDuration) - position;
		if (mLastIndex != index)
		{
			if (index > mLastIndex)
			{
				for (int i = mLastIndex + 1; i <= index; i++) drawImage(i);
			}
			else
			{
				mHasPrevious = false;
				mBitmap.eraseColor(0x00000000);
				for (int i = 0; i <= index; i++) drawImage(i);
			}
			mLastIndex = index;
		}
		return delay;
	}
	
	private Drawable mDrawable;
	
	public Drawable getDrawable()
	{
		if (mDrawable == null)
		{
			mDrawable = new Drawable()
			{
				private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
				
				@Override
				public int getIntrinsicWidth()
				{
					return mFrames[0].width;
				}
				
				@Override
				public int getIntrinsicHeight()
				{
					return mFrames[0].height;
				}
				
				@Override
				public void setColorFilter(ColorFilter colorFilter)
				{
					mPaint.setColorFilter(colorFilter);
				}
				
				@Override
				public void setAlpha(int alpha)
				{
					mPaint.setAlpha(alpha);
				}
				
				@Override
				public int getOpacity()
				{
					return PixelFormat.TRANSPARENT;
				}
				
				@Override
				public void draw(Canvas canvas)
				{
					Rect bounds = getBounds();
					canvas.save();
					canvas.scale((float) bounds.width() / getIntrinsicWidth(),
							(float) bounds.height() / getIntrinsicHeight());
					int delay = AnimatedPngDecoder.this.draw();
					canvas.drawBitmap(mBitmap, 0, 0, mPaint);
					canvas.restore();
					if (delay >= 0)
					{
						delay -= 20;
						if (delay > 0)
						{
							delay = Math.min(delay, 500);
							HANDLER.postDelayed(AnimatedPngDecoder.this, delay);
						}
						else invalidateSelf();
					}
				}
			};
		}
		return mDrawable;
	}
	
	@Override
	public void run()
	{
		mDrawable.invalidateSelf();
	}
}