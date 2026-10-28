# ADR-0005: Download exchange rates and version them

## Status
Accepted. Relaxes the "no network" invariant for this one purpose.

## Context
Foreign card purchases are stored in the currency the SMS names (USD, EUR, …), so spending is split
across currencies. Cards and accounts need a currency of their own (#50) and the user wants to see
everything in one currency (#51), which needs exchange rates. Typed-in rates go stale, and the owner
asked for rates fetched online that they can still change at any time, with a change affecting only
transactions from then on.

## Decision
- The app gets Android's internet permission, used only to download a public rate list for USD,
  SAR, EGP, EUR and GBP from free sources that need no key (fawazahmed0 currency-api via jsDelivr or
  its Cloudflare mirror, then open.er-api.com). The request carries nothing about the user; SMS,
  amounts and merchants never leave the phone.
- Rates are stored as units per US dollar in `exchange_rates`, one row per **rate version** with the
  time it took effect. A download that changes a rate, or a rate the user types, adds a version
  effective from that moment. Nothing is overwritten.
- An amount converts through USD with the versions in effect at its transaction's time. Times
  before the first version use the earliest one. Built-in starting rates make conversion work before
  any download.
- Rates are downloaded once a day when the setting is on. A rate the user set is kept by daily
  downloads; "Update from internet" replaces all rates.
- A missing rate means no conversion rather than a guessed one.

## Consequences
- Settings and onboarding say the internet is used only for exchange rates.
- Historical market rates are not used: earlier transactions keep the rate that was current when
  the app first knew them, which the owner chose over looking up past rates.
