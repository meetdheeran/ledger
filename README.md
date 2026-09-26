# Ledger

Your spending, read from the bank messages already on your phone.

No bank login. No account aggregator. No server. **No internet permission at all** — the app
cannot reach the network even if something in it tried to, which makes "nothing leaves your
phone" a property of the app rather than a promise in a privacy policy.

Built for the UAE first (AED), but the bank-specific parts are data, not code.

---

## What it does

- **Reads the last 30 days** of SMS on first run and builds a record from them.
- **Bank messages only.** Anything from a sender that cannot be tied to a bank is ignored and
  never stored anywhere.
- **Finds your cards by itself.** A card appears the first time it shows up in a message — there
  is no setup screen asking you to type card numbers. Each card is tracked separately, and you
  can give it a name.
- **Sorts merchants into categories** automatically, and remembers your corrections: fix
  "Carrefour" once and every past and future Carrefour transaction follows.
- **Keeps up on its own.** New bank messages are picked up as they arrive.
- **Shows what it could not read.** Bank messages the rules failed on go to an Inbox tab instead
  of being dropped, so a total is never quietly missing something.
- **Opens with a password** you set the first time, and locks again whenever you leave the app.

## What it deliberately does not do

- Talk to the network. There is no `INTERNET` permission and no HTTP library in the build.
- Get backed up. Cloud backup and device-to-device transfer are both excluded, so the database
  does not travel to a new phone or to Google.
- Encrypt the database. **This was a choice, not an oversight** — the options were a password
  screen alone or a password that also encrypts the data, and the screen-only option was taken.
  Anyone with the phone unlocked and developer access can read the database file directly. If
  that matters later, it is a real piece of work to retrofit, not a setting.

## How the parsing works

The *grammar* of a transaction message is general and lives in code
(`sms/SmsParser.kt`): a currency and an amount, a verb that gives the direction, a masked card,
a merchant, sometimes a balance. Banks across the UAE say the same things in a slightly
different order.

The parts that actually vary live in **`app/src/main/assets/rules.json`**:

- `banks` — which sender IDs belong to which bank. Matching ignores case, spaces and
  punctuation, so `ENBD-Alert` matches the token `ENBD`.
- `merchantCategories` — which merchant names map to which category. Longest match wins.

Adding a bank or a merchant is a data change. That is what makes "works for other people's
banks too" realistic rather than a rewrite.

A few details that matter more than they look:

- **OTP messages are rejected outright.** An OTP for a purchase quotes the amount, so without
  that check every confirmed payment would be counted twice.
- **"Credit card" is stripped before deciding direction.** The word "credit" in a card's name
  says nothing about which way the money went, and treating it as a signal turns every card
  purchase into income.
- **Amounts next to "balance" or "limit" are not the transaction.** They are captured separately.
- **Money is stored in minor units as integers.** Floating point accumulates error the moment
  you start summing it, which for a spending total is the one thing that must not happen.
- **Every message has a content-derived hash.** Importing the same SMS twice is a no-op, which
  is what lets a re-scan run freely.

## Build

Needs JDK 17 and Android SDK 34. Pinned toolchain, same as Prism — do not bump: newer libraries
ship Kotlin 2.2 metadata and break this combination.

```bash
./gradlew assembleDebug
```

Install on a connected phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Why |
|---|---|
| `READ_SMS` | The 30-day backfill. This is the entire data source. |
| `RECEIVE_SMS` | Picking up new bank messages as they arrive. |

`READ_SMS` is a restricted permission on the Play Store. Sideloading is unaffected; public
distribution would need a declaration.

## Status

v1.2: dashboard and activity tools (2026-09-26).

### Dashboard and navigation

- Five tabs: Home, Activity, Insights, Cards, and Inbox; dark surfaces with mint,
  blue, and violet accents.
- Browse months across the dashboard, charts, card totals, and activity. The
  initial import/rescan still reads 30 days; older saved history remains visible.
- Search transactions by merchant, bank, card suffix, type, category, or amount.
  Combine transaction-type, card, category, currency, all-history, and date-sort
  filters. Card filters use both bank and suffix, so matching digits across banks
  do not mix their transactions.
- Set, edit, or remove a separate AED spending budget for each month. Budgets
  are stored on the device and use the same expense rules as the dashboard.
- Insights show six-month spending, category shares, top merchants, spending by
  day, expense count, and the largest expense. Charts use imported SMS only;
  months with limited history are not a complete bank statement.
- Tap a card or category to open its matching activity. Transaction corrections,
  renaming, and missing-sender registration remain available.
- Rescan shows a busy state and reports failures. Sample-data loading is no
  longer exposed in the normal UI, preventing accidental demo transactions.

### What counts as spending

The dashboard totals **purchases, bill payments, and bank fees in AED**. Salary,
incoming/outgoing transfers, card repayments, cash withdrawals, and refunds have
separate transaction types and are excluded. Refunds are shown separately, rather
than deducted from an unrelated month's purchases. Repayment alerts can describe
both sides of the same payment; neither side is counted as another purchase.

An unexplained debit is **Needs review**, not automatically an expense. Tap an
activity item to see its original SMS, change its type, or change its merchant
category. Type corrections apply to that transaction only and survive rescans.
Declined, pending, future-dated payment notices and OTPs do not count as spending.
Foreign-currency activity retains its currency and is excluded from AED totals;
the app does not invent an exchange rate. Multiple transaction amounts in one
SMS require review.

### UAE coverage and limits

Bank recognition has been expanded using the retail-bank names in the
[CBUAE August 2026 register](https://centralbank.ae/media/hw0lreyi/cb-register-august-2026.pdf).
Sender aliases are matching rules, not a bank-certified directory of SMS IDs.
The same purpose rules apply to every recognised bank and card product. For a
missing sender, use **Inbox → Missing a bank? Add its SMS sender**. This adds an
exact sender match and rescans the last 30 days. Sender recognition is not proof
of SMS authenticity.

Universal accuracy across all bank templates is not claimed. The automated
purpose rules currently cover English message patterns. Arabic digits and
dirham currency labels are normalised; unsupported wording with a readable
amount goes to Inbox. Ambiguous messages with a direction/amount are visible as
Needs review and excluded from totals. Test fixtures are synthetic, not validated
samples from every bank. Duplicate detection is still based on the same source
message, not reconciliation of different SMS alerts describing one purchase.

### Upgrading and checking

The Room v1 → v2 migration is additive. Existing stored SMS are reclassified on
unlock (including those older than 30 days), and on rescan; this does not reset
the password or erase card names or merchant rules. Account suffixes no longer
create cards. Install as an update using the same signing key; do not uninstall
to resolve a signing mismatch, since uninstalling would remove local history.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
python tools/check_storage.py
```

The Python check runs the migration and production DAO queries against SQLite,
compares the resulting schema to Room's exported v2 schema, and verifies retained
data, category/card totals, income separation, currency exclusion, and duplicate
constraints. It complements JVM tests; on-device upgrade/UI verification is
still required before distribution.
