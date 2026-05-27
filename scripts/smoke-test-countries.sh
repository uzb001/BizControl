#!/usr/bin/env bash
# =====================================================================
# Countries module smoke test (V15)
# Exercises:
#   - countries CRUD + archive/restore lifecycle
#   - case-insensitive duplicate prevention
#   - country_id FK enforced on warehouses + suppliers
#   - delete blocked while referenced (archive instead)
#   - permission gating (no-auth → 403)
#
#   bash scripts/smoke-test-countries.sh [BASE_URL]
#   default BASE_URL = http://localhost:8080/api
# =====================================================================
set -u
BASE="${1:-http://localhost:8080/api}"
TS=$(date +%s)
PASS=0; FAIL=0
ok(){ echo "  PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "  FAIL: $1 (got: $2)"; FAIL=$((FAIL+1)); }
code(){ curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== Countries module smoke =="
SIGNUP=$(curl -s -X POST "$BASE/auth/signup" -H "Content-Type: application/json" \
  -d "{\"fullName\":\"CountryTest\",\"email\":\"countries_${TS}@test.com\",\"password\":\"Passw0rd!\",\"companyName\":\"CntryCo $TS\",\"businessType\":\"trading\",\"mainCurrency\":\"UZS\"}")
TOKEN=$(echo "$SIGNUP" | grep -o "\"token\":\"[^\"]*\"" | head -1 | sed "s/\"token\":\"//;s/\"//")
[ ${#TOKEN} -gt 20 ] || { echo "signup failed: $SIGNUP"; exit 1; }
A=(-H "Authorization: Bearer $TOKEN")
jid(){ grep -o "\"id\":[0-9]*" | head -1 | sed "s/\"id\"://"; }

LIST=$(curl -s "${A[@]}" "$BASE/countries")
[ "$LIST" = "[]" ] && ok "countries list empty for new company" || bad "list empty" "$LIST"

CHINA=$(curl -s -X POST "$BASE/countries" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"China","code":"CN","currency":"CNY","timezone":"Asia/Shanghai","language":"zh"}')
CN_ID=$(echo "$CHINA" | jid)
[ -n "$CN_ID" ] && ok "created China #$CN_ID" || bad "create china" "$CHINA"

DUP=$(code -X POST "$BASE/countries" "${A[@]}" -H "Content-Type: application/json" -d '{"name":"china"}')
[ "$DUP" = 400 ] && ok "duplicate (case-insensitive) rejected" || bad "duplicate" "$DUP"

UPD=$(code -X PUT "$BASE/countries/$CN_ID" "${A[@]}" -H "Content-Type: application/json" -d '{"timezone":"Asia/Beijing"}')
[ "$UPD" = 200 ] && ok "country updated" || bad "update" "$UPD"

RU_ID=$(curl -s -X POST "$BASE/countries" "${A[@]}" -H "Content-Type: application/json" \
  -d '{"name":"Russia","code":"RU","currency":"RUB"}' | jid)
[ -n "$RU_ID" ] && ok "created Russia #$RU_ID" || bad "create russia" "?"

LIST_ACT=$(curl -s "${A[@]}" "$BASE/countries?status=active")
echo "$LIST_ACT" | grep -q China && echo "$LIST_ACT" | grep -q Russia && ok "active list contains both" || bad "active list" "$LIST_ACT"

W=$(curl -s -X POST "$BASE/warehouses" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"name\":\"Shanghai DC\",\"code\":\"SH\",\"type\":\"main\",\"countryId\":$CN_ID}")
WID=$(echo "$W" | jid)
[ -n "$WID" ] && ok "warehouse with countryId created (#$WID)" || bad "warehouse create" "$W"
echo "$W" | grep -q "\"countryId\":$CN_ID" && ok "warehouse persists countryId" || bad "wh countryId" "$W"

BAD=$(code -X POST "$BASE/warehouses" "${A[@]}" -H "Content-Type: application/json" -d '{"name":"Bogus","type":"main","countryId":99999}')
[ "$BAD" = 404 ] && ok "warehouse bogus countryId → 404" || bad "wh bogus" "$BAD"

S=$(curl -s -X POST "$BASE/suppliers" "${A[@]}" -H "Content-Type: application/json" \
  -d "{\"name\":\"Beijing Mfg\",\"phone\":\"+861234\",\"countryId\":$CN_ID,\"currency\":\"CNY\"}")
SID=$(echo "$S" | jid)
[ -n "$SID" ] && ok "supplier with countryId created (#$SID)" || bad "supplier create" "$S"

DEL=$(code -X DELETE "$BASE/countries/$CN_ID" "${A[@]}")
[ "$DEL" = 400 ] && ok "delete-in-use blocked (400)" || bad "delete blocked" "$DEL"

ARC=$(code -X POST "$BASE/countries/$CN_ID/archive" "${A[@]}")
[ "$ARC" = 200 ] && ok "archive while in use OK" || bad "archive" "$ARC"

RES=$(code -X POST "$BASE/countries/$CN_ID/restore" "${A[@]}")
[ "$RES" = 200 ] && ok "restore archived OK" || bad "restore" "$RES"

DEL_RU=$(code -X DELETE "$BASE/countries/$RU_ID" "${A[@]}")
[ "$DEL_RU" = 204 ] && ok "delete unreferenced country OK (204)" || bad "delete unref" "$DEL_RU"

NOAUTH=$(code "$BASE/countries")
[ "$NOAUTH" = 403 ] && ok "no-auth → 403" || bad "no-auth" "$NOAUTH"

echo
echo "==================================================="
echo " Countries: $PASS passed, $FAIL failed"
echo "==================================================="
exit $FAIL
