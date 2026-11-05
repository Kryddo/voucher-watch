package ro.voucherwatch.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Ecranul de alarma: apare peste ecranul blocat si ramane pana opresti alarma. */
public class AlarmActivity extends Activity {
    static final String ACTION_OPEN_EMAG = "ro.voucherwatch.OPEN_EMAG";
    static final String EXTRA_PREVIEW = "preview";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean preview;
    private ValueAnimator pulse;

    private final Runnable watcher = new Runnable() {
        @Override
        public void run() {
            if (!preview && !WatchService.prefs(AlarmActivity.this).getBoolean("alarm", false)) {
                finish();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        SharedPreferences p = WatchService.prefs(this);
        String kind = preview ? "Disponibil acum" : p.getString("alarmTitle", "Alarmă");
        String name = preview ? "Voucher Auchan 1+1 „Unul pentru tine, unul pentru un prieten”, 300 lei"
                : p.getString("alarmName", "");
        String detail = preview ? "În stoc · 300 lei" : p.getString("alarmDetail", "");

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        // Cerc rosu care pulseaza
        FrameLayout pulseBox = new FrameLayout(this);
        View ring = new View(this);
        ring.setBackground(Ui.oval(Ui.RED));
        pulseBox.addView(ring, new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER));
        View core = new View(this);
        core.setBackground(Ui.oval(Ui.RED));
        pulseBox.addView(core, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        root.addView(pulseBox, new LinearLayout.LayoutParams(dp(150), dp(150)));
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1500);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            ring.setScaleX(0.15f + 0.85f * f);
            ring.setScaleY(0.15f + 0.85f * f);
            ring.setAlpha(0.5f * (1f - f));
        });
        pulse.start();

        TextView k = Ui.text(this, kind.toUpperCase(new Locale("ro", "RO")), 13, Ui.RED, Ui.MEDIUM);
        k.setLetterSpacing(0.12f);
        k.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams klp = Ui.matchWrap(this, 28);
        root.addView(k, klp);

        TextView n = Ui.text(this, name, 24, Ui.TEXT, Ui.MEDIUM);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(4);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setLineSpacing(dp(2), 1f);
        root.addView(n, Ui.matchWrap(this, 12));

        TextView d = Ui.text(this, detail, 17, Ui.TEXT_2, Ui.REGULAR);
        d.setGravity(Gravity.CENTER);
        root.addView(d, Ui.matchWrap(this, 12));

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView stop = Ui.primaryButton(this, "Oprește alarma", v -> {
            if (!preview) stopAlarm();
            finish();
        });
        stop.setTextSize(17);
        stop.setPadding(0, dp(20), 0, dp(20));
        root.addView(stop, Ui.matchWrap(this, 0));

        TextView open = Ui.secondaryButton(this, "Deschide eMAG", v -> {
            if (preview) finish();
            else openEmag();
        });
        root.addView(open, Ui.matchWrap(this, 10));

        setContentView(root);
        // Dupa setContentView: abia acum exista fereastra (inainte dadea crash).
        try {
            if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } catch (Exception ignored) {
        }

        if (!preview && ACTION_OPEN_EMAG.equals(getIntent().getAction())) openEmag();
        if (preview) handler.postDelayed(this::finish, 5000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(watcher, 1000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(watcher);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }

    private void stopAlarm() {
        startService(new Intent(this, WatchService.class).setAction(WatchService.ACTION_STOP_ALARM));
    }

    private void openEmag() {
        String url = WatchService.prefs(this).getString("alarmUrl", Checker.VOUCHER_URL);
        stopAlarm();
        Runnable go = () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
            finish();
        };
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    go.run();
                }

                @Override
                public void onDismissError() {
                    go.run();
                }

                @Override
                public void onDismissCancelled() {
                    finish();
                }
            });
        } else {
            go.run();
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
