package ro.voucherwatch.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Afla de la sistem daca SMS-ul a plecat sau nu. */
public class SmsSentReceiver extends BroadcastReceiver {
    static final String EXTRA_TEST = "test";
    static final String EXTRA_TO = "to";

    @Override
    public void onReceive(Context c, Intent intent) {
        boolean test = intent.getBooleanExtra(EXTRA_TEST, false);
        String to = intent.getStringExtra(EXTRA_TO);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        int code = getResultCode();
        if (code == Activity.RESULT_OK) {
            WatchService.log(c, "SMS trimis către " + to + (test ? " (test)" : "") + ".");
            Sms.setStatus(c, (test ? "Test trimis la " : "Trimis la ") + time);
        } else {
            WatchService.log(c, "SMS eșuat către " + to + " (cod " + code + ").");
            Sms.setStatus(c, "Eșuat la " + time + " (cod " + code + ")");
            if (!test) WatchService.notifySmsFailed(c, to);
        }
    }
}
