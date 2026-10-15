#!/bin/bash
# ============================================
# Run Script for greenfield
# Launches both flows (TMS -> broker orders, broker events -> TMS) with their Groovy beans.
# ============================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v jbang &>/dev/null; then
  echo "ERROR: jbang not found. Install from https://www.jbang.dev/installation/" >&2
  exit 1
fi

missing=()
for var in SANDBOX_CLIENT_ID SANDBOX_CLIENT_SECRET SANDBOX_INBOUND_API_KEY; do
  if [[ -z "${!var:-}" ]]; then
    missing+=("$var")
  fi
done
if (( ${#missing[@]} > 0 )); then
  echo "ERROR: required environment variable(s) not set: ${missing[*]}" >&2
  exit 1
fi

echo "Starting greenfield integration..."
jbang camel@apache/camel run \
  tms-shipment-to-broker-order.camel.yaml \
  broker-event-to-tms-event.camel.yaml \
  ShipmentToBrokerOrderMapper.groovy RejectedShipments.groovy BrokerEventToTmsEventMapper.groovy \
  --camel-version=4.22.1 \
  --properties=application.properties
