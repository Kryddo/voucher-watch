package ro.voucherwatch.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Tema intunecata, minimalista: neutre + verde (activ / in stoc) + rosu (alarma / erori). */
final class Ui {
    static final int BG = 0xFF0C0D10;
    static final int SURFACE = 0xFF16171B;
    static final int SURFACE_2 = 0xFF202127;
    static final int SELECTED = 0xFF2E2F36;
    static final int STROKE = 0xFF25262C;
    static final int TEXT = 0xFFEDEEF0;
    static final int TEXT_2 = 0xFF9B9DA6;
    static final int TEXT_3 = 0xFF65676F;
    static final int GREEN = 0xFF34D399;
    static final int RED = 0xFFF26464;

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- forme ----------

    static GradientDrawable rect(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable outlined(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = rect(c, fill, radiusDp);
        d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static Drawable ripple(int rippleColor, Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** Fundal transparent care raspunde la atingere. */
    static Drawable touch(Context c, float radiusDp) {
        return ripple(0x1AFFFFFF, null, rect(c, Color.WHITE, radiusDp));
    }

    // ---------- text ----------

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(new Locale("ro", "RO")), 12, TEXT_3, MEDIUM);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static TextView sheetTitle(Context c, String s) {
        TextView t = text(c, s, 19, TEXT, MEDIUM);
        t.setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 14));
        return t;
    }

    // ---------- blocuri ----------

    static View dot(Context c, int color) {
        View v = new View(c);
        v.setBackground(oval(color));
        return v;
    }

    static View divider(Context c, float insetDp) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(c, 0.7f)));
        lp.leftMargin = dp(c, insetDp);
        v.setLayoutParams(lp);
        return v;
    }

    /** Container cu colturi rotunjite; randurile din el se decupeaza pe forma lui. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(rect(c, SURFACE, 18));
        l.setClipToOutline(true);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBaselineAligned(false);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static LinearLayout.LayoutParams size(Context c, float wDp, float hDp) {
        return new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp));
    }

    // ---------- controale ----------

    static TextView primaryButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 15.5f, BG, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        b.setBackground(ripple(0x33000000, rect(c, TEXT, 14), null));
        b.setOnClickListener(l);
        return b;
    }

    static TextView secondaryButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 15.5f, TEXT, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        b.setBackground(ripple(0x1AFFFFFF, rect(c, SURFACE_2, 14), null));
        b.setOnClickListener(l);
        return b;
    }

    static EditText input(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(TEXT_3);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setTypeface(REGULAR);
        e.setInputType(inputType);
        e.setHighlightColor(0x5534D399);
        e.setBackground(rect(c, SURFACE_2, 12));
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        return e;
    }

    static void styleSwitch(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFB5B7BF}));
        s.setTrackTintList(new ColorStateList(states, new int[]{GREEN, 0xFF3A3B42}));
    }

    /** Rand de optiune intr-un panou de jos: titlu, descriere optionala, bifa daca e ales. */
    static LinearLayout optionRow(Context c, String title, String sub, boolean selected, View.OnClickListener l) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        r.setBackground(selected ? ripple(0x1AFFFFFF, rect(c, SURFACE_2, 14), null) : touch(c, 14));
        LinearLayout col = column(c);
        col.addView(text(c, title, 15.5f, TEXT, REGULAR));
        if (sub != null) {
            TextView s = text(c, sub, 13, TEXT_2, REGULAR);
            s.setPadding(0, dp(c, 2), 0, 0);
            col.addView(s);
        }
        r.addView(col, weight());
        if (selected) r.addView(text(c, "✓", 16, GREEN, MEDIUM));
        r.setOnClickListener(l);
        return r;
    }

    // ---------- panou de jos (bottom sheet) ----------

    static Dialog sheet(Context c, View content) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = column(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        float r = dp(c, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        wrap.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 22));
        View grip = new View(c);
        grip.setBackground(rect(c, 0xFF3A3B42, 2));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = dp(c, 16);
        wrap.addView(grip, glp);
        wrap.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.6f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        return d;
    }

    static void show(Dialog d) {
        d.show();
        Window w = d.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private Ui() {
    }
}
