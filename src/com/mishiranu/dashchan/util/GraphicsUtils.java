package com.mishiranu.dashchan.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.util.Base64;
import android.util.Pair;
import android.view.Gravity;
import androidx.core.graphics.ColorUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.graphics.ScriptC_GammaCorrection;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;

public class GraphicsUtils {
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	private static final float CONTRAST_GAIN = 2.5f;

	private static final ColorMatrixColorFilter CONTRAST_FILTER = new ColorMatrixColorFilter(new float[] {
		CONTRAST_GAIN, 0f, 0f, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, CONTRAST_GAIN, 0f, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, 0f, CONTRAST_GAIN, 0f, (1f - CONTRAST_GAIN) * 255f,
		0f, 0f, 0f, 1f, 0f
	});

	private static final ColorMatrixColorFilter BLACK_CHROMA_KEY_FILTER = new ColorMatrixColorFilter(new float[] {
		0f, 0f, 0f, 0f, 0f,
		0f, 0f, 0f, 0f, 0f,
		0f, 0f, 0f, 0f, 0f,
		-1f/3f, -1f/3f, -1f/3f, 0f, 255f
	});

	public static final ColorMatrixColorFilter INVERT_FILTER = new ColorMatrixColorFilter(new float[] {
		-1f, 0f, 0f, 0f, 255f,
		0f, -1f, 0f, 0f, 255f,
		0f, 0f, -1f, 0f, 255f,
		0f, 0f, 0f, 1f, 0f
	});

	public static final boolean SKIA_SUPPORTS_GAMMA_CORRECTION;

	static {
		// PNG image with gAMA chunk filled with 0xff7f7f7f
		String imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAABGdBTUEAAAAQ" +
				"lpJwKQAAAApJREFUCB1jqAcAAIEAgFTzwt4AAAAASUVORK5CYII=";
		byte[] imageBytes = Base64.decode(imageBase64, 0);
		Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
		int pixel = bitmap.getPixel(0, 0);
		SKIA_SUPPORTS_GAMMA_CORRECTION = pixel != 0xff7f7f7f;
	}

	public static int modifyColorGain(int color, float gain) {
		int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
		return Color.argb(Color.alpha(color), Math.min((int) (r * gain), 0xff), Math.min((int) (g * gain), 0xff),
				Math.min((int) (b * gain), 0xff));
	}

	public static boolean isLight(int color) {
		return (Color.red(color) + Color.green(color) + Color.blue(color)) / 3 >= 0x80;
	}

	public static int mixColors(int background, int foreground) {
		int ba = Color.alpha(background), fa = Color.alpha(foreground);
		int a = fa + ba * (0xff - fa) / 0xff;
		int r = (Color.red(foreground) * fa + Color.red(background) * ba * (0xff - fa) / 0xff) / a;
		int g = (Color.green(foreground) * fa + Color.green(background) * ba * (0xff - fa) / 0xff) / a;
		int b = (Color.blue(foreground) * fa + Color.blue(background) * ba * (0xff - fa) / 0xff) / a;
		return Color.argb(Math.min(a, 0xff), Math.min(r, 0xff), Math.min(g, 0xff), Math.min(b, 0xff));
	}

	public static int applyAlpha(int color, float alpha) {
		return ColorUtils.blendARGB(0x00ffffff & color, color, alpha);
	}

