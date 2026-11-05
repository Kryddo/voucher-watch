package ro.voucherwatch.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Pornirea monitorizarii: imediat sau la o ora aleasa (ex. 06:45).
 * Pana la ora aleasa aplicatia nu ruleaza deloc; o alarma de sistem exacta o porneste.
 */
final class Scheduler {
    static final String MODE_NOW = "now";
    static final String MODE_AT = "at";

    // ---------- setari ----------

    static String startMode(Context c) {
        return WatchService.prefs(c).getString("startMode", MODE_AT);
    }

    static boolean isAt(Context c) {
        return MODE_AT.equals(startMode(c));
    }

    static int startHour(Context c) {
        return WatchService.prefs(c).getInt("startHour", 6);
    }

    static int startMinute(Context c) {
        return WatchService.prefs(c).getInt("startMinute", 45);
    }

    static int intervalSec(Context c) {
        return WatchService.prefs(c).getInt("intervalSec", 15);
    }

    static String timeLabel(Context c) {
        return String.format(Locale.US, "%02d:%02d", startHour(c), startMinute(c));
    }

    static String intervalLabel(int sec) {
        if (sec < 60) return sec + " secunde";
        if (sec == 60) return "1 minut";
        return (sec / 60) + " minute";
    }

    static void setStart(Context c, String mode, int hour, int minute) {
        SharedPreferences p = WatchService.prefs(c);
        p.edit().putString("startMode", mode).putInt("startHour", hour).putInt("startMinute", minute).apply();
        if (p.getBoolean("enabled", false)) enable(c);
    }

    static void setInterval(Context c, int sec) {
        WatchService.prefs(c).edit().putInt("intervalSec", sec).apply();
    }

    /** Monitorizarea e pornita, dar asteapta ora programata. */
    static boolean isScheduled(Context c) {
        SharedPreferences p = WatchService.prefs(c);
        return p.getBoolean("enabled", false) && p.getLong("startAt", 0) > System.currentTimeMillis();
    }

    // ---------- pornire / oprire ----------

    /** Porneste monitorizarea conform setarilor (imediat sau la ora aleasa). */
    static void enable(Context c) {
        if (isAt(c)) {
            enableAt(c, nextOccurrence(startHour(c), startMinute(c)));
        } else {
            WatchService.prefs(c).edit().putBoolean("enabled", true).putLong("startAt", 0).apply();
            cancelAlarm(c);
            c.startForegroundService(new Intent(c, WatchService.class).setAction(WatchService.ACTION_START));
        }
    }

    /** Monitorizare pornita, dar fara nicio verificare pana la momentul `at`. */
    static void enableAt(Context c, long at) {
        WatchService.prefs(c).edit().putBoolean("enabled", true).putLong("startAt", at).apply();
        c.startService(new Intent(c, WatchService.class).setAction(WatchService.ACTION_PAUSE));
        scheduleAlarm(c, at);
        WatchService.log(c, "Programat: încep verificarea " + describe(at) + ".");
    }

    static void disable(Context c) {
        WatchService.prefs(c).edit().putBoolean("enabled", false).putLong("startAt", 0).apply();
        cancelAlarm(c);
        c.startService(new Intent(c, WatchService.class).setAction(WatchService.ACTION_STOP));
    }

    /** A sosit ora programata. */
    static void onAlarm(Context c) {
        SharedPreferences p = WatchService.prefs(c);
        if (!p.getBoolean("enabled", false)) return;
        p.edit().putLong("startAt", 0).apply();
        WatchService.log(c, "Ora programată a sosit — încep verificarea.");
        try {
            c.startForegroundService(new Intent(c, WatchService.class).setAction(WatchService.ACTION_START));
        } catch (Exception e) {
            WatchService.log(c, "Nu pot porni monitorizarea: " + e);
            WatchService.notifyStartFailed(c);
        }
    }

    /** Dupa restart sau actualizare: reface alarma sau reia verificarea. */
    static void resume(Context c) {
        SharedPreferences p = WatchService.prefs(c);
        if (!p.getBoolean("enabled", false)) return;
        long at = p.getLong("startAt", 0);
        if (at > System.currentTimeMillis()) {
            scheduleAlarm(c, at);
            return;
        }
        p.edit().putLong("startAt", 0).apply();
        try {
            c.startForegroundService(new Intent(c, WatchService.class));
        } catch (Exception e) {
            WatchService.log(c, "Nu pot relua monitorizarea: " + e);
            WatchService.notifyStartFailed(c);
        }
    }

    // ---------- alarma de sistem ----------

    static long nextOccurrence(int hour, int minute) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, hour);
        t.set(Calendar.MINUTE, minute);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
        return t.getTimeInMillis();
    }

    static String describe(long at) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(at);
        String hm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(at));
        if (sameDay(now, t)) return "azi la " + hm;
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(now, t)) return "mâine la " + hm;
        return "pe " + new SimpleDateFormat("dd.MM", Locale.US).format(new Date(at)) + " la " + hm;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static PendingIntent alarmPi(Context c) {
        return PendingIntent.getBroadcast(c, 30, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleAlarm(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent op = alarmPi(c);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
                WatchService.log(c, "Fără permisiunea de alarme exacte: pornirea poate întârzia.");
            } else {
                // Alarma de tip ceas: cea mai sigura (nu e amanata de economisirea bateriei).
                PendingIntent show = PendingIntent.getActivity(c, 31, new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), op);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op);
        }
    }

    static void cancelAlarm(Context c) {
        c.getSystemService(AlarmManager.class).cancel(alarmPi(c));
    }

    private Scheduler() {
    }
}
