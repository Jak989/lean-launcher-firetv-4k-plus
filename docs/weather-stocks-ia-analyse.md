# Informationsgehalt Wetter + Aktien: Ist-Analyse und Umbauplan

Stand: Code gelesen in `~/lean-launcher/app/src/io/github/leanlauncher/`
(WeatherPanel.java, WeatherDetailActivity.java, Weather.java, StocksPanel.java,
StocksActivity.java, Stocks.java, HomeActivity.java).

Basis: 960x540 dp, 3 m Sehabstand, 55 Zoll, D-Pad-Fernbedienung, kein Touch.
Faustregel TV: lesbar ab ~14 sp, bequem ab 16 sp. 12 sp ist Fernsehtest-Grenze,
10 sp ist nicht lesbar. Jede Zeile/Datenpunkt muss in <2 s beantwortbar sein,
sonst ist sie Deko.

---

## Teil A: Wetter

### A0. Die drei Räume, die die App heute vermischt

Eine gute Wetter-App trennt strikt:

| Raum | Frage | Inhalt | Wo |
|---|---|---|---|
| 1 Kachel | "Was erwartet mich heute?" | Jetzt, Urteil, heute Min/Max, Regenrisiko, nächste 12 h | Homescreen, 5 s |
| 2 Tagesdetail | "Wann und wie stark?" | Stundenverlauf als Grafik, Sonne, UV, Böen, Menge in mm | 1 Klick, 20 s |
| 3 Woche | "Was zieh ich am Freitag an?" | 7 Tage, Trend, Wochenaggregat | eigener Abschnitt |

Aktuell sind Raum 2 und 3 auf einer Seite vermischt: 4 Faktenkarten → Kurve →
8 Tageszeilen → 24 h Tabelle. Die Woche steht VOR der Stunde. Falsche Reihenfolge.

### A1. Überflüssig (raus oder umbauen)

1. **"STUNDE FÜR STUNDE" als 7-Spalten-Tabelle, 24 Zeilen.** Das ist eine
   Tabellenkalkulation, nicht eine Wetteransicht. Kein Mensch liest 24 Zeilen
   × 7 Spalten auf 3 m Abstand. Die Spalte "FEUCHTE" ändert sich pro Stunde
   um ±5 % und ist für niemanden entscheidungsrelevant. Wind stündlich ebenfalls
   nicht (Wind wird in Böen relevant, siehe A3).
2. **Grafik UND Tabelle für dieselben 24 Stunden.** Doppelte Information =
   der Nutzer weiß nicht, welches er lesen soll.
3. **"Sonstiges" der Faktenkarten:** "unwahrscheinlich / schwül / kaum" als
   Untertitel ist nett, aber es frisst eine 3-Zeilen-Karte. Ein Wort reicht.
4. **5 Tagesspalten in der Homescreen-Kachel, zusätzlich zu "Jetzt".** Eine
   Spalte ist "Jetzt" mit dem Ist-Wert — der steht daneben schon groß. Heute
   doppelt. Und 5 Tage × Min/Max auf 540 dp Breite = 30 dp pro Spalte = 11 sp
   Beschriftung: grenzwertig am TV.
5. **Kachel-Metazeile "2° / -2° · gefühlt -3° · 3% Regen"** ist eigentlich gut —
   aber sie ist die einzige Stelle mit dem Regenrisiko, und sie ist 12 sp klein
   unter einer 11-sp-Handlungszeile. Wichtiges liegt klein unten.

### A2. Fehlt (entscheidungsrelevant, wird heute nicht gezeigt)

Rang nach tatsächlichem Entscheidungswert für einen Pendler/Fahrradfahrer/Autofahrer
auf einer Tagesansicht (nicht Wunschliste: Priorität = würde der Nutzer dafür
einen Blick opfern?):

