package io.github.leanlauncher;

/**
 * Senderliste mit EPG-Zuordnung.
 *
 * Jede Stream-URL wurde live gegen HTTP 200 + gueltige #EXTM3U-Playlist geprueft.
 * Die EPG-IDs stammen aus der XMLTV-Quelle epgshare01 (DE1) und wurden gegen die
 * dortigen <channel id="...">-Eintraege abgeglichen.
 */
final class Channels {

    private Channels() { }

    static final String[] NAMES = {
            "Das Erste", "ZDF", "3sat", "arte", "KiKA", "ONE",
            "phoenix", "tagesschau24", "ARD alpha", "ZDFinfo", "ZDFneo",
    };

    static final String[] URLS = {
            "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/high/master.m3u8",
            "https://zdf-hls-18.akamaized.net/hls/live/2016501/dach/veryhigh/master.m3u8",
            "https://artesimulcast.akamaized.net/hls/live/2030993/artelive_de/master.m3u8",
            "https://kika-live.ard-mcdn.de/kika/live/hls/de/master.m3u8",
            "https://mcdn-one.ard.de/ardone/hls/master.m3u8",
            "https://zdf-hls-19.akamaized.net/hls/live/2016502/de/high/master.m3u8",
            "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            "https://mcdn.br.de/br/fs/ard_alpha/hls/de/master.m3u8",
            "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
            "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
    };

    /** Kanal-Kennungen in der XMLTV-EPG-Quelle (epgshare01 DE1). */
    static final String[] EPG_IDS = {
            "Das.Erste.de", "ZDF.de", "3sat.de", "ARTE.de", "KIKA.de", "ONE.de",
            "PHOENIX.de", "tagesschau24.de", "ARD.alpha.de", "ZDFinfo.de", "ZDFneo.de",
    };

    /** Senderfarben (an die Sender-CI angenaehert). */
    static final int[] COLORS = {
            0xFF1B4A8F, 0xFFC9570F, 0xFFB01B2E, 0xFFD9541B, 0xFFE88A1A, 0xFF2E6B3E,
            0xFF7A2E86, 0xFF1B4A8F, 0xFF5A2D82, 0xFF1E5C8A, 0xFF8A3A1E,
    };

    static final String EPG_URL = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz";
}
