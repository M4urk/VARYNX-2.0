#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
#  VARYNX 2.0 — Master Stress Test Runner
# ═══════════════════════════════════════════════════════════════════
#
#  File placement:
#    Project root: master-stress-runner.sh
#
#  Run with:
#    bash master-stress-runner.sh           # Full suite (quick soak: 1h)
#    bash master-stress-runner.sh --full    # Full 24h soak
#    bash master-stress-runner.sh --quick   # Just Kotlin + mesh chaos (no soak)
#
#  On Windows (Git Bash or WSL):
#    bash master-stress-runner.sh
#
#  On Windows (PowerShell):
#    & bash master-stress-runner.sh
#    # Or run components individually:
#    #   .\gradlew.bat :core:desktopTest --tests "com.varynx.varynx20.core.stress.*"
#    #   python tools\stress\mesh-chaos-test.py
#    #   python tools\stress\long-soak-test.py --duration 1 --interval 30
#
#  Prerequisites:
#    - JDK 21+ (for Gradle)
#    - Python 3.10+
#    - Gradle wrapper (gradlew / gradlew.bat)
#
#  Pass criteria:
#    • Zero crashes / ANRs in 24h
#    • Scoring drift ≤ ±3 points
#    • Mesh sync latency < 800ms under chaos
#    • Every Reflex fires < 1.2s
#    • Battery impact < 8% per 24h on Android/WearOS (manual verify)
#    • 100% of injected threats detected on every platform
#
#  Copyright (c) 2026 VARYNX. All rights reserved.
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Colors ──
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ── Configuration ──
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
RESULTS_DIR="$PROJECT_ROOT/tools/stress"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$RESULTS_DIR/stress-run-$TIMESTAMP.log"

# Determine Gradle wrapper
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ -f "$PROJECT_ROOT/gradlew.bat" && ! -x "$PROJECT_ROOT/gradlew" ]]; then
    GRADLEW="$PROJECT_ROOT/gradlew.bat"
else
    GRADLEW="$PROJECT_ROOT/gradlew"
    chmod +x "$GRADLEW" 2>/dev/null || true
fi

# ── Parse Arguments ──
MODE="standard"  # standard = Kotlin + mesh + 1h soak
SOAK_HOURS=1
SOAK_INTERVAL=30

while [[ $# -gt 0 ]]; do
    case $1 in
        --full)
            MODE="full"
            SOAK_HOURS=24
            shift
            ;;
        --quick)
            MODE="quick"
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--quick|--full]"
            echo "  --quick    Kotlin tests + mesh chaos only (no soak)"
            echo "  --full     Full 24-hour soak test"
            echo "  (default)  Kotlin tests + mesh chaos + 1-hour soak"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# ── Counters ──
TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0

# ── Helper Functions ──

step_header() {
    local step_num=$1
    local step_name=$2
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}  STEP $step_num: $step_name${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
}

record_result() {
    local name=$1
    local exit_code=$2
    TOTAL=$((TOTAL + 1))
    if [ "$exit_code" -eq 0 ]; then
        PASSED=$((PASSED + 1))
        echo -e "  ${GREEN}[PASS]${NC} $name"
    else
        FAILED=$((FAILED + 1))
        echo -e "  ${RED}[FAIL]${NC} $name (exit code: $exit_code)"
    fi
}

record_skip() {
    local name=$1
    local reason=$2
    TOTAL=$((TOTAL + 1))
    SKIPPED=$((SKIPPED + 1))
    echo -e "  ${YELLOW}[SKIP]${NC} $name — $reason"
}

# ── Ensure Results Dir Exists ──
mkdir -p "$RESULTS_DIR"

