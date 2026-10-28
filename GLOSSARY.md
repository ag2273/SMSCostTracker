# Glossary

## Transaction
A single financial event parsed from a bank SMS: amount, currency, merchant, occurrence time, source address.

## Amount
The transaction value stored in minor units (for example, piastres or halalas) together with its currency.

## Merchant
The payee or counterparty named in an SMS, such as a shop, utility, or telecom operator.

## Category
One of the user-facing expense buckets: Groceries, Dining, Transport, Fuel, Utilities, Telecom, Rent, Health, Education, Shopping, Entertainment, Travel, Fees, Transfers, Other.

## Classification
Assigning a Category to a Transaction from its Merchant, using the on-device model.

## Correction
A user's manual change of a Transaction's Category. A Correction overrides the model for that Merchant and teaches the model to classify similar Merchants.

## Bank template
Per-bank parsing rules (sender IDs, amount/date/merchant patterns) for turning SMS text into a Transaction.

## Account
The sender plus the last digits of the instrument a Transaction was charged to (for example `SAB 1214`). Used to filter and total spending.

## Channel
Whether a Transaction was charged through a Card (PoS, online purchase, mada, wallet) or directly from a bank Account (transfer, bill payment, standing order). The card number and the funding account both appear in some SMS; Channel decides which one is the Account.

## Instrument
A number that identifies where money moved: a card last-4 or a bank account number, scoped to the sender.

## Instrument registry
The user-confirmed map of Instruments to Channels for each sender. It is created by the account/card review step or by correcting a Transaction, and is consulted before fallback parsing.

## Message type
What a bank SMS reports: Purchase, Bill or transfer, Cash withdrawal, Credit/refund, Own-account move, Statement, Declined, or Notice. Only the first three become Transactions.

## Message label
A user's reading of one stored SMS: its Message type and optionally its Merchant, Amount and Instrument digits.

## Template
A Message label generalised to every SMS from the same sender with the same wording, where numbers, dates, masked digits and the labelled Merchant may differ.

## Thread selection
The user's choice of SMS conversations belonging to banks. Only selected threads are imported.

## Sync
Reading new messages from the selected threads and turning them into Transactions. Runs automatically when the app opens and every 6 hours (unless turned off in Settings), and on demand via Re-import.

## Duplicate
A Transaction with the same amount, merchant, and occurrence time within 24 hours of another. Duplicates collapse into a single Transaction.

## Analysis
Offline aggregation and filtering of Transactions into lists and charts.

## Date range
A filter over Transaction occurrence time: all time, a month, a quarter, or a custom start and end.

## Recurring series
Payments to one Merchant in one currency that repeat on a Cycle, found by Analysis from stored Transactions. It has a typical Amount (Fixed within ±5%, or Variable such as utilities), a last and next expected date, and a status: Active, Late (one expected payment missed) or Ended (two or more missed). A series is split by Account only when two series run at one Merchant on different cards. A pause longer than two cycles does not end a series; when payments resume, it is current again. Series that ended more than a year ago are not listed.

## Cycle
How often a Recurring series repeats: weekly, monthly (±3 days), quarterly or yearly. Monthly and longer cycles follow the calendar date of the last payment.

## Recurring override
The user's answer about a Recurring series: confirmed (optionally renamed, optionally with the Cycle the user chose) or dismissed. Choosing a Cycle, or tracking a Merchant as a subscription from a Transaction, makes detection follow that Cycle even when it would not have found the series. It is kept by series key (Merchant key, currency and, when split, Account) so a dismissed series is not suggested again after a re-parse.

## Subscription
The UI name for a Recurring series. The Subscriptions screen totals current series per currency as a monthly equivalent (yearly ÷ 12, quarterly ÷ 3, weekly × 52 ÷ 12) and marks each one Active, Late, Ended or Price changed.

## Alert
A local notification about a confirmed Subscription: Late (no payment 3 days after the expected date), Price changed (a fixed-amount series charged more than 5% above or below its earlier price) or Due tomorrow. Each event alerts once. Alerts are off until the user turns them on in Settings, and are checked after each Sync and once a day.

## Exchange rate
Units of a currency per US dollar, for USD, SAR, EGP, EUR and GBP. Rates ship with the app and the user can change them in Settings; they convert amounts through USD. The app never downloads rates.

## Rate version
One exchange rate with the time it took effect. Built-in rates apply from the beginning; every rate the user saves is a new version from that moment. An amount is converted with the version in effect when its Transaction happened, so a change never rewrites earlier Transactions.

## Account currency
The currency a card's or account's amounts are shown in. The user chooses it in Cards & accounts; otherwise it is the currency most of its Transactions use (or, for one with few Transactions, the one its bank mostly uses). A Transaction in another currency is shown converted with the Rate version in effect when it happened, and its stated amount stays visible.

## Linked account
The account a debit card draws from. A linked debit card uses its account's currency, and filtering by the account includes the card's Transactions. Credit cards are never linked.
