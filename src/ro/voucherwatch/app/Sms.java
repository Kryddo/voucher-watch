package ro.voucherwatch.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

import java.util.ArrayList;

/** SMS automat catre un prieten cand eMAG trece de pagina de asteptare (o singura data). */
final class Sms {
    static final String DEFAULT_TEXT = "Black Friday a început pe eMAG";

    static boolean enabled(Context c) {
        return WatchService.prefs(c).getBoolean("smsEnabled", false);
    }

    static String number(Context c) {
        return WatchService.prefs(c).getString("smsNumber", "");
    }

    static String text(Context c) {
        return WatchService.prefs(c).getString("smsText", DEFAULT_TEXT);
    }

    /** Ultimul rezultat, ex. „Trimis la 07:14” sau „Test trimis la 23:20”. */
    static String status(Context c) {
        return WatchService.prefs(c).getString("smsStatus", null);
    }

    static boolean permitted(Context c) {
        return c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Pastreaza doar cifrele si „+”; null daca nu arata a numar de telefon. */
    static String cleanNumber(String raw) {
        if (raw == null) return null;
        String n = raw.replaceAll("[\\s\\-().]", "");
        return n.matches("\\+?\\d{9,15}") ? n : null;
    }

    static void save(Context c, boolean enabled, String number, String text) {
        WatchService.prefs(c).edit().putBoolean("smsEnabled", enabled).putString("smsNumber", number)
                .putString("smsText", text).apply();
    }

    static void setStatus(Context c, String status) {
        WatchService.prefs(c).edit().putString("smsStatus", status).apply();
    }

    static String mask(String number) {
        if (number == null || number.length() < 4) return "•••";
        return "•••" + number.substring(number.length() - 3);
    }

    /** Trimite SMS-ul; rezultatul (trimis / esuat) ajunge in SmsSentReceiver. */
    static boolean send(Context c, String number, String text, boolean test) {
        if (!permitted(c)) {
            WatchService.log(c, "SMS netrimis: lipsește permisiunea de SMS.");
            setStatus(c, "Lipsește permisiunea de SMS");
            return false;
        }
        try {
            SmsManager sm = manager(c);
            ArrayList<String> parts = sm.divideMessage(text);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Intent result = new Intent(c, SmsSentReceiver.class)
                        .putExtra(SmsSentReceiver.EXTRA_TEST, test)
                        .putExtra(SmsSentReceiver.EXTRA_TO, mask(number));
                sent.add(PendingIntent.getBroadcast(c, 60 + i, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            sm.sendMultipartTextMessage(number, null, parts, sent, null);
            WatchService.log(c, "Trimit SMS către " + mask(number) + (test ? " (test)" : "") + "…");
            return true;
        } catch (Exception e) {
            WatchService.log(c, "SMS eșuat: " + e);
            setStatus(c, "Eșuat: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static SmsManager manager(Context c) {
        int sub = SubscriptionManager.getDefaultSmsSubscriptionId();
        boolean valid = sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager base = c.getSystemService(SmsManager.class);
            return valid ? base.createForSubscriptionId(sub) : base;
        }
        return valid ? SmsManager.getSmsManagerForSubscriptionId(sub) : SmsManager.getDefault();
    }

    /** Apelat cand eMAG se redeschide: trimite SMS-ul o singura data, apoi se dezactiveaza. */
    static void onSiteReopened(Context c) {
        SharedPreferences p = WatchService.prefs(c);
        String number = number(c);
        if (!enabled(c) || number.isEmpty()) return;
        p.edit().putBoolean("smsEnabled", false).apply(); // o singura data, chiar daca eMAG se mai inchide
        send(c, number, text(c), false);
    }

    private Sms() {
    }
}
