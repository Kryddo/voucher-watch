package ro.voucherwatch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Serviciu in prim-plan care verifica produsele periodic si porneste alarma. */
public class WatchService extends Service {
    static final String ACTION_START = "ro.voucherwatch.START";
    static final String ACTION_STOP = "ro.voucherwatch.STOP";
    static final String ACTION_PAUSE = "ro.voucherwatch.PAUSE";
    static final String ACTION_STOP_ALARM = "ro.voucherwatch.STOP_ALARM";
    static final String ACTION_TEST_ALARM = "ro.voucherwatch.TEST_ALARM";

    private static final String CH_STATUS = "status";
    private static final String CH_ALARM = "alarm";
    private static final String CH_INFO = "info";
    private static final int ID_STATUS = 1, ID_ALARM = 2, ID_START_FAILED = 4, ID_REOPENED = 5, ID_SMS_FAILED = 6;
    private static final String TAG = "VoucherWatch";

    private volatile Thread worker;
    private PowerManager.WakeLock wakeLock;
    private MediaPlayer player;
    private Vibrator vibrator;
    private int savedAlarmVolume = -1;

    // ---------- stocare comuna (merge si inainte de prima deblocare a telefonului) ----------

    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static synchronized void log(Context c, String msg) {
        Log.i(TAG, msg);
        SharedPreferences p = prefs(c);
        String line = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        String[] lines = (line + "\n" + p.getString("log", "")).split("\n");
        if (lines.length > 80) lines = Arrays.copyOf(lines, 80);
        p.edit().putString("log", String.join("\n", lines)).apply();
    }

    static String shortTitle(String t, int max) {
        if (t == null) return "Produs";
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel status = new NotificationChannel(CH_STATUS, "Stare monitorizare",
                NotificationManager.IMPORTANCE_LOW);
        NotificationChannel alarm = new NotificationChannel(CH_ALARM, "Alarmă",
                NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null); // sunetul il porneste aplicatia, in bucla
        alarm.enableVibration(false);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel info = new NotificationChannel(CH_INFO, "Reduceri, stoc și erori",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannels(Arrays.asList(status, alarm, info));
    }

    /** Daca pornirea programata esueaza, macar anunta utilizatorul. */
    static void notifyStartFailed(Context c) {
        ensureChannels(c);
        PendingIntent open = PendingIntent.getActivity(c, 40, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Monitorizarea nu a putut porni")
                .setContentText("Deschide aplicația ca s-o pornești.")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_START_FAILED, n);
    }

    // ---------- ciclul de viata ----------

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        SharedPreferences p = prefs(this);

        if (ACTION_STOP.equals(action)) {
            p.edit().putBoolean("enabled", false).putLong("startAt", 0).putBoolean("siteClosed", false).apply();
            log(this, "Monitorizare oprită.");
            stopAlarm();
            stopWorker();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            // Programat pentru mai tarziu: nicio verificare pana atunci.
            stopWorker();
            if (player == null) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_ALARM.equals(action)) {
            stopAlarm();
            if (worker == null) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        // START, TEST_ALARM sau repornire automata (intent fara actiune)
        try {
            Notification n = statusNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID_STATUS, n);
            }
        } catch (Exception e) {
            log(this, "Nu pot porni în prim-plan: " + e);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            p.edit().putBoolean("enabled", true).putLong("startAt", 0).apply();
            if (worker == null) {
                // siteClosed NU se reseteaza aici: daca eMAG era inchis cand am pus pauza
                // si s-a redeschis intre timp, prima verificare trebuie sa anunte redeschiderea.
                log(this, "Monitorizare activă — verific la "
                        + Scheduler.intervalLabel(Scheduler.intervalSec(this)) + ".");
            }
        } else if (action == null && Scheduler.isScheduled(this)) {
            Scheduler.scheduleAlarm(this, p.getLong("startAt", 0));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        } else if (action == null && p.getBoolean("enabled", false) && worker == null) {
            log(this, "Monitorizare reluată automat.");
        }

        if (ACTION_TEST_ALARM.equals(action)) {
            startAlarm("Test alarmă", "Așa arată alarma când un produs devine disponibil.",
                    "Apasă „Oprește alarma” ca s-o oprești.", null);
        }

        if (p.getBoolean("enabled", false) && !Scheduler.isScheduled(this)) {
            startWorker();
        } else if (!ACTION_TEST_ALARM.equals(action)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return worker != null ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopWorker();
        stopAlarm();
        super.onDestroy();
    }

    // ---------- verificarea periodica ----------

    private synchronized void startWorker() {
        if (worker != null) return;
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voucherwatch:poll");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        Thread t = new Thread(this::loop, "voucher-poll");
        worker = t;
        t.start();
    }

