package com.mishiranu.dashchan.media;

import android.util.Pair;
import chan.util.StringUtils;
import com.mishiranu.dashchan.util.IOUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExifData {
	private static final int EXIF_TAG_DESCRIPTION = 0x010e;
	private static final int EXIF_TAG_MAKE = 0x010f;
	private static final int EXIF_TAG_MODEL = 0x0110;
	private static final int EXIF_TAG_ORIENTATION = 0x0112;
	private static final int EXIF_TAG_SOFTWARE = 0x0131;
	private static final int EXIF_TAG_DATE_TIME = 0x0132;
	private static final int EXIF_TAG_EXPOSURE_TIME = 0x829a;
	private static final int EXIF_TAG_F_NUMBER = 0x829d;
	private static final int EXIF_TAG_EXIF_IFD = 0x8769;
	private static final int EXIF_TAG_GPS_IFD = 0x8825;
	private static final int EXIF_TAG_ISO_SPEED = 0x8827;
	private static final int EXIF_TAG_SHUTTER_SPEED = 0x9201;
	private static final int EXIF_TAG_APERTURE = 0x9202;
	private static final int EXIF_TAG_BRIGHTNESS = 0x9203;
	private static final int EXIF_TAG_FOCAL_LENGTH = 0x920a;

	private static final String KEY_EXIF_OFFSET = "exifOffset";
	private static final String KEY_GPS_OFFSET = "gpsOffset";

	private static final String KEY_DESCRIPTION = "description";
	private static final String KEY_MAKE = "make";
	private static final String KEY_MODEL = "model";
	private static final String KEY_ORIENTATION = "orientation";
	private static final String KEY_SOFTWARE = "software";
	private static final String KEY_DATE_TIME = "dateTime";
	private static final String KEY_FOCAL_LENGTH = "focalLength";
	private static final String KEY_SHUTTER_SPEED = "shutterSpeed";
	private static final String KEY_APERTURE = "aperture";
	private static final String KEY_ISO_SPEED = "isoSpeed";
	private static final String KEY_BRIGHTNESS = "brightness";

	private static final String KEY_LATITUDE = "latitude";
	private static final String KEY_LATITUDE_REF = "latitudeRef";
	private static final String KEY_LONGITUDE = "longitude";
	private static final String KEY_LONGITUDE_REF = "longitudeRef";

	public static final ExifData EMPTY;

	static {
		EMPTY = new ExifData(null);
		EMPTY.userMetadata = Collections.emptyList();
	}

	private final Map<String, String> exif;

	private ExifData(Map<String, String> exif) {
		this.exif = exif;
	}

	private static boolean add(List<Pair<String, String>> userMetadata,
			Map<String, String> exif, String key, String title) {
		String value = exif.get(key);
		if (!StringUtils.isEmpty(value)) {
			userMetadata.add(new Pair<>(title, value));
			return true;
		}
		return false;
	}

	private List<Pair<String, String>> userMetadata = null;

	@SuppressWarnings("UnusedAssignment")
	public List<Pair<String, String>> getUserMetadata() {
		if (userMetadata == null) {
			synchronized (this) {
				if (userMetadata == null) {
					List<Pair<String, String>> userMetadata;
					if (exif != null) {
						userMetadata = new ArrayList<>();
						boolean addDivider = false;
						addDivider |= add(userMetadata, exif, KEY_DESCRIPTION, "Description");
						addDivider |= add(userMetadata, exif, KEY_MAKE, "Manufacturer");
						addDivider |= add(userMetadata, exif, KEY_MODEL, "Model");
						addDivider |= add(userMetadata, exif, KEY_SOFTWARE, "Software");
						addDivider |= add(userMetadata, exif, KEY_DATE_TIME, "Date");
						int rotation = getRotation();
						if (rotation != 0) {
							userMetadata.add(new Pair<>("Rotation", rotation + "°"));
							addDivider = true;
						}
						if (addDivider) {
							userMetadata.add(null);
							addDivider = false;
						}
						addDivider |= add(userMetadata, exif, KEY_FOCAL_LENGTH, "Focal length");
						addDivider |= add(userMetadata, exif, KEY_SHUTTER_SPEED, "Shutter speed");
						addDivider |= add(userMetadata, exif, KEY_APERTURE, "Aperture");
						addDivider |= add(userMetadata, exif, KEY_ISO_SPEED, "ISO speed");
						addDivider |= add(userMetadata, exif, KEY_BRIGHTNESS, "Brightness");
						if (addDivider) {
							userMetadata.add(null);
							addDivider = false;
						}
						String geolocation = getGeolocation(true);
						if (geolocation != null) {
							userMetadata.add(new Pair<>("Location", geolocation));
							addDivider = true;
						}
						userMetadata = Collections.unmodifiableList(userMetadata);
					} else {
						userMetadata = Collections.emptyList();
					}
					this.userMetadata = userMetadata;
				}
			}
		}
		return userMetadata;
	}

	public int getRotation() {
		if (exif != null) {
			String orientation = exif.get(KEY_ORIENTATION);
			if (orientation != null) {
				switch (orientation) {
					case "8": {
						return 90;
					}
					case "3": {
						return 180;
					}
					case "6": {
						return 270;
					}
				}
			}
		}
		return 0;
	}

	private String formatLocationValue(double value) {
		int degrees = (int) value;
		value -= degrees;
		value *= 60;
		int minutes = (int) value;
		value -= minutes;
		value *= 60;
		int seconds = (int) value;
		return degrees + "°" + minutes + "'" + seconds + "\"";
	}

	public String getGeolocation(boolean userReadable) {
		if (exif != null) {
			String latitude = exif.get(KEY_LATITUDE);
			String longitude = exif.get(KEY_LONGITUDE);
			if (latitude != null && longitude != null) {
				String latitudeRef = exif.get(KEY_LATITUDE_REF);
				String longitudeRef = exif.get(KEY_LONGITUDE_REF);
				if (StringUtils.isEmptyOrWhitespace(latitudeRef)) {
					latitudeRef = "N";
				}
				if (StringUtils.isEmptyOrWhitespace(longitudeRef)) {
					longitudeRef = "E";
				}
				if (userReadable) {
					double latitudeValue = Double.parseDouble(latitude);
					double longitudeValue = Double.parseDouble(longitude);
					return formatLocationValue(latitudeValue) + latitudeRef + " " + formatLocationValue(longitudeValue)
							+ longitudeRef;
				} else {
					return ("S".equals(latitudeRef) ? "-" : "") + latitude + ","
							+ ("W".equals(longitudeRef) ? "-" : "") + longitude;
				}
			}
		}
		return null;
	}

	public ExifData mergeTo(ExifData exifData) {
		if (exifData == null || exifData.exif == null || exifData.exif.isEmpty()) {
			return this;
		} else if (exif == null || exif.isEmpty()) {
			return exifData;
		} else {
			LinkedHashMap<String, String> exif = new LinkedHashMap<>(exifData.exif);
			exif.putAll(this.exif);
			return new ExifData(Collections.unmodifiableMap(exif));
		}
	}

	private static String extractIfdString(byte[] exifBytes, int offset, int format) {
		if (format == 2 && offset >= 0 && exifBytes.length > offset && exifBytes[offset] != 0x00) {
			for (int i = offset; i < exifBytes.length; i++) {
				if (exifBytes[i] == 0x00) {
					return new String(exifBytes, offset, i - offset);
				}
			}
		}
		return null;
	}

	private static double convertIfdRational(byte[] exifBytes, int offset, int format, boolean littleEndian) {
		if ((format == 5 || format == 10) && offset >= 0 && exifBytes.length >= offset + 8) {
			int numerator = IOUtils.bytesToInt(littleEndian, offset, 4, exifBytes);
			int denominator = IOUtils.bytesToInt(littleEndian, offset + 4, 4, exifBytes);
			return (double) numerator / denominator;
		}
		return Double.NaN;
	}

	private static String convertIfdGpsString(byte[] exifBytes, int offset, int format, boolean littleEndian,
			int count) {
		if ((format == 5 || format == 10) && offset >= 0 && exifBytes.length >= offset + 8) {
			double value = 0.0;
			int[] denominators = {1, 60, 3600};
			count = Math.max(Math.min(count, 3), 1);
			for (int i = 0; i < count; i++) {
				double itValue = convertIfdRational(exifBytes, offset + 8 * i, format, littleEndian);
				if (Double.isNaN(itValue)) {
					break;
				}
				value += itValue / denominators[i];
			}
			return String.format(Locale.US, "%.7f", value);
		}
		return null;
	}

	private static String formatDoubleSimple(double value) {
		return StringUtils.stripTrailingZeros(String.format(Locale.US, "%.1f", value));
	}

	private enum Ifd {GENERAL, GPS}

	private static boolean extractIfd(Map<String, String> exif, Ifd ifd,
			byte[] exifBytes, int offset, boolean littleEndian) {
		if (offset >= 0 && exifBytes.length >= offset + 2) {
			int entries = IOUtils.bytesToInt(littleEndian, offset, 2, exifBytes);
			if (exifBytes.length >= offset + 2 + 12 * entries) {
				for (int i = 0, position = offset + 2; i < entries; i++, position += 12) {
					int type = IOUtils.bytesToInt(littleEndian, position, 2, exifBytes);
					int format = IOUtils.bytesToInt(littleEndian, position + 2, 2, exifBytes);
					int count = IOUtils.bytesToInt(littleEndian, position + 4, 4, exifBytes);
					int value = IOUtils.bytesToInt(littleEndian, position + 8, 4, exifBytes);
					int valueShort = IOUtils.bytesToInt(littleEndian, position + 8, 2, exifBytes);
					if (ifd == Ifd.GENERAL) {
						switch (type) {
							case EXIF_TAG_DESCRIPTION: {
								exif.put(KEY_DESCRIPTION, extractIfdString(exifBytes, value, format));
								break;
							}
							case EXIF_TAG_MAKE: {
								exif.put(KEY_MAKE, extractIfdString(exifBytes, value, format));
								break;
							}
							case EXIF_TAG_MODEL: {
								exif.put(KEY_MODEL, extractIfdString(exifBytes, value, format));
								break;
							}
							case EXIF_TAG_ORIENTATION: {
								exif.put(KEY_ORIENTATION, Integer.toString(valueShort));
								break;
							}
							case EXIF_TAG_SOFTWARE: {
								exif.put(KEY_SOFTWARE, extractIfdString(exifBytes, value, format));
								break;
							}
							case EXIF_TAG_DATE_TIME: {
								exif.put(KEY_DATE_TIME, extractIfdString(exifBytes, value, format));
								break;
							}
							case EXIF_TAG_EXPOSURE_TIME:
							case EXIF_TAG_SHUTTER_SPEED: {
								if (!exif.containsValue(KEY_SHUTTER_SPEED)) {
									double valueDouble = convertIfdRational(exifBytes, value, format, littleEndian);
									if (type == EXIF_TAG_SHUTTER_SPEED) {
										valueDouble = Math.pow(2, -valueDouble);
									}
									if (valueDouble > 0) {
										String exposureTime;
										if (valueDouble <= 0.5) {
											exposureTime = "1/" + (int) (1 / valueDouble + 0.5);
										} else {
											exposureTime = formatDoubleSimple(valueDouble);
										}
										exif.put(KEY_SHUTTER_SPEED, exposureTime + " sec.");
									}
								}
								break;
							}
							case EXIF_TAG_F_NUMBER:
							case EXIF_TAG_APERTURE: {
								if (!exif.containsKey(KEY_APERTURE)) {
									double valueDouble = convertIfdRational(exifBytes, value, format, littleEndian);
									if (type == EXIF_TAG_APERTURE) {
										valueDouble = Math.pow(2, valueDouble / 2);
									}
									if (valueDouble > 0) {
										exif.put(KEY_APERTURE, "f/" + formatDoubleSimple(valueDouble));
									}
								}
								break;
							}
							case EXIF_TAG_EXIF_IFD: {
								exif.put(KEY_EXIF_OFFSET, Integer.toString(value));
								break;
							}
							case EXIF_TAG_GPS_IFD: {
								exif.put(KEY_GPS_OFFSET, Integer.toString(value));
								break;
							}
							case EXIF_TAG_ISO_SPEED: {
								exif.put(KEY_ISO_SPEED, Integer.toString(valueShort));
								break;
							}
							case EXIF_TAG_BRIGHTNESS: {
								double valueDouble = convertIfdRational(exifBytes, value, format, littleEndian);
								exif.put(KEY_BRIGHTNESS, formatDoubleSimple(valueDouble) + " EV");
								break;
							}
							case EXIF_TAG_FOCAL_LENGTH: {
								double valueDouble = convertIfdRational(exifBytes, value, format, littleEndian);
								exif.put(KEY_FOCAL_LENGTH, formatDoubleSimple(valueDouble) + " mm");
								break;
							}
						}
					} else if (ifd == Ifd.GPS) {
						switch (type) {
							case 0x0001: {
								exif.put(KEY_LATITUDE_REF, Character.toString((char) value));
								break;
							}
							case 0x0002: {
								exif.put(KEY_LATITUDE, convertIfdGpsString(exifBytes, value, format,
										littleEndian, count));
								break;
							}
							case 0x0003: {
								exif.put(KEY_LONGITUDE_REF, Character.toString((char) value));
								break;
							}
							case 0x0004: {
								exif.put(KEY_LONGITUDE, convertIfdGpsString(exifBytes, value, format,
										littleEndian, count));
								break;
							}
						}
					}
				}
				return true;
			}
			return false;
		}
		return false;
	}

	private static boolean extractIfd(Map<String, String> exif, Ifd ifd, byte[] exifBytes,
			int baseOffset, String offsetStringKey, boolean littleEndian) {
		String offsetString = exif.get(offsetStringKey);
		if (offsetString != null) {
			int offset = -1;
			try {
				offset = Integer.parseInt(offsetString);
			} catch (NumberFormatException e) {
				// Ignore
			}
			if (offset >= 0) {
				return extractIfd(exif, ifd, exifBytes, baseOffset + offset, littleEndian);
			}
		}
		return false;
	}

	public static ExifData extract(byte[] exifBytes, int offset) {
		if (exifBytes.length >= offset + 8) {
			int tiffHeader = IOUtils.bytesToInt(false, offset, 4, exifBytes);
			Boolean littleEndian;
			if (tiffHeader == 0x49492a00) {
				littleEndian = true;
			} else if (tiffHeader == 0x4d4d002a) {
				littleEndian = false;
			} else {
				littleEndian = null;
			}
			if (littleEndian != null) {
				LinkedHashMap<String, String> exif = new LinkedHashMap<>();
				int ifdOffset = IOUtils.bytesToInt(littleEndian, offset + 4, 4, exifBytes);
				extractIfd(exif, Ifd.GENERAL, exifBytes, offset + ifdOffset, littleEndian);
				extractIfd(exif, Ifd.GENERAL, exifBytes, offset, KEY_EXIF_OFFSET, littleEndian);
				extractIfd(exif, Ifd.GPS, exifBytes, offset, KEY_GPS_OFFSET, littleEndian);
				return new ExifData(Collections.unmodifiableMap(exif));
			}
		}
		return null;
	}
}
