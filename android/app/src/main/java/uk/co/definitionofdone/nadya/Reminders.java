package uk.co.definitionofdone.nadya;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.util.Calendar;

/** The daily reminder: scheduled on the phone, skipped if she has already practised today. */
final class Reminders {
    private Reminders() {}

    static final String CHANNEL = "daily";

    static void schedule(Context c) {
        SharedPreferences p = Store.prefs(c);
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, p.getInt("remHour", 18));
        t.set(Calendar.MINUTE, p.getInt("remMin", 0));
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_MONTH, 1);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = alarmIntent(c);
        am.cancel(pi);
        // Scheduled every day, even with reminders off, so the widget's mood also updates at that time.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
    }

    private static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getBroadcast(c, 1, new Intent(c, ReminderReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("A daily nudge from Nadya to practise");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static void notifyIfNeeded(Context c) {
        SharedPreferences p = Store.prefs(c);
        if (!p.getBoolean("remOn", true) || !allowed(c)) return;
        String last = Store.last(c);
        if (last != null && Store.daysSince(last) == 0) return; // already practised today

        String mood = Store.mood(c);
        int streak = Store.streak(c);
        String title, body;
        if (last == null) {
            title = "Nadya is waiting for you";
            body = "Your first history lesson takes about 3 minutes.";
        } else if (mood.equals("sad")) {
            title = "Nadya misses you…";
            body = "One quick lesson to start a new streak?";
        } else if (mood.equals("miserable")) {
            title = "Nadya is all alone with the Tsar";
            body = "Please come back for a quick lesson!";
        } else if (streak > 0) {
            title = "Keep your " + streak + "-day streak going!";
            body = "Today’s history lesson takes about 3 minutes.";
        } else {
            title = "Time for today’s history lesson";
            body = "Just 3 minutes with Nadya.";
        }

        createChannel(c);
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_nadya)
                .setLargeIcon(BitmapFactory.decodeResource(c.getResources(), Store.moodImage(mood)))
                .setColor(0xFF2D5BD7)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(1, n);
    }
}
