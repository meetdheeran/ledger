"""Exercise migration and production DAO SQL against SQLite using synthetic data.

Run from the project root: python tools/check_storage.py
This complements JVM parser tests; it is not an Android instrumentation test.
"""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schemas = root / "app/schemas/com.meetdheeran.ledger.data.LedgerDb"
source = root / "app/src/main/kotlin/com/meetdheeran/ledger/data"


def create_schema(version):
    db = sqlite3.connect(":memory:")
    data = json.loads((schemas / f"{version}.json").read_text())["database"]
    for entity in data["entities"]:
        name = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity["indices"]:
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))
    return db


db = create_schema(1)
db.execute("INSERT INTO cards (bank,last4,label,firstSeen) VALUES ('Test Bank','1234','My card',1)")
db.execute("INSERT INTO merchant_rules VALUES ('SHOP','GROCERIES')")
db.execute("""INSERT INTO txns (sourceHash,timestamp,amountMinor,currency,direction,
    merchant,cardLast4,bank,category,balanceMinor,body)
    VALUES ('old',100,10000,'AED','DEBIT','SHOP','1234','Test Bank','GROCERIES',90000,'synthetic message')""")
old_row = db.execute("SELECT * FROM txns").fetchone()
for sql in re.findall(r'db\.execSQL\("([^"]+)"\)', (source / "LedgerDb.kt").read_text()):
    db.execute(sql)
fresh = create_schema(2)
for table in ("cards", "txns", "merchant_rules", "unparsed"):
    assert db.execute(f"PRAGMA table_info({table})").fetchall() == fresh.execute(f"PRAGMA table_info({table})").fetchall(), table
assert db.execute("SELECT * FROM txns").fetchone() == old_row + ("REVIEW", 0, 0)
assert db.execute("SELECT label FROM cards").fetchone()[0] == "My card"
assert db.execute("SELECT category FROM merchant_rules").fetchone()[0] == "GROCERIES"

dao = (source / "Dao.kt").read_text()
queries = {}
for match in re.finditer(r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)', dao, re.S):
    queries[match[3]] = match[1] or match[2]
params = {"from": 0, "to": 200}
assert db.execute(queries["spendBetween"], params).fetchone()[0] == 0, "Unreviewed legacy rows must not count"

cases = [
    ("PURCHASE", "DEBIT", 10000, "AED"),
    ("BILL_PAYMENT", "DEBIT", 4000, "AED"),
    ("FEE", "DEBIT", 500, "AED"),
    ("SALARY", "CREDIT", 800000, "AED"),
    ("OTHER_INCOME", "CREDIT", 2000, "AED"),
    ("TRANSFER_IN", "CREDIT", 90000, "AED"),
    ("TRANSFER_OUT", "DEBIT", 70000, "AED"),
    ("CARD_REPAYMENT", "DEBIT", 200000, "AED"),
    ("CARD_REPAYMENT", "CREDIT", 200000, "AED"),
    ("REFUND", "CREDIT", 3000, "AED"),
    ("CASH_WITHDRAWAL", "DEBIT", 50000, "AED"),
    ("REVIEW", "DEBIT", 999000, "AED"),
    ("PURCHASE", "DEBIT", 2500, "USD"),
]
for i, (kind, direction, amount, currency) in enumerate(cases):
    db.execute("""INSERT INTO txns (sourceHash,timestamp,amountMinor,currency,direction,
        merchant,cardLast4,bank,category,body,kind,parserVersion)
        VALUES (?,100,?,?,?,'SHOP','1234','Test Bank','OTHER','synthetic message',?,2)""",
        (str(i), amount, currency, direction, kind))
assert db.execute(queries["spendBetween"], params).fetchone()[0] == 14500
assert db.execute(queries["incomeBetween"], params).fetchone()[0] == 802000
assert sum(row[2] for row in db.execute(queries["spendByCard"], params)) == 14500
assert sum(row[1] for row in db.execute(queries["spendByCategory"], params)) == 14500
assert db.execute(queries["spendBetween"], {"from": 201, "to": 300}).fetchone()[0] == 0
db.execute(queries["setTxnKind"], {"id": 1, "kind": "TRANSFER_IN", "direction": "CREDIT"})
assert db.execute("SELECT kind, direction, classificationOverridden FROM txns WHERE id=1").fetchone() == ("TRANSFER_IN", "CREDIT", 1)
db.execute("INSERT INTO cards (bank,last4,label,firstSeen) VALUES ('Test Bank','9876','Account mistaken for card',1)")
db.execute(queries["removeUnreferencedCards"])
assert db.execute("SELECT last4,label FROM cards").fetchall() == [("1234", "My card")]
try:
    db.execute("UPDATE txns SET sourceHash='old' WHERE sourceHash='0'")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError("Duplicate source hashes must be rejected")
print("PASS: v1 -> v2 schema, retained data/labels/rules, all spending aggregates, income, dates, FX exclusion, corrections, phantom cards, duplicate protection")
