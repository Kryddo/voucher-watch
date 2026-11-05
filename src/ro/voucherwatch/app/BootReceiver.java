package ro.voucherwatch.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dupa restart sau actualizare: reface alarma programata sau reia monitorizarea. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.resume(context);
    }
}
