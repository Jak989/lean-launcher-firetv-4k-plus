package io.github.leanlauncher;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * EPG mit lokalem Cache.
 *
 * Erste Version hat bei jedem Start 25 MB XML geladen und geparst - auf dem
 * Stick ein Ruckel-Grund. Jetzt:
 *   1. Beim Start sofort den Cache von der Platte lesen (Millisekunden).
 *   2. Im Hintergrund aktualisieren, wenn der Cache aelter als 30 min ist.
 *   3. Nur die 11 gewuenschten Sender werden behalten (~3 KB JSON).
 */
final class Epg {

    private Epg() { }

    static final class Now {
        public String title = "";
        public String next = "";
        public int minutesLeft = -1;
        public long stopMs = 0;
        public long startMs = 0;

        /** Anteil der bereits gelaufenen Sendezeit (0..1), -1 wenn unbekannt. */
        public float progress() {
            if (startMs <= 0 || stopMs <= startMs) return -1f;
            long now = System.currentTimeMillis();
            float p = (float) (now - startMs) / (float) (stopMs - startMs);
            return p < 0 ? 0 : (p > 1 ? 1 : p);
        }
    }

    private static final long REFRESH_MS = 30 * 60 * 1000;
    private static final String FILE = "epg_cache.json";

    private static Map<String, Now> mem;
    private static long loadedAt = 0;

    /** Liefert sofort den Cache (oder leer). Nie blockierend. */
    static synchronized Map<String, Now> cached(Context c) {
        if (mem != null) return mem;
        mem = readCache(c);
        return mem;
    }

    /** Aktualisiert im Hintergrund, wenn noetig. Ruft danach onDone. */
    static void refreshIfStale(final Context c, final Runnable onDone) {
        if (mem != null && System.currentTimeMillis() - loadedAt < REFRESH_MS) return;

        new Thread(new Runnable() {
            public void run() {
                try {
                    long fileAge = System.currentTimeMillis() - new File(c.getFilesDir(), FILE).lastModified();
                    if (fileAge > REFRESH_MS) {
                        Map<String, Now> fresh = fetch();
                        if (!fresh.isEmpty()) {
                            writeCache(c, fresh);
                            synchronized (Epg.class) {
                                mem = fresh;
                                loadedAt = System.currentTimeMillis();
                            }
                        }
                    } else {
                        synchronized (Epg.class) {
                            mem = readCache(c);
                            loadedAt = System.currentTimeMillis();
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    // ---------------- Cache ----------------

    private static Map<String, Now> readCache(Context c) {
        Map<String, Now> out = new HashMap<String, Now>();
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return out;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();

            JSONObject root = new JSONObject(bo.toString("UTF-8"));
            long stamp = root.optLong("ts", 0);
            long now = System.currentTimeMillis();
            // Restzeit neu berechnen, nicht aus dem Cache uebernehmen
            JSONObject chans = root.optJSONObject("channels");
            if (chans != null) {
                for (String id : Channels.EPG_IDS) {
                    JSONObject o = chans.optJSONObject(id);
                    if (o == null) continue;
                    Now n2 = new Now();
                    n2.title = o.optString("title", "");
                    n2.next = o.optString("next", "");
                    long stop = o.optLong("stop", 0);
                    n2.startMs = o.optLong("start", 0);
                    n2.stopMs = stop;
                    if (stop > now) n2.minutesLeft = (int) ((stop - now) / 60000);
                    out.put(id, n2);
                }
            }
            loadedAt = stamp;
        } catch (Throwable ignored) { }
        return out;
    }

    private static void writeCache(Context c, Map<String, Now> data) {
        try {
            JSONObject root = new JSONObject();
            root.put("ts", System.currentTimeMillis());
            JSONObject chans = new JSONObject();
            for (Map.Entry<String, Now> e : data.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("title", e.getValue().title);
                o.put("next", e.getValue().next);
                o.put("stop", e.getValue().stopMs);
                o.put("start", e.getValue().startMs);
                chans.put(e.getKey(), o);
            }
            root.put("channels", chans);

            FileOutputStream fo = new FileOutputStream(new File(c.getFilesDir(), FILE));
            fo.write(root.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) { }
    }

    // ---------------- Download + Parse (streamend) ----------------

    private static final Set<String> WANT = new HashSet<String>();
    static {
        for (String id : Channels.EPG_IDS) WANT.add(id);
    }

    private static Map<String, Now> fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Channels.EPG_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(45000);
        c.setRequestProperty("User-Agent", "LeanLauncher/2.0");

        long now = System.currentTimeMillis();
        Map<String, Now> out = new HashMap<String, Now>();
        Map<String, Long> nextStart = new HashMap<String, Long>();

        InputStream raw = new BufferedInputStream(c.getInputStream(), 16384);
        BufferedReader r = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(raw), "UTF-8"), 32768);

        StringBuilder block = new StringBuilder(1024);
        boolean inBlock = false;
        String line;
        int guard = 0;

        while ((line = r.readLine()) != null) {
            if (++guard > 6000000) break;

            if (!inBlock) {
                int p = line.indexOf("<programme ");
                if (p < 0) continue;
                inBlock = true;
                block.setLength(0);
                block.append(line.substring(p));
            } else {
                block.append('\n').append(line);
            }

            if (block.indexOf("</programme>") >= 0) {
                inBlock = false;
                handle(block.toString(), now, out, nextStart);
                block.setLength(0);
            } else if (block.length() > 65536) {
                inBlock = false;
                block.setLength(0);
            }
        }
        r.close();
        c.disconnect();
        return out;
    }

    private static void handle(String b, long now,
                               Map<String, Now> out, Map<String, Long> nextStart) {
        String ch = attr(b, "channel");
        if (ch == null || !WANT.contains(ch)) return;

        long start = parseTime(attr(b, "start"));
        long stop = parseTime(attr(b, "stop"));
        String title = tag(b, "title");
        if (title.length() == 0) return;

        if (start <= now && now < stop) {
            Now n = new Now();
            n.title = title;
            n.startMs = start;
            n.stopMs = stop;
            n.minutesLeft = (int) Math.max(0, (stop - now) / 60000);
            out.put(ch, n);
        } else if (start > now) {
            Long cur = nextStart.get(ch);
            if (cur == null || start < cur) {
                nextStart.put(ch, start);
                Now n = out.get(ch);
                if (n != null) n.next = title;
            }
        }
    }

    private static String attr(String block, String name) {
        String key = " " + name + "=\"";
        int i = block.indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = block.indexOf('"', s);
        return e < 0 ? null : block.substring(s, e).trim();
    }

    private static String tag(String block, String name) {
        int i = block.indexOf("<" + name);
        if (i < 0) return "";
        int gt = block.indexOf('>', i);
        int end = block.indexOf("</" + name + ">", gt);
        if (gt < 0 || end < 0) return "";
        return block.substring(gt + 1, end).replaceAll("<[^>]+>", "")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim();
    }

    private static long parseTime(String v) {
        if (v == null || v.length() < 14) return 0;
        try {
            String tz = v.length() >= 20 ? v.substring(15, 20) : "+0000";
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT" + tz));
            return f.parse(v.substring(0, 14)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
