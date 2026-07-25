#!/bin/bash

echo "--- Android Emulator Diagnostic & Fix ---"

# Check if ADB is available
ADB="/Users/antonio-bravo/Library/Android/sdk/platform-tools/adb"
if [ ! -f "$ADB" ]; then
    ADB="adb"
fi

echo "Step 1: Killing all lingering processes..."
killall adb 2>/dev/null
$ADB start-server
sleep 2

echo "Step 2: Testing connection..."
$ADB devices
RESPONSE=$($ADB -s emulator-5554 shell ls / 2>&1)

if [[ $RESPONSE == *"closed"* ]]; then
    echo "--------------------------------------------------------"
    echo "DIAGNOSIS: Internal Emulator Failure (Disk/System corruption)."
    echo "The simple restart didn't work. Escalating to Level 2."
    echo "--------------------------------------------------------"
    echo ""
    echo "SOLUTION (WIPE DATA):"
    echo "1. Close the emulator."
    echo "2. In Device Manager, click (⋮) on your device."
    echo "3. Select 'WIPE DATA' (This resets the phone to factory settings)."
    echo "4. Start the emulator again."
    echo ""
    echo "If Wipe Data fails, delete the emulator and create a new one using API 35 or 34."
    echo "--------------------------------------------------------"
else
    echo "SUCCESS: Emulator responded. Try running the app."
fi
