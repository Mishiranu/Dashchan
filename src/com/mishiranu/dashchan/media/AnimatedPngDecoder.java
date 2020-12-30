package com.mishiranu.dashchan.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public class AnimatedPngDecoder implements Runnable {
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());
	private static final byte[] IEND_CHUNK = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82};

	private final Bitmap bitmap;
	private final Canvas canvas;
	private final Frame[] frames;
	private final int duration;

	private long startTime;

	private static class Frame {
		public byte[] bytes;
		public Bitmap bitmap;

		public final int width, height;
		public final int x, y;
		public final int startTime;

		public final boolean restoreBackground;
		public final boolean restorePrevious;
		public final boolean blendOver;

		public Frame(int width, int height, int x, int y, int startTime, boolean restoreBackground,
				boolean restorePrevious, boolean blendOver) {
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

	public AnimatedPngDecoder(FileHolder fileHolder) throws IOException {
		// Transform APNG to multiple PNG images and decode them
		byte[] buffer = new byte[8192];
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		boolean validFile = false;
		byte[] head = null;
		Frame currentFrame = null;
		Frame[] frames = null;
		int framesCount = 0;
		int totalTime = 0;
		CRC32 crc32 = new CRC32();
		try (InputStream input = new BufferedInputStream(fileHolder.openInputStream())) {
			if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) {
				throw new IOException();
			}
			output.write(buffer, 0, 8);
			while (true) {
				if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) {
					throw new IOException();
				}
				int length = IOUtils.bytesToInt(false, 0, 4, buffer);
				if (length < 0) {
					break;
				}
				length += 4;
				String name = new String(buffer, 4, 4);
				if (GraphicsUtils.isUselessPngChunk(name)) {
					if (!IOUtils.skipExactlyCheck(input, length)) {
						throw new IOException();
					}
					continue;
				}
				switch (name) {
					case "acTL": {
						if (length != 12) {
							throw new IOException();
						}
						if (!IOUtils.readExactlyCheck(input, buffer, 0, 12)) {
							throw new IOException();
						}
						int totalFramesCount = IOUtils.bytesToInt(false, 0, 4, buffer);
						if (totalFramesCount <= 0) {
							throw new IOException();
						}
						frames = new Frame[totalFramesCount];
						continue;
					}
					case "fcTL": {
						if (frames == null) {
							throw new IOException();
						}
						if (framesCount == frames.length) {
							throw new IOException();
						}
						if (length != 30) {
							throw new IOException();
						}
						if (!IOUtils.readExactlyCheck(input, buffer, 0, 30)) {
							throw new IOException();
						}
						if (currentFrame == null) {
							head = output.toByteArray();
						} else {
							currentFrame.bytes = output.toByteArray();
						}
						output.reset();
						int width = IOUtils.bytesToInt(false, 4, 4, buffer);
						int height = IOUtils.bytesToInt(false, 8, 4, buffer);
						int x = IOUtils.bytesToInt(false, 12, 4, buffer);
						int y = IOUtils.bytesToInt(false, 16, 4, buffer);
						int delayNumerator = IOUtils.bytesToInt(false, 20, 2, buffer);
						int delayDenominator = IOUtils.bytesToInt(false, 22, 2, buffer);
						if (delayDenominator <= 0) {
							delayDenominator = 100;
						}
						int delay = delayNumerator * 1000 / delayDenominator;
						if (delay <= 10) {
							delay = 100; // Like in Firefox
						}
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
					case "fdAT": {
						if (frames == null) {
							throw new IOException();
						}
						IOUtils.intToBytes(length - 8, false, 0, 4, buffer);
						output.write(buffer, 0, 4);
						@SuppressWarnings("CharsetObjectCanBeUsed")
						byte[] nameBytes = "IDAT".getBytes("ISO-8859-1");
						output.write(nameBytes);
						crc32.update(nameBytes);
						if (!IOUtils.skipExactlyCheck(input, 4)) {
							throw new IOException();
						}
						length -= 8;
						while (length > 0) {
							int count = input.read(buffer, 0, Math.min(buffer.length, length));
							if (count == -1) {
								throw new IOException();
							}
							output.write(buffer, 0, count);
							crc32.update(buffer, 0, count);
							length -= count;
						}
						if (!IOUtils.skipExactlyCheck(input, 4)) {
							throw new IOException();
						}
						IOUtils.intToBytes((int) crc32.getValue(), false, 0, 4, buffer);
						output.write(buffer, 0, 4);
						crc32.reset();
						continue;
					}
					case "IDAT": {
						if (frames == null) {
							throw new IOException();
						}
						break;
					}
				}
				output.write(buffer, 0, 8);
				while (length > 0) {
					int count = input.read(buffer, 0, Math.min(buffer.length, length));
					output.write(buffer, 0, count);
					length -= count;
				}
				if ("IEND".equals(name)) {
					if (head == null) {
						throw new IOException();
					}
					currentFrame.bytes = output.toByteArray();
					validFile = true;
					break;
				}
			}
		}
		if (!validFile) {
			throw new IOException();
		}
		if (framesCount != frames.length) {
			throw new IOException();
		}
		for (Frame frame : frames) {
			if (frame.bytes == null) {
				throw new IOException();
			}
		}
		try {
			for (Frame frame : frames) {
				IOUtils.intToBytes(frame.width, false, 16, 4, head);
				IOUtils.intToBytes(frame.height, false, 20, 4, head);
				crc32.update(head, 12, 17);
				IOUtils.intToBytes((int) crc32.getValue(), false, 29, 4, head);
				crc32.reset();
				frame.bitmap = BitmapFactory.decodeStream(new FrameInputStream(head, frame));
				Float gammaCorrection = fileHolder.getImageGammaCorrectionForSkia();
				if (gammaCorrection != null) {
					frame.bitmap = GraphicsUtils.applyGammaCorrection(frame.bitmap, gammaCorrection);
				}
				if (frame.bitmap == null) {
					recycleFrames(frames);
					throw new IOException();
				}
			}
			bitmap = Bitmap.createBitmap(frames[0].width, frames[0].height, Bitmap.Config.ARGB_8888);
			canvas = new Canvas(bitmap);
		} catch (OutOfMemoryError e) {
			recycleFrames(frames);
			throw new IOException(e);
		}
		this.frames = frames;
		duration = totalTime;
		startTime = SystemClock.elapsedRealtime();
	}

	private static void recycleFrames(Frame[] frames) {
		for (Frame frame : frames) {
			if (frame.bitmap != null) {
				frame.bitmap.recycle();
				frame.bitmap = null;
			}
		}
	}

	private static class FrameInputStream extends InputStream {
		private final byte[] head;
		private final Frame frame;

		private int position;

		private int getTotalCount() {
			return head.length + frame.bytes.length + IEND_CHUNK.length;
		}

		public FrameInputStream(byte[] head, Frame frame) {
			this.head = head;
			this.frame = frame;
		}

		@Override
		public int read() {
			int position = this.position;
			if (position < head.length) {
				this.position++;
				return head[position] & 0xff;
			}
			position -= head.length;
			if (position < frame.bytes.length) {
				this.position++;
				return frame.bytes[position] & 0xff;
			}
			position -= frame.bytes.length;
			if (position < IEND_CHUNK.length) {
				this.position++;
				return IEND_CHUNK[position] & 0xff;
			}
			return -1;
		}

		@Override
		public int read(@NonNull byte[] buffer) {
			return read(buffer, 0, buffer.length);
		}

		@Override
		public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) {
			int position = this.position;
			if (position < head.length) {
				return copy(buffer, byteOffset, byteCount, head, position);
			}
			position -= head.length;
			if (position < frame.bytes.length) {
				return copy(buffer, byteOffset, byteCount, frame.bytes, position);
			}
			position -= frame.bytes.length;
			if (position < IEND_CHUNK.length) {
				return copy(buffer, byteOffset, byteCount, IEND_CHUNK, position);
			}
			return -1;
		}

		private int copy(byte[] buffer, int byteOffset, int byteCount, byte[] from, int position) {
			int count = Math.min(from.length - position, byteCount);
			System.arraycopy(from, position, buffer, byteOffset, count);
			this.position += count;
			return count;
		}

		@Override
		public long skip(long byteCount) {
			int totalCount = getTotalCount();
			int left = totalCount - position;
			if (left < byteCount) {
				position = totalCount;
				return left;
			} else {
				position += byteCount;
				return byteCount;
			}
		}

		@Override
		public int available() {
			return getTotalCount() - position;
		}
	}

	public void recycle() {
		recycleFrames(frames);
	}

	private int lastIndex = -1;
	private boolean hasPrevious = false;
	private int[] previousColors;

	private final Paint clearPaint = new Paint(); {
		clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
	}

	private final Paint drawPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

	private void drawImage(int index) {
		if (index > 0) {
			Frame frame = frames[index - 1];
			if (frame.restoreBackground) {
				canvas.drawRect(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height, clearPaint);
			} else if (frame.restorePrevious) {
				bitmap.setPixels(previousColors, 0, bitmap.getWidth(), 0, 0,
						bitmap.getWidth(), bitmap.getHeight());
			}
		}
		Frame frame = frames[index];
		if (frame.restorePrevious) {
			if (!hasPrevious) {
				if (previousColors == null) {
					previousColors = new int[bitmap.getWidth() * bitmap.getHeight()];
				}
				bitmap.getPixels(previousColors, 0, bitmap.getWidth(), 0, 0,
						bitmap.getWidth(), bitmap.getHeight());
				hasPrevious = true;
			}
		} else {
			hasPrevious = false;
		}
		if (!frame.blendOver) {
			canvas.drawRect(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height, clearPaint);
		}
		canvas.drawBitmap(frame.bitmap, frame.x, frame.y, drawPaint);
	}

	private int draw() {
		int position = 0;
		if (duration > 0) {
			long time = SystemClock.elapsedRealtime();
			if (startTime == 0) {
				startTime = time;
			}
			position = (int) ((time - startTime) % duration);
		}
		int index = 0;
		int delay = -1;
		int count = frames.length;
		for (int i = 0; i < count; i++) {
			if (position >= frames[i].startTime) {
				index = i;
			} else {
				break;
			}
		}
		if (count > 1) {
			delay = (index + 1 < count ? frames[index + 1].startTime : duration) - position;
		}
		if (lastIndex != index) {
			if (index > lastIndex) {
				for (int i = lastIndex + 1; i <= index; i++) {
					drawImage(i);
				}
			} else {
				hasPrevious = false;
				bitmap.eraseColor(0x00000000);
				for (int i = 0; i <= index; i++) {
					drawImage(i);
				}
			}
			lastIndex = index;
		}
		return delay;
	}

	private Drawable drawable;

	public Drawable getDrawable() {
		if (drawable == null) {
			drawable = new BaseDrawable() {
				private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

				@Override
				public int getIntrinsicWidth() {
					return frames[0].width;
				}

				@Override
				public int getIntrinsicHeight() {
					return frames[0].height;
				}

				@Override
				public void setColorFilter(ColorFilter colorFilter) {
					paint.setColorFilter(colorFilter);
				}

				@Override
				public void setAlpha(int alpha) {
					paint.setAlpha(alpha);
				}

				@Override
				public void draw(@NonNull Canvas canvas) {
					Rect bounds = getBounds();
					canvas.save();
					canvas.scale((float) bounds.width() / getIntrinsicWidth(),
							(float) bounds.height() / getIntrinsicHeight());
					int delay = AnimatedPngDecoder.this.draw();
					canvas.drawBitmap(bitmap, 0, 0, paint);
					canvas.restore();
					if (delay >= 0) {
						delay -= 20;
						if (delay > 0) {
							delay = Math.min(delay, 500);
							HANDLER.postDelayed(AnimatedPngDecoder.this, delay);
						} else {
							invalidateSelf();
						}
					}
				}
			};
		}
		return drawable;
	}

	@Override
	public void run() {
		drawable.invalidateSelf();
	}
}
