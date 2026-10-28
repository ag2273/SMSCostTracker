# Domain docs

This repository has a single bounded context: turning bank SMS into categorised spending on the device.

## Where the domain lives

- `GLOSSARY.md` at the repo root defines the domain terms: Transaction, Amount, Merchant, Category, Classification, Correction, Bank template, Account, Channel, Instrument, Instrument registry, Message type, Message label, Template, Thread selection, Sync, Duplicate, Analysis, Date range, Recurring series, Cycle, Recurring override, Subscription. Use these words in code, issues and UI copy; add a term here before introducing a new concept.
- `docs/adr/` records decisions. Read the relevant ADR before changing that area, and add a new numbered ADR when a decision changes. Superseded parts are marked in the newer ADR, not deleted.
  - ADR-0001: on-device ML for classification (pre-trained model part superseded by ADR-0003)
  - ADR-0002: card/account types are learned from the user (instrument registry)
  - ADR-0003: message reading is learned on the device from user labels (`SmsModel`)
  - ADR-0004: SMS are read by structure with a shared Arabic/English lexicon, not by template
- `docs/ux/user-scenario.md` is the approved UX flow; `mockups/` holds the screen mockups it refers to.

## Code map

| Package | Domain role |
|---|---|
| `sms` | Reads selected SMS threads from the device |
| `parsing` | Rule parser, duplicate detection |
| `ml` | Learned reading: tokenizer, templates, message-type classifier (ADR-0003) |
| `classification` | Category classifier and seed data |
| `data` | Room entities, DAOs, importer, repository |
| `analysis` | Filters, aggregation, recurring payments, CSV export |
| `ui` | Compose screens, sheets and components |

## Invariants

- SMS text never leaves the device; no network calls for analysis.
- Amounts are stored in minor units with their currency; currencies are never mixed in totals.
- The instrument registry decides Card vs Account; learned or parsed digits only nominate candidates.
- User corrections and labels always override rules and models.
