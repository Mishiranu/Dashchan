package com.mishiranu.dashchan.media;

import com.mishiranu.dashchan.util.IOUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JpegData {
	public final ExifData exifData;
	public final boolean forbidRegionDecoder;

	private JpegData(ExifData exifData, boolean forbidRegionDecoder) {
		this.exifData = exifData;
		this.forbidRegionDecoder = forbidRegionDecoder;
	}

	public static JpegData extract(InputStream fileInput) throws IOException {
		byte[] exifBytes = null;
		byte[] sofBytes = null;
		InputStream input = new BufferedInputStream(fileInput, 327680);
		byte[] buffer = new byte[2];
		while (true) {
			int oneByte = input.read();
			if (oneByte == 0xff) {
				oneByte = input.read();
				if ((oneByte & 0xe0) == 0xe0) {
					// Application data (0xe0 for JFIF, 0xe1 for EXIF) or comment (0xfe)
					if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) {
						break;
					}
					int size = IOUtils.bytesToInt(false, 0, 2, buffer);
					if (oneByte == 0xe1 && size > 14) {
						byte[] data = new byte[size - 8];
						if (!IOUtils.readExactlyCheck(input, data, 0, 6)) {
							break;
						}
						boolean isExif = new String(data).startsWith("Exif");
						if (!IOUtils.readExactlyCheck(input, data, 0, data.length)) {
							break;
						}
						if (isExif) {
							exifBytes = data;
						}
					} else {
						if (!IOUtils.skipExactlyCheck(input, size - 2)) {
							break;
						}
					}
				} else if (oneByte == 0xc0 || oneByte == 0xc1 || oneByte == 0xc2) {
					if (!IOUtils.readExactlyCheck(input, buffer, 0, 2)) {
						break;
					}
					int size = IOUtils.bytesToInt(false, 0, 2, buffer) - 2;
					byte[] data = new byte[size];
					if (!IOUtils.readExactlyCheck(input, data, 0, size)) {
						break;
					}
					sofBytes = data;
				} else if (oneByte == 0xda) {
					break;
				}
			}
			if (oneByte == -1) {
				break;
			}
		}
		boolean forbidRegionDecoder = sofBytes != null && sofBytes.length > 7 && (sofBytes[5] & 0xff) == 1
				&& (sofBytes[7] & 0xff) != 0x11;
		ExifData exifData = exifBytes != null ? ExifData.extract(exifBytes, 0) : null;
		return new JpegData(exifData == null && exifBytes != null ? ExifData.EMPTY : exifData, forbidRegionDecoder);
	}
}