| Rang | Datenpunkt | Warum | Open-Meteo-Feld | Heute da? |
|---|---|---|---|---|
| 1 | **Böen (gusts) statt/neben Wind** | Wind 7 km/h sagt nichts, Böe 45 km/h heißt: Fahrrad bleiben lassen, Markise hoch | `hourly=wind_gusts_10m`, `current=wind_gusts_10m` | ✗ |
| 2 | **Niederschlagsmenge in mm/Tag** | 3 % Risiko kann 0,1 mm oder 12 mm heißen. Entscheidung "Route" hängt an der MENGE, nicht der Chance | `daily=precipitation_sum` | ✗ |
| 3 | **Regenzeitfenster** ("Regen 15–18 Uhr") | Der einzige Grund, warum Stundenansicht existiert. Chance 24 h × Symbol beantwortet das nicht ohne Lesen | `hourly=precipitation` aggregiert | ✗ (nur Tabelle) |
| 4 | **UV-Index** | Für Fahrrad/Sport relevant, im November (Laufzeit!) unwichtig, im Sommer wichtig. Apple/"Wetter.de" zeigen ihn. Kosten: 1 Kästchen | `daily=uv_index_max` | ✗ |
| 5 | **Tages-Vortag-Vergleich / Trend** ("2° kälter als gestern") | Apple zeigt "4° niedriger als gestern". Sehr billig, sehr wirksam | lokale Rechnung | ✗ |
| 6 | **Luftqualität (EAQI)** | Open-Meteo `air-quality-api` gratis. Für Asthmatiker/Radsport. Optional, nicht Prio 1 | separate API | ✗ |

Bewertung der in der Frage genannten Punkte:
- **Sonnenstunden:** ja, aber als **Sonnenstunden heute** (z. B. "3 h") – nicht
  pro Tag in jeder Woche. `daily=sunshine_duration`.
- **Taupunkt:** nein. Nerd-Daten, kein Pendler-Entscheid. Feuchtigkeit + gefühlt
  deckt das ab.
- **Luftdruck:** nein auf der Tagesansicht. (Höchstens als Wetterfrosch-Gag.)
- **Sichtweite:** nur bei Nebel-Code (45/48) relevant – dann 1 Zeile, nicht
  Standard.
- **Gefühlte Temp:** ja, aber prominent (siehe B).
- **Luftqualität:** optional, 1 Zeile unten.

### A3. Stundenansicht: wie es Profis machen

