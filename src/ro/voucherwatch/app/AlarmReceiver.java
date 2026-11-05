package ro.voucherwatch.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Primeste alarma de la ora programata si porneste monitorizarea. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.onAlarm(context);
    }
}
