# ADR-0003: Learn message reading on the device from user labels

## Status
Accepted. Supersedes the "pre-trained model" and "ML deduces parsing rules" parts of ADR-0001.

## Context
ADR-0001 called for a pre-trained on-device model. No labelled corpus of Egypt/Saudi bank SMS
exists to train one, and an on-device LLM is too large and not available on every phone. The
regex parser handles common wording but cannot learn: a message it misreads stays misread.

## Decision
- Keep the rule parser. It is the cold-start reader and the source of weak labels.
- Let the user label a stored SMS: its Message type (purchase, bill/transfer, withdrawal,
  credit, own-account move, statement, declined, notice) and optionally its merchant, amount
  and card/account digits. Labels live in `message_labels`, keyed by `raw_sms.id`.
- Train an `SmsModel` on the device from those labels, in pure Kotlin, at import and after each
  label. No model file ships in the APK.
  1. Exact label: the labelled message is read as labelled.
  2. Template: bank SMS are machine-generated. Each label becomes a per-sender template where
     numbers, dates and masked digits are variable and the labelled merchant span is a wildcard.
     Every message with the same template takes the label's type and reads merchant, amount and
     instrument from the same positions.
  3. Type classifier: weighted multinomial Naive Bayes over words, bigrams and the sender,
     trained on rule types for all stored SMS plus user labels at higher weight. It overrides
     the rules only for a type the user has labelled, at 90% probability or more.
- The instrument registry (ADR-0002) still decides Card vs Account for learned digits.
- Saving or clearing a label re-parses all stored SMS.

## Consequences
- One correction fixes every message of that bank template, including future imports.
- Without labels behaviour is unchanged from the rule parser.
- Templates are deliberately strict: different wording needs its own label.
- A pre-trained model can later replace the classifier behind `SmsModel` without changing the
  label storage or the parser interface.
