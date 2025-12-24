# Avtodigix UI Flow

## Overview
The UI is a five-screen, linear onboarding flow with explicit **Back**/**Next** navigation. Each screen is full-screen and scrollable, allowing dense status and warning content.

**Navigation order:**
1. Welcome/Onboarding
2. Connection
3. Summary (traffic-light cards)
4. Live Metrics
5. DTC List

The **Finish** action returns to the Welcome screen.

## Screen Details

### 1) Welcome/Onboarding
**Purpose:** Set expectations and provide safety guidance before diagnostics start.

**Key strings:**
- "Welcome to Avtodigix"
- "A guided start for your vehicle health session."
- "You will review: connection setup, a traffic-light summary, live metrics, and diagnostic trouble codes."
- "Safety reminder: keep the vehicle in Park, engage the parking brake, and work in a well-ventilated area."

**Components:**
- Headline text
- Body copy with session overview
- Safety warning text (red)
- Primary button: **Get started**

### 2) Connection
**Purpose:** Show pairing status, checks, and driving warning.

**Key strings:**
- "Status: Not connected"
- "Plug in the OBD-II adapter, enable Bluetooth, and select “Avtodigix” to pair."
- "Connection checklist: adapter powered, Bluetooth on, VIN detected, and data link stable."
- "Warning: do not drive while pairing or running diagnostics."

**Components:**
- Status card (MaterialCardView)
- Checklist text
- Warning text
- Navigation: **Back** / **Next**

### 3) Summary (Traffic-Light Cards)
**Purpose:** Provide an urgency-based snapshot of system health.

**Key strings:**
- "System Summary"
- "Traffic-light status cards highlight urgency."
- Powertrain: "GREEN — Stable"
- Emissions: "YELLOW — Needs attention"
- Braking: "RED — Critical"

**Components:**
- Three colored cards (green/yellow/red) with title, status, and explanation
- Footer guidance: "Tap Next to review live metrics and thresholds."
- Navigation: **Back** / **Next**

### 4) Live Metrics
**Purpose:** Show sample real-time telemetry with thresholds and warnings.

**Key strings:**
- "Live Metrics"
- "Streaming data with thresholds and context."
- "Engine temperature: 92°C (Normal)"
- "Battery voltage: 12.1V (Low — charge soon)"
- "Fuel trim: +8% (Within range)"
- "Oil pressure: 28 psi (Observe — low at idle)"
- "Warning: if a metric turns red, pull over and review DTCs."

**Components:**
- Metrics list (TextView rows)
- Warning text
- Navigation: **Back** / **Next**

### 5) DTC List
**Purpose:** Communicate diagnostic trouble codes with severity and guidance.

**Key strings:**
- "Diagnostic Trouble Codes"
- "Active and stored codes with severity and guidance."
- "P0301 — Misfire detected (Cylinder 1) • Severity: Critical"
- "P0420 — Catalyst efficiency below threshold • Severity: Moderate"
- "U0121 — Lost communication with ABS module • Severity: Critical"
- "Explanation: clearing codes does not fix the root cause. Resolve faults before clearing."

**Components:**
- Three DTC cards (code + action guidance)
- Explanation footer
- Navigation: **Back** / **Finish**
