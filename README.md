# Voucher Watch

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