**Apple Weather (iOS/iPadOS/Weather-App):** Die Stundenleiste ist **horizontal
scrollbar, ~10–12 Stunden**, pro Spalte **drei Elemente**: Uhrzeit oben (z. B.
"Jetzt", "13"). Stunde, dann Temperatur-Zahl, dazwischen ein **Regen-Chance-Balken /
Icon für Niederschlag**; ein kleines Balken-/Zahlenband **unter der Temperatur**
zeigt die Regenwahrscheinlichkeit. Uhrzeit und Temperatur sind feste Spalten;
**Wind, Feuchte, UV, Sicht, Luftdruck stehen NICHT in der Stundenleiste**, sondern
über ein **Umschaltmenü oben rechts** ("Temperatur / Niederschlag / Wind /
Feuchte / UV / Sicht / Luftdruck"). Ein Tap wechselt *die ganze Grafik* auf diese
Kurve. Kein Lesen von Zahlenwänden — die Grafik *ist* die Daten.

**Google Wetter:** Stundenleiste mit 6–8 Spalten, **nur Temperatur + Symbol +
Regen-%**; der 24-h-Verlauf liegt als **Linien-/Flächendiagramm** dahinter (mit
Regenbalken darunter), nicht als Tabelle. Niederschlag als farbige Fläche.

**Windy / CARROT / wetter.com / WetterOnline:** zeigen die Stunde ebenfalls als
**Grafik + horizontales Scroll-Band**, nicht als Liste. WetterOnline hat eine
Tabelle (Tab-Reiter "Tabelle") – aber als *zweite* Ansicht, versteckt.

**Fazit:** Die Tabelle ist nicht "falsch", aber sie ist 1998. Heute:
**eine Grafik + horizontales Stundenband mit 3 Spalten**, Details über Optionen.
Und: Die Kachel ist 5 s, die Detailseite 20 s — also Detailseite:
1 Grafik (groß), 1 Stundenband (klein), 1 Faktenzeile.

Konkreter Umbau (Reihenfolge): Grafik → Stundenband → Faktenstreifen → Woche.

### A4. Die wichtigste fehlende Information (Pendler/Fahrrad/Auto)

Eindeutig: **Niederschlagsmenge in mm + Böen + Zeitfenster**, in dieser Kombi.
"3 % Regen" ist die am wenigsten aussagekräftige Zahl im ganzen Panel, weil sie
gerade im verrauschten Open-Meteo-Modell (Ensemble ohne Kalibrierung) fast
immer unter 30 % landet. Ein Fahrradfahrer entscheidet über **mm** ("1 mm in
einer Stunde = nass, 0,1 mm = trocken") und **Böe** ("45 km/h = Gegenwind
auf dem Rückweg"). Beides fehlt.

---

## Teil B: Aktien

### B0. Was heute konkret schlecht ist

1. **"MSCI World 187,43 / -3,34 %" zeigt den iShares-MSCI-World-ETF URTH in USD.**
   Der Index selbst (MSCI World in EUR) steht bei einem deutschen Anleger
   nicht auf dem Schirm — er sieht einen ETF-Kurs in Dollar, den er nie kauft
   (er kauft z. B. einen LU/XEON-ETF in EUR). Schlimmer: **keine Währung, kein
   Handelsplatz, kein Kurs-Stichtag**. "187,43" ohne Währung ist keine
   Information. Ein -3,34 % bei einem US-ETF enthält zusätzlich EUR/USD-Bewegung
   — ein deutscher Anleger liest es als "mein Portfolio fällt" und bekommt die
   falsche Zahl (seine EUR-Version kann flach oder im Plus liegen).
2. **"Heute" ist eine Lüge um 3 Uhr nachts.** Yahoo liefert `regularMarketPrice`
   des letzten Handelstags; die App zeigt es als "HEUTE" mit Mini-Kurve und
   Prozentwert wie live. Kein "Geschlossen", kein "Stand 22:00". Da stimme A3.
3. **Es fehlt der Vortagesschluss / der absolute Betrag.** Ohne Vortagesschluss
   kann der Nutzer "-3,34 %" nicht auf den Kurs zurückrechnen. Aktuelle
   Apps zeigen beides: "187,43" und "-6,48 (-3,34 %)".
4. **Nur Prozentwerte in den Nebenkacheln** — "Gold 0,12 %", "Bitcoin -1,20 %":
   prozentual vergleichbar, aber ohne Kurs und ohne absolute Tagesveränderung
   sagt es nichts über Größenordnung. Zumindest bei Gold/Bitcoin (sehr
   unterscheidbare Skalen) sinnvoll den Kurs.
5. **MSCI World als Hauptwert, S&P 500/DAX/Nasdaq als Stützräder:** Drei
   US-Indizes plus Gold, Bitcoin, EUR/USD — kein einziger europäischer
   Einzelwert/Breitmarkttitel, kein Bund-Future, kein Öl. Die Auswahl ist
   "Homepage von Yahoo Finance 2014", nicht "deutscher Privatanleger 2026".
6. **Sparkline ohne Referenz:** Eine Mini-Kurve, die auf 0..1 normiert ist,
   sagt "irgendwie gefallen". Wo ist die Nulllinie (Vortagesschluss)? Ohne
   sie ist die Kurve nicht falsch, aber bedeutungslos.
7. **Kein Börsenstatus:** US-Markt schließt in München 22 Uhr, aber im November
   (MEZ) London 17:30, Xetra 17:30. Wer um 18 Uhr öffnet, sieht "DAX -0,4 %",
   als wäre es live. Das ist ein Vertrauensbruch und bei Aktien schlimmer als
   bei Wetter (Wetter ist gefroren, Aktien sind handelbar).
8. **Keine Währungs-Info überhaupt.** Die App hat `q.currency` geladen, zeigt es
   aber nirgendwo (auch nicht im Detail). Das ist der einfachste Fix überhaupt.

### B1. Kennzahlen, die auf einen Blick relevant sind

Für Privatanleger auf einer TV-Kachel (begrenzt auf 5 s):

| Rang | Kennzahl | Apple Stocks | Yahoo/Google | TradingView | heute da? |
|---|---|---|---|---|---|
| 1 | **Kurs** | ✓ | ✓ | ✓ | ✓ |
| 2 | **Tagesveränderung in %** | ✓ | ✓ | ✓ | ✓ |
| 3 | **Tagesveränderung absolut** | ✓ | ✓ | ✓ | ✗ |
| 4 | **Vortagesschluss (Referenz)** | ✓ (Kurve) | ✓ | ✓ | ✗ |
| 5 | **"Geschlossen / Stand HH:MM"** | ✓ (Marktzustand) | ✓ | ✓ | ✗ (nur Detailseite "Stand") |
| 6 | **Währung** | ✓ ("USD") | ✓ | ✓ | ✗ |
| 7 | Handelsplatz | – (Börse) | ✓ (Yahoo) | ✓ | ✗ |
| 8 | 52-W-Hoch/Tief | ✓ | ✓ | ✓ | ✗ |
| 9 | Volumen | – | ✓ | ✓ | ✗ |
| 10 | Eröffnung/Extrem / Tagesspanne | – | ✓ | ✓ | ✗ |

Reihenfolge im Kopfbereich (wie Apple Stocks): **Name → Kurs (größte Zahl) →
absolut + Prozent in einer Zeile → Zeit/Status**. 52-W und Volumen sind
Detailseiten-Material, nicht Kachel.

### B2. "MSCI World als URTH in USD" — irreführend? Ja.

Warum:
- URTH ist ein **US-ETF auf den MSCI World**, in USD gehandelt an NYSE Arca.
  Der deutsche Privatanleger kennt keine 187,43 als "MSCI World"-Stand; er kennt
  entweder den **MSCI World Index in Punkten (z. B. 3.700)** oder einen
  **EUR-ETF-Kurs (z. B. 88 €)**. 187,43 ist dritte Zahl.
- Der Prozentwert enthält **USD/EUR** — bei einem fallenden USD fällt URTH,
  obwohl der MSCI World in EUR steigt. Also ist "-3,34 %" hier auch inhaltlich
  falsch für einen EUR-Anleger.
- Die App hat es genau deshalb gemacht: der Index-Ticker (^990100-USD-STRD) liefert
  bei Yahoo Müll (-50 %). Diesen Kompromiss muss man **im UI transparent machen**,
  sonst verkauft man dem Nutzer eine Zahl, die er nicht versteht.

Empfehlung (bezahlbar, mit Yahoo-chart-API):
a) **URTH behalten**, aber Label "MSCI World (URTH · USD)" und Währungszeile.
b) Zusätzlich deutsches Pendant in EUR holen: `^GDAXI` (DAX) oder besser
   ein EU-ETF wie `IWDA.AS` (iShares Core MSCI World, Amsterdam, EUR) oder
   `XWD.TO` (Kanada, CAD). Yahoo liefert `IWDA.AS` in EUR — d. h. der Nutzer
   sieht seine Währung. **Empfehlung: URTH (USD) *und* IWDA.AS (EUR) oder
   einfach DAX als Hauptwert.** Aber deutscher Anleger sollte einen **EUR-Wert
   in EUR** sehen.
