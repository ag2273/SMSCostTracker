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

## Thread selection
The user's choice of SMS conversations belonging to banks. Only selected threads are imported.

## Sync
The on-demand process of reading selected threads and turning their messages into Transactions.

## Duplicate
A Transaction with the same amount, merchant, and occurrence time within 24 hours of another. Duplicates collapse into a single Transaction.

## Analysis
Offline aggregation and filtering of Transactions into lists and charts.

## Date range
A filter over Transaction occurrence time: all time, a month, a quarter, or a custom start and end.
