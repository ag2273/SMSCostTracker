# ADR-0005: Preloaded, user-editable exchange rates, versioned in time

## Status
Accepted. Keeps the "no network" invariant: the app has no internet permission.

## Context
Foreign card purchases are stored in the currency the SMS names (USD, EUR, …), so spending is split
across currencies. Cards and accounts need a currency of their own (#50) and the owner wants to see
everything in one currency (#51), which needs exchange rates. Downloading rates was considered and
rejected by the owner: the app must never connect to the internet.

## Decision
- Rates for USD, SAR, EGP, EUR and GBP ship with the app (units per US dollar). The user can change
  any rate in Settings → Exchange rates at any time.
- Rates are stored in `exchange_rates`, one row per **rate version** with the time it took effect.
  Built-in rates take effect from the beginning of time; a rate the user saves takes effect from that
  moment. Nothing is overwritten, so a change applies only to transactions from then on.
- An amount converts through USD with the versions in effect at its transaction's time. A missing
  rate means no conversion rather than a guessed one.
- The app has no internet permission and makes no network calls.

## Consequences
- Rates are as current as the app's release and the user's own edits; accuracy depends on the user
  updating rates that move (EGP in particular).
- Historical market rates are not used: earlier transactions keep the rate in effect when they
  happened, by the app's own versions.