c) Handelsplatz nie unbedingt anzeigen, aber als Werkzeugzeile "URTH · NYSE Arca · USD"

### B3. "Markt geschlossen" — wie Profis das zeigen

- **Apple Stocks:** Zeigt den aktuellen Kurs und darunter **"Closed"** bzw.
  "As of [time]" oder den letzten Handelstag + Uhrzeit. Der Name des Marktes
  steht im Chart-Kopf. Bei geschlossenem Markt sind Kurse/Sparkline in
  gedämpftem Grau (nicht grün/rot) und der Prozentwert bekommt den Zeitstempel,
  nicht "heute".
- **Yahoo Finance:** Badge "At close" / "Market closed" und Uhrzeit in der
  Zeile; bei Nachmarkt-Bewegung explizit "After hours".
- **Google Finance:** "As of {Date} {Time}".
- **TradingView:** Status-Punkt + "Market closed".

Wichtig: **geschlossen heißt nicht "falsch", nur "eingefroren"**. Der Fehler
ist nicht die alte Zahl, sondern sie ohne Zeitstempel als live auszugeben.

Kurzfassung des Statusfeldes: aus `meta` der chart-API gibt es
`marketState` / `currentTradingPeriod` und `regularMarketTime` — damit kann
man "Offen bis 17:30" / "Geschlossen · Stand 17:30" / "Vorbörse" ableiten.

### B4. 5–8 Werte für einen deutschen Privatanleger

Vorschlag (nicht die aktuelle Auswahl), geordnet nach Relevanz:

