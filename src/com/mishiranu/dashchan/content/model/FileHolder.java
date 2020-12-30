package com.mishiranu.dashchan.content.model;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Pair;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.media.PngData;
import com.mishiranu.dashchan.media.WebViewDecoder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.MimeTypes;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public abstract class FileHolder {
	public abstract String getName();
	public abstract int getSize();
	public abstract InputStream openInputStream() throws IOException;
	public abstract ParcelFileDescriptor openFileDescriptor() throws IOException;

	public String getExtension() {
		return StringUtils.getFileExtension(getName());
	}

	public enum ImageType {NOT_IMAGE, IMAGE_JPEG, IMAGE_PNG, IMAGE_GIF, IMAGE_WEBP, IMAGE_BMP, IMAGE_SVG}

	private static class ImageData {
		public final ImageType type;
		public final JpegData jpegData;
		public final PngData pngData;
		public final int width;
		public final int height;

		public ImageData(ImageType type, JpegData jpegData, PngData pngData, int width, int height) {
			this.type = type;
			this.jpegData = jpegData;
			this.pngData = pngData;
			this.width = width;
			this.height = height;
		}

		public int getRotation() {
			JpegData jpegData = this.jpegData;
			return jpegData != null && jpegData.exifData != null ? jpegData.exifData.getRotation() : 0;
		}

		public Float getGammaCorrectionForSkia() {
			if (GraphicsUtils.SKIA_SUPPORTS_GAMMA_CORRECTION) {
				return null;
			} else {
				PngData pngData = this.pngData;
				return pngData != null ? pngData.getGammaCorrection() : null;
			}
		}

		public boolean isRegionDecoderSupported() {
			JpegData jpegData = this.jpegData;
			return jpegData == null || !jpegData.forbidRegionDecoder;
		}
	}

	private transient ImageData imageData;

	private static final XmlPullParserFactory PARSER_FACTORY;

	static {
		try {
			PARSER_FACTORY = XmlPullParserFactory.newInstance();
		} catch (XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	private static final int[] SIGNATURE_JPEG = {0xff, 0xd8, 0xff};
	private static final int[] SIGNATURE_PNG = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
	private static final int[] SIGNATURE_GIF = {'G', 'I', 'F', '8', -1, 'a'};
	private static final int[] SIGNATURE_WEBP = {'R', 'I', 'F', 'F', -1, -1, -1, -1, 'W', 'E', 'B', 'P'};
	private static final int[] SIGNATURE_BMP = {'B', 'M'};

	private static boolean startsWith(byte[] where, int[] what) {
		if (where == null || what == null) {
			return false;
		}
		if (what.length > where.length) {
			return false;
		}
		for (int i = 0; i < what.length; i++) {
			if (what[i] >= 0 && what[i] != (where[i] & 0xff)) {
				return false;
			}
		}
		return true;
	}

	private ImageData obtainImageData() {
		ImageType type = ImageType.NOT_IMAGE;
		JpegData jpegData = null;
		PngData pngData = null;
		int width = -1;
		int height = -1;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		readBitmapSimple(options);
		if (options.outWidth > 0 && options.outHeight > 0) {
			byte[] signature = null;
			boolean success = false;
			try (InputStream input = openInputStream()) {
				signature = new byte[12];
				success = IOUtils.readExactlyCheck(input, signature, 0, signature.length);
			} catch (IOException e) {
				// Ignore
			}
			if (success) {
				if (startsWith(signature, SIGNATURE_PNG)) {
					type = ImageType.IMAGE_PNG;
				} else if (startsWith(signature, SIGNATURE_JPEG)) {
					type = ImageType.IMAGE_JPEG;
				} else if (startsWith(signature, SIGNATURE_GIF)) {
					type = ImageType.IMAGE_GIF;
				} else if (startsWith(signature, SIGNATURE_WEBP)) {
					type = ImageType.IMAGE_WEBP;
				} else if (startsWith(signature, SIGNATURE_BMP)) {
					type = ImageType.IMAGE_BMP;
				}
				if (type != ImageType.NOT_IMAGE) {
					boolean swapDimensions = false;
					if (type == ImageType.IMAGE_JPEG) {
						try (InputStream input = openInputStream()) {
							jpegData = JpegData.extract(input);
						} catch (IOException e) {
							// Ignore
						}
						if (jpegData != null && jpegData.exifData != null) {
							int rotation = jpegData.exifData.getRotation();
							swapDimensions = rotation % 180 != 0;
						}
					} else if (type == ImageType.IMAGE_PNG) {
						try (InputStream input = openInputStream()) {
							pngData = PngData.extract(input);
						} catch (IOException e) {
							// Ignore
						}
					}
					if (swapDimensions) {
						width = options.outHeight;
						height = options.outWidth;
					} else {
						width = options.outWidth;
						height = options.outHeight;
					}
				}
			}
		} else {
			try (InputStream input = openInputStream()) {
				XmlPullParser parser = PARSER_FACTORY.newPullParser();
				parser.setInput(input, null);
				int token;
				OUTER: while ((token = parser.next()) != XmlPullParser.END_DOCUMENT) {
					switch (token) {
						case XmlPullParser.START_TAG: {
							if ("svg".equals(parser.getName())) {
								String widthString = parser.getAttributeValue(null, "width");
								String heightString = parser.getAttributeValue(null, "height");
								if (widthString != null && heightString != null) {
									try {
										width = Integer.parseInt(widthString);
										height = Integer.parseInt(heightString);
									} catch (NumberFormatException e) {
										width = -1;
										height = -1;
									}
								}
								type = ImageType.IMAGE_SVG;
								break OUTER;
							}
							break;
						}
					}
				}
			} catch (IOException | XmlPullParserException e) {
				// Ignore
			}
		}
		return new ImageData(type, jpegData, pngData, width, height);
	}

	private ImageData getImageData() {
		if (imageData == null) {
			synchronized (this) {
				if (imageData == null) {
					imageData = obtainImageData();
				}
			}
		}
		return imageData;
	}

	public ImageType getImageType() {
		return getImageData().type;
	}

	public boolean isImage() {
		return getImageData().type != ImageType.NOT_IMAGE;
	}

	public JpegData getJpegData() {
		return getImageData().jpegData;
	}

	public PngData getPngData() {
		return getImageData().pngData;
	}

	public int getImageRotation() {
		return getImageData().getRotation();
	}

	public Float getImageGammaCorrectionForSkia() {
		return getImageData().getGammaCorrectionForSkia();
	}

	public boolean isImageRegionDecoderSupported() {
		return getImageData().isRegionDecoderSupported();
	}

	public int getImageWidth() {
		return getImageData().width;
	}

	public int getImageHeight() {
		return getImageData().height;
	}

	public Bitmap readImageBitmap(int maxSize, boolean mayUseRegionDecoder, boolean mayUseWebViewDecoder) {
		ImageData imageData = getImageData();
		if (imageData.type != ImageType.NOT_IMAGE) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calculateInSampleSize(maxSize, imageData.width, imageData.height);
			Bitmap bitmap = readBitmapInternal(options, mayUseRegionDecoder, mayUseWebViewDecoder);
			if (bitmap != null) {
				int rotation = imageData.getRotation();
				bitmap = GraphicsUtils.applyRotation(bitmap, rotation);
				Float gammaCorrection = imageData.getGammaCorrectionForSkia();
				if (gammaCorrection != null) {
					bitmap = GraphicsUtils.applyGammaCorrection(bitmap, gammaCorrection);
				}
			}
			return bitmap;
		}
		return null;
	}

	private Bitmap readBitmapInternal(BitmapFactory.Options options, boolean mayUseRegionDecoder,
			boolean mayUseWebViewDecoder) {
		ImageData imageData = getImageData();
		if (imageData.type == ImageType.NOT_IMAGE) {
			return null;
		}
		if (imageData.type != ImageType.IMAGE_SVG) {
			Bitmap bitmap = readBitmapSimple(options);
			if (bitmap != null) {
				return bitmap;
			}
			if (mayUseRegionDecoder && imageData.isRegionDecoderSupported()) {
				BitmapRegionDecoder decoder = null;
				try (InputStream input = openInputStream()) {
					decoder = BitmapRegionDecoder.newInstance(input, false);
					return decoder.decodeRegion(new Rect(0, 0, decoder.getWidth(), decoder.getHeight()), options);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (decoder != null) {
						decoder.recycle();
					}
				}
			}
		}
		if (mayUseWebViewDecoder) {
			return WebViewDecoder.loadBitmap(this, options);
		}
		return null;
	}

	private Bitmap readBitmapSimple(BitmapFactory.Options options) {
		try (InputStream input = openInputStream()) {
			return BitmapFactory.decodeStream(input, null, options);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static int calculateInSampleSize(int max, int width, int height) {
		if (width > max || height > max) {
			int size = Math.max(width, height);
			int scale = (size + max - 1) / max;
			int inSampleSize = 1;
			while (scale > inSampleSize) {
				inSampleSize *= 2;
			}
			return inSampleSize;
		}
		return 1;
	}

	private static class FileFileHolder extends FileHolder {
		private final File file;

		public FileFileHolder(File file) {
			this.file = file;
		}

		@Override
		public String getName() {
			return file.getName();
		}

		@Override
		public int getSize() {
			return (int) file.length();
		}

		@Override
		public FileInputStream openInputStream() throws IOException {
			return new FileInputStream(file);
		}

		@Override
		public ParcelFileDescriptor openFileDescriptor() throws IOException {
			ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			if (descriptor == null) {
				throw new FileNotFoundException("File descriptor is null");
			}
			return descriptor;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof FileFileHolder) {
				return ((FileFileHolder) o).file.equals(file);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return file.hashCode();
		}
	}

	private static class ContentFileHolder extends FileHolder {
		private final String uriString;
		private final String name;
		private final int size;

		public ContentFileHolder(Uri uri, String name, int size) {
			this.uriString = uri.toString();
			this.name = name;
			try (InputStream input = openInputStream()) {
				int newSize = 0;
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) >= 0) {
					newSize += count;
				}
				size = newSize;
			} catch (IOException e) {
				// Ignore
			}
			this.size = size;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int getSize() {
			return size;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			try {
				InputStream inputStream = MainApplication.getInstance()
						.getContentResolver().openInputStream(toUri());
				if (inputStream == null) {
					throw new IOException("InputStream is empty");
				}
				return inputStream;
			} catch (SecurityException e) {
				throw new IOException(e);
			} catch (Exception e) {
				e.printStackTrace();
				throw new IOException(e);
			}
		}

		@Override
		public ParcelFileDescriptor openFileDescriptor() throws IOException {
			try {
				ParcelFileDescriptor descriptor = MainApplication.getInstance()
						.getContentResolver().openFileDescriptor(toUri(), "r");
				if (descriptor == null) {
					throw new FileNotFoundException("File descriptor is null");
				}
				return descriptor;
			} catch (SecurityException e) {
				throw new IOException(e);
			}
		}

		private Uri toUri() {
			return Uri.parse(uriString);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof ContentFileHolder) {
				return ((ContentFileHolder) o).uriString.equals(uriString);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return uriString.hashCode();
		}
	}

	private static long fileNameStart = System.currentTimeMillis();

	public static FileHolder obtain(File file) {
		return new FileFileHolder(file);
	}

	public static FileHolder obtain(Uri uri) {
		String scheme = uri.getScheme();
		if ("file".equals(scheme)) {
			String path = uri.getPath();
			return new FileFileHolder(new File(path));
		} else if ("content".equals(scheme)) {
			try (Cursor cursor = MainApplication.getInstance()
					.getContentResolver().query(uri, null, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
					if (nameIndex >= 0 && sizeIndex >= 0) {
						String name = cursor.getString(nameIndex);
						int size = cursor.getInt(sizeIndex);
						if (StringUtils.isEmpty(name)) {
							@SuppressWarnings("deprecation")
							String column = MediaStore.MediaColumns.DATA;
							int dataIndex = cursor.getColumnIndex(column);
							if (dataIndex >= 0) {
								String data = cursor.getString(dataIndex);
								if (data != null) {
									name = new File(data).getName();
								}
							}
						}
						if (StringUtils.isEmpty(name)) {
							int mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
							if (mimeTypeIndex >= 0) {
								String mimeType = cursor.getString(mimeTypeIndex);
								if (mimeType != null) {
									String extension = MimeTypes.toExtension(mimeType);
									if (!StringUtils.isEmpty(extension)) {
										name = ++fileNameStart + "." + extension;
									}
								}
							}
						}
						if (!StringUtils.isEmpty(name)) {
							return new ContentFileHolder(uri, name, size);
						}
					}
				}
			} catch (SecurityException e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	public static FileHolder obtain(DataFile file) {
		Pair<File, Uri> fileOrUri = file.getFileOrUri();
		if (fileOrUri.first != null) {
			return obtain(fileOrUri.first);
		} else if (fileOrUri.second != null) {
			return obtain(fileOrUri.second);
		} else {
			return null;
		}
	}
}
