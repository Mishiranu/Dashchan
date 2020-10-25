package com.mishiranu.dashchan.content.model;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import chan.util.StringUtils;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.media.WebViewBitmapDecoder;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.MimeTypes;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public abstract class FileHolder implements Serializable {
	private static final long serialVersionUID = 1L;

	public abstract String getName();
	public abstract int getSize();
	public abstract InputStream openInputStream() throws IOException;
	public abstract Descriptor openDescriptor() throws IOException;

	public interface Descriptor extends Closeable {
		FileDescriptor getFileDescriptor() throws IOException;
	}

	public String getExtension() {
		return StringUtils.getFileExtension(getName());
	}

	public enum ImageType {NOT_IMAGE, IMAGE_JPEG, IMAGE_PNG, IMAGE_GIF, IMAGE_WEBP, IMAGE_BMP, IMAGE_SVG}

	private static class ImageData {
		public ImageType type = ImageType.NOT_IMAGE;
		public JpegData jpegData;
		public int width = -1;
		public int height = -1;
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

	private ImageData getImageData() {
		synchronized (this) {
			if (imageData == null) {
				imageData = new ImageData();
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
						// Ignore exception
					}
					if (success) {
						ImageType type = null;
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
						if (type != null) {
							imageData.type = type;
							boolean rotate = false;
							if (type == ImageType.IMAGE_JPEG) {
								imageData.jpegData = JpegData.extract(this);
								int rotation = imageData.jpegData.getRotation();
								rotate = rotation == 90 || rotation == 270;
							}
							if (rotate) {
								imageData.width = options.outHeight;
								imageData.height = options.outWidth;
							} else {
								imageData.width = options.outWidth;
								imageData.height = options.outHeight;
							}
						}
					}
				} else {
					try (InputStream input = openInputStream()) {
						XmlPullParser parser = PARSER_FACTORY.newPullParser();
						parser.setInput(input, null);
						int type;
						OUTER: while ((type = parser.getEventType()) != XmlPullParser.END_DOCUMENT) {
							switch (type) {
								case XmlPullParser.START_TAG: {
									if ("svg".equals(parser.getName())) {
										int width, height;
										try {
											width = Integer.parseInt(parser.getAttributeValue(null, "width"));
											height = Integer.parseInt(parser.getAttributeValue(null, "height"));
										} catch (NumberFormatException | NullPointerException e) {
											width = -1;
											height = -1;
										}
										imageData.type = ImageType.IMAGE_SVG;
										imageData.width = width;
										imageData.height = height;
										break OUTER;
									}
									break;
								}
							}
							parser.next();
						}
					} catch (IOException | XmlPullParserException e) {
						// Ignore exception
					}
				}
			}
			return imageData;
		}
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

	public int getRotation() {
		JpegData jpegData = getJpegData();
		return jpegData != null ? jpegData.getRotation() : 0;
	}

	public boolean isRegionDecoderSupported() {
		JpegData jpegData = getJpegData();
		return jpegData == null || !jpegData.forbidRegionDecoder;
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
			if (bitmap != null && imageData.jpegData != null) {
				int rotation = imageData.jpegData.getRotation();
				if (rotation != 0) {
					Matrix matrix = new Matrix();
					matrix.setRotate(-rotation);
					Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
							matrix, false);
					bitmap.recycle();
					bitmap = newBitmap;
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
			if (mayUseRegionDecoder && isRegionDecoderSupported()) {
				InputStream input = null;
				BitmapRegionDecoder decoder = null;
				try {
					input = openInputStream();
					decoder = BitmapRegionDecoder.newInstance(input, false);
					return decoder.decodeRegion(new Rect(0, 0, decoder.getWidth(), decoder.getHeight()), options);
				} catch (IOException e) {
					Log.persistent().stack(e);
				} finally {
					IOUtils.close(input);
					if (decoder != null) {
						decoder.recycle();
					}
				}
			}
		}
		if (mayUseWebViewDecoder) {
			return WebViewBitmapDecoder.loadBitmap(this, options);
		}
		return null;
	}

	private Bitmap readBitmapSimple(BitmapFactory.Options options) {
		InputStream input = null;
		try {
			input = openInputStream();
			return BitmapFactory.decodeStream(input, null, options);
		} catch (IOException e) {
			Log.persistent().stack(e);
			return null;
		} finally {
			IOUtils.close(input);
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
		private static final long serialVersionUID = 1L;

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
		public Descriptor openDescriptor() throws IOException {
			return new FileFileHolderDescriptor(openInputStream());
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

		private static class FileFileHolderDescriptor implements Descriptor {
			private final FileInputStream fileInputStream;

			public FileFileHolderDescriptor(FileInputStream fileInputStream) {
				this.fileInputStream = fileInputStream;
			}

			@Override
			public FileDescriptor getFileDescriptor() throws IOException {
				return fileInputStream.getFD();
			}

			@Override
			public void close() throws IOException {
				fileInputStream.close();
			}
		}
	}

	private static class ContentFileHolder extends FileHolder {
		private static final long serialVersionUID = 1L;

		private final Context context;
		private final String uriString;
		private final String name;
		private final int size;

		public ContentFileHolder(Context context, Uri uri, String name, int size) {
			this.context = context.getApplicationContext();
			this.uriString = uri.toString();
			this.name = name;
			InputStream input = null;
			try {
				int newSize = 0;
				byte[] buffer = new byte[8192];
				input = openInputStream();
				int count;
				while ((count = input.read(buffer)) >= 0) {
					newSize += count;
				}
				size = newSize;
			} catch (IOException e) {
				// Ignore exception
			} finally {
				IOUtils.close(input);
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
				InputStream inputStream = context.getContentResolver().openInputStream(toUri());
				if (inputStream == null) {
					throw new IOException("InputStream is empty");
				}
				return inputStream;
			} catch (SecurityException e) {
				throw new IOException(e);
			} catch (Exception e) {
				Log.persistent().stack(e);
				throw new IOException(e);
			}
		}

		@Override
		public Descriptor openDescriptor() throws IOException {
			try {
				return new ContentFileHolderDescriptor(context.getContentResolver().openFileDescriptor(toUri(), "r"));
			} catch (SecurityException e) {
				throw new IOException(e);
			} catch (Exception e) {
				Log.persistent().stack(e);
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

		private static class ContentFileHolderDescriptor implements Descriptor {
			private final ParcelFileDescriptor parcelFileDescriptor;

			public ContentFileHolderDescriptor(ParcelFileDescriptor parcelFileDescriptor) {
				this.parcelFileDescriptor = parcelFileDescriptor;
			}

			@Override
			public FileDescriptor getFileDescriptor() {
				return parcelFileDescriptor.getFileDescriptor();
			}

			@Override
			public void close() throws IOException {
				parcelFileDescriptor.close();
			}
		}
	}

	private static long fileNameStart = System.currentTimeMillis();

	public static FileHolder obtain(File file) {
		return new FileFileHolder(file);
	}

	public static FileHolder obtain(Context context, Uri uri) {
		String scheme = uri.getScheme();
		if ("file".equals(scheme)) {
			String path = uri.getPath();
			return new FileFileHolder(new File(path));
		} else if ("content".equals(scheme)) {
			Cursor cursor = null;
			try {
				cursor = context.getContentResolver().query(uri, null, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = Math.max(cursor.getColumnIndex(OpenableColumns.SIZE), 0);
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
							return new ContentFileHolder(context, uri, name, size);
						}
					}
				}
			} catch (SecurityException e) {
				return null;
			} catch (Exception e) {
				Log.persistent().stack(e);
				return null;
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
		return null;
	}
}
