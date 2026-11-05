package uk.co.definitionofdone.nadya;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.notifyIfNeeded(c);
        NadyaWidget.refresh(c);
        Reminders.schedule(c); // tomorrow's
    }
}
