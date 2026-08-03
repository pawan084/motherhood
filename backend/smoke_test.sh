#!/usr/bin/env bash
# Post-deploy smoke test (sayli's pattern). Usage:
#   ./smoke_test.sh https://api.example.com
# Exits non-zero on the first failure. Read-only: no data is written.
set -euo pipefail

BASE="${1:?usage: smoke_test.sh <base-url>}"

fail() { echo "FAIL  $1"; exit 1; }
pass() { echo "PASS  $1"; }

# 1. Liveness.
curl -fsS "$BASE/health" | grep -q '"ok":true' || fail "/health"
pass "/health"

# 2. Catalogs present and complete.
CONFIG=$(curl -fsS "$BASE/config")
echo "$CONFIG" | grep -q '"safety_gate_enabled":true' || fail "/config safety gate flag"
echo "$CONFIG" | grep -q 'trying_to_conceive' || fail "/config journey stages"
echo "$CONFIG" | grep -q 'hi-Latn' || fail "/config languages"
pass "/config"

# 3. Learner routes reject unauthenticated calls (the IDOR posture).
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/care-context")
[ "$CODE" = "401" ] || fail "unauthenticated /care-context expected 401, got $CODE"
pass "auth required on learner routes"

# 4. Device registration mints a token (guest surface alive).
curl -fsS -X POST "$BASE/device/register" | grep -q device_token || fail "/device/register"
pass "/device/register"

# 5. Admin surface is up and closed.
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/admin/overview")
[ "$CODE" = "401" ] || fail "unauthenticated /admin/overview expected 401, got $CODE"
pass "admin closed"

echo "ALL SMOKE CHECKS PASSED"
