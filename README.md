# SatoshiWatch

Plně anonymní **watch-only monitor bitcoinových adres** pro Android. Pasivně hlídá
trezorové adresy (Coldcard, Trezor, …) a okamžitě upozorní na příchozí i odchozí
transakce — bez privátních klíčů, bez účtů, bez telemetrie.

## 📥 Stažení

**[⬇️ SatoshiWatch-1.1.0-debug.apk](https://github.com/Topkadas/SatoshiWatch/raw/main/dist/SatoshiWatch-1.1.0-debug.apk)** (36 MB, Android 8.0+)

Instalace: stáhni APK do telefonu → otevři → povol instalaci z neznámých zdrojů.
Jde o debug build podepsaný vývojovým klíčem; ověřený build si můžeš kdykoli
zkompilovat sám ze zdrojáků (`./gradlew assembleDebug`) — proto je projekt open source.

Novinky v 1.1.0:
- **Widget na plochu (4×2):** až 3 sledované adresy se zůstatkem a posledním
  pohybem (↑/↓ + relativní čas), tlačítko ručního obnovení; překresluje se po
  každé synchronizaci.
- **Aktualizace přímo z GitHubu:** Nastavení → Aktualizace aplikace. Ruční
  kontrola `dist/version.json`, stažení přes aplikačního klienta (respektuje
  Tor proxy) a ověření SHA-256 před instalací. Žádné automatické pingování.

## Bezpečnostní principy

| Princip | Implementace |
|---|---|
| Nulová registrace | Žádné přihlašování, e-maily ani identifikátory zařízení |
| Lokální úložiště | Room + **SQLCipher** (AES-256), passphrase náhodná, zabalená klíčem v **Android KeyStore** (`DatabaseKeyManager`) |
| Šifrovaná nastavení | `EncryptedSharedPreferences` (AES-256-GCM/SIV) |
| Síťové soukromí | Přímé spojení na konfigurovatelný uzel; výchozí `https://mempool.space/api` + `wss://mempool.space/api/v1/ws`; volitelná **SOCKS5 proxy** (Orbot/Tor) — DNS se řeší až v proxy (OkHttp SOCKS používá unresolved adresy, žádný DNS únik) |
| Nulová telemetrie | Žádný Firebase/Analytics/Sentry; jediné závislosti třetích stran: OkHttp, Retrofit, SQLCipher, ZXing. **Záměrná odchylka od zadání:** místo ML Kit se QR dekóduje přes ZXing core — ML Kit SDK odesílá diagnostiku na Google (datatransport/Firelog) mimo aplikační OkHttp klient, tedy i mimo Tor proxy, což je v přímém rozporu s principem nulové telemetrie |
| Zálohy | `allowBackup=false` + `data_extraction_rules` — nic neopouští zařízení |
| Cleartext | Zakázán; výjimka jen pro `.onion` (Tor onion služby mempool uzlů běží na http) |
| Zamčená obrazovka | Notifikace mají `publicVersion` bez částek a adres |

## Architektura

Clean Architecture + MVVM, DI přes Hilt, Kotlin Coroutines + Flow.

```
core/
  validation/BitcoinAddressValidator.kt   – offline validace P2PKH, P2SH, Bech32(m) vč. checksumů
  crypto/DatabaseKeyManager.kt            – KeyStore wrapping SQLCipher passphrase
data/
  local/    – Room entity, DAO, AppDatabase (SQLCipher)
  remote/   – MempoolApiService (Retrofit), MempoolWebSocketListener (OkHttp WS),
              NetworkClientProvider (proxy, přestavba klientů při změně nastavení)
  settings/ – SettingsRepository (EncryptedSharedPreferences → StateFlow)
  repository/WatchRepository.kt           – jediný zdroj pravdy, deduplikace notifikací
domain/
  TransactionParser.kt                    – směr (vin ⇒ ODCHOZÍ / vout ⇒ PŘÍCHOZÍ) a změna bilance
worker/TransactionCheckWorker.kt          – periodický sken (WorkManager, ≥15 min)
service/TransactionWatchService.kt        – foreground služba, trvalý WebSocket, reconnect s backoffem
notifications/NotificationHelper.kt       – 3 kanály (odchozí=kritický, příchozí, služba)
ui/                                       – Compose: Dashboard, AddAddress (+QR sken), Settings
```

### Hybridní monitorování
- **WorkManager** (výchozí zapnuto, 15/30/60 min) — úsporné, přežívá reboot.
- **Foreground služba** (volitelná) — WebSocket `track-addresses`, push
  `address-transactions` / `multi-address-transactions`; nový blok navíc spouští
  REST sweep, který spolehlivě zachytí potvrzení i RBF/reorg.
- Obě cesty sdílí Room DB ⇒ jedna transakce = jedna notifikace (mempool fáze
  a potvrzení zvlášť). Historie při přidání adresy se importuje **bez** notifikací.

## Sestavení

1. Android Studio (Hedgehog+) → *Open* → složka projektu; Gradle wrapper se dogeneruje
   (`gradle wrapper` v kořeni, nebo nechte AS). JDK 17.
2. `./gradlew :app:assembleDebug` / `:app:testDebugUnitTest` (BIP-173/350 vektory).
3. minSdk 26, targetSdk 34.

## Tor / vlastní uzel

- **Orbot**: Nastavení → SOCKS5 proxy → `127.0.0.1:9050`; URL lze přepnout na onion
  adresu mempool instance (`http://…onion/api`).
- **Vlastní uzel** (Umbrel, myNode, RaspiBlitz s mempool aplikací):
  `http://umbrel.local:3006/api` — pozor, mimo `.onion` vyžaduje https.
