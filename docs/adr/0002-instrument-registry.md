# ADR-0002: Learn card/account types from the user, not per-bank rules

## Status
Accepted

## Context
Bank SMS formats vary. A message can contain both a card last-4 and the funding
account (`Card (1214) from (***001)`). Keyword rules per bank are fragile and
must be extended for every new sender. ADR-0001 called for ML to deduce parsing
rules, but no labelled corpus exists and an on-device LLM is costly.

## Decision
- Detect instrument candidates (masked numbers with card/account context) from
  stored SMS, grouped by sender.
- Ask the user once to label each candidate as Card or Account; persist this in
  the `instruments` table keyed by `(address, digits)`.
- Offer "Not an account" for false positives; ignored entries are persisted and
  excluded from parsing and future reviews.
- Parsing consults the registry first. Context only decides between a registered
  card and a registered account: purchases prefer Card, transfers/bills prefer
  Account.
- A per-transaction correction writes back to the same registry, so future
  imports and rebuilds learn the bank's wording.
- Keyword inference remains only as a fallback for candidates the user has not
  labelled yet.

## Consequences
- No new code per bank; correctness improves through use.
- Cold start still relies on fallback inference until the review step is done.
- The registry is data, not a model; replacing the fallback with a small
  on-device classifier later does not change the interface.
