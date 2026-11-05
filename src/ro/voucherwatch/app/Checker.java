package ro.voucherwatch.app;

import android.text.Html;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Descarca o pagina de produs eMAG si extrage titlul, disponibilitatea si pretul. */
final class Checker {
    static final String VOUCHER_URL = "https://www.emag.ro/voucher-auchan-unul-pentru-tine-unul-pentru-un-prieten-"
            + "300-lei-valabil-pana-pe-3-2-2026-1/pd/DS63P77BM/";
    private static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private static final Pattern OFFER =
            Pattern.compile("EM\\.offer\\s*=\\s*\\{\\s*availability:\\s*(\\{.*?\\})\\s*,\\s*\\n");
    private static final Pattern SCHEMA =
            Pattern.compile("\"availability\":\\s*\"https?://schema\\.org/(\\w+)\"");
    private static final Pattern PRICE_OFFER = Pattern.compile("price:\\s*\\{\"current\":([\\d.]+)");
    private static final Pattern PRICE_ANY = Pattern.compile("\"price\":\\s*\"?([\\d.]+)");
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern ANY_URL = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern EMAG_PD =
            Pattern.compile("https?://(?:www\\.|m\\.)?emag\\.ro/(.*?/pd/[A-Za-z0-9]+)/?");
    private static final Pattern PD_CODE = Pattern.compile("/pd/([A-Za-z0-9]+)");

    static final class Result {
        final String code, text, title;
        final double price;

        Result(String code, String text, double price, String title) {
            this.code = code;
            this.text = text;
            this.price = price;
            this.title = title;
        }

        boolean available() {
            return !isUnavailable(code);
        }
    }

    static boolean isUnavailable(String code) {
        return "unavailable".equals(code) || "out_of_stock".equals(code);
    }

    /** eMAG si-a inchis temporar site-ul (ex. pagina de asteptare dinaintea Black Friday). */
    static final class ClosedException extends IOException {
        ClosedException(String pageTitle) {
            super(pageTitle);
        }
    }

    static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "ro-RO,ro;q=0.9");
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            int status = c.getResponseCode();
            if (status == 511) throw new IOException("HTTP 511 (VPN pornit?)");
            if (status != 200) {
                String body = readAll(c.getErrorStream(), 65536);
                if (status >= 500 && body.contains("eMAG")) {
                    Matcher t = TITLE.matcher(body);
                    String title = t.find()
                            ? Html.fromHtml(t.group(1), Html.FROM_HTML_MODE_LEGACY).toString().trim() : "eMAG";
                    throw new ClosedException(title);
                }
                throw new IOException("HTTP " + status);
            }
            return readAll(c.getInputStream(), 16 << 20);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream stream, int max) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < max) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Intoarce null daca pagina nu contine starea produsului (ex. coada de asteptare). */
    static Result parse(String html) {
        String code = null, text = null;
        Matcher m = OFFER.matcher(html);
        if (m.find()) {
            try {
                JSONObject o = new JSONObject(m.group(1));
                if (o.has("code")) code = o.getString("code");
                if (o.has("text")) text = o.getString("text");
            } catch (Exception ignored) {
            }
        }
        if (code == null) {
            Matcher s = SCHEMA.matcher(html);
            if (s.find()) {
                String v = s.group(1);
                text = v;
                code = (v.equals("OutOfStock") || v.equals("SoldOut") || v.equals("Discontinued"))
                        ? "out_of_stock" : v;
            }
        }
        if (code == null) return null;
        double price = -1;
        Matcher p = PRICE_OFFER.matcher(html);
        Matcher p2 = PRICE_ANY.matcher(html);
        try {
            if (p.find()) price = Double.parseDouble(p.group(1));
            else if (p2.find()) price = Double.parseDouble(p2.group(1));
        } catch (NumberFormatException ignored) {
        }
        String title = null;
        Matcher t = TITLE.matcher(html);
        if (t.find()) {
            title = Html.fromHtml(t.group(1), Html.FROM_HTML_MODE_LEGACY).toString().trim();
            if (title.endsWith(" - eMAG.ro")) title = title.substring(0, title.length() - 10).trim();
            if (title.isEmpty()) title = null;
        }
        return new Result(code, text != null ? text : code, price, title);
    }

    /** Gaseste un link eMAG intr-un text (ex. textul trimis prin „Distribuie”) si il curata. */
    static String normalize(String input) {
        if (input == null) return null;
        Matcher m = ANY_URL.matcher(input.trim());
        while (m.find()) {
            String url = m.group();
            if (!url.contains("emag.ro")) continue;
            Matcher pd = EMAG_PD.matcher(url);
            if (pd.find()) return "https://www.emag.ro/" + pd.group(1) + "/";
            return url.replaceAll("[).,;]+$", "");
        }
        return null;
    }

    static String productId(String url) {
        Matcher m = PD_CODE.matcher(url);
        return m.find() ? m.group(1) : Integer.toHexString(url.hashCode());
    }

    static String titleFromUrl(String url) {
        int pd = url.indexOf("/pd/");
        String path = pd > 0 ? url.substring(0, pd) : url;
        String slug = path.substring(path.lastIndexOf('/') + 1).replace('-', ' ').trim();
        if (slug.isEmpty()) return "Produs eMAG";
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }

    static String formatPrice(double v) {
        if (v < 0) return "–";
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(new Locale("ro", "RO")));
        return f.format(v) + " lei";
    }

    private Checker() {
    }
}