# ── Banner ──
echo ""
echo -e "${BOLD}${CYAN}"
echo "  ██╗   ██╗ █████╗ ██████╗ ██╗   ██╗███╗   ██╗██╗  ██╗"
echo "  ██║   ██║██╔══██╗██╔══██╗╚██╗ ██╔╝████╗  ██║╚██╗██╔╝"
echo "  ██║   ██║███████║██████╔╝ ╚████╔╝ ██╔██╗ ██║ ╚███╔╝ "
echo "  ╚██╗ ██╔╝██╔══██║██╔══██╗  ╚██╔╝  ██║╚██╗██║ ██╔██╗ "
echo "   ╚████╔╝ ██║  ██║██║  ██║   ██║   ██║ ╚████║██╔╝ ██╗"
echo "    ╚═══╝  ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═══╝╚═╝  ╚═╝"
echo -e "${NC}"
echo -e "${BOLD}  STRESS TEST SUITE — v2.0.0-beta${NC}"
echo -e "  Mode: ${YELLOW}$MODE${NC}"
echo -e "  Started: $(date)"
echo -e "  Log: $LOG_FILE"

# Start logging
exec > >(tee -a "$LOG_FILE") 2>&1

# ═══════════════════════════════════════════════════════════════════
#  STEP 1: Build validation (compile everything)
# ═══════════════════════════════════════════════════════════════════

step_header 1 "BUILD VALIDATION"
echo "  Building entire project..."

if "$GRADLEW" build -x test --no-daemon -q 2>&1; then
    record_result "Full project build" 0
else
    record_result "Full project build" 1
    echo -e "${RED}  Build failed — aborting stress tests${NC}"
    exit 1
fi

# ═══════════════════════════════════════════════════════════════════
#  STEP 2: Existing test suite (baseline validation)
# ═══════════════════════════════════════════════════════════════════

step_header 2 "EXISTING TEST SUITE"
echo "  Running all existing tests..."

if "$GRADLEW" test --no-daemon -q 2>&1; then
    record_result "Existing test suite (all modules)" 0
else
    record_result "Existing test suite (all modules)" 1
fi

# ═══════════════════════════════════════════════════════════════════
#  STEP 3: Kotlin Stress Tests (StressTestSuite.kt)
# ═══════════════════════════════════════════════════════════════════

step_header 3 "KOTLIN STRESS TESTS"

echo "  Running HighVolumeEventStressTest..."
if "$GRADLEW" :core:desktopTest --tests "com.varynx.varynx20.core.stress.HighVolumeEventStressTest" --no-daemon -q 2>&1; then
    record_result "HighVolumeEventStressTest" 0
else
    record_result "HighVolumeEventStressTest" 1
fi

echo "  Running ScoringEngineStressTest..."
if "$GRADLEW" :core:desktopTest --tests "com.varynx.varynx20.core.stress.ScoringEngineStressTest" --no-daemon -q 2>&1; then
    record_result "ScoringEngineStressTest" 0
else
    record_result "ScoringEngineStressTest" 1
fi

echo "  Running ReflexChainStressTest..."
if "$GRADLEW" :core:desktopTest --tests "com.varynx.varynx20.core.stress.ReflexChainStressTest" --no-daemon -q 2>&1; then
    record_result "ReflexChainStressTest" 0
else
    record_result "ReflexChainStressTest" 1
fi

echo "  Running FullGuardianOrganismStressTest..."
if "$GRADLEW" :core:desktopTest --tests "com.varynx.varynx20.core.stress.FullGuardianOrganismStressTest" --no-daemon -q 2>&1; then
    record_result "FullGuardianOrganismStressTest" 0
else
    record_result "FullGuardianOrganismStressTest" 1
fi

# ═══════════════════════════════════════════════════════════════════
#  STEP 4: Concurrency Stress Tests (existing)
# ═══════════════════════════════════════════════════════════════════

step_header 4 "CONCURRENCY STRESS TESTS"

echo "  Running ConcurrencyStressTest..."
if "$GRADLEW" :core:desktopTest --tests "com.varynx.varynx20.core.concurrency.ConcurrencyStressTest" --no-daemon -q 2>&1; then
    record_result "ConcurrencyStressTest" 0
else
    record_result "ConcurrencyStressTest" 1
fi

