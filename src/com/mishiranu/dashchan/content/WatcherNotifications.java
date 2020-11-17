package com.mishiranu.dashchan.content;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Hasher;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class WatcherNotifications {
	private static final Executor EXECUTOR = ConcurrentUtils
			.newSingleThreadPool(1000, "WatcherNotifications", null);

	public static void configure(Context context) {
		if (C.API_OREO) {
			NotificationManager notificationManager = (NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationChannel channel = new NotificationChannel(C.NOTIFICATION_CHANNEL_REPLIES,
					context.getString(R.string.replies), NotificationManager.IMPORTANCE_HIGH);
			channel.enableLights(true);
			channel.enableVibration(true);
			notificationManager.createNotificationChannel(channel);
		}
	}

	public static void notifyReplies(Context context, int color, boolean important, boolean sound, boolean vibration,
			String title, String chanName, String boardName, String threadNumber,
			List<PagesDatabase.InsertResult.Reply> replies) {
		EXECUTOR.execute(new Task(context, color, important, sound, vibration, title,
				chanName, boardName, threadNumber, replies, Collections.emptyList()));
	}

	public static void cancelReplies(Context context,
			String chanName, String boardName, String threadNumber, Collection<PostNumber> postNumbers) {
		EXECUTOR.execute(new Task(context, 0, false, false, false, null,
				chanName, boardName, threadNumber, Collections.emptyList(), postNumbers));
	}

	private static class Task implements Runnable {
		private static final String GROUP_REPLIES = "replies";

		public final Context context;
		public final int color;
		public final boolean important;
		public final boolean sound;
		public final boolean vibration;
		public final String title;
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public final List<PagesDatabase.InsertResult.Reply> replies;
		public final Collection<PostNumber> removePostNumbers;

		private Task(Context context, int color, boolean important, boolean sound, boolean vibration,
				String title, String chanName, String boardName, String threadNumber,
				List<PagesDatabase.InsertResult.Reply> replies, Collection<PostNumber> removePostNumbers) {
			this.context = context.getApplicationContext();
			this.color = color;
			this.important = important;
			this.sound = sound;
			this.vibration = vibration;
			this.title = title;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.replies = replies;
			this.removePostNumbers = removePostNumbers;
		}

		private static String makeTag(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
			return StringUtils.formatHex(Hasher.getInstanceSha256().calculate(chanName + "/" +
					boardName + "/" + threadNumber + "/" + postNumber));
		}

		private static void configureNotification(NotificationCompat.Builder builder, int color) {
			builder.setSmallIcon(R.drawable.ic_notification);
			if (C.API_LOLLIPOP) {
				builder.setColor(color);
			}
		}

		private static void applyPreferencesPreOreo(NotificationCompat.Builder builder,
				int color, boolean important, boolean sound, boolean vibration, String title) {
			builder.setPriority(important ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);
			if (important) {
				if (C.API_LOLLIPOP && !sound && !vibration) {
					builder.setVibrate(new long[0]);
				}
				builder.setLights(color, 1000, 1000);
				builder.setTicker(title);
			}
			builder.setDefaults((sound ? NotificationCompat.DEFAULT_SOUND : 0) |
					(vibration ? NotificationCompat.DEFAULT_VIBRATE : 0));
		}

		private static String buildLongComment(String comment) {
			StringBuilder builder = new StringBuilder();
			boolean nextNewLine = false;
			for (String line : comment.split("\n")) {
				if (!line.isEmpty()) {
					line = line.trim();
					if (!line.isEmpty()) {
						line = line.replaceAll(" {2,}", " ");
						boolean newLine = nextNewLine;
						nextNewLine = !line.contains(">>") || !line.replaceAll(">>\\d+", "").trim().isEmpty();
						if (builder.length() > 0) {
							builder.append(newLine ? '\n' : ' ');
						}
						builder.append(line);
					}
				}
			}
			return builder.toString();
		}

		@Override
		public void run() {
			NotificationManager notificationManager = (NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (!replies.isEmpty()) {
				notifyReplies(notificationManager);
			}
			if (!removePostNumbers.isEmpty()) {
				cancelReplies(notificationManager);
			}
		}

		private void notifyReplies(NotificationManager notificationManager) {
			String title = context.getString(R.string.reply_in_thread__format, this.title);
			for (PagesDatabase.InsertResult.Reply reply : replies) {
				NotificationCompat.Builder builder = new NotificationCompat
						.Builder(context, C.NOTIFICATION_CHANNEL_REPLIES);
				builder.setContentTitle(title);
				String comment = StringUtils.clearHtml(reply.comment);
				builder.setContentText(comment.replace('\n', ' ').replaceAll(" {2,}", " "));
				builder.setStyle(new NotificationCompat.BigTextStyle().bigText(buildLongComment(comment)));
				builder.setWhen(reply.timestamp);
				configureNotification(builder, color);
				if (C.API_NOUGAT) {
					builder.setGroup(GROUP_REPLIES);
					if (C.API_OREO) {
						builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
					} else {
						builder.setDefaults(0);
					}
				} else {
					applyPreferencesPreOreo(builder, color, important, sound, vibration, title);
				}
				String tag = makeTag(chanName, boardName, threadNumber, reply.postNumber);
				Intent intent = new Intent(context, MainActivity.class).setAction(tag)
						.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
						.putExtra(C.EXTRA_CHAN_NAME, chanName)
						.putExtra(C.EXTRA_BOARD_NAME, boardName)
						.putExtra(C.EXTRA_THREAD_NUMBER, threadNumber)
						.putExtra(C.EXTRA_POST_NUMBER, reply.postNumber.toString());
				builder.setContentIntent(PendingIntent.getActivity(context, 0, intent,
						PendingIntent.FLAG_UPDATE_CURRENT));
				notificationManager.notify(tag, C.NOTIFICATION_ID_REPLIES, builder.build());
			}
			if (C.API_NOUGAT) {
				NotificationCompat.Builder builder = new NotificationCompat
						.Builder(context, C.NOTIFICATION_CHANNEL_REPLIES);
				configureNotification(builder, color);
				if (!C.API_OREO) {
					applyPreferencesPreOreo(builder, color, important, sound, vibration, title);
				}
				builder.setGroup(GROUP_REPLIES);
				builder.setGroupSummary(true);
				notificationManager.notify(C.NOTIFICATION_ID_REPLIES, builder.build());
			}
		}

		private void cancelReplies(NotificationManager notificationManager) {
			Set<String> tags = null;
			if (C.API_NOUGAT) {
				StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
				if (notifications != null && notifications.length > 0) {
					tags = new HashSet<>();
					for (StatusBarNotification notification : notifications) {
						String tag = notification.getTag();
						if (tag != null && notification.getId() == C.NOTIFICATION_ID_REPLIES) {
							tags.add(tag);
						}
					}
				}
			}
			for (PostNumber postNumber : removePostNumbers) {
				String tag = makeTag(chanName, boardName, threadNumber, postNumber);
				notificationManager.cancel(tag, C.NOTIFICATION_ID_REPLIES);
				if (tags != null) {
					tags.remove(tag);
				}
			}
			if (C.API_NOUGAT && tags != null && tags.isEmpty()) {
				notificationManager.cancel(C.NOTIFICATION_ID_REPLIES);
			}
		}
	}
}
