# ADR-0004: Read SMS by structure and a shared lexicon, not by template

## Status
Accepted. Refines the rule parser that ADR-0003 builds on.

## Context
The rule parser grew phrase by phrase. It handled the messages it was written for, but every new
wording needed a new regex and stop-word, and rules were easy to over-fit to one bank. The app
must work for banks and wordings it has never seen.

## Decision
- `SmsFieldReader` splits an SMS into labelled values: "label: value" lines and inline
  "marker value" phrases. Every known marker also ends the previous value.
- `SmsLexicon` lists the Arabic and English words for each role (amount, merchant, from, to, card,
  account, balance, fee, date, reference, code, purpose). It names no bank; new wording is a new
  word in the right role.
- A value's shape (amount, masked digits, date, text) decides what it is, so "From: ***001" is an
  account and "From: Hungerstation" a merchant without special cases.
- The message type comes from weighted cues (code, declined, own-account, statement, incoming,
  spending); a non-spending type wins when its weight is high enough.
- The amount is taken from amount-labelled values first and never from balance, fee or reference
  values or from digits inside masked account numbers.
- Real user SMS are used only locally to measure accuracy, with rules written on some banks and
  checked on held-out ones. They are never committed; tests use invented values only, and
  `.gitignore` blocks SMS exports.

## Consequences
- Parser rules are fewer and general; accuracy on unseen banks is measured, not assumed.
- Some wordings still need lexicon entries; the learned templates (ADR-0003) cover the rest per user.
