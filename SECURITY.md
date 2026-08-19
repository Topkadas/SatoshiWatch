# Security & Transparency / Bezpečnost a transparentnost

**[English](#english)** | **[Česky](#česky)**

---

## English

### Who wrote this app

This application was **designed and written by Claude, an AI assistant by
Anthropic** (model Claude Fable 5), working under the direction of the
repository owner in August 2026. Every line of source code in this repository
was produced in that AI-assisted development process and is published here in
full for anyone to inspect.

The development included automated multi-agent reviews: the address-validation
cryptography (Base58Check, Bech32/Bech32m) was verified against the official
BIP-173 and BIP-350 test vectors with an independent Python re-implementation,
the mempool.space REST/WebSocket contract was checked against upstream
documentation, and dedicated security, Android-API and integration audits were
run and their findings fixed before each release.

**Honest framing of responsibility:** an AI cannot carry legal responsibility,
and no honest software author can guarantee the absence of all bugs. This
software is provided **“as is”, without warranty of any kind**. What we offer
instead of promises is full transparency: open source, reproducible checks
(below), and a design where the app never touches anything it could steal.

### Why the app cannot steal your bitcoin

SatoshiWatch is **watch-only by design**. There is no code path that accepts,
stores, or transmits private keys, seeds, or passphrases — there is nowhere to
even type them in. The app only ever sends bitcoin *addresses* (public
information) to the node **you** configure. The worst thing a malicious fork of
this app could do is reveal *which addresses you watch* — which is why Tor
routing and self-hosted nodes are first-class features.

### Verified properties of the released APK (v1.2.0)

Checked directly on the released binary, not just the source:

**Permissions embedded in the APK** (nothing else):

```
INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC,
CAMERA, RECEIVE_BOOT_COMPLETED, REQUEST_INSTALL_PACKAGES,
WAKE_LOCK + ACCESS_NETWORK_STATE (added automatically by WorkManager)
```

No access to contacts, SMS, location, files, microphone, or device accounts.

**Every network URL embedded in the compiled bytecode** — exactly three are
functional:

| URL | Purpose |
|---|---|
| `https://mempool.space/api/` | default node (user-replaceable) |
| `wss://mempool.space/api/v1/ws` | default realtime feed (user-replaceable) |
| `https://raw.githubusercontent.com/Topkadas/SatoshiWatch/main/dist/version.json` | manual update check only |

The remaining strings found in the scan are inert XML namespaces and
documentation links inside standard AndroidX libraries. **There are no
analytics, tracking, or crash-reporting endpoints.** Google ML Kit was
deliberately rejected during development because its SDK uploads diagnostics
outside the app’s HTTP client (and therefore outside Tor); QR decoding uses the
fully offline ZXing library instead.

### Reproduce the checks yourself

```bash
# permissions in the APK
aapt2 dump badging SatoshiWatch-1.2.0-debug.apk | grep uses-permission

# every URL embedded in the bytecode
unzip -o SatoshiWatch-1.2.0-debug.apk "classes*.dex" -d dex/
python3 -c "
import re, glob
urls = set()
for p in glob.glob('dex/*.dex'):
    urls |= {m.group().decode() for m in re.finditer(rb'(?:https?|wss?)://[a-zA-Z0-9./_\\-]+', open(p,'rb').read())}
print('\n'.join(sorted(urls)))"

# or simply build the APK from this source tree and compare behaviour
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

### Update integrity

In-app updates are checked manually only (no automatic phoning home), fetched
through the app’s own HTTP client (honouring the Tor proxy), and verified
against a **SHA-256** digest from `dist/version.json` before installation.
Android additionally refuses any update APK that is not signed with the same
key as the installed version, so a compromised repository alone cannot push a
malicious update to existing installs.

### Known limitations

- No independent third-party security audit has been performed.
- Releases are currently signed with a development (debug) key.
- Using the default public mempool.space node reveals your IP and queried
  addresses to that server — use Orbot/Tor or your own node for full privacy.

### Reporting a vulnerability

Please open a GitHub issue (or contact the repository owner) with details.
Reports that include a reproduction are fixed with priority.

---

## Česky

### Kdo aplikaci napsal

Tuto aplikaci **navrhl a naprogramoval Claude, AI asistent společnosti
Anthropic** (model Claude Fable 5), pod vedením správce tohoto repozitáře
v srpnu 2026. Veškerý zdrojový kód vznikl v tomto AI-asistovaném procesu a je
zde v úplnosti zveřejněn k volné kontrole.

Součástí vývoje byly automatizované multi-agentní revize: kryptografie validace
adres (Base58Check, Bech32/Bech32m) byla ověřena proti oficiálním testovacím
vektorům BIP-173 a BIP-350 nezávislou reimplementací v Pythonu, kontrakt
mempool.space REST/WebSocket API byl zkontrolován proti dokumentaci a proběhly
samostatné audity bezpečnosti, Android API a integrace — nálezy byly opraveny
před každým vydáním.

**Poctivé vymezení odpovědnosti:** AI nemůže nést právní odpovědnost a žádný
poctivý autor softwaru nemůže zaručit nepřítomnost všech chyb. Software je
poskytován **„tak, jak je“, bez jakýchkoli záruk**. Místo slibů nabízíme úplnou
transparentnost: otevřený kód, reprodukovatelné kontroly (níže) a návrh, při
kterém se aplikace nikdy nedotkne ničeho, co by šlo ukrást.

### Proč aplikace nemůže ukrást vaše bitcoiny

SatoshiWatch je **výhradně watch-only**. Neexistuje žádná cesta kódem, která by
přijímala, ukládala nebo odesílala privátní klíče, seed či passphrase — není je
ani kam zadat. Aplikace odesílá pouze bitcoinové *adresy* (veřejný údaj), a to
na uzel, který si **sami** nastavíte. To nejhorší, co by zlovolná úprava této
aplikace mohla udělat, je prozradit, *které adresy sledujete* — právě proto je
směrování přes Tor a vlastní uzel plnohodnotnou funkcí.

### Ověřené vlastnosti vydaného APK (v1.2.0)

Kontrolováno přímo na vydané binárce, nejen na zdrojácích:

**Oprávnění zabudovaná v APK** (nic jiného):

```
INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC,
CAMERA, RECEIVE_BOOT_COMPLETED, REQUEST_INSTALL_PACKAGES,
WAKE_LOCK + ACCESS_NETWORK_STATE (přidává automaticky WorkManager)
```

Žádný přístup ke kontaktům, SMS, poloze, souborům, mikrofonu ani účtům.

**Všechny síťové URL v přeloženém bytecode** — funkční jsou přesně tři:

| URL | Účel |
|---|---|
| `https://mempool.space/api/` | výchozí uzel (uživatelsky přepsatelný) |
| `wss://mempool.space/api/v1/ws` | výchozí realtime kanál (přepsatelný) |
| `https://raw.githubusercontent.com/Topkadas/SatoshiWatch/main/dist/version.json` | pouze ruční kontrola aktualizací |

Zbylé nalezené řetězce jsou neaktivní XML namespaces a odkazy na dokumentaci
uvnitř standardních AndroidX knihoven. **Žádná analytika, tracking ani crash
reporting.** Google ML Kit byl při vývoji záměrně odmítnut, protože jeho SDK
odesílá diagnostiku mimo aplikačního klienta (tedy i mimo Tor); QR kódy dekóduje
plně offline knihovna ZXing.

### Ověřte si to sami

Postup (příkazy výše v anglické sekci): výpis oprávnění přes `aapt2 dump
badging`, sken URL v `classes*.dex`, anebo prostě sestavte APK z tohoto repa
(`./gradlew :app:assembleDebug`) a porovnejte chování.

### Integrita aktualizací

Aktualizace se kontrolují pouze ručně (žádné automatické hlášení), stahují se
přes aplikačního klienta (respektuje Tor proxy) a před instalací se ověřují
proti **SHA-256** otisku z `dist/version.json`. Android navíc odmítne každý
update, který není podepsán stejným klíčem jako nainstalovaná verze — samotné
kompromitování repozitáře tedy stávajícím uživatelům podvrženou aktualizaci
nenainstaluje.

### Známé limity

- Neproběhl nezávislý externí bezpečnostní audit.
- Vydání jsou zatím podepsána vývojovým (debug) klíčem.
- Výchozí veřejný uzel mempool.space vidí vaši IP a dotazované adresy — pro
  plné soukromí použijte Orbot/Tor nebo vlastní uzel.

### Hlášení zranitelností

Otevřete GitHub issue (nebo kontaktujte správce repozitáře) s podrobnostmi.
Hlášení s postupem reprodukce mají přednost.