	@SuppressLint("RtlHardcoded")
	public static int getDrawableColor(Context context, Drawable drawable, int gravity) {
		float density = ResourceUtils.obtainDensity(context);
		int size = Math.max(drawable.getMinimumWidth(), drawable.getMinimumHeight());
		if (size == 0) {
			size = (int) (64f * density);
		}
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		try {
			drawable.setBounds(0, 0, size, size);
			drawable.draw(new Canvas(bitmap));
			int x, y;
			switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
				case Gravity.LEFT: {
					x = 0;
					break;
				}
				case Gravity.RIGHT: {
					x = size - 1;
					break;
				}
				default: {
					x = size / 2;
					break;
				}
			}
			switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
				case Gravity.TOP: {
					y = 0;
					break;
				}
				case Gravity.BOTTOM: {
					y = size - 1;
					break;
				}
				default: {
					y = size / 2;
					break;
				}
			}
			return bitmap.getPixel(x, y);
		} finally {
			bitmap.recycle();
		}
	}

	private static final Field FIELD_GRADIENT_STATE;
	private static final Field FIELD_GRADIENT_STATE_RADIUS;

	static {
		Field gradientStateField = null;
		Field gradientStateRadiusField = null;
		if (!C.API_NOUGAT) {
			Field field;
			try {
				field = GradientDrawable.class.getDeclaredField("mGradientState");
				field.setAccessible(true);
				gradientStateField = field;
				field = field.getType().getDeclaredField("mRadius");
				field.setAccessible(true);
				gradientStateRadiusField = field;
			} catch (Exception e) {
				e.printStackTrace();
				gradientStateField = null;
				gradientStateRadiusField = null;
			}
		}
		FIELD_GRADIENT_STATE = gradientStateField;
		FIELD_GRADIENT_STATE_RADIUS = gradientStateRadiusField;
	}

	public static float getCornerRadius(GradientDrawable drawable) {
		if (C.API_NOUGAT) {
			return drawable.getCornerRadius();
		} else if (FIELD_GRADIENT_STATE != null && FIELD_GRADIENT_STATE_RADIUS != null) {
			try {
				Object state = FIELD_GRADIENT_STATE.get(drawable);
				return FIELD_GRADIENT_STATE_RADIUS.getFloat(state);
			} catch (Exception e) {
				e.printStackTrace();
				return 0f;
			}
		} else {
			return 0f;
		}
	}

	public static Bitmap reduceThumbnailSize(Resources resources, Bitmap bitmap) {
		int newSize = (int) (72f * ResourceUtils.obtainDensity(resources));
		return reduceBitmapSize(bitmap, newSize, true);
	}

	public static Bitmap reduceBitmapSize(Bitmap bitmap, int newSize, boolean recycleOld) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int oldSize = Math.min(width, height);
		float scale = newSize / (float) oldSize;
		if (scale >= 1.0) {
			return bitmap;
		}
		Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, (int) (width * scale), (int) (height * scale), true);
		if (recycleOld && resizedBitmap != bitmap) {
			bitmap.recycle();
		}
		return resizedBitmap;
	}

	public static class Reencoding {
		public static final String FORMAT_JPEG = "jpeg";
		public static final String FORMAT_PNG = "png";

		public final String format;
		public final int quality;
		public final int reduce;

		public Reencoding(String format, int quality, int reduce) {
			this.format = FORMAT_JPEG.equals(format) || FORMAT_PNG.equals(format) ? format : FORMAT_JPEG;
			this.quality = Math.max(1, Math.min(quality, 100));
			this.reduce = Math.max(1, Math.min(reduce, 8));
		}

		public static boolean allowQuality(String format) {
			return FORMAT_JPEG.equals(format);
		}
	}

	public static boolean canRemoveMetadata(FileHolder fileHolder) {
		return fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG
				|| fileHolder.getImageType() == FileHolder.ImageType.IMAGE_PNG;
	}

	public static class SkipRange {
		public final int start;
		public final int count;

		private SkipRange(int start, int count) {
			this.start = start;
			this.count = count;
		}
	}

	public static class TransformationData {
		public final ArrayList<SkipRange> skipRanges;
		public final byte[] decodedBytes;
		public final String newFileName;
		public final int newWidth;
		public final int newHeight;

		private TransformationData(ArrayList<SkipRange> skipRanges, byte[] decodedBytes, String newFileName,
				int newWidth, int newHeight) {
			this.skipRanges = skipRanges;
			this.decodedBytes = decodedBytes;
			this.newFileName = newFileName;
			this.newWidth = newWidth;
			this.newHeight = newHeight;
		}
	}

	public static TransformationData transformImageForPosting(FileHolder fileHolder, String fileName,
			boolean removeMetadata, Reencoding reencoding) {
		ArrayList<SkipRange> skipRanges = null;
		byte[] decodedBytes = null;
		String newFileName = null;
		int newWidth = -1;
		int newHeight = -1;
		if (reencoding != null && fileHolder.isImage()) {
			Bitmap bitmap;
			try {
				bitmap = fileHolder.readImageBitmap(Integer.MAX_VALUE, true, true);
			} catch (Exception | OutOfMemoryError e) {
				bitmap = null;
			}
			if (bitmap != null) {
				try {
					if (reencoding.reduce > 1) {
						Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
								Math.max(bitmap.getWidth() / reencoding.reduce, 1),
								Math.max(bitmap.getHeight() / reencoding.reduce, 1), true);
						if (scaledBitmap != bitmap) {
							bitmap.recycle();
							bitmap = scaledBitmap;
						}
						newWidth = bitmap.getWidth();
						newHeight = bitmap.getHeight();
					}
					boolean png = Reencoding.FORMAT_PNG.equals(reencoding.format);
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					bitmap.compress(Reencoding.FORMAT_PNG.equals(reencoding.format) ? Bitmap.CompressFormat.PNG
							: Bitmap.CompressFormat.JPEG, reencoding.quality, output);
					decodedBytes = output.toByteArray();
					int index = fileName.lastIndexOf('.');
					newFileName = (index >= 0 ? fileName.substring(0, index) : fileName) + (png ? ".png" : ".jpeg");
				} finally {
					bitmap.recycle();
				}
			}
		} else if (removeMetadata) {
			if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_JPEG) {
				try (InputStream input = new BufferedInputStream(fileHolder.openInputStream(), 16 * 1024)) {
					int position = 0;
					byte[] buffer = new byte[2];
					while (true) {
						int oneByte = input.read();
						position++;
						if (oneByte == 0xff) {
							oneByte = input.read();
							position++;
							if ((oneByte & 0xe0) == 0xe0 || oneByte == 0xfe) {
								// Application data (0xe0 for JFIF, 0xe1 for EXIF) or comment (0xfe)
								if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) {
									break;
								}
								int size = IOUtils.bytesToInt(false, 0, 2, buffer);
								if (!IOUtils.skipExactlyCheck(input, size - 2)) {
									break;
								}
								if (skipRanges == null) {
									skipRanges = new ArrayList<>();
								}
								skipRanges.add(new SkipRange(position - 2, size + 2));
								position += size;
							}
						}
						if (oneByte == -1) {
							break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_PNG) {
				try (InputStream input = fileHolder.openInputStream()) {
					if (IOUtils.skipExactlyCheck(input, 8)) {
						int position = 8;
						byte[] buffer = new byte[8];
						while (true) {
							if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) {
								break;
							}
							int size = IOUtils.bytesToInt(false, 0, 4, buffer);
							String name = new String(buffer, 4, 4);
							if (!IOUtils.skipExactlyCheck(input, size + 4)) {
								break;
							}
							if (isUselessPngChunk(name)) {
								if (skipRanges == null) {
									skipRanges = new ArrayList<>();
								}
								skipRanges.add(new SkipRange(position, size + 12));
							}
							position += size + 12;
							if ("IEND".equals(name)) {
								int fileSize = fileHolder.getSize();
								if (fileSize > position) {
									if (skipRanges == null) {
										skipRanges = new ArrayList<>();
									}
									skipRanges.add(new SkipRange(position, fileSize - position));
								}
								break;
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return skipRanges != null || decodedBytes != null || newFileName != null
				? new TransformationData(skipRanges, decodedBytes, newFileName, newWidth, newHeight) : null;
	}

	public static boolean isUselessPngChunk(String name) {
		return "iTXt".equals(name) || "tEXt".equals(name) || "zTXt".equals(name) ||
				"tIME".equals(name) || "eXIf".equals(name);
	}

	public static boolean isBlackAndWhiteCaptchaImage(Bitmap image) {
		if (image != null) {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = new int[width];
			for (int i = 0; i < height; i++) {
				image.getPixels(pixels, 0, width, 0, i, width, 1);
				for (int j = 0; j < width; j++) {
					int color = pixels[j];
					int a = Color.alpha(color);
					int r = Color.red(color);
					int g = Color.green(color);
					int b = Color.blue(color);
					if (a >= 0x20) {
						int max = Math.max(r, Math.max(g, b));
						int min = Math.min(r, Math.min(g, b));
						if (max - min >= 0x1a) {
							return false; // 10%
						}
					}
				}
			}
			return true;
		}
		return false;
	}

	public static Pair<Bitmap, Boolean> handleBlackAndWhiteCaptchaImage(Bitmap image) {
		return handleBlackAndWhiteCaptchaImage(image, null, 0, 0);
	}

	public static Pair<Bitmap, Boolean> handleBlackAndWhiteCaptchaImage(Bitmap image, Bitmap overlay,
			int overlayX, int overlayY) {
		if (image != null) {
			int width = image.getWidth(), height = image.getHeight();
			Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(mask);
			canvas.drawColor(Color.WHITE);
			Paint paint = new Paint();
			paint.setColorFilter(CONTRAST_FILTER);
			canvas.drawBitmap(image, 0, 0, paint);
			if (overlay != null) {
				canvas.drawBitmap(overlay, overlayX, overlayY, paint);
			}
			image.recycle();
			paint.reset();
			Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			canvas = new Canvas(result);
			paint.setColorFilter(BLACK_CHROMA_KEY_FILTER);
			canvas.drawBitmap(mask, 0, 0, paint);
			mask.recycle();
			image = result;
			return new Pair<>(image, true);
		}
		return new Pair<>(null, false);
	}

	public static Bitmap generateNoise(int size, int scale, int colorFrom, int colorTo) {
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
		for (int i = 0; i < pixels.length; i += realSize * scale) {
			for (int j = 0; j < realSize; j += scale) {
				int a = random.nextInt(aTo - aFrom + 1) + aFrom;
				int r = random.nextInt(rTo - rFrom + 1) + rFrom;
				int g = random.nextInt(gTo - gFrom + 1) + gFrom;
				int b = random.nextInt(bTo - bFrom + 1) + bFrom;
				for (int k = 0; k < scale; k++) {
					pixels[i + j + k] = Color.argb(a, r, g, b);
				}
			}
			for (int j = 1; j < scale; j++) {
				System.arraycopy(pixels, i, pixels, i + j * realSize, realSize);
			}
		}
		return Bitmap.createBitmap(pixels, realSize, realSize, Bitmap.Config.ARGB_8888);
	}

	public static Bitmap mutateBitmap(Bitmap bitmap) {
		if (bitmap.isMutable()) {
			return bitmap;
		} else {
			Bitmap newBitmap = bitmap.copy(bitmap.getConfig(), true);
			if (newBitmap != bitmap) {
				bitmap.recycle();
			}
			return newBitmap;
		}
	}

	public static Bitmap applyRotation(Bitmap bitmap, int rotation) {
		if (rotation / 90 * 90 != rotation) {
			throw new IllegalArgumentException("Invalid rotation: " + rotation);
		}
		if (bitmap == null) {
			return null;
		}
		if (rotation % 360 == 0) {
			return bitmap;
		}
		Matrix matrix = new Matrix();
		matrix.setRotate(-rotation);
		try {
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
		} finally {
			bitmap.recycle();
		}
	}

	public static Bitmap applyGammaCorrection(Bitmap bitmap, float gammaCorrection) {
		if (bitmap == null) {
			return null;
		}
		bitmap = mutateBitmap(bitmap);
		if (bitmap == null) {
			return null;
		}
		if (C.API_LOLLIPOP) {
			RenderScript renderScript = RenderScript.create(MainApplication.getInstance());
			Allocation allocation = Allocation.createFromBitmap(renderScript, bitmap);
			ScriptC_GammaCorrection script = new ScriptC_GammaCorrection(renderScript);
			script.set_gammaCorrection(gammaCorrection);
			script.forEach_apply(allocation);
			allocation.copyTo(bitmap);
			allocation.destroy();
			renderScript.destroy();
		} else {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int[] pixels = new int[width];
			for (int y = 0; y < height; y++) {
				bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
				for (int x = 0; x < width; x++) {
					int color = pixels[x];
					float red = Color.red(color) / 255f;
					float green = Color.green(color) / 255f;
					float blue = Color.blue(color) / 255f;
					red = (float) Math.pow(red, gammaCorrection);
					green = (float) Math.pow(green, gammaCorrection);
					blue = (float) Math.pow(blue, gammaCorrection);
					pixels[x] = Color.argb(Color.alpha(color), (int) (red * 255 + 0.5f),
							(int) (green * 255 + 0.5f), (int) (blue * 255 + 0.5f));
				}
				bitmap.setPixels(pixels, 0, width, 0, y, width, 1);
			}
		}
		return bitmap;
	}
}