| # | Wert | Ticker | Währung | Warum |
|---|---|---|---|---|
| 1 | **DAX** | `^GDAXI` | EUR | Heimatmarkt, Xetra 09–17:30, einziger Wert mit "live" beim Blick am Abend |
| 2 | **MSCI World / URTH (oder IWDA.AS)** | `URTH` / `IWDA.AS` | USD / EUR | Weltportfolio, zentrale Kennzahl |
| 3 | **S&P 500** | `^GSPC` | USD | globaler Maßstab, US-Zeit (15:30–22:00 MEZ, im Winter 15:30–21:00 MEZ) |
| 4 | **EUR/USD** | `EURUSD=X` | – | Wechselkurs verzerrt *jede* DE-Anlage in USD |
| 5 | **Gold (XAU/USD oder GC=F)** | `GC=F` | USD/Unze | Krise/Tickermetall, deutsche Anleger sehr präsent |
| 6 | **Bund-Future oder EuroStoxx 50** | `^STOXX50E` | EUR | europäische Alternative zum US-Fokus |
| 7 | **Bitcoin** | `BTC-USD` | USD | volatil, aber beliebt; **immer mit USD kennzeichnen** |
| 8 | *(optional)* Nasdaq / Öl (WTI) | `^IXIC` / `CL=F` | USD | nur wenn Platz |

Empfohlen: **8 Slots = DAX, MSCI World/URTH, S&P 500, EUR/USD, Gold, EuroStoxx, Bitcoin, (Nasdaq)**. Und: **Hauptwert sollte DAX oder MSCI World in EUR sein**, nicht URTH-USD, weil der Prozentwert dann stimmt.

### B5. Mehrere Werte auf engem Raum ohne Tabellenkalkulation

Das heutige Raster (2 Zeilen à 3: "S&P 500 + 0,4 %") ist *eine* Tabelle. Besser:

- **Kachel-Hierarchie statt Raster:** 1 Hauptwert (groß, Kurve) + 5–6 Werte als
  **Chips/Zeilen mit Mikro-Sparkline** — jede noch mit eigener Kurve, nur sehr
  klein (24 dp hoch).
- **Vertikale Liste statt Grid:** Apple Stocks macht das so — jede Zeile 32–40 dp,
  Name links, Sparkline mittig (60–80 dp breit), Kurs + % rechts. Das liest man
  in einem Wisch, ohne Zickzack-Suche. Auf 240 dp Breite ist Grid quälend; eine
  Liste mit 7 Zeilen à 16 dp ist auch machbar.
- **Farbe nur wo nötig:** grün/rot als einzige Farbe; keine Flächen.
- **Zwei Zahlen pro Zeile:** "187,43" und "-3,34 %" auf einer Zeile, Prozent
  rechtsbündig — wie Apple.

---

## Teil C: Konkrete Umsetzung (mit Zahlen), priorisiert nach Wirkung

### Wetter — Priorität 1 (höchste Wirkung, wenig Arbeit)

**W1. Woche und Stunde tauschen + Stundenverlauf nach vorne.** In
`WeatherDetailActivity.buildUi()`: `addHourTable()` raus, stattdessen
Grafik+Stundenband **direkt nach den Faktenkarten**, Woche danach. 5 Zeilen
scrollen statt 30.

**W2. Stundentabelle 24×7 → Stundenband 12 Spalten × 3 Werte.**

| Element | Wert jetzt | Vorschlag |
|---|---|---|
| Spalten | 7 (Zeit, Symbol, Temp, mm, Risiko, Wind, Feuchte) | **3** (Zeit, Symbol+Temp, Regen-% als Balken) |
| Zeilen | 24 | **1 Zeile** (horizontal scroll: 12 sichtbar, gesamt 24) |
| Spaltenbreite | 64–84 dp | **52 dp** × 12 = 624 dp (passt in Detailpage mit 56+56 Padding: 848 dp Innenbreite) |
| Höhe | 24 × ~22 dp = 528 dp | **84 dp** |
| Schrift Zeit/Temp | 13/14 sp | **14/18 sp** |
| Zusätzliches | – | **Regenmenge als Balken** (Höhe = mm) unter Temp, **Böe** als Zahl nur in der 3-Stunden-Unterzeile |

Das ist der ganze Stundenblock: 12 Spalten sichtbar, 24 Stunden per Scroll. Details
(Wind, Feuchte, mm-Zahlen) nur für die angetippte Stunde (Focus) unten als
Zeile: "14 Uhr · 3,4 °C · 1,2 mm · Böe 45 km/h · 87 %" → 1 Zeile 16 sp statt 24×7.

**W3. Faktenkarten: 4 → 3 + Böen/Menge.**

