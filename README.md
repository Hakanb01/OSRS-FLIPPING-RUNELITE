# OSRS Flipping (RuneLite plugin)

> Not affiliated with Jagex. RuneScape and Old School RuneScape are trademarks of Jagex Limited.

OSRS Flipping sends your Grand Exchange trades (bought, sold, partly filled, cancelled) to **your own OSRS Flipping Discord bot** and shows your flipping profit in a side panel. It **only reads and shows**. It never clicks, places or changes offers, fills in prices or quantities, marks widgets as "click here", swaps menu entries, or uses reflection or JNI. You do everything in the game yourself.

## What it does

- Tracks every GE slot per account (several accounts on one pc stay apart) and sends cumulative fills, so a partly filled offer that completes or is cancelled counts once. Login replays of your slots are never booked as new trades.
- Panel: link status and queue, running offers with a fill bar, "best flip for your gp" (from the bot; type a budget or opt in to send your gp), net profit today / per account / per item (after tax), a small profit chart and your last 10 flips.
- In game, display only: sell offers in the GE tinted green/red by profit against your known buy price, and a tooltip with the profit on inventory items. Both can be switched off.

## Privacy and safety

- **Sending is OFF by default.** Switching it on shows RuneLite's third-party warning. With it off the plugin makes no network request at all.
- Network: only RuneLite's `OkHttpClient`, from a worker thread, never the client thread. Only `https://` addresses (plain `http://` only for localhost).
- The link code is a `secret` config item and is never logged. Gp stack and account name/hash each have their own opt-in (off).
- Trades wait in a queue that is saved with the tracking state; they are removed only after the bot confirmed them.

## Install (Nederlands)

### Tijdens het testen: developer-modus

1. Installeer JDK 11 en clone deze repo; ga naar `runelite-plugin/`.
2. `./gradlew run` start RuneLite in developer-modus met deze plugin geladen (`FlipperPluginTest`). Of laad hem in je IDE door `FlipperPluginTest.main` te draaien met `--developer-mode`.
3. `./gradlew build test` bouwt en draait de unit tests.

### Koppelen aan de bot

1. Vraag de eigenaar van de bot om het **Server URL** (https://...).
2. In Discord: `/runelite link`. De bot toont een code, alleen voor jou.
3. RuneLite: tandwiel bij **OSRS Flipping**: plak de code bij **Link code**, vul **Server URL** in.
4. Zet **Send trades** aan en bevestig de waarschuwing. Pas dan wordt er iets verstuurd. Verlopen of verloren? Doe `/runelite link` opnieuw en plak de nieuwe code.

### Later: Plugin Hub

De eigenaar zet `runelite-plugin/` in een aparte publieke repo (met `runelite-plugin.properties`, `LICENSE`, `icon.png`) en dient die in met een pull request op [runelite/plugin-hub](https://github.com/runelite/plugin-hub): een bestand `plugins/osrs-flipping` met `repository=` en `commit=`. Plugin Hub-goedkeuring is een externe beoordeling; let op de [regels](https://github.com/runelite/plugin-hub#plugin-submission) en de [Jagex third-party-richtlijnen](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1).

## Known limits

- Item sinks without a GE event (shop sales, dropping) are not seen; the cost basis follows offers only.
- The bot cannot be reached from the internet until its owner opens a port (see `docs/runelite.md`).
- Built and unit-tested with Java 11 (`./gradlew build test`, 38 tests). Not yet tried by hand in a live RuneLite client.
- Tax on a sale is worked out per fill; when one fill of the GE mixes prices the split can be 1 gp off (the GE only reports totals).

## License

BSD 2-Clause, see `LICENSE` and `THIRD_PARTY_NOTICES.md`.
