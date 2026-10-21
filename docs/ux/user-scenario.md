# SMSCostTracker — full user scenario

**Status:** approved UX. Phase 1 is implemented in the Android app (onboarding, Home, Activity, filters, detail, category correction, minimal Settings, Cards & accounts, unparsed messages, re-import). Insights is a first-pass summary; export and deeper analytics remain phase 2.
**Palette:** Ledger
**Visuals:** [`user-scenario.png`](../../mockups/user-scenario.png) · [`storyboard.png`](../../mockups/storyboard.png) · [`review/`](../../mockups/review/)

## 1. Scenario at a glance

**Persona:** Salma, an Android user in Saudi Arabia and Egypt. She has SAB, Al Rajhi, CIB, SNB and Riyad Bank accounts and cards, and spends in SAR and EGP.

**Goal:** understand where her money went without logging into every bank and without uploading her SMS.

**Promise:** everything is parsed on the phone. SMS text never leaves the device. No bank credentials are requested.

**Success test:** she can answer “what did I spend this month?” in under 10 seconds, correct a wrong category once, and never wonder whether her messages were uploaded.

## 2. First run — screens 01–07

One-time setup. The user grants access, chooses the bank conversations to scan, reviews what was found, confirms the card/account numbers, and lands on Home.

| # | Screen | What the user does | What the system does | Exit state |
|---|---|---|---|---|
| 01 | Welcome & privacy | Reads the promise, taps **Get started** | Explains local-only parsing before asking for anything | No permission requested yet |
| 02 | SMS permission | Taps **Allow SMS access** | Shows the Android system prompt; explains what is read | Permission granted, or the denied path in §5 |
| 03 | Choose bank threads | Confirms the suggested bank threads; can deselect | Scans only selected conversations; shows 2,174 messages | 5 threads selected |
| 04 | Scan progress | Watches progress or cancels | Parses on-device; shows transactions, skipped lines, unparsed SMS | 1,284 transactions, 890 skipped, 6 unparsed |
| 05 | Review instruments | Reviews 12 numbers grouped by sender; chooses **Review SAB first** | Suggests Card vs Account from SMS wording | SAB review opened |
| 06 | Confirm SAB numbers | Confirms 1214 Card, 001 Account, 0542 Card, 9981 Card; taps **Save 4 and review Al Rajhi** | Saves confirmed instruments and advances | SAB complete; Al Rajhi next |
| 07 | Import complete | Chooses **Go to Home** or **Review 6 values** | Summarises the import and the remaining review work | 6 of 12 confirmed; 6 need review |

### Why this order

- The privacy promise comes before the permission request, so the user knows why SMS access is needed.
- Thread selection comes before scanning, so the user is not asked to trust a black box that reads every message.
- Instruments are grouped by sender, not shown as one flat list of 12. The split between card and account is visible before the user edits anything.
- The first-run review is resumable. A user can finish SAB, leave, and confirm the other senders from Home or Settings later.

## 3. Daily review — screens 08–13

The main loop: see the month, narrow the list, understand one transaction, correct it, and see the effect.

| # | Screen | What the user does | What the system does | Sample state |
|---|---|---|---|---|
| 08 | Home | Glances at the month; switches SAR/EGP; opens pending review or a recent transaction | Shows spend, cards vs accounts, top categories, recent activity, review banner | SAR 4,820.00 · Cards 3,140 · Accounts 1,680 · EGP 3,240 |
| 09 | Activity | Searches or filters; opens Jarir Bookstore | Groups by date; shows category, instrument and review flag | 128 transactions in October |
| 10 | Filters | Narrows to this month, Shopping, SAB 1214, SAR, merchant Amazon | Shows 4 active filters, clear-all, and a live result count | 18 matches |
| 11 | Transaction detail | Checks what was parsed, then the source SMS and the why-it-matched explanation | Puts parsed fields first; diagnostics second; corrections at the bottom | Jarir Bookstore · SAR 1,249.00 · Al Rajhi 4432 · Other 62% match |
| 12 | Change category | Chooses Shopping; taps **Save correction** | Explains that the classifier learns from the correction | Other → Shopping; future Jarir visits become Shopping |
| 13 | Insights | Checks the category mix, card/account split, trend and top merchants | Uses the corrected data; keeps currency explicit | Shopping 26%, Other 18% after the correction |

### The deliberate before/after

Home (08), Activity (09) and Detail (11) show the state **before** the Jarir correction. Activity shows the category and a Review flag; Home and Detail show the confidence:

- Jarir Bookstore, SAR 1,249.00, is classified as **Other · 62% match**. The 62% is the classifier confidence, not the category share.
- Home therefore shows **Shopping 18%** and **Other 26%**.