| Karte | Wert | Untertitel |
|---|---|---|
| REGEN | "1,2 mm · 3 %" | "bis 17 Uhr" (Zeitfenster aus Stundenwerten) |
| WIND | "7 km/h · Böe 36" | "kaum" / "auffrischend" |
| SONNE | "07:31–16:28" | "3 h Sonne" (sunshine_duration) |
| UV | "1 (niedrig)" | oder ganz lassen im Winter |

Reihenfolge geändert: Regenmenge vor Chance, Böe nach Wind. 4 Karten à 200 dp breit
(864/4 − 8), Höhe 72 dp, Wert 20 sp, Untertitel 12 sp.

**W4. Grafik: Nulllinie + Beschriftung + Regenbalken.** 120 dp hoch, 24 Stützpunkte,
Y-Achse mit 2 Labels (Min/Max), Regen als halbtransparente Fläche (blau) unter der
Kurve. Also wie Google/Apple.

### Wetter — Priorität 2

**W5. Kachel: 5 Tage → 3 Tage + "Heute/Heute Abend".** 540 dp / 7 = 77 dp pro
Spalte ist gut, aber 5 Tage × 13 sp Zahlen sind am TV die untere Grenze.
Vorschlag: **Heute | Morgen | Übermorgen | Sa | So** (5) *oder* besser
**Heute-Abend | Morgen | Übermorgen** (3) mit größeren Zahlen (16 sp). Die
Woche steht auf der Detailseite. Achtung: wenn die Kachel breiter wird (oder der
Nutzer 5 Tage als "grob" mag), W1 zuerst.

**W6. Handlungszeile als wichtigstes Element aufwerten:** 15 sp (aktuell
unten 11 sp). Sie ist die einzige Antwort auf "was zieh ich an".

**W7. "kälter/wärmer als gestern"** an die Metazeile hängen: `2° / -2° · gefühlt -3° · 2° kälter als gestern · 3 %`.

### Aktien — Priorität 1

**S1. Marktstatus + Zeitstempel in Kachel und Detailseite.**
- Kachel: Kopfzeile "MÄRKTE" → "MÄRKTE · geschlossen" (9 sp) oder unter Kurs.
- Detailseite: "Stand 17:30 · geschlossen" statt "Stand 18:42 Uhr" (was heute die
  Ladezeit zeigt, nicht die Kurszeit!). **Das ist ein Bug:** `StocksActivity.fill()`
  setzt `new Date()` — also die Zeit des Abrufs, nicht `regularMarketTime`.
  Wenn der Cache 10 min alt ist, lügt die App um 10 min. `Stocks.Quote` braucht
  ein `long marketTime`-Feld aus `meta.regularMarketTime * 1000`.
- Anzeigeformat: "Geschlossen · Di, 22:00" / "Offen · 15:32" / "Vorbörse".

**S2. Absolute Veränderung überall.** `Quote` hat `open` und `close` (ungefüllt!).
Ergänzen: `prevClose` aus `chartPreviousClose`, dann
`changeAbs = price - prevClose`. Anzeige: `-6,48 (−3,34 %)`.
- Wichtig: `fmt()` für große Werte formatiert mit `replace(',', '.')` →
  `String.format(Locale.GERMANY, "%,.0f").replace(',', '.')` macht aus "12.345"
  US-Format, nicht DE. **Bug:** für Kurse ≥10000 wird `,`→`.` getauscht, aber
  `Locale.GERMANY` nutzt schon `.` als Tausendertrenner → Doppel-Murks. Einfach
  `String.format(Locale.GERMANY, "%,.0f")` (liefert "12.345").

**S3. Währung anzeigen.** `Quote.currency` ist geladen. Anzeige:
- Hauptwert: "187,43 USD" (oder kleiner Index: "USD" 11 sp unter/neben).
- Jede Nebenwert-Zeile: rechts "USD" in 10 sp grau, wenn abweichend von EUR.

**S4. Sparkline mit Nulllinie.** Die Kurve normiert auf min/max → Vortagesschluss
ist nicht erkennbar. Besser: min/max so setzen, dass `prevClose` in der Mitte
liegt (symmetrisch), plus dünne gestrichelte Linie bei prevClose. Damit sieht
man "Kurs liegt X % unter Vortag" sofort.

**S5. Werteliste ändern (B4).** Reihenfolge: **DAX (EUR), MSCI World (URTH·USD),
S&P 500, EUR/USD, Gold, EuroStoxx 50, Bitcoin, Nasdaq**. Hauptwert DAX oder
MSCI World (EUR-Variante `IWDA.AS`).

