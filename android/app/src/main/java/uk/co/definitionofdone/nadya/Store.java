package uk.co.definitionofdone.nadya;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Progress the web app reports, and Nadya's mood worked out from it (same rules as the app). */
final class Store {
    private Store() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nadya", Context.MODE_PRIVATE);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Days since the last practice day, or -1 if never practised. */
    static int daysSince(String last) {
        if (last == null) return -1;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long a = f.parse(last).getTime(), b = f.parse(today()).getTime();
            return (int) Math.round((b - a) / 86400000.0);
        } catch (Exception e) {
            return -1;
        }
    }

    static String last(Context c) {
        return prefs(c).getString("last", null);
    }

    static String mood(Context c) {
        String last = last(c);
        if (last == null) return "hello";
        int g = daysSince(last);
        if (g <= 0) return "happy";
        if (g == 1) return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 18 ? "worried" : "waiting";
        if (g == 2) return "sad";
        return "miserable";
    }

    static int streak(Context c) {
        String last = last(c);
        if (last == null) return 0;
        return daysSince(last) <= 1 ? prefs(c).getInt("streak", 0) : 0;
    }

    static int moodImage(String mood) {
        switch (mood) {
            case "happy": return R.drawable.nadya_happy;
            case "waiting": return R.drawable.nadya_waiting;
            case "worried": return R.drawable.nadya_worried;
            case "sad": return R.drawable.nadya_sad;
            case "miserable": return R.drawable.nadya_miserable;
            default: return R.drawable.nadya_hello;
        }
    }
}
