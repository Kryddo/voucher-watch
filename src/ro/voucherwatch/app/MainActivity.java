package ro.voucherwatch.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    /** Doar pentru teste prin adb: porneste monitorizarea peste N secunde. */
    static final String EXTRA_TEST_START_IN = "vw_test_start_in_sec";
    /** Doar pentru teste prin adb: arata ecranul de alarma fara sunet. */
    static final String EXTRA_PREVIEW_ALARM = "vw_preview_alarm";
    /** Doar pentru teste prin adb: porneste monitorizarea conform setarilor (ca butonul). */
    static final String EXTRA_ENABLE = "vw_enable";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout alarmBox, warnBox, availCard, priceCard;
    private TextView statusTitle, statusSub, startValue, intervalValue, availCount, priceCount;
    private View statusDot;
    private Switch monitorSwitch;
    private boolean suppressSwitch;
    private String lastSignature = "";
    private static final int REQ_SMS = 2;
    private TextView smsValue, smsStatusView;
    private Runnable pendingSms;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, 1500);
        }
    };

    // =====================================================================
    // Ecranul
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout content = Ui.column(this);
        content.setPadding(dp(20), dp(14), dp(20), dp(120));

        // Bara de sus: titlu + meniu
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, "Voucher Watch", 26, Ui.TEXT, Ui.MEDIUM), Ui.weight());
        TextView menu = Ui.text(this, "⋯", 22, Ui.TEXT_2, Ui.MEDIUM);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(Ui.ripple(0x1AFFFFFF, null, Ui.oval(0xFFFFFFFF)));
        menu.setContentDescription("Meniu");
        menu.setOnClickListener(v -> showMenu());
        top.addView(menu, Ui.size(this, 44, 44));
        content.addView(top, Ui.matchWrap(this, 10));

        alarmBox = Ui.column(this);
        content.addView(alarmBox, Ui.matchWrap(this, 0));
        warnBox = Ui.column(this);
        content.addView(warnBox, Ui.matchWrap(this, 0));

        // Monitorizare: stare + pornire + frecventa
        addSectionHeader(content, "Monitorizare");
        LinearLayout mon = Ui.card(this);
        LinearLayout sr = Ui.row(this);
        sr.setPadding(dp(16), dp(16), dp(12), dp(16));
        statusDot = Ui.dot(this, Ui.TEXT_3);
        sr.addView(statusDot, Ui.size(this, 9, 9));
        LinearLayout sc = Ui.column(this);
        sc.setPadding(dp(14), 0, dp(8), 0);
        statusTitle = Ui.text(this, "", 16.5f, Ui.TEXT, Ui.MEDIUM);
        statusSub = Ui.text(this, "", 13, Ui.TEXT_2, Ui.REGULAR);
        statusSub.setPadding(0, dp(2), 0, 0);
        sc.addView(statusTitle);
        sc.addView(statusSub);
        sr.addView(sc, Ui.weight());
        monitorSwitch = new Switch(this);
        Ui.styleSwitch(monitorSwitch);
        monitorSwitch.setContentDescription("Monitorizare");
        monitorSwitch.setOnCheckedChangeListener((b, on) -> {
            if (suppressSwitch) return;
            if (on) {
                if (!batteryOk()) askBattery();
                Scheduler.enable(this);
            } else {
                Scheduler.disable(this);
            }
            handler.postDelayed(() -> refresh(true), 300);
        });
        sr.addView(monitorSwitch);
        mon.addView(sr);
        mon.addView(Ui.divider(this, 16));
        startValue = Ui.text(this, "", 15, Ui.TEXT_2, Ui.REGULAR);
        mon.addView(settingRow("Pornire", startValue, v -> showStartSheet()));
        mon.addView(Ui.divider(this, 16));
        intervalValue = Ui.text(this, "", 15, Ui.TEXT_2, Ui.REGULAR);
        mon.addView(settingRow("Frecvență", intervalValue, v -> showIntervalSheet()));
        content.addView(mon, Ui.matchWrap(this, 0));

        // SMS automat catre un prieten la lansare
        addSectionHeader(content, "SMS la lansare");
        LinearLayout smsCard = Ui.card(this);
        smsValue = Ui.text(this, "", 15, Ui.TEXT_2, Ui.REGULAR);
        smsCard.addView(settingRow("Când se redeschide eMAG", smsValue, v -> showSmsSheet()));
        content.addView(smsCard, Ui.matchWrap(this, 0));

        // Produse
        availCount = addSectionHeader(content, "Revine în stoc");
        availCard = Ui.card(this);
        content.addView(availCard, Ui.matchWrap(this, 0));
        priceCount = addSectionHeader(content, "Reduceri de preț");
        priceCard = Ui.card(this);
        content.addView(priceCard, Ui.matchWrap(this, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll);

        // Butonul +
        TextView fab = Ui.text(this, "+", 30, Ui.BG, Ui.REGULAR);
        fab.setGravity(Gravity.CENTER);
        fab.setIncludeFontPadding(false);
        fab.setPadding(0, 0, 0, dp(2));
        fab.setBackground(Ui.ripple(0x33000000, Ui.oval(Ui.TEXT), null));
        fab.setElevation(dp(6));
        fab.setContentDescription("Adaugă produs");
        fab.setOnClickListener(v -> showAddSheet(null, null));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);

        setContentView(root);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        try {
            if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } catch (Exception ignored) {
        }

        if (!notifOk()) askNotifications();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(true);
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    private TextView addSectionHeader(LinearLayout content, String label) {
        LinearLayout h = Ui.row(this);
        h.setPadding(dp(4), 0, dp(4), 0);
        h.addView(Ui.sectionLabel(this, label), Ui.weight());
        TextView count = Ui.text(this, "", 12, Ui.TEXT_3, Ui.MEDIUM);
        h.addView(count);
        LinearLayout.LayoutParams lp = Ui.matchWrap(this, 30);
        lp.bottomMargin = dp(10);
        content.addView(h, lp);
        return count;
    }

    private View settingRow(String label, TextView value, View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(16), dp(15), dp(14), dp(15));
        r.setBackground(Ui.touch(this, 0));
        r.addView(Ui.text(this, label, 15, Ui.TEXT, Ui.REGULAR), Ui.weight());
        r.addView(value);
        TextView chev = Ui.text(this, "›", 20, Ui.TEXT_3, Ui.REGULAR);
        chev.setIncludeFontPadding(false);
        chev.setPadding(dp(10), 0, 0, dp(2));
        r.addView(chev);
        r.setOnClickListener(l);
        return r;
    }

    // =====================================================================
    // Actualizare
    // =====================================================================

    private void refresh(boolean force) {
        SharedPreferences p = WatchService.prefs(this);
        long startAt = p.getLong("startAt", 0);
        String sig = Store.raw(this) + '|' + p.getBoolean("enabled", false) + '|' + startAt
                + '|' + Scheduler.describe(startAt) + '|' + p.getBoolean("alarm", false)
                + '|' + p.getString("lastCheck", "") + '|' + p.getBoolean("siteClosed", false) + '|' + Scheduler.startMode(this) + '|' + Scheduler.timeLabel(this)
                + '|' + Scheduler.intervalSec(this) + '|' + notifOk() + batteryOk() + fullScreenOk() + exactOk()
                + '|' + Sms.enabled(this) + Sms.number(this) + Sms.status(this) + Sms.permitted(this);
        if (!force && sig.equals(lastSignature)) return;
        lastSignature = sig;

        // Stare
        boolean enabled = p.getBoolean("enabled", false);
        boolean scheduled = Scheduler.isScheduled(this);
        suppressSwitch = true;
        monitorSwitch.setChecked(enabled);
        suppressSwitch = false;
        if (!enabled) {
            statusDot.setBackground(Ui.oval(Ui.TEXT_3));
            statusTitle.setText("Oprită");
            statusSub.setText(Scheduler.isAt(this)
                    ? "Pornește-o ca să verifice de la " + Scheduler.timeLabel(this)
                    : "Nu verifică nimic");
        } else if (scheduled) {
            statusDot.setBackground(Ui.oval(Ui.TEXT));
            statusTitle.setText("Programată");
            statusSub.setText("Începe " + Scheduler.describe(startAt) + " · până atunci nu verifică");
        } else {
            statusDot.setBackground(Ui.oval(Ui.GREEN));
            statusTitle.setText("Activă");
            String last = p.getString("lastCheck", null);
            if (p.getBoolean("siteClosed", false)) {
                statusSub.setText("eMAG e închis până la lansare · verific la "
                        + Scheduler.intervalLabel(Scheduler.intervalSec(this)));
            } else {
                statusSub.setText(last != null ? "Ultima verificare " + last : "Pornesc verificarea…");
            }
        }
        startValue.setText(Scheduler.isAt(this) ? "La " + Scheduler.timeLabel(this) : "Imediat");
        intervalValue.setText(Scheduler.intervalLabel(Scheduler.intervalSec(this)));

        String smsStatus = Sms.status(this);
        if (Sms.enabled(this)) smsValue.setText(Sms.number(this));
        else if (smsStatus != null && smsStatus.startsWith("Trimis")) smsValue.setText(smsStatus);
        else smsValue.setText("Oprit");
        if (smsStatusView != null && smsStatus != null) {
            smsStatusView.setText(smsStatus);
            smsStatusView.setTextColor(smsStatus.contains("trimis") || smsStatus.startsWith("Trimis") ? Ui.GREEN : Ui.RED);
        }

        renderAlarm(p);
        renderWarnings();

        // Produse
        List<Store.Product> avail = new ArrayList<>(), price = new ArrayList<>();
        for (Store.Product x : Store.load(this)) {
            if (Store.MODE_PRICE.equals(x.mode)) price.add(x);
            else avail.add(x);
        }
        boolean active = enabled && !scheduled;
        fillCard(availCard, avail, Store.MODE_AVAIL, active);
        fillCard(priceCard, price, Store.MODE_PRICE, active);
        availCount.setText(avail.isEmpty() ? "" : String.valueOf(avail.size()));
        priceCount.setText(price.isEmpty() ? "" : String.valueOf(price.size()));
    }

    private void renderAlarm(SharedPreferences p) {
        alarmBox.removeAllViews();
        if (!p.getBoolean("alarm", false)) return;
        LinearLayout c = Ui.column(this);
        c.setBackground(Ui.outlined(this, Ui.SURFACE, Ui.RED, 18));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout h = Ui.row(this);
        h.addView(Ui.dot(this, Ui.RED), Ui.size(this, 8, 8));
        TextView t = Ui.text(this, p.getString("alarmTitle", "Alarmă"), 15.5f, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(dp(10), 0, 0, 0);
        h.addView(t, Ui.weight());
        c.addView(h);
        TextView n = Ui.text(this, p.getString("alarmName", "") + "\n" + p.getString("alarmDetail", ""),
                14, Ui.TEXT_2, Ui.REGULAR);
        n.setPadding(0, dp(8), 0, dp(14));
        c.addView(n);
        c.addView(Ui.primaryButton(this, "Oprește alarma", v -> {
            service(WatchService.ACTION_STOP_ALARM);
            handler.postDelayed(() -> refresh(true), 300);
        }));
        alarmBox.addView(c, Ui.matchWrap(this, 20));
    }

    private void renderWarnings() {
        warnBox.removeAllViews();
        boolean smsOk = !Sms.enabled(this) || Sms.permitted(this);
        if (notifOk() && batteryOk() && fullScreenOk() && exactOk() && smsOk) return;
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.row(this);
        h.setPadding(dp(16), dp(16), dp(16), dp(4));
        h.addView(Ui.dot(this, Ui.RED), Ui.size(this, 8, 8));
        TextView t = Ui.text(this, "Permisiuni necesare", 15.5f, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(dp(10), 0, 0, 0);
        h.addView(t, Ui.weight());
        c.addView(h);
        if (!notifOk()) c.addView(settingRow("Notificări", hint(), v -> askNotifications()));
        if (!batteryOk()) c.addView(settingRow("Rulare în fundal", hint(), v -> askBattery()));
        if (!fullScreenOk()) c.addView(settingRow("Alarmă pe ecran complet", hint(), v -> askFullScreen()));
        if (!exactOk()) c.addView(settingRow("Alarme exacte", hint(), v -> askExact()));
        if (!smsOk) c.addView(settingRow("Trimitere SMS", hint(), v -> requestSms(() -> { })));
        warnBox.addView(c, Ui.matchWrap(this, 20));
    }

    // =====================================================================
    // SMS la lansare
    // =====================================================================

    private void showSmsSheet() {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "SMS la lansare"));
        TextView desc = Ui.text(this, "Când eMAG trece de pagina de așteptare, trimit automat un SMS "
                + "de pe telefonul tău. O singură dată.", 13.5f, Ui.TEXT_2, Ui.REGULAR);
        desc.setPadding(dp(4), 0, dp(4), 0);
        box.addView(desc);
        boolean closed = WatchService.prefs(this).getBoolean("siteClosed", false);
        TextView site = Ui.text(this, closed
                        ? "Acum eMAG e închis, deci SMS-ul pleacă la redeschidere."
                        : "Acum eMAG pare deschis: SMS-ul pleacă după următoarea închidere și redeschidere.",
                13, closed ? Ui.GREEN : Ui.TEXT_3, Ui.REGULAR);
        site.setPadding(dp(4), dp(6), dp(4), 0);
        box.addView(site);

        box.addView(fieldLabel("Număr de telefon"), Ui.matchWrap(this, 18));
        EditText phone = Ui.input(this, "07xx xxx xxx", InputType.TYPE_CLASS_PHONE);
        phone.setText(Sms.number(this));
        box.addView(phone, Ui.matchWrap(this, 8));

        box.addView(fieldLabel("Mesaj"), Ui.matchWrap(this, 16));
        EditText msg = Ui.input(this, Sms.DEFAULT_TEXT, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        msg.setText(Sms.text(this));
        msg.setMaxLines(4);
        box.addView(msg, Ui.matchWrap(this, 8));

        TextView status = Ui.text(this, "", 13, Ui.TEXT_2, Ui.REGULAR);
        status.setPadding(dp(4), dp(12), dp(4), 0);
        String st = Sms.status(this);
        if (st != null) {
            status.setText(st);
            status.setTextColor(st.contains("trimis") || st.startsWith("Trimis") ? Ui.GREEN : Ui.RED);
        }
        box.addView(status);
        smsStatusView = status;

        Dialog[] d = new Dialog[1];
        LinearLayout buttons = Ui.row(this);
        TextView test = Ui.secondaryButton(this, "Trimite test", v -> {
            String n = Sms.cleanNumber(phone.getText().toString());
            if (n == null) {
                status.setText("Număr de telefon invalid.");
                status.setTextColor(Ui.RED);
                return;
            }
            String text = msg.getText().toString().trim();
            String body = "Test: " + (text.isEmpty() ? Sms.DEFAULT_TEXT : text);
            requestSms(() -> {
                status.setText("Se trimite testul…");
                status.setTextColor(Ui.TEXT_2);
                Sms.send(this, n, body, true);
            });
        });
        buttons.addView(test, Ui.weight());
        TextView save = Ui.primaryButton(this, Sms.enabled(this) ? "Salvează" : "Activează", v -> {
            String n = Sms.cleanNumber(phone.getText().toString());
            if (n == null) {
                status.setText("Număr de telefon invalid.");
                status.setTextColor(Ui.RED);
                return;
            }
            String text = msg.getText().toString().trim();
            Sms.save(this, true, n, text.isEmpty() ? Sms.DEFAULT_TEXT : text);
            WatchService.log(this, "SMS la lansare activat către " + Sms.mask(n) + ".");
            d[0].dismiss();
            requestSms(() -> Toast.makeText(this, "Activat: trimit SMS-ul când se redeschide eMAG",
                    Toast.LENGTH_LONG).show());
            refresh(true);
        });
        LinearLayout.LayoutParams slp = Ui.weight();
        slp.leftMargin = dp(10);
        buttons.addView(save, slp);
        box.addView(buttons, Ui.matchWrap(this, 16));

        if (Sms.enabled(this)) {
            TextView off = Ui.text(this, "Dezactivează", 15, Ui.RED, Ui.REGULAR);
            off.setGravity(Gravity.CENTER);
            off.setPadding(0, dp(14), 0, dp(6));
            off.setBackground(Ui.touch(this, 14));
            off.setOnClickListener(v -> {
                Sms.save(this, false, Sms.number(this), Sms.text(this));
                WatchService.log(this, "SMS la lansare dezactivat.");
                d[0].dismiss();
                refresh(true);
            });
            box.addView(off, Ui.matchWrap(this, 6));
        }

        d[0] = Ui.sheet(this, box);
        d[0].setOnDismissListener(x -> smsStatusView = null);
        Ui.show(d[0]);
    }

    private void requestSms(Runnable then) {
        if (Sms.permitted(this)) {
            then.run();
            return;
        }
        pendingSms = then;
        requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_SMS) return;
        Runnable r = pendingSms;
        pendingSms = null;
        if (Sms.permitted(this)) {
            if (r != null) r.run();
        } else {
            Toast.makeText(this, "Fără permisiunea de SMS nu pot trimite mesajul.", Toast.LENGTH_LONG).show();
        }
        refresh(true);
    }

    private TextView hint() {
        return Ui.text(this, "Permite", 15, Ui.TEXT_2, Ui.REGULAR);
    }

    private void fillCard(LinearLayout card, List<Store.Product> list, String mode, boolean active) {
        card.removeAllViews();
        if (list.isEmpty()) {
            LinearLayout r = Ui.row(this);
            r.setPadding(dp(16), dp(16), dp(16), dp(16));
            r.setBackground(Ui.touch(this, 0));
            r.addView(Ui.text(this, "Niciun produs", 15, Ui.TEXT_3, Ui.REGULAR), Ui.weight());
            r.addView(Ui.text(this, "Adaugă", 15, Ui.TEXT, Ui.MEDIUM));
            r.setOnClickListener(v -> showAddSheet(null, mode));
            card.addView(r);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) card.addView(Ui.divider(this, 16));
            card.addView(productRow(list.get(i), active));
        }
    }

    private View productRow(Store.Product p, boolean active) {
        LinearLayout r = Ui.column(this);
        r.setPadding(dp(16), dp(15), dp(16), dp(15));
        r.setBackground(Ui.touch(this, 0));

        TextView title = Ui.text(this, p.title != null ? p.title : Checker.titleFromUrl(p.url), 15, Ui.TEXT, Ui.REGULAR);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(1), 1f);
        r.addView(title);

        LinearLayout meta = Ui.row(this);
        meta.setPadding(0, dp(9), 0, 0);
        int dotColor;
        String state;
        if (p.error != null) {
            dotColor = Ui.RED;
            state = "Eroare · " + p.error;
        } else if (p.code == null) {
            dotColor = Ui.TEXT_3;
            state = active ? "Se verifică…" : "Neverificat";
        } else {
            dotColor = p.available() ? Ui.GREEN : Ui.TEXT_3;
            state = p.text;
        }
        meta.addView(Ui.dot(this, dotColor), Ui.size(this, 7, 7));
        TextView st = Ui.text(this, state, 13, Ui.TEXT_2, Ui.REGULAR);
        st.setPadding(dp(8), 0, dp(8), 0);
        st.setSingleLine(true);
        st.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(st, Ui.weight());
        meta.addView(Ui.text(this, Checker.formatPrice(p.price), 15, Ui.TEXT, Ui.MEDIUM));
        r.addView(meta);

        SpannableStringBuilder extra = new SpannableStringBuilder();
        if (Store.MODE_PRICE.equals(p.mode)) {
            if (p.basePrice > 0 && p.price >= 0 && Math.abs(p.price - p.basePrice) > 0.001) {
                double pct = (p.price - p.basePrice) / p.basePrice * 100;
                String d = (pct < 0 ? "−" : "+") + String.format(Locale.US, "%.0f%%", Math.abs(pct))
                        + " față de " + Checker.formatPrice(p.basePrice);
                int s = extra.length();
                extra.append(d);
                if (pct < 0) {
                    extra.setSpan(new ForegroundColorSpan(Ui.GREEN), s, extra.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                extra.append("Preț inițial ").append(Checker.formatPrice(p.basePrice));
            }
            if (p.target >= 0) extra.append("  ·  țintă ").append(Checker.formatPrice(p.target));
            extra.append("  ·  ");
        }
        extra.append(p.alarm ? "Alarmă" : "Notificare");
        if (p.lastCheck != null) extra.append("  ·  ").append(p.lastCheck);
        TextView ex = Ui.text(this, extra, 12.5f, Ui.TEXT_3, Ui.REGULAR);
        ex.setPadding(0, dp(6), 0, 0);
        r.addView(ex);

        r.setOnClickListener(v -> showProductSheet(p));
        return r;
    }

    // =====================================================================
    // Panouri
    // =====================================================================

    private void showMenu() {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "Opțiuni"));
        Dialog[] d = new Dialog[1];
        box.addView(Ui.optionRow(this, "Testează alarma", "Sună până o oprești", false, v -> {
            d[0].dismiss();
            startForegroundService(new Intent(this, WatchService.class).setAction(WatchService.ACTION_TEST_ALARM));
        }));
        box.addView(Ui.optionRow(this, "Jurnal", "Ultimele evenimente", false, v -> {
            d[0].dismiss();
            showLog();
        }));
        d[0] = Ui.sheet(this, box);
        Ui.show(d[0]);
    }

    private void showLog() {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "Jurnal"));
        String log = WatchService.prefs(this).getString("log", "");
        TextView t = Ui.text(this, log.isEmpty() ? "Nimic încă." : log, 12, Ui.TEXT_2, Typeface.MONOSPACE);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(dp(4), 0, dp(4), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        box.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        Ui.show(Ui.sheet(this, box));
    }

    private void showStartSheet() {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "Pornire"));
        Dialog[] d = new Dialog[1];
        boolean at = Scheduler.isAt(this);
        box.addView(Ui.optionRow(this, "Imediat", "Verifică de cum pornești monitorizarea", !at, v -> {
            d[0].dismiss();
            Scheduler.setStart(this, Scheduler.MODE_NOW, Scheduler.startHour(this), Scheduler.startMinute(this));
            refresh(true);
        }));
        box.addView(Ui.optionRow(this, "La ora " + Scheduler.timeLabel(this),
                "Nu verifică nimic până atunci · atinge ca să alegi ora", at, v -> {
                    d[0].dismiss();
                    pickTime();
                }));
        d[0] = Ui.sheet(this, box);
        Ui.show(d[0]);
    }

    private void pickTime() {
        new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, (view, h, m) -> {
            Scheduler.setStart(this, Scheduler.MODE_AT, h, m);
            refresh(true);
            if (WatchService.prefs(this).getBoolean("enabled", false)) {
                Toast.makeText(this, "Programată: începe "
                        + Scheduler.describe(WatchService.prefs(this).getLong("startAt", 0)), Toast.LENGTH_SHORT).show();
            }
        }, Scheduler.startHour(this), Scheduler.startMinute(this), true).show();
    }

    private void showIntervalSheet() {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "Frecvență verificare"));
        Dialog[] d = new Dialog[1];
        int cur = Scheduler.intervalSec(this);
        int[] options = {15, 30, 60, 300};
        String[] subs = {"Pentru lansări, ex. Black Friday", null, null, "Consum minim de date"};
        for (int i = 0; i < options.length; i++) {
            int sec = options[i];
            box.addView(Ui.optionRow(this, Scheduler.intervalLabel(sec), subs[i], sec == cur, v -> {
                d[0].dismiss();
                Scheduler.setInterval(this, sec);
                refresh(true);
            }));
        }
        d[0] = Ui.sheet(this, box);
        Ui.show(d[0]);
    }

    private void showProductSheet(Store.Product p) {
        LinearLayout box = Ui.column(this);
        TextView t = Ui.text(this, p.title != null ? p.title : Checker.titleFromUrl(p.url), 17, Ui.TEXT, Ui.MEDIUM);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(4), 0, dp(4), 0);
        box.addView(t);
        String state = p.error != null ? "Eroare" : p.text != null ? p.text : "Neverificat";
        TextView meta = Ui.text(this, state + "  ·  " + Checker.formatPrice(p.price)
                + "  ·  " + (Store.MODE_PRICE.equals(p.mode) ? "Reducere de preț" : "Revine în stoc"),
                13.5f, Ui.TEXT_2, Ui.REGULAR);
        meta.setPadding(dp(4), dp(6), dp(4), dp(16));
        box.addView(meta);

        Dialog[] d = new Dialog[1];
        box.addView(Ui.optionRow(this, "Deschide pe eMAG", null, false, v -> {
            d[0].dismiss();
            open(p.url);
        }));

        LinearLayout ar = Ui.row(this);
        ar.setPadding(dp(14), dp(10), dp(8), dp(10));
        LinearLayout ac = Ui.column(this);
        ac.addView(Ui.text(this, "Alarmă până o opresc", 15.5f, Ui.TEXT, Ui.REGULAR));
        ac.addView(Ui.text(this, "Altfel doar notificare", 13, Ui.TEXT_2, Ui.REGULAR));
        ar.addView(ac, Ui.weight());
        Switch sw = new Switch(this);
        Ui.styleSwitch(sw);
        sw.setChecked(p.alarm);
        sw.setOnCheckedChangeListener((b, on) -> {
            Store.setAlarm(this, p.key(), on);
            refresh(true);
        });
        ar.addView(sw);
        box.addView(ar);

        TextView del = Ui.text(this, "Șterge", 15.5f, Ui.RED, Ui.REGULAR);
        del.setPadding(dp(14), dp(14), dp(14), dp(14));
        del.setBackground(Ui.touch(this, 14));
        boolean[] armed = {false};
        del.setOnClickListener(v -> {
            if (!armed[0]) {
                armed[0] = true;
                del.setText("Atinge din nou ca să ștergi");
                return;
            }
            Store.remove(this, p.key());
            WatchService.log(this, "Șters: " + WatchService.shortTitle(p.title, 40));
            d[0].dismiss();
            refresh(true);
        });
        box.addView(del);

        d[0] = Ui.sheet(this, box);
        Ui.show(d[0]);
    }

    // ---------- adaugare ----------

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        int testIn = intent.getIntExtra(EXTRA_TEST_START_IN, 0);
        if (testIn > 0) {
            Scheduler.enableAt(this, System.currentTimeMillis() + testIn * 1000L);
            Toast.makeText(this, "Test: pornire în " + testIn + " s", Toast.LENGTH_SHORT).show();
        }
        if (intent.getBooleanExtra(EXTRA_ENABLE, false)) Scheduler.enable(this);
        if (intent.getBooleanExtra(EXTRA_PREVIEW_ALARM, false)) {
            startActivity(new Intent(this, AlarmActivity.class).putExtra(AlarmActivity.EXTRA_PREVIEW, true));
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            showAddSheet(text != null ? text : "", null);
        }
        setIntent(new Intent(this, MainActivity.class));
    }

    private String clipboardUrl() {
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            ClipData clip = cm != null ? cm.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                return Checker.normalize(String.valueOf(clip.getItemAt(0).coerceToText(this)));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showAddSheet(String preset, String presetMode) {
        String url = preset != null ? Checker.normalize(preset) : clipboardUrl();
        final String[] mode = {presetMode != null ? presetMode : Store.MODE_AVAIL};
        final boolean[] alarmTouched = {false};

        LinearLayout box = Ui.column(this);
        box.addView(Ui.sheetTitle(this, "Adaugă produs"));

        box.addView(fieldLabel("Link eMAG"));
        EditText link = Ui.input(this, "https://www.emag.ro/…",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setText(url != null ? url : "");
        link.setMaxLines(3);
        box.addView(link, Ui.matchWrap(this, 8));

        box.addView(fieldLabel("Tip"), Ui.matchWrap(this, 18));
        LinearLayout seg = Ui.row(this);
        seg.setBackground(Ui.rect(this, Ui.SURFACE_2, 12));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        TextView segAvail = segment("Revine în stoc");
        TextView segPrice = segment("Reducere de preț");
        seg.addView(segAvail, Ui.weight());
        seg.addView(segPrice, Ui.weight());
        box.addView(seg, Ui.matchWrap(this, 8));

        TextView desc = Ui.text(this, "", 13, Ui.TEXT_3, Ui.REGULAR);
        desc.setPadding(dp(4), dp(8), dp(4), 0);
        box.addView(desc);

        EditText target = Ui.input(this, "Preț țintă în lei (opțional)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(target, Ui.matchWrap(this, 12));

        LinearLayout ar = Ui.row(this);
        ar.setPadding(dp(4), 0, 0, 0);
        LinearLayout ac = Ui.column(this);
        ac.addView(Ui.text(this, "Alarmă până o opresc", 15, Ui.TEXT, Ui.REGULAR));
        ac.addView(Ui.text(this, "Altfel primești doar o notificare", 13, Ui.TEXT_3, Ui.REGULAR));
        ar.addView(ac, Ui.weight());
        Switch alarmSw = new Switch(this);
        Ui.styleSwitch(alarmSw);
        alarmSw.setOnClickListener(v -> alarmTouched[0] = true);
        ar.addView(alarmSw);
        box.addView(ar, Ui.matchWrap(this, 18));

        TextView status = Ui.text(this, "", 13, Ui.TEXT_2, Ui.REGULAR);
        status.setPadding(dp(4), dp(12), dp(4), 0);
        box.addView(status);

        Dialog[] d = new Dialog[1];
        LinearLayout buttons = Ui.row(this);
        buttons.addView(Ui.secondaryButton(this, "Anulează", v -> d[0].dismiss()), Ui.weight());
        TextView add = Ui.primaryButton(this, "Adaugă", null);
        LinearLayout.LayoutParams alp = Ui.weight();
        alp.leftMargin = dp(10);
        buttons.addView(add, alp);
        box.addView(buttons, Ui.matchWrap(this, 18));

        Runnable applyMode = () -> {
            boolean avail = Store.MODE_AVAIL.equals(mode[0]);
            styleSegment(segAvail, avail);
            styleSegment(segPrice, !avail);
            desc.setText(avail
                    ? "Te anunț când produsul trece din „Indisponibil” în stoc."
                    : "Te anunț când scade prețul. Cu preț țintă, doar sub el.");
            target.setVisibility(avail ? View.GONE : View.VISIBLE);
            if (!alarmTouched[0]) alarmSw.setChecked(avail);
        };
        segAvail.setOnClickListener(v -> {
            mode[0] = Store.MODE_AVAIL;
            applyMode.run();
        });
        segPrice.setOnClickListener(v -> {
            mode[0] = Store.MODE_PRICE;
            applyMode.run();
        });
        applyMode.run();
        if (preset != null && url == null) {
            status.setText("Nu am găsit un link eMAG în textul distribuit.");
            status.setTextColor(Ui.RED);
        }

        d[0] = Ui.sheet(this, box);
        add.setOnClickListener(v -> submitAdd(d[0], add, status, link, target, alarmSw, mode[0]));
        Ui.show(d[0]);
    }

    private void submitAdd(Dialog d, TextView add, TextView status, EditText link, EditText target,
                           Switch alarmSw, String mode) {
        String url = Checker.normalize(link.getText().toString());
        if (url == null) {
            status.setText("Lipește un link de produs de pe emag.ro");
            status.setTextColor(Ui.RED);
            return;
        }
        double tgt = -1;
        String ts = target.getText().toString().trim().replace(',', '.');
        if (Store.MODE_PRICE.equals(mode) && !ts.isEmpty()) {
            try {
                tgt = Double.parseDouble(ts);
            } catch (NumberFormatException e) {
                status.setText("Prețul țintă nu e un număr valid.");
                status.setTextColor(Ui.RED);
                return;
            }
        }
        final double targetPrice = tgt;
        final boolean alarm = alarmSw.isChecked();
        add.setEnabled(false);
        add.setAlpha(0.5f);
        status.setText("Verific produsul…");
        status.setTextColor(Ui.TEXT_2);

        new Thread(() -> {
            Checker.Result r = null;
            String err = null;
            try {
                r = Checker.parse(Checker.fetch(url));
                if (r == null) err = "nu găsesc starea produsului pe pagină";
            } catch (Exception e) {
                err = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final Checker.Result res = r;
            final String error = err;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Store.Product p = new Store.Product();
                p.url = url;
                p.id = Checker.productId(url);
                p.mode = mode;
                p.alarm = alarm;
                p.target = targetPrice;
                p.added = System.currentTimeMillis();
                p.title = res != null && res.title != null ? res.title : Checker.titleFromUrl(url);
                if (res != null) {
                    p.code = res.code;
                    p.text = res.text;
                    p.price = res.price;
                    p.basePrice = res.price;
                    p.lastCheck = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                } else {
                    p.error = error;
                }
                if (!Store.add(this, p)) {
                    status.setText("Produsul e deja în această secțiune.");
                    status.setTextColor(Ui.RED);
                    add.setEnabled(true);
                    add.setAlpha(1f);
                    return;
                }
                WatchService.log(this, "Adăugat (" + (Store.MODE_PRICE.equals(mode) ? "reducere" : "stoc")
                        + "): " + WatchService.shortTitle(p.title, 40));
                d.dismiss();
                String msg = "Adăugat";
                if (res == null) msg += " · nu am putut citi pagina acum (" + error + ")";
                else if (Store.MODE_AVAIL.equals(mode) && res.available()) msg += " · e deja în stoc";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refresh(true);
            });
        }).start();
    }

    private TextView fieldLabel(String s) {
        TextView t = Ui.text(this, s, 13, Ui.TEXT_2, Ui.MEDIUM);
        t.setPadding(dp(4), 0, 0, 0);
        return t;
    }

    private TextView segment(String s) {
        TextView t = Ui.text(this, s, 14, Ui.TEXT_2, Ui.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(11), dp(8), dp(11));
        return t;
    }

    private void styleSegment(TextView t, boolean selected) {
        t.setBackground(selected ? Ui.rect(this, Ui.SELECTED, 10) : null);
        t.setTextColor(selected ? Ui.TEXT : Ui.TEXT_2);
    }

    // =====================================================================
    // Serviciu si permisiuni
    // =====================================================================

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void service(String action) {
        startService(new Intent(this, WatchService.class).setAction(action));
    }

    private boolean notifOk() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    private boolean batteryOk() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean fullScreenOk() {
        return Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    private boolean exactOk() {
        return Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private void askBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void askFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void askExact() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