### Aktien — Priorität 2

**S6. Detailseite: 52-W-Bereich + Tagesspanne.** Pro Zeile 1 zusätzliche
Mini-Balken "Tagesspanne" (offen–hoch–tief) — wie Apple Stocks' Chart-Kopf,
aber nur wenn Platz (1 Zeile 16 dp). Der Nutzer sieht sofort: "Kurs nahe
Tageshoch".

**S7. Detailseite: "KURS" mit 2 Nachkommastellen.** Heute `%.2f` für Kurse
100–10000 (DAX 18.742 → "18742.12"? nein: ≥10000 → `%,.0f` = "18.742").
Für DAX/Indizes: 0 Nachkommastellen ist richtig; für Gold 2; für EUR/USD 4;
für Bitcoin 0/2. Das ist bereits grob richtig, aber `fmt` wählt nach Höhe,
nicht nach Instrument. Besser `Quote.decimals` pro Instrument.

### Layout-Zahlen für den Umbau (960×540)

Detailseite Wetter (innen 848 dp nach 56/56-Padding):

| Block | Höhe |
|---|---|
| Kopf "WETTER · MÜNCHEN" | 16 |
| Ist-Zeile (Icon 84 + Wert 40 sp) | 96 |
| Faktenstreifen (4 Karten) | 72 |
| Stundengrafik | 140 |
| Stundenband (12 Spalten) | 84 |
| "DIE NÄCHSTEN TAGE" + 7 Zeilen à 36 | 280 |
| **Summe** | **688 → 148 dp Scroll** |

Aktien-Kachel 240×128 (innen 208 nach 16/16):

| Block | Höhe |
|---|---|
| "MÄRKTE · geschlossen" | 14 |
| Name + Status (15 sp) | 20 |
| Kurs 30 sp | 36 |
| Kurve 38 (mit Nulllinie) | 38 |
| 3 Zeilen à 22 = 6 Werte | 66 |
| **Summe** | **174 > 128** |

→ Die Kachel ist **zu klein für 6 Nebenwerte plus Kurve** (Ist 174 von 128 dp,
56 dp Überhang — deshalb quetscht der Code die Zeilen auf `72f*dp/rows` zusammen
und 22 dp Zellen in 24-dp-Zeilen; das ist der Grund, warum die Kachel gedrängt
wirkt). Lösung: Kurve
38 → 32 und **pro Zeile 2 Werte** (Name + % + Kurs in 2 Zeilen), also 3 Zeilen
à 30 dp = 90: 14+20+36+32+90 = 192 > 128. Nein.
→ **Nur 4 Nebenwerte** in 2 Zeilen à 22 (44): 14+20+36+32+44 = 146. Immer noch >128.
→ Konkret: Kurs 28 sp statt 30 (+30 dp Höhe), Kurve 0 (Sparkline in Detailseite),
**keine Sparkline auf Kachel**: 14+20+30+2×22 = 108 dp — passt in 128 mit
12 dp Luft. Oder 5 Werte in 3 Zeilen.
**Empfehlung:** Kachel = 1 Hauptwert (Name 14 sp, Kurs 28 sp, % 14 sp, Minikurve
28 dp) + **4 Nebenwerte in 2×2** (Name 11 sp, % 12 sp). Das ist 14+20+30+28+
2×22+8 = 136 → nahe 128, Kurve 24 dp → 132. Noch 4 dp zu viel: Padding
12/10 → 8/8. Passt.

---

## Kurzfassung: die 10 Dinge, die ich zuerst mache

1. Stunden-Tabelle (24×7) streichen → 12-Spalten-Band mit 3 Werten + Grafik.
2. Böen und mm/Tag ergänzen — das ist der eigentliche Informationsgehalt.
3. Woche nach unten, Stunde nach oben.
4. Marktstatus "geschlossen/offen" + echte Kurszeit statt Abrufzeit.
5. Absolute Veränderung + Vortagesschluss überall.
6. Währung (USD/EUR) an jeden Wert.
7. URTH-USD nicht als "MSCI World" ohne Kontext; EUR-Pendant oder Label.
8. Werteliste: DAX (EUR) als Hauptwert, EuroStoxx statt Nasdaq.
9. Handlungszeile 11 → 15 sp, "kälter als gestern" dazu.
10. Sparkline mit Nulllinie (Vortagesschluss), sonst keine Kurve auf der Kachel.
