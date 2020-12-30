package com.mishiranu.dashchan.media;

import com.mishiranu.dashchan.util.IOUtils;
import java.io.IOException;
import java.io.InputStream;

public class PngData {
	public final boolean hasMetadata;

	private final boolean hasGammaCorrection;
	private final float gammaCorrection;

	private PngData(boolean hasMetadata, boolean hasGammaCorrection, float gammaCorrection) {
		this.hasMetadata = hasMetadata;
		this.hasGammaCorrection = hasGammaCorrection;
		this.gammaCorrection = gammaCorrection;
	}

	public Float getGammaCorrection() {
		return hasGammaCorrection ? gammaCorrection : null;
	}

	public static PngData extract(InputStream input) throws IOException {
		boolean hasMetadata = false;
		boolean hasGammaCorrection = false;
		boolean ignoreGammaCorrection = false;
		float gammaCorrection = 1f;
		if (IOUtils.skipExactlyCheck(input, 8)) {
			byte[] buffer = new byte[8];
			OUT: while (true) {
				if (!IOUtils.readExactlyCheck(input, buffer, 0, 8)) {
					break;
				}
				int size = IOUtils.bytesToInt(false, 0, 4, buffer);
				String name = new String(buffer, 4, 4);
				boolean handled = false;
				switch (name) {
					case "tEXt":
					case "zTXt":
					case "iTXt":
					case "tIME":
					case "eXIf": {
						hasMetadata = true;
						break;
					}
					case "gAMA": {
						if (size == 4) {
							if (!IOUtils.readExactlyCheck(input, buffer, 0, 4)) {
								break OUT;
							}
							int value = IOUtils.bytesToInt(false, 0, 4, buffer);
							// Same check as in libpng
							if (value >= 16 && value <= 625000000) {
								gammaCorrection = 100000f / 2.2f / value;
								hasGammaCorrection = (int) (gammaCorrection * 100 + 0.5f) != 100;
							}
							handled = true;
						}
						break;
					}
					case "sRGB":
					case "iCCP": {
						ignoreGammaCorrection = true;
						break;
					}
					case "IEND": {
						break OUT;
					}
				}
				if (!IOUtils.skipExactlyCheck(input, (handled ? 0 : size) + 4)) {
					break;
				}
			}
		}
		if (ignoreGammaCorrection) {
			hasGammaCorrection = false;
			gammaCorrection = 1f;
		}
		return new PngData(hasMetadata, hasGammaCorrection, gammaCorrection);
	}
}
