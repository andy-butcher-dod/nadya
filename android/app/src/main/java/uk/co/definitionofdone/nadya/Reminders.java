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

    // Nadya's (comically) bossy reminders: {title, text}. {n} is the current streak.
    private static final String[][] FIRST = {
            {"Comrade! Report for history!", "Nadya has prepared your first lesson. Attendance is compulsory."},
            {"Nadya is waiting. Impatiently.", "Your first lesson takes 3 minutes. She has cleared her whole schedule."},
    };
    private static final String[][] STREAK = {
            {"Comrade! Your {n}-day streak needs you", "Nadya has issued a decree: one lesson, today. No excuses."},
            {"Nadya is tapping her tiny wooden foot", "Your {n}-day streak won\u2019t keep itself. Three minutes. Go!"},
            {"This is your daily reminder. Nadya\u2019s orders.", "Keep the {n}-day streak alive, or Nadya will sulk all week."},
            {"The Five-Year Plan says: practise today", "Target: one lesson. Deadline: midnight. Nadya is watching."},
            {"Don\u2019t make Nadya come over there", "Your {n}-day streak is on the line. One quick lesson, please!"},
    };
    private static final String[][] WORRIED = {
            {"Midnight is coming, comrade!", "Your {n}-day streak ends tonight. Nadya refuses to let that happen."},
            {"Nadya is pacing up and down", "{n} days of history, and you\u2019re going to stop NOW? Three minutes!"},
            {"Comrade! Your {n}-day streak needs you", "Nadya has issued a decree: one lesson, today. No excuses."},
            {"Don\u2019t make Nadya come over there", "Your {n}-day streak is on the line. One quick lesson, please!"},
    };
    private static final String[][] SAD = {
            {"Nadya noticed you were missing", "She\u2019s not angry. Just disappointed. One lesson to make up?"},
            {"Where were you yesterday, comrade?", "Nadya kept your seat warm. Back to history, please!"},
    };
    private static final String[][] MISERABLE = {
            {"Nadya has called an emergency meeting", "Item 1 on the agenda: you. Item 2: a quick history lesson."},
            {"Nadya is all alone with the Tsar", "And frankly he\u2019s terrible company. Please come back!"},
            {"Official notice from Nadya", "Your history lessons miss you. So does she. Three minutes, today."},
    };

    private static String[] pick(String[][] options) {
        return options[new java.util.Random().nextInt(options.length)];
    }

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
        String[] m = pick(last == null ? FIRST
                : mood.equals("sad") ? SAD
                : mood.equals("miserable") ? MISERABLE
                : mood.equals("worried") ? WORRIED
                : STREAK);
        String title = m[0].replace("{n}", String.valueOf(streak));
        String body = m[1].replace("{n}", String.valueOf(streak));

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
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(1, n);
    }
}