    private synchronized void stopWorker() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void loop() {
        Thread me = Thread.currentThread();
        try {
            while (worker == me) {
                List<Store.Product> list = Store.load(this);
                for (int i = 0; i < list.size() && worker == me; i++) {
                    Store.Product p = list.get(i);
                    checkProduct(p.key(), p.url);
                    if (i < list.size() - 1) Thread.sleep(700 + (long) (Math.random() * 800));
                }
                if (worker != me) break;
                prefs(this).edit().putString("lastCheck", now()).apply();
                updateStatus();
                // Asteapta intervalul ales; il reciteste in fiecare secunda, ca schimbarile sa conteze imediat.
                long start = System.currentTimeMillis();
                long jitter = (long) (Math.random() * 2000);
                while (worker == me
                        && System.currentTimeMillis() - start < Scheduler.intervalSec(this) * 1000L + jitter) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    /** Ce trebuie anuntat dupa o verificare. */
    private static final class Event {
        final boolean alarm;
        final String kind, name, detail, url;
        final int id;

        Event(boolean alarm, String kind, String name, String detail, String url, int id) {
            this.alarm = alarm;
            this.kind = kind;
            this.name = name;
            this.detail = detail;
            this.url = url;
            this.id = id;
        }
    }

    /** eMAG a pus pagina de asteptare: nu e o eroare, doar asteptam redeschiderea. */
    private void markSiteClosed(String pageTitle) {
        SharedPreferences p = prefs(this);
        if (!p.getBoolean("siteClosed", false)) {
            p.edit().putBoolean("siteClosed", true).apply();
            log(this, "eMAG e închis temporar („" + pageTitle + "”). Continui să verific.");
        }
    }

    private void markSiteOpen() {
        SharedPreferences p = prefs(this);
        if (p.getBoolean("siteClosed", false)) {
            p.edit().putBoolean("siteClosed", false).apply();
            info(ID_REOPENED, "eMAG s-a redeschis", "Site-ul merge din nou. Verific produsele tale.",
                    "https://www.emag.ro/");
            Sms.onSiteReopened(this);
        }
    }

    /** SMS-ul automat nu a putut fi trimis. */
    static void notifySmsFailed(Context c, String to) {
        ensureChannels(c);
        PendingIntent open = PendingIntent.getActivity(c, 41, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("SMS-ul nu a putut fi trimis")
                .setContentText("Către " + to + ". Deschide aplicația ca să-l retrimiți.")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_SMS_FAILED, n);
    }

    private void checkProduct(String key, String url) {
        Checker.Result r = null;
        String err = null;
        String closedPage = null;
        try {
            r = Checker.parse(Checker.fetch(url));
            if (r == null) err = "nu găsesc starea produsului (coadă de așteptare?)";
        } catch (Checker.ClosedException e) {
            closedPage = e.getMessage();
        } catch (Exception e) {
            err = e.getMessage() != null ? e.getMessage() : e.toString();
        }
        if (closedPage != null) markSiteClosed(closedPage);
        else if (r != null) markSiteOpen();

        Event ev = null;
        synchronized (Store.class) {
            List<Store.Product> list = Store.load(this);
            Store.Product p = Store.find(list, key);
            if (p == null) return; // sters intre timp
            p.lastCheck = now();
            int notifId = 1000 + (key.hashCode() & 0xffff);

            if (closedPage != null) {
                // Site inchis: pastram ultima stare cunoscuta, fara sa numaram ca eroare.
                p.fails = 0;
                p.error = null;
            } else if (r == null) {
                p.fails++;
                p.error = err;
                log(this, "Eroare (" + p.fails + ") " + shortTitle(p.title, 30) + ": " + err);
                if (p.fails == 5) {
                    ev = new Event(false, "Nu pot citi pagina", p.title, err + ". Verifică manual.", p.url, notifId);
                }
            } else {
                boolean wasAvailable = p.available();
                double lastPrice = p.price;
                boolean changed = !r.code.equals(p.code) || Math.abs(r.price - lastPrice) > 0.001;
                if (p.fails >= 5) log(this, "Pagina se citește din nou: " + shortTitle(p.title, 30));
                p.fails = 0;
                p.error = null;
                if (r.title != null) p.title = r.title;
                p.code = r.code;
                p.text = r.text;
                if (r.price >= 0) {
                    p.price = r.price;
                    if (p.basePrice < 0) p.basePrice = r.price;
                }
                if (changed) {
                    log(this, shortTitle(p.title, 30) + ": " + r.text + ", " + Checker.formatPrice(r.price));
                }

                if (Store.MODE_AVAIL.equals(p.mode)) {
                    if (r.available() && !wasAvailable) {
                        ev = new Event(p.alarm, "Disponibil acum", p.title,
                                r.text + " · " + Checker.formatPrice(r.price), p.url, notifId);
                    } else if (!r.available() && wasAvailable) {
                        ev = new Event(false, "Din nou indisponibil", p.title, r.text, p.url, notifId);
                    }
                } else if (r.price >= 0) {
                    boolean dropped = lastPrice >= 0 && r.price < lastPrice - 0.001;
                    boolean firstUnderTarget = lastPrice < 0 && p.target >= 0 && r.price <= p.target;
                    boolean meetsTarget = p.target < 0 || r.price <= p.target;
                    if ((dropped && meetsTarget) || firstUnderTarget) {
                        String from = lastPrice >= 0 ? Checker.formatPrice(lastPrice) + " → " : "";
                        String delta = lastPrice > 0 ? String.format(Locale.US, " (−%.0f%%)",
                                (lastPrice - r.price) / lastPrice * 100) : "";
                        ev = new Event(p.alarm, "Reducere de preț", p.title,
                                from + Checker.formatPrice(r.price) + delta, p.url, notifId);
                    }
                }
            }
            Store.save(this, list);
        }

        if (ev != null) {
            if (ev.alarm) startAlarm(ev.kind, ev.name, ev.detail, ev.url);
            else info(ev.id, ev.kind + " · " + shortTitle(ev.name, 40), ev.detail, ev.url);
        }
    }

    // ---------- notificari ----------

    private void updateStatus() {
        if (worker == null) return;
        getSystemService(NotificationManager.class).notify(ID_STATUS, statusNotification());
    }

    private PendingIntent servicePi(int req, String action) {
        return PendingIntent.getService(this, req, new Intent(this, WatchService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent activityPi(int req, Intent i) {
        return PendingIntent.getActivity(this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Action action(String label, PendingIntent pi) {
        return new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), label, pi).build();
    }

    private Notification statusNotification() {
        List<Store.Product> list = Store.load(this);
        Notification.InboxStyle style = new Notification.InboxStyle();
        int shown = 0;
        for (Store.Product p : list) {
            if (shown++ == 6) break;
            String state = p.error != null ? "Eroare" : p.text != null ? p.text : "–";
            style.addLine(shortTitle(p.title, 30) + "  ·  " + state + " · " + Checker.formatPrice(p.price));
        }
        String last = prefs(this).getString("lastCheck", null);
        String text = (last != null && worker != null ? "Ultima verificare " + last : "Pornesc verificarea…")
                + " · la " + Scheduler.intervalLabel(Scheduler.intervalSec(this));
        style.setSummaryText(text);
        Notification.Builder b = new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Monitorizare activă · " + list.size()
                        + (list.size() == 1 ? " produs" : " produse"))
                .setContentText(text)
                .setStyle(style)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(activityPi(1, new Intent(this, MainActivity.class)))
                .addAction(action("Oprește", servicePi(2, ACTION_STOP)));
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private void info(int id, String title, String text, String url) {
        log(this, title + " — " + text);
        Intent open = url != null ? new Intent(Intent.ACTION_VIEW, Uri.parse(url)) : new Intent(this, MainActivity.class);
        Notification n = new Notification.Builder(this, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(activityPi(id, open))
                .build();
        getSystemService(NotificationManager.class).notify(id, n);
    }

    // ---------- alarma (suna pana o opresti) ----------

    private synchronized void startAlarm(String kind, String name, String detail, String url) {
        prefs(this).edit().putBoolean("alarm", true).putString("alarmTitle", kind).putString("alarmName", name)
                .putString("alarmDetail", detail).putString("alarmUrl", url != null ? url : Checker.VOUCHER_URL)
                .apply();
        log(this, "ALARMĂ: " + kind + " — " + shortTitle(name, 40) + " · " + detail);

        Intent full = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        Intent open = new Intent(this, AlarmActivity.class).setAction(AlarmActivity.ACTION_OPEN_EMAG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent fullPi = activityPi(10, full);
        Notification n = new Notification.Builder(this, CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(kind + " · " + shortTitle(name, 40))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(name + "\n" + detail))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(action("Oprește alarma", servicePi(11, ACTION_STOP_ALARM)))
                .addAction(action("Deschide eMAG", activityPi(12, open)))
                .build();
        getSystemService(NotificationManager.class).notify(ID_ALARM, n);
        startSound();
    }

    private void startSound() {
        if (player != null) return;
        AudioManager am = getSystemService(AudioManager.class);
        try {
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) {
            savedAlarmVolume = -1;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        int[] types = {RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION};
        for (int type : types) {
            Uri uri = RingtoneManager.getDefaultUri(type);
            if (uri == null) continue;
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(attrs);
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                break;
            } catch (Exception e) {
                mp.release();
                log(this, "Sunetul " + type + " nu merge: " + e);
            }
        }
        try {
            vibrator = Build.VERSION.SDK_INT >= 31
                    ? getSystemService(VibratorManager.class).getDefaultVibrator()
                    : getSystemService(Vibrator.class);
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 500}, 0), attrs);
        } catch (Exception e) {
            vibrator = null;
        }
    }

    private synchronized void stopAlarm() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (savedAlarmVolume >= 0) {
            try {
                getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            } catch (Exception ignored) {
            }
            savedAlarmVolume = -1;
        }
        getSystemService(NotificationManager.class).cancel(ID_ALARM);
        SharedPreferences p = prefs(this);
        if (p.getBoolean("alarm", false)) {
            p.edit().putBoolean("alarm", false).apply();
            log(this, "Alarmă oprită.");
        }
    }
}
