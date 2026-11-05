package ro.voucherwatch.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lista de produse urmarite, salvata ca JSON. Apelurile se sincronizeaza pe Store.class. */
final class Store {
    static final String MODE_AVAIL = "avail";
    static final String MODE_PRICE = "price";

    static final class Product {
        String id, url, title, mode = MODE_AVAIL;
        boolean alarm = true;
        double target = -1;        // doar pentru reduceri: anunta doar sub acest pret
        String code, text, lastCheck, error;
        double price = -1, basePrice = -1;
        int fails;
        long added;

        String key() {
            return mode + ":" + id;
        }

        boolean available() {
            return code != null && !Checker.isUnavailable(code);
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id).put("url", url).put("title", title).put("mode", mode).put("alarm", alarm)
                    .put("target", target).put("price", price).put("basePrice", basePrice)
                    .put("fails", fails).put("added", added);
            if (code != null) o.put("code", code);
            if (text != null) o.put("text", text);
            if (lastCheck != null) o.put("lastCheck", lastCheck);
            if (error != null) o.put("error", error);
            return o;
        }

        static Product fromJson(JSONObject o) {
            Product p = new Product();
            p.id = o.optString("id", null);
            p.url = o.optString("url", null);
            p.title = o.optString("title", null);
            p.mode = o.optString("mode", MODE_AVAIL);
            p.alarm = o.optBoolean("alarm", true);
            p.target = o.optDouble("target", -1);
            p.price = o.optDouble("price", -1);
            p.basePrice = o.optDouble("basePrice", -1);
            p.fails = o.optInt("fails", 0);
            p.added = o.optLong("added", 0);
            p.code = o.optString("code", null);
            p.text = o.optString("text", null);
            p.lastCheck = o.optString("lastCheck", null);
            p.error = o.optString("error", null);
            return p;
        }
    }

    static synchronized List<Product> load(Context c) {
        SharedPreferences prefs = WatchService.prefs(c);
        String raw = prefs.getString("products", null);
        List<Product> out = new ArrayList<>();
        if (raw == null) {
            out.add(migrateVoucher(prefs));
            save(c, out);
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                Product p = Product.fromJson(a.getJSONObject(i));
                if (p.id != null && p.url != null) out.add(p);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static synchronized void save(Context c, List<Product> list) {
        JSONArray a = new JSONArray();
        for (Product p : list) {
            try {
                a.put(p.toJson());
            } catch (JSONException ignored) {
            }
        }
        WatchService.prefs(c).edit().putString("products", a.toString()).apply();
    }

    static synchronized String raw(Context c) {
        return WatchService.prefs(c).getString("products", "");
    }

    static Product find(List<Product> list, String key) {
        for (Product p : list) if (p.key().equals(key)) return p;
        return null;
    }

    /** Adauga produsul; intoarce false daca exista deja in aceeasi sectiune. */
    static synchronized boolean add(Context c, Product p) {
        List<Product> list = load(c);
        if (find(list, p.key()) != null) return false;
        list.add(p);
        save(c, list);
        return true;
    }

    static synchronized void remove(Context c, String key) {
        List<Product> list = load(c);
        Product p = find(list, key);
        if (p != null) {
            list.remove(p);
            save(c, list);
        }
    }

    static synchronized void setAlarm(Context c, String key, boolean alarm) {
        List<Product> list = load(c);
        Product p = find(list, key);
        if (p != null) {
            p.alarm = alarm;
            save(c, list);
        }
    }

    /** Prima rulare dupa actualizare: voucherul urmarit deja devine primul produs din lista. */
    private static Product migrateVoucher(SharedPreferences prefs) {
        Product v = new Product();
        v.url = Checker.VOUCHER_URL;
        v.id = Checker.productId(v.url);
        v.title = "Voucher Auchan 1+1 „Unul pentru tine, unul pentru un prieten”, 300 lei";
        v.mode = MODE_AVAIL;
        v.alarm = true;
        v.code = prefs.getString("code", null);
        v.text = prefs.getString("text", null);
        try {
            v.price = Double.parseDouble(prefs.getString("price", "-1"));
        } catch (NumberFormatException e) {
            v.price = -1;
        }
        v.basePrice = v.price;
        v.lastCheck = prefs.getString("lastCheck", null);
        v.added = System.currentTimeMillis();
        return v;
    }

    private Store() {
    }
}
