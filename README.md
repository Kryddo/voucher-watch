# Voucher Watch

**English** · [Română](#română)

A personal Android app that watches products on eMAG (Romania's largest online store) and tells you when they:

- **come back in stock**: an alarm rings until you turn it off (full screen, even when the phone is locked);
- **drop in price**: a notification or alarm, optionally only below a target price.

### Features

- Add products by link, with the **+** button or with **Share** from the eMAG app or a browser.
- **Scheduled start** (e.g. 06:45): until then the app doesn't run at all; an exact system alarm starts it.
- Adjustable **check frequency**: 15 s, 30 s, 1 min or 5 min.
- Recognizes eMAG's waiting page (e.g. before Black Friday) and alerts you when the site **reopens**.
- **SMS on launch**: optionally sends one SMS automatically when eMAG reopens.
- Dark, minimal interface, an event log and an alarm test.

### How it works

| File | Role |
|---|---|
| `MainActivity.java` | main screen, add and settings panels |
| `WatchService.java` | background service that checks the pages and starts the alarm |
| `Checker.java` | downloads the eMAG page and extracts stock, price and title (from schema.org data) |
| `Scheduler.java` | scheduled start (exact system alarm) |
| `Store.java` | list of watched products |
| `Sms.java`, `SmsSentReceiver.java` | the automatic SMS on launch |
| `AlarmActivity.java` | the alarm screen |
| `Ui.java` | colour palette and UI components |

### Build

No Gradle, only the Android SDK tools:

- JDK (uses `/opt/android-studio/jbr`)
- `~/Android/Sdk/build-tools/36.0.0` and `~/Android/Sdk/platforms/android-37.0`

```bash
./build.sh                          # produces VoucherWatch.apk
adb install -r VoucherWatch.apk     # installs / updates the app on the phone
```

On the first run, `build.sh` generates `release.keystore`. **The key is not in the repo.** Back it up separately, because updates must be signed with the same key.

Tested on a Samsung Galaxy S24 (Android 15).

---

## Română

Aplicație Android personală care urmărește produse de pe eMAG și te anunță când:

- **revin în stoc**: pornește o alarmă care sună până o oprești (pe tot ecranul, chiar și cu telefonul blocat);
- **le scade prețul**: notificare sau alarmă, opțional doar sub un preț țintă.

## Funcții

- Produse adăugate după link, cu butonul **+** sau din **Distribuie** în aplicația eMAG/browser.
- **Pornire programată** (ex. 06:45): până atunci aplicația nu rulează deloc; o alarmă de sistem exactă o pornește.
- **Frecvență** reglabilă: 15 s, 30 s, 1 min, 5 min.
- Recunoaște pagina de așteptare a eMAG (ex. înainte de Black Friday) și anunță când site-ul **s-a redeschis**.
- **SMS la lansare**: trimite automat un SMS (o singură dată) când eMAG se redeschide.
- Interfață întunecată, minimalistă; jurnal de evenimente; test de alarmă.

## Build

Fără Gradle, doar cu uneltele din Android SDK:

- JDK (folosește `/opt/android-studio/jbr`)
- `~/Android/Sdk/build-tools/36.0.0` și `~/Android/Sdk/platforms/android-37.0`

```bash
./build.sh                          # produce VoucherWatch.apk
adb install -r VoucherWatch.apk     # instalează / actualizează pe telefon
```

La prima rulare, `build.sh` generează `release.keystore`. **Cheia nu e în repo**: fă-i backup separat,
pentru că actualizările trebuie semnate cu aceeași cheie (altfel trebuie dezinstalată aplicația, cu tot cu date).

## Structură

| Fișier | Rol |
|---|---|
| `MainActivity.java` | ecranul principal, panourile de adăugare / setări |
| `WatchService.java` | serviciul care verifică paginile și pornește alarma |
| `Checker.java` | descarcă pagina eMAG și extrage stocul, prețul, titlul |
| `Scheduler.java` | pornirea programată (alarmă de sistem exactă) |
| `Store.java` | lista de produse urmărite |
| `Sms.java`, `SmsSentReceiver.java` | SMS-ul automat la lansare |
| `AlarmActivity.java` | ecranul de alarmă |
| `Ui.java` | paleta de culori și componentele vizuale |

Testat pe Samsung Galaxy S24 (Android 15).
