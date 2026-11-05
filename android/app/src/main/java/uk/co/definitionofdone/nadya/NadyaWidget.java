package uk.co.definitionofdone.nadya;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: Nadya in her current mood, plus a short line about the streak. */
public class NadyaWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(c));
    }

    static RemoteViews views(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        String mood = Store.mood(c);
        v.setImageViewResource(R.id.img, Store.moodImage(mood));
        v.setTextViewText(R.id.txt, text(mood, Store.streak(c)));
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.root, pi);
        return v;
    }

    static String text(String mood, int streak) {
        switch (mood) {
            case "happy": return streak > 1 ? streak + "-day streak!" : "Done for today!";
            case "waiting": return streak > 0 ? "Keep your " + streak + "-day streak!" : "Ready for today?";
            case "worried": return "Streak ends at midnight!";
            case "sad": return "I missed you…";
            case "miserable": return "Please come back!";
            default: return "Let’s start!";
        }
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, NadyaWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c));
    }
}
