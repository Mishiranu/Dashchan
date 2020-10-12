package chan.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import chan.annotation.Public;
import com.mishiranu.dashchan.util.Log;
import java.lang.reflect.Array;
import java.util.Collection;
import org.json.JSONException;
import org.json.JSONObject;

@Public
public class CommonUtils {
	@SuppressWarnings("EqualsReplaceableByObjectsCall")
	@Public
	public static boolean equals(Object first, Object second) {
		return first == second || first != null && first.equals(second);
	}

	@Public
	public static boolean sleepMaxRealtime(long startRealtime, long interval) {
		long time = interval - (SystemClock.elapsedRealtime() - startRealtime);
		if (time <= 0) {
			return false;
		}
		try {
			Thread.sleep(time);
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return true;
		}
	}

	@Public
	public static String optJsonString(JSONObject jsonObject, String name) {
		return optJsonString(jsonObject, name, null);
	}

	@Public
	public static String optJsonString(JSONObject jsonObject, String name, String fallback) {
		try {
			return getJsonString(jsonObject, name);
		} catch (JSONException e) {
			return fallback;
		}
	}

	@Public
	public static String getJsonString(JSONObject jsonObject, String name) throws JSONException {
		if (jsonObject.has(name) && jsonObject.isNull(name)) {
			return null;
		} else {
			return jsonObject.getString(name);
		}
	}

	@Public
	public static String restoreCloudFlareProtectedEmails(String string) {
		int index = 0;
		StringBuilder builder = null;
		while (true) {
			index = builder != null ? builder.indexOf("/cdn-cgi/l/email-protection", index)
					: string.indexOf("/cdn-cgi/l/email-protection");
			if (index >= 0) {
				if (builder == null) {
					builder = new StringBuilder(string);
				}
				int index1 = builder.lastIndexOf("<", index);
				int index2 = builder.lastIndexOf("<a", index);
				int index3 = builder.indexOf(">", index);
				int index4 = builder.indexOf("</a>", index);
				if (index1 == index2 && index3 > index) {
					int index5 = builder.indexOf("\"", index);
					if (index5 == -1) {
						index5 = builder.indexOf("'", index);
					}
					if (index5 == -1) {
						index5 = builder.indexOf(" ", index);
					}
					if (index5 == -1) {
						break;
					}
					String url = builder.substring(index, index5);
					int index6 = url.indexOf('#');
					String hash = null;
					boolean replaceTag = false;
					if (index6 >= 0) {
						hash = url.substring(index6 + 1);
					} else {
						index6 = builder.indexOf("data-cfemail=", index1);
						if (index6 >= 0 && index6 < index3) {
							index6 += 14;
							index5 = builder.indexOf("\"", index6);
							if (index5 == -1) {
								index5 = builder.indexOf("'", index6);
							}
							if (index5 == -1) {
								index5 = builder.indexOf(" ", index6--);
							}
							if (index5 == -1) {
								break;
							}
							hash = builder.substring(index6, index5);
							replaceTag = true;
						}
					}
					if (hash != null && hash.length() % 2 == 0) {
						int x = Integer.parseInt(hash.substring(0, 2), 16);
						StringBuilder email = new StringBuilder(hash.length() / 2 - 1);
						for (int i = 2; i < hash.length(); i += 2) {
							int b = Integer.parseInt(hash.substring(i, i + 2), 16);
							email.append((char) (b ^ x));
						}
						if (replaceTag) {
							builder.replace(index1, index4 + 4, email.toString());
							index = index1 + email.length();
						} else {
							builder.replace(index, index5, "mailto:" + email.toString());
							index = builder.indexOf(">", index) + 1;
						}
						continue;
					}
				}
				if (index4 == -1) {
					break;
				}
				index = index4 + 4;
			} else {
				break;
			}
		}
		if (builder != null) {
			while (true) {
				int start = builder.indexOf("<script data-cfhash");
				if (start >= 0) {
					int end = builder.indexOf("</script>");
					if (end > start) {
						end += 9;
						builder.delete(start, end);
						continue;
					}
				}
				break;
			}
			string = builder.toString();
		}
		return string;
	}

	@Public
	public static Bitmap trimBitmap(Bitmap bitmap, int backgroundColor) {
		if (bitmap == null) {
			return null;
		}
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int[] pixels = new int[Math.max(width, height)];
		int actualLeft = 0;
		int actualRight = width;
		int actualTop = 0;
		int actualBottom = height;
		OUT: for (int i = 0; i < width; i++) {
			bitmap.getPixels(pixels, 0, 1, i, 0, 1, height);
			for (int j = 0; j < height; j++) {
				if (pixels[j] != backgroundColor) {
					actualLeft = i;
					break OUT;
				}
			}
		}
		OUT: for (int i = width - 1; i >= 0; i--) {
			bitmap.getPixels(pixels, 0, 1, i, 0, 1, height);
			for (int j = 0; j < height; j++) {
				if (pixels[j] != backgroundColor) {
					actualRight = i + 1;
					break OUT;
				}
			}
		}
		OUT: for (int i = 0; i < height; i++) {
			bitmap.getPixels(pixels, 0, width, 0, i, width, 1);
			for (int j = 0; j < width; j++) {
				if (pixels[j] != backgroundColor) {
					actualTop = i;
					break OUT;
				}
			}
		}
		OUT: for (int i = height - 1; i >= 0; i--) {
			bitmap.getPixels(pixels, 0, width, 0, i, width, 1);
			for (int j = 0; j < width; j++) {
				if (pixels[j] != backgroundColor) {
					actualBottom = i + 1;
					break OUT;
				}
			}
		}
		if (actualLeft != 0 || actualTop != 0 || actualRight != width || actualBottom != height) {
			if (actualRight > actualLeft && actualBottom > actualTop) {
				Bitmap newBitmap = Bitmap.createBitmap(actualRight - actualLeft, actualBottom - actualTop,
						Bitmap.Config.ARGB_8888);
				new Canvas(newBitmap).drawBitmap(bitmap, -actualLeft, -actualTop, null);
				return newBitmap;
			}
			return null;
		}
		return bitmap;
	}

	@Public
	public static void writeLog(Object... data) {
		Log.persistent().write(data);
	}

	public static <T> T[] removeNullItems(T[] array, Class<T> itemClass) {
		if (array != null) {
			int nullItems = 0;
			for (T item : array) {
				if (item == null) {
					nullItems++;
				}
			}
			if (nullItems == array.length) {
				array = null;
			} else if (nullItems > 0) {
				@SuppressWarnings("unchecked")
				T[] newArray = (T[]) Array.newInstance(itemClass, array.length - nullItems);
				int i = 0;
				for (T item : array) {
					if (item != null) {
						newArray[i++] = item;
					}
				}
				array = newArray;
			}
		}
		return array;
	}

	public static <T> T[] toArray(Collection<? extends T> collection, Class<T> itemClass) {
		if (collection != null && !collection.isEmpty()) {
			@SuppressWarnings("unchecked")
			T[] array = (T[]) Array.newInstance(itemClass, collection.size());
			return collection.toArray(array);
		}
		return null;
	}
}