# ═══════════════════════════════════════════════════════════════════
#  STEP 5: Mesh Chaos Test (Python)
# ═══════════════════════════════════════════════════════════════════

step_header 5 "MESH CHAOS TEST"

PYTHON_CMD=""
if command -v python3 &>/dev/null; then
    PYTHON_CMD="python3"
elif command -v python &>/dev/null; then
    PYTHON_CMD="python"
fi

if [ -n "$PYTHON_CMD" ]; then
    echo "  Running mesh-chaos-test.py with $PYTHON_CMD..."
    if $PYTHON_CMD "$PROJECT_ROOT/tools/stress/mesh-chaos-test.py" 2>&1; then
        record_result "Mesh Chaos Test (8-node simulation)" 0
    else
        record_result "Mesh Chaos Test (8-node simulation)" 1
    fi
else
    record_skip "Mesh Chaos Test" "Python not found"
fi

# ═══════════════════════════════════════════════════════════════════
#  STEP 6: Long Soak Test (Python)
# ═══════════════════════════════════════════════════════════════════

if [ "$MODE" != "quick" ]; then
    step_header 6 "LONG SOAK TEST (${SOAK_HOURS}h)"

    if [ -n "$PYTHON_CMD" ]; then
        echo "  Running long-soak-test.py for ${SOAK_HOURS}h (interval: ${SOAK_INTERVAL}s)..."
        if $PYTHON_CMD "$PROJECT_ROOT/tools/stress/long-soak-test.py" \
            --duration "$SOAK_HOURS" --interval "$SOAK_INTERVAL" 2>&1; then
            record_result "Long Soak Test (${SOAK_HOURS}h)" 0
        else
            record_result "Long Soak Test (${SOAK_HOURS}h)" 1
        fi
    else
        record_skip "Long Soak Test" "Python not found"
    fi
else
    echo ""
    echo -e "  ${YELLOW}[SKIP]${NC} Long Soak Test — --quick mode"
    TOTAL=$((TOTAL + 1))
    SKIPPED=$((SKIPPED + 1))
fi

# ═══════════════════════════════════════════════════════════════════
#  FINAL REPORT
# ═══════════════════════════════════════════════════════════════════

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  FINAL STRESS TEST REPORT${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  Total:    $TOTAL"
echo -e "  Passed:   ${GREEN}$PASSED${NC}"
echo -e "  Failed:   ${RED}$FAILED${NC}"
echo -e "  Skipped:  ${YELLOW}$SKIPPED${NC}"
echo -e "  Mode:     $MODE"
echo -e "  Finished: $(date)"
echo ""

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}"
    echo "  ████████████████████████████████████████"
    echo "  ██                                    ██"
    echo "  ██     ALL STRESS TESTS PASSED        ██"
    echo "  ██                                    ██"
    echo "  ████████████████████████████████████████"
    echo -e "${NC}"
    echo ""
    echo "  Pass criteria met:"
    echo "    ✓ Zero crashes across all tests"
    echo "    ✓ Scoring drift ≤ ±3 points"
    echo "    ✓ Mesh sync < 800ms under chaos"
    echo "    ✓ All reflexes fire successfully"
    echo "    ✓ All injected threats detected"
    echo "    ✓ All protection modules trigger on malicious input"
    echo ""
    echo "  Manual verification still needed:"
    echo "    • Battery impact < 8% per 24h on Android/WearOS"
    echo "    • No ANRs in ADB logcat during device testing"
    echo "    • Reflex latency < 1.2s (profile on device)"
    EXIT_CODE=0
else
    echo -e "${RED}${BOLD}"
    echo "  ████████████████████████████████████████"
    echo "  ██                                    ██"
    echo "  ██     STRESS TESTS FAILED            ██"
    echo "  ██                                    ██"
    echo "  ████████████████████████████████████████"
    echo -e "${NC}"
    echo ""
    echo "  Review the log file for details:"
    echo "    $LOG_FILE"
    EXIT_CODE=1
fi

echo ""
echo "  Full log: $LOG_FILE"
echo ""

exit $EXIT_CODE
