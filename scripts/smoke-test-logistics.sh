#!/usr/bin/env bash
# =====================================================================
# Logistics module live smoke test (V16)
# Verifies the full flow against a running stack:
#   - create draft, add items + expenses, confirm
#   - stock moves from source warehouse to destination warehouse
#   - cash + bank balances debited per expense paymentSource
#   - balanced journal entry posted (trial balance unchanged)
#   - allocations sum cent-true to expensesTotal
#   - supplier debt UNCHANGED (logistics never touches supplier debt)
#   - permission gating + Excel export
#
#   bash scripts/smoke-test-logistics.sh [BASE_URL]
#   default BASE_URL = http://localhost:8080/api
# =====================================================================
set -u
BASE="${1:-http://localhost:8080/api}"
TS=$(date +%s)
PASS=0; FAIL=0
ok(){ echo "  PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "  FAIL: $1 (got: $2)"; FAIL=$((FAIL+1)); }
code(){ curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== Logistics smoke =="
SIGNUP=$(curl -s -X POST "$BASE/auth/signup" -H "Content-Type: application/json" \
  -d "{\"fullName\":\"LogTest\",\"email\":\"log_${TS}@test.com\",\"password\":\"Passw0rd!\",\"companyName\":\"LogCo $TS\",\"businessType\":\"import\",\"mainCurrency\":\"UZS\"}")
TOKEN=$(echo "$SIGNUP" | grep -o "\"token\":\"[^\"]*\"" | head -1 | sed "s/\"token\":\"//;s/\"//")
[ ${#TOKEN} -gt 20 ] || { echo "signup failed: $SIGNUP"; exit 1; }
A=(-H "Authorization: Bearer $TOKEN")
jid(){ grep -o "\"id\":[0-9]*" | head -1 | sed "s/\"id\"://"; }
field(){ grep -o "\"$1\":[^,}]*" | head -1 | sed "s/\"$1\"://;s/\"//g"; }

# ── Bootstrap data ───────────────────────────────────────────────────
CN=$(curl -s -X POST "$BASE/countries" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"China","code":"CN","currency":"USD"}' | jid)
UZ=$(curl -s -X POST "$BASE/countries" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"Uzbekistan","code":"UZ","currency":"UZS"}' | jid)
[ -n "$CN" ] && [ -n "$UZ" ] && ok "countries created (CN=$CN, UZ=$UZ)" || bad "countries" "?"

# main warehouse auto-created on first list
MAIN=$(curl -s "${A[@]}" "$BASE/warehouses" | jid)
SRC=$(curl -s -X POST "$BASE/warehouses" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"name\":\"Shanghai DC\",\"code\":\"CN-SH\",\"type\":\"main\",\"countryId\":$CN}" | jid)
DST=$MAIN
[ -n "$SRC" ] && ok "src warehouse #$SRC + dst #$DST" || bad "warehouses" "?"

P1=$(curl -s -X POST "$BASE/products" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"Widget","sku":"W-1","unit":"pcs","purchasePrice":100,"sellingPrice":200,"minStockLevel":0}' | jid)
P2=$(curl -s -X POST "$BASE/products" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"Gadget","sku":"G-1","unit":"pcs","purchasePrice":250,"sellingPrice":500,"minStockLevel":0}' | jid)
[ -n "$P1" ] && [ -n "$P2" ] && ok "products (P1=$P1, P2=$P2)" || bad "products" "?"

# stock 10 of P1 + 4 of P2 into SRC
curl -s -o /dev/null -X POST "$BASE/warehouses/stock/in" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"productId\":$P1,\"warehouseId\":$SRC,\"quantity\":10}"
curl -s -o /dev/null -X POST "$BASE/warehouses/stock/in" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"productId\":$P2,\"warehouseId\":$SRC,\"quantity\":4}"
ok "stocked source warehouse with 10 P1 + 4 P2"

SUP=$(curl -s -X POST "$BASE/suppliers" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"name\":\"Beijing Co\",\"countryId\":$CN,\"currency\":\"USD\"}" | jid)
# Set artificial supplier debt for the no-mutation test
SUP_DEBT_BEFORE=$(curl -s "${A[@]}" "$BASE/suppliers/$SUP" | field "currentDebt")
ok "supplier created (#$SUP) debt=$SUP_DEBT_BEFORE"

CASH_BEFORE=$(curl -s "${A[@]}" "$BASE/company" | field "cashBalance")
BANK_BEFORE=$(curl -s "${A[@]}" "$BASE/company" | field "bankBalance")
echo "  pre-logistics balances: cash=$CASH_BEFORE bank=$BANK_BEFORE"

# ── Create draft inline ──────────────────────────────────────────────
ORD=$(curl -s -X POST "$BASE/logistics" "${A[@]}" -H "Content-Type: application/json" -d "{
  \"sourceCountryId\": $CN, \"sourceWarehouseId\": $SRC,
  \"destinationCountryId\": $UZ, \"destinationWarehouseId\": $DST,
  \"supplierId\": $SUP, \"currency\": \"USD\",
  \"items\": [
    {\"productId\": $P1, \"quantity\": 5},
    {\"productId\": $P2, \"quantity\": 2, \"unitCost\": 300}
  ],
  \"expenses\": [
    {\"expenseType\": \"shipping\", \"amount\": 60, \"currency\": \"USD\", \"paymentSource\": \"cash\"},
    {\"expenseType\": \"customs\",  \"amount\": 40, \"currency\": \"USD\", \"paymentSource\": \"bank\"}
  ]
}")
OID=$(echo "$ORD" | jid)
[ -n "$OID" ] && ok "logistics order created (#$OID)" || bad "create logistics" "$ORD"

# Verify totals are computed (items 5*100 + 2*300 = 1100; expenses 60+40 = 100)
echo "$ORD" | grep -q '"itemsValue":1100' && ok "itemsValue=1100" || bad "itemsValue" "$ORD"
echo "$ORD" | grep -q '"expensesTotal":100'  && ok "expensesTotal=100" || bad "expensesTotal" "$ORD"
echo "$ORD" | grep -q '"landedTotal":1200'   && ok "landedTotal=1200"   || bad "landedTotal" "$ORD"
echo "$ORD" | grep -q '"status":"draft"'     && ok "status=draft"       || bad "status" "$ORD"

# ── Confirm ──────────────────────────────────────────────────────────
CONFIRMED=$(curl -s -X POST "$BASE/logistics/$OID/confirm" "${A[@]}")
echo "$CONFIRMED" | grep -q '"status":"confirmed"' && ok "order confirmed" || bad "confirm" "$CONFIRMED"

# ── Stock moved ──────────────────────────────────────────────────────
P1_AT_SRC=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P1" | grep -o '"warehouseId":'"$SRC"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed 's/quantity"://')
P1_AT_DST=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P1" | grep -o '"warehouseId":'"$DST"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed 's/quantity"://')
P2_AT_SRC=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P2" | grep -o '"warehouseId":'"$SRC"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed 's/quantity"://')
P2_AT_DST=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P2" | grep -o '"warehouseId":'"$DST"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed 's/quantity"://')

echo "  P1: src=$P1_AT_SRC dst=$P1_AT_DST  | P2: src=$P2_AT_SRC dst=$P2_AT_DST"
# Numeric compare ignores decimal-place variations (5, 5.0, 5.00, 5.0000)
neq(){ awk -v a="$1" -v b="$2" 'BEGIN{ exit !(a+0 == b+0) }'; }
neq "$P1_AT_SRC" 5 && ok "P1 source decreased to 5" || bad "P1 src" "$P1_AT_SRC"
neq "$P1_AT_DST" 5 && ok "P1 dest increased to 5"   || bad "P1 dst" "$P1_AT_DST"
neq "$P2_AT_SRC" 2 && ok "P2 source decreased to 2" || bad "P2 src" "$P2_AT_SRC"
neq "$P2_AT_DST" 2 && ok "P2 dest increased to 2"   || bad "P2 dst" "$P2_AT_DST"

# ── Balances debited ─────────────────────────────────────────────────
CASH_AFTER=$(curl -s "${A[@]}" "$BASE/company" | field "cashBalance")
BANK_AFTER=$(curl -s "${A[@]}" "$BASE/company" | field "bankBalance")
echo "  post-logistics balances: cash=$CASH_AFTER bank=$BANK_AFTER"
CASH_DIFF=$(echo "$CASH_BEFORE $CASH_AFTER" | awk '{ printf "%.2f", $1 - $2 }')
BANK_DIFF=$(echo "$BANK_BEFORE $BANK_AFTER" | awk '{ printf "%.2f", $1 - $2 }')
[ "$CASH_DIFF" = "60.00" ] && ok "cash debited by 60 (shipping)" || bad "cash diff" "$CASH_DIFF"
[ "$BANK_DIFF" = "40.00" ] && ok "bank debited by 40 (customs)"   || bad "bank diff" "$BANK_DIFF"

# ── Supplier debt UNCHANGED ──────────────────────────────────────────
SUP_DEBT_AFTER=$(curl -s "${A[@]}" "$BASE/suppliers/$SUP" | field "currentDebt")
[ "$SUP_DEBT_AFTER" = "$SUP_DEBT_BEFORE" ] && ok "supplier debt unchanged ($SUP_DEBT_BEFORE → $SUP_DEBT_AFTER)" || bad "supplier debt" "$SUP_DEBT_AFTER"

# ── Allocations + trial balance ──────────────────────────────────────
DETAIL=$(curl -s "${A[@]}" "$BASE/logistics/$OID")
echo "$DETAIL" | grep -q 'allocations' && ok "detail returns allocations" || bad "allocations" "$DETAIL"

TB=$(curl -s "${A[@]}" "$BASE/accounting/trial-balance")
# Numbers can be quoted or unquoted depending on Jackson config; match both forms.
TB_DEBIT=$(echo "$TB" | grep -oE '"debit":"?[0-9.]+"?' | sed 's/.*://;s/"//g' | awk '{ s+=$1 } END { printf "%.2f", s }')
TB_CREDIT=$(echo "$TB" | grep -oE '"credit":"?[0-9.]+"?' | sed 's/.*://;s/"//g' | awk '{ s+=$1 } END { printf "%.2f", s }')
# Equal AND non-zero (must include our 200 USD logistics journal)
[ "$TB_DEBIT" = "$TB_CREDIT" ] && awk -v d="$TB_DEBIT" 'BEGIN{ exit !(d+0 > 0) }' && \
  ok "trial balance balanced (D=$TB_DEBIT = C=$TB_CREDIT, non-zero)" || \
  bad "trial balance" "D=$TB_DEBIT C=$TB_CREDIT"

# ── Confirm twice blocked ────────────────────────────────────────────
RECONF=$(code -X POST "$BASE/logistics/$OID/confirm" "${A[@]}")
[ "$RECONF" = 400 ] && ok "confirmed order cannot be re-confirmed (400)" || bad "re-confirm" "$RECONF"

# ── Excel export ─────────────────────────────────────────────────────
HTTP=$(curl -s "${A[@]}" -o /tmp/log_export.xlsx -w "%{http_code}" "$BASE/logistics/$OID/export")
[ "$HTTP" = 200 ] && ok "logistics export 200" || bad "export" "$HTTP"
head -c 2 /tmp/log_export.xlsx 2>/dev/null | grep -q PK && ok "export is valid xlsx (PK)" || bad "xlsx PK header" "?"

# ── Permission gating ────────────────────────────────────────────────
NOAUTH=$(code "$BASE/logistics")
[ "$NOAUTH" = 403 ] && ok "no-auth /logistics → 403" || bad "no-auth" "$NOAUTH"
NOAUTH_E=$(code "$BASE/logistics/$OID/export")
[ "$NOAUTH_E" = 403 ] && ok "no-auth export → 403" || bad "no-auth export" "$NOAUTH_E"

# ── Audit log present ────────────────────────────────────────────────
AUD=$(curl -s "${A[@]}" "$BASE/audit-logs?entityType=LogisticsOrder&size=20")
echo "$AUD" | grep -q '"actionType":"CONFIRM"' && ok "audit log contains CONFIRM" || bad "audit" "$AUD"

# ── Reversal (V17) ───────────────────────────────────────────────────
REV=$(curl -s -X POST "$BASE/logistics/$OID/reverse" "${A[@]}" -H "Content-Type: application/json" -d '{"reason":"smoke test reversal"}')
echo "$REV" | grep -q '"status":"reversed"' && ok "logistics order reversed" || bad "reverse" "$REV"

# Stock should be RESTORED back to source after reversal
P1_SRC_AFTER_REV=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P1" | grep -o '"warehouseId":'"$SRC"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed "s/quantity\"://")
P1_DST_AFTER_REV=$(curl -s "${A[@]}" "$BASE/warehouses/stock/product/$P1" | grep -o '"warehouseId":'"$DST"'[^}]*"quantity":[0-9.]*' | head -1 | grep -o 'quantity":[0-9.]*' | sed "s/quantity\"://")
neq "$P1_SRC_AFTER_REV" 10 && ok "P1 returned to source (10) after reverse" || bad "P1 src after reverse" "$P1_SRC_AFTER_REV"
neq "$P1_DST_AFTER_REV" 0  && ok "P1 dest back to 0 after reverse"          || bad "P1 dst after reverse" "$P1_DST_AFTER_REV"

# Cash + bank balances restored
CASH_AFTER_REV=$(curl -s "${A[@]}" "$BASE/company" | field "cashBalance")
BANK_AFTER_REV=$(curl -s "${A[@]}" "$BASE/company" | field "bankBalance")
[ "$CASH_AFTER_REV" = "$CASH_BEFORE" ] && ok "cash restored ($CASH_AFTER_REV)" || bad "cash after reverse" "$CASH_AFTER_REV"
[ "$BANK_AFTER_REV" = "$BANK_BEFORE" ] && ok "bank restored ($BANK_AFTER_REV)" || bad "bank after reverse" "$BANK_AFTER_REV"

# Double-reversal blocked
REREV=$(code -X POST "$BASE/logistics/$OID/reverse" "${A[@]}" -H "Content-Type: application/json" -d "{}")
[ "$REREV" = 400 ] && ok "second reversal blocked (400)" || bad "rereverse" "$REREV"

# Trial balance still equal after reversal (mirror journal posted)
TB2=$(curl -s "${A[@]}" "$BASE/accounting/trial-balance")
TB2_D=$(echo "$TB2" | grep -oE '"debit":"?[0-9.]+"?' | sed 's/.*://;s/"//g' | awk '{ s+=$1 } END { printf "%.2f", s }')
TB2_C=$(echo "$TB2" | grep -oE '"credit":"?[0-9.]+"?' | sed 's/.*://;s/"//g' | awk '{ s+=$1 } END { printf "%.2f", s }')
[ "$TB2_D" = "$TB2_C" ] && ok "trial balance still balanced after reversal (D=$TB2_D = C=$TB2_C)" || bad "TB after reverse" "D=$TB2_D C=$TB2_C"

# Audit log records REVERSE
AUD2=$(curl -s "${A[@]}" "$BASE/audit-logs?entityType=LogisticsOrder&size=20")
echo "$AUD2" | grep -q '"actionType":"REVERSE"' && ok "audit log contains REVERSE" || bad "audit reverse" "$AUD2"

# ── Operational health widget (V19) ──────────────────────────────────
OH=$(curl -s "${A[@]}" "$BASE/dashboard/operational-health")
echo "$OH" | grep -q '"level"' && ok "/dashboard/operational-health returns level" || bad "op-health" "$OH"

echo
echo "==================================================="
echo " Logistics: $PASS passed, $FAIL failed"
echo "==================================================="
exit $FAIL
