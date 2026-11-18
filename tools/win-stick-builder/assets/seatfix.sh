#!/system/bin/sh
# Seat-preset fix: clears the vehicle-settings app's data. A reboot is needed for it
# to take effect (the Stick Builder reboots at the end when this toggle is selected).
#
# !! RISKY AND UNVERIFIED !!
# Use ONLY when the seat preset is already corrupted. This runs `pm clear`, which wipes the
# whole state of that app. A community report warns it may clear ALL of your user settings,
# not just the seat preset. It has NOT been verified here. If the seat preset works, do not run this.
pm clear com.neusoft.optimus.wheeljack.setting
