package io.github.leanlauncher;

/**
 * Nachrichten-Radios. Alle URLs wurden live gegen HTTP 200/206 + audio/mpeg geprueft.
 */
final class Radio {

    private Radio() { }

    static final String[] NAMES = {
            "Deutschlandfunk",   // reine Nachrichten
            "B5 aktuell",        // BR24
            "Inforadio rbb",
            "SWR Aktuell",
            "DLF Nova",
    };

    static final String[] URLS = {
            "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3",
            "https://dispatcher.rndfnk.com/br/br24/live/mp3/mid",
            "https://dispatcher.rndfnk.com/rbb/inforadio/live/mp3/mid",
            "https://liveradio.swr.de/sw282p3/swr3/play.mp3",
            "https://st03.sslstream.dlf.de/dlf/03/128/mp3/stream.mp3",
    };

    static final int[] COLORS = {
            0xFF1E5C8A, 0xFFB01B2E, 0xFF7A2E86, 0xFF2E6B3E, 0xFFC9570F,
    };
}
