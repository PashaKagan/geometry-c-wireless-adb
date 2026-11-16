#!/system/bin/sh
# Seat-preset fix: clears the vehicle-settings app's data. A reboot is needed for it
# to take effect (the Stick Builder reboots at the end when this toggle is selected).
# Use only when the seat preset is corrupted -- this wipes that app's settings.
pm clear com.neusoft.optimus.wheeljack.setting