After the correction (12), Insights (13) shows the post-correction state:

- Jarir Bookstore is **Shopping**.
- **Shopping 26%**, **Other 18%**.

This is the core product story: a single correction changes the month, and the app shows the user that it learned.

## 4. Management — screens 14–16

| # | Screen | What the user can do | Notes |
|---|---|---|---|
| 14 | Settings | Open linked banks, cards & accounts, import, export, privacy, appearance, demo data | The review banner is the first row, so unfinished setup is never buried |
| 15 | Cards & accounts | Confirm, rename or ignore instruments; see confirmed vs pending | 4 cards · 2 accounts confirmed; 6 values still need review |
| 16 | Export & backup | Export CSV, share a summary, connect Google Drive | CSV scope is explicit; SMS text never leaves the phone |

## 5. Guardrails

These are the states that keep the app trustworthy when the data is imperfect.

| State | What the user sees | What the app does |
|---|---|---|
| SMS permission denied | A plain explanation, **Not now**, and a path to system Settings | The app still opens; import waits for access. No repeated prompt loop. |
| 6 instruments pending | A Home banner: “6 card/account values need review” | Import continues; affected transactions are tagged, not hidden. |
| 6 unparsed SMS | The count is surfaced in Import/Settings | The app does not guess. The user can inspect the source text. |
| Mixed currencies | SAR/EGP switch and explicit currency labels on Home, Activity and Insights | SAR and EGP are never added together and never silently converted. |
| Duplicate or balance lines | Skipped-line count during scan and in Import | Balance lines are not treated as spending; duplicates are ignored. |
| False-positive sender | The sender can be unselected in Settings → Linked banks | Its transactions can be excluded without deleting the SMS. |
| SMS access revoked later | Import is paused; existing data remains readable | The app explains how to restore access and does not silently fail. |

## 6. Sample data contract

The mockups intentionally share one data set so reviewers can check consistency across screens.

| Fact | Value |
|---|---|
| SMS scanned | 2,174 |
| Transactions created | 1,284 |
| Skipped lines | 890 |
| Unparsed SMS | 6 |
| Instruments found | 12 |
| Confirmed instruments | 6 (SAB 4, Al Rajhi 2) |
| Pending instruments | 6 (CIB 2, SNB 2, Riyad 2) |
| Confirmed instrument types | 4 cards · 2 accounts |
| SAB transactions | 700 (`1214` 318, `001` 296, `0542` 74, `9981` 12) |
| Al Rajhi transactions | 269 (`4432` 241, `8890` 28) |
| CIB transactions | 121 (`7788` 102, `2201` 19) |
| SNB + Riyad transactions | 194 |
| October SAR total | 4,820.00 |
| Cards / Accounts | 3,140 / 1,680 |
| EGP total | 3,240 |
| Categories before Jarir correction | Groceries 32% · Dining 24% · Shopping 18% · Other 26% |
| Categories after Jarir correction | Groceries 32% · Dining 24% · Shopping 26% · Other 18% |

## 7. Decisions locked after review

1. **First-run length.** Keep the seven-screen setup, with instrument review resumable from Home or Settings.
2. **Pending instruments and totals.** Import continues; affected transactions are tagged, not hidden or blocked.
3. **Unparsed SMS.** Add a dedicated **Unparsed messages** list under Settings; never guess.
4. **Before/after categories.** Home and Activity show the state before a correction; Insights reflects the corrected state. A correction updates all past transactions and future imports.
5. **Currency.** SAR and EGP stay separate, with an explicit switch and no conversion.
6. **Export.** CSV plus Android share is phase 2. Export transactions and corrections, never SMS text.
7. **Review actions.** Per-number confirmation is the safe default; no "mark all as cards" shortcut in phase 1.
8. **Implementation scope.** Phase 1 covers screens 01–12 plus minimal Settings, Cards & accounts, Unparsed messages and re-import. Insights is a first-pass summary. Export and deeper analytics remain phase 2.
9. **Data.** The app uses real SMS only; the mockup sample data is not seeded. Parsing runs on-device with IBM Plex Sans (English first).

## 8. Files

| Asset | File |
|---|---|
| Full scenario diagram | [`mockups/user-scenario.png`](../../mockups/user-scenario.png) |
| All 16 screens | [`mockups/storyboard.png`](../../mockups/storyboard.png) |
| Annotated screens | [`mockups/review/`](../../mockups/review/) |
| Phone-only screens | [`mockups/screens_png/`](../../mockups/screens_png/) |
| Screen generator | [`mockups/screens_html.py`](../../mockups/screens_html.py) |
| Review/storyboard generator | [`mockups/compose.py`](../../mockups/compose.py) |
