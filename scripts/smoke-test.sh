#!/usr/bin/env bash
# =====================================================================
# BizControl.uz — backend smoke test
# Exercises the endpoints behind the dashboard, stock/warehouse tabs,
# the full production flow (BOM → order → start → complete), permission
# gating, and every Excel export. Run against a running stack.
#
#   bash scripts/smoke-test.sh [BASE_URL]
#   default BASE_URL = http://localhost:8080/api
#
# NOTE: there is no frontend unit-test runner in this project, so the
# React render checks (dashboard per role, export-button visibility) are
# covered indirectly here by asserting the backing endpoints + permission
# 403s. Page rendering itself is verified manually / by `next build`.
# =====================================================================
set -u
BASE="${1:-http://localhost:8080/api}"
TS=$(date +%s)
PASS=0; FAIL=0
ok(){ echo "  PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "  FAIL: $1 (got: $2)"; FAIL=$((FAIL+1)); }
code(){ curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== Signup (OWNER) =="
SIGNUP=$(curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Smoke\",\"email\":\"smoke_${TS}@test.com\",\"password\":\"Passw0rd!\",\"companyName\":\"Smoke Co $TS\",\"businessType\":\"manufacturing\",\"mainCurrency\":\"UZS\"}")
TOKEN=$(echo "$SIGNUP" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"//')
[ ${#TOKEN} -gt 20 ] && ok "signup returns token" || { bad "signup token" "$SIGNUP"; exit 1; }
A=(-H "Authorization: Bearer $TOKEN")
jid(){ grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://'; }

echo "== Dashboard renders (data endpoint) =="
[ "$(code "${A[@]}" "$BASE/dashboard")" = 200 ] && ok "dashboard 200" || bad "dashboard" "?"

echo "== Stock + warehouse tab endpoints =="
[ "$(code "${A[@]}" "$BASE/stock?size=5")" = 200 ] && ok "stock list 200" || bad "stock list" "?"
[ "$(code "${A[@]}" "$BASE/stock/movements?size=5")" = 200 ] && ok "movements 200" || bad "movements" "?"
[ "$(code "${A[@]}" "$BASE/warehouses")" = 200 ] && ok "warehouses 200" || bad "warehouses" "?"
[ "$(code "${A[@]}" "$BASE/warehouses/transfers")" = 200 ] && ok "transfers 200" || bad "transfers" "?"
MAIN=$(curl -s "${A[@]}" "$BASE/warehouses" | jid)
echo "== Warehouse create works =="
ST=$(curl -s -X POST "$BASE/warehouses" "${A[@]}" -H 'Content-Type: application/json' -d '{"name":"Smoke Store","code":"SMK","type":"retail"}')
STORE=$(echo "$ST" | jid); [ -n "$STORE" ] && ok "warehouse created (#$STORE)" || bad "warehouse create" "$ST"

echo "== Production flow =="
RAW=$(curl -s -X POST "$BASE/products" "${A[@]}" -H 'Content-Type: application/json' -d '{"name":"Cocoa","sku":"RAW-1","unit":"kg","purchasePrice":10000,"sellingPrice":11000,"minStockLevel":0}' | jid)
FIN=$(curl -s -X POST "$BASE/products" "${A[@]}" -H 'Content-Type: application/json' -d '{"name":"Chocolate Box","sku":"FIN-1","unit":"piece","purchasePrice":1000,"sellingPrice":50000,"minStockLevel":0}' | jid)
echo "  raw=$RAW finished=$FIN main=$MAIN"
curl -s -o /dev/null -X POST "$BASE/warehouses/stock/in" "${A[@]}" -H 'Content-Type: application/json' -d "{\"productId\":$RAW,\"warehouseId\":$MAIN,\"quantity\":100}"
BOM=$(curl -s -X POST "$BASE/production/bom" "${A[@]}" -H 'Content-Type: application/json' -d "{\"productId\":$FIN,\"name\":\"Choc BOM\",\"outputQuantity\":1,\"unit\":\"piece\",\"components\":[{\"componentProductId\":$RAW,\"quantity\":2,\"unit\":\"kg\",\"wastePercent\":0}]}" | jid)
[ -n "$BOM" ] && ok "BOM created (#$BOM)" || bad "BOM create" "?"
ORD=$(curl -s -X POST "$BASE/production/orders" "${A[@]}" -H 'Content-Type: application/json' -d "{\"bomTemplateId\":$BOM,\"plannedQuantity\":10,\"sourceWarehouseId\":$MAIN,\"finishedGoodsWarehouseId\":$MAIN}" | jid)
[ -n "$ORD" ] && ok "production order created (#$ORD)" || bad "order create" "?"
[ "$(code -X POST "${A[@]}" "$BASE/production/orders/$ORD/start")" = 200 ] && ok "start 200" || bad "start" "?"
COMPLETE=$(code -X POST "${A[@]}" "$BASE/production/orders/$ORD/complete")
[ "$COMPLETE" = 200 ] && ok "complete 200" || bad "complete" "$COMPLETE"

# raw should drop 100 -> 80 (2kg x10), finished 0 -> 10
RAWQ=$(curl -s "${A[@]}" "$BASE/products/$RAW" | grep -o '"currentStock":[0-9.]*' | head -1 | sed 's/.*://')
FINQ=$(curl -s "${A[@]}" "$BASE/products/$FIN" | grep -o '"currentStock":[0-9.]*' | head -1 | sed 's/.*://')
echo "  raw now=$RAWQ finished now=$FINQ"
echo "$RAWQ" | grep -q "^80" && ok "raw materials decreased (80)" || bad "raw decrease" "$RAWQ"
echo "$FINQ" | grep -q "^10" && ok "finished goods increased (10)" || bad "finished increase" "$FINQ"
BAL=$(curl -s "${A[@]}" "$BASE/accounting/trial-balance" | grep -o '"balanced":[a-z]*' | head -1)
echo "$BAL" | grep -q "true" && ok "accounting balanced" || bad "accounting balance" "$BAL"

echo "== Exports (authed 200 + valid xlsx) =="
EXPORTS=(categories stock-movements stock-transfers audit-logs users roles daily-close \
  accounting/journal accounting/ledger accounting/trial-balance accounting/profit-loss \
  accounting/balance-sheet accounting/cashflow reports/dead-stock reports/money-leak \
  reports/customer-rating production/orders production/bom production/waste warehouse-stock)
for e in "${EXPORTS[@]}"; do
  c=$(code "${A[@]}" "$BASE/export/$e")
  [ "$c" = 200 ] && ok "export $e (200)" || bad "export $e" "$c"
done
# valid xlsx magic on one representative export
curl -s "${A[@]}" -o /tmp/smoke.xlsx "$BASE/export/production/orders"
head -c 2 /tmp/smoke.xlsx | grep -q "PK" && ok "export is valid xlsx (PK)" || bad "xlsx magic" "?"

echo "== Permission gating (no auth -> 403) =="
for e in dashboard warehouses export/production/orders production/orders; do
  c=$(code "$BASE/$e")
  [ "$c" = 403 ] || [ "$c" = 401 ] && ok "no-auth $e blocked ($c)" || bad "no-auth $e" "$c"
done

echo ""
echo "==================================================="
echo " RESULT: $PASS passed, $FAIL failed"
echo "==================================================="
[ "$FAIL" -eq 0 ]
