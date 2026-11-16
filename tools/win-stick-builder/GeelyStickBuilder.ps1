<#
  Geely Geometry C — USB Stick Builder (Windows)
  Detects a USB drive, formats it FAT32, and writes a boot-hook stick that applies
  the features you tick. Pure-shell features are self-contained; features needing
  binaries read them from the .\assets folder next to this script and auto-disable
  if the asset is missing.

  Run via Run-GeelyStickBuilder.cmd (self-elevates; formatting needs admin).
  No warranty. Formatting ERASES the chosen drive. See the repo docs.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ---- paths --------------------------------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AssetsDir = Join-Path $ScriptDir 'assets'

# exact names the firmware hook looks for
$TrigDir   = 'b832bc61472727635baffcf25dd28e9f239273e2'
$TrigInner = 'd4ad0e797c738a44a9dd1b9231d201e8374dfda2.sh'

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

# ---- feature catalogue --------------------------------------------------
# Each feature: Key, Label, Help, SystemWrite (needs /system rw), Assets (files
# that must exist in .\assets for the feature to be buildable).
$Features = @(
    [pscustomobject]@{ Key='wadb';   Label='Enable wireless ADB (TCP 5555, persistent)';          Help='Starts adbd on 5555, port persists across cold boot.'; SystemWrite=$false; Assets=@() }
    [pscustomobject]@{ Key='root';   Label='Install persistent root (patched adbd + su daemon)';   Help='Root over wireless ADB and su for allow-listed apps. No boot flash.'; SystemWrite=$true;  Assets=@('adbd_root','rootadb.sh','geelysu','su','geelysu.allow') }
    [pscustomobject]@{ Key='wheel';  Label='Steering-wheel button fix (media next/prev/play)';     Help='Installs the log-tailing daemon and autostarts it.'; SystemWrite=$true;  Assets=@('steering.sh') }
    [pscustomobject]@{ Key='setapp'; Label='Install Settings shortcut app';                        Help='Adds an icon that opens the full Android Settings.'; SystemWrite=$false; Assets=@('Settings_1.0.apk') }
    [pscustomobject]@{ Key='anim';   Label='Set UI animation scale';                               Help='Lighter/snappier UI. Choose the scale on the right.'; SystemWrite=$false; Assets=@() }
    [pscustomobject]@{ Key='seat';   Label='Seat-preset fix';                                      Help='Runs assets\seatfix.sh (you provide the exact command).'; SystemWrite=$false; Assets=@('seatfix.sh') }
    [pscustomobject]@{ Key='backup'; Label='Read-only partition backup to the stick';              Help='dd of the critical small partitions (never touches resources). Cannot brick.'; SystemWrite=$false; Assets=@() }
    [pscustomobject]@{ Key='apps';   Label='Install any extra APKs in assets\apps\';               Help='pm install every *.apk found in assets\apps.'; SystemWrite=$false; Assets=@() }
)

function Test-Asset([string]$name) { Test-Path (Join-Path $AssetsDir $name) }
function Test-FeatureReady($f) {
    if ($f.Key -eq 'apps') {
        $d = Join-Path $AssetsDir 'apps'
        return (Test-Path $d) -and (@(Get-ChildItem -Path $d -Filter *.apk -File -ErrorAction SilentlyContinue).Count -gt 0)
    }
    foreach ($a in $f.Assets) { if (-not (Test-Asset $a)) { return $false } }
    return $true
}

# ---- USB drive detection (safe: USB bus only, never the system drive) ---
function Get-UsbDrives {
    $sys = ($env:SystemDrive).TrimEnd(':')
    $out = @()
    try {
        Get-Disk -ErrorAction Stop | Where-Object { $_.BusType -eq 'USB' } | ForEach-Object {
            $disk = $_
            Get-Partition -DiskNumber $disk.Number -ErrorAction SilentlyContinue |
                Where-Object { $_.DriveLetter } | ForEach-Object {
                    $dl = [string]$_.DriveLetter
                    if ($dl -and ($dl.ToUpper() -ne $sys.ToUpper())) {
                        $vol = $null
                        try { $vol = Get-Volume -DriveLetter $dl -ErrorAction Stop } catch {}
                        $out += [pscustomobject]@{
                            Letter  = "$dl`:"
                            Label   = if ($vol -and $vol.FileSystemLabel) { $vol.FileSystemLabel } else { '(no label)' }
                            SizeGB  = [math]::Round($disk.Size / 1GB, 1)
                            Disk    = $disk.Number
                        }
                    }
                }
        }
    } catch {}
    $out
}

# ---- shell (done.sh) generation ----------------------------------------
# Built from single-quoted lines so no PowerShell interpolation leaks in.
# Writes LF, no BOM.
function Build-DoneSh([hashtable]$sel, [string]$animVal) {
    $nl = "`n"
    $sb = New-Object System.Text.StringBuilder
    function L($s) { [void]$sb.Append($s); [void]$sb.Append($nl) }

    L '#!/system/bin/sh'
    L '# Generated by Geely Stick Builder. Runs as root via the USB boot hook.'
    L 'USB=/mnt/media_rw/udisk1'
    L 'BB=/system/bin/busybox'
    L 'SYS=/dev/block/platform/mtk-msdc.0/by-name/system'
    L 'IR=/system/bin/install-recovery.sh'
    L 'log(){ echo "$($BB date 2>/dev/null): $*" >> "$USB/done.log" 2>/dev/null; }'
    L 'sys_rw(){ mount -o rw,remount -t ext4 $SYS /system 2>>"$USB/done.log"; }'
    L 'sys_ro(){ mount -o ro,remount -t ext4 $SYS /system 2>>"$USB/done.log"; sync; }'
    L 'log "=== stick build start ==="'
    L 'log "id: $(id)"'

    $sysNeeded = ($sel['root'] -or $sel['wheel'])
    if ($sysNeeded) {
        L '# ---------- /system installs ----------'
        L 'sys_rw'
        if ($sel['root']) {
            L '# persistent root'
            L 'cp "$USB/assets/adbd_root"   /system/bin/adbd_root   && chmod 755 /system/bin/adbd_root'
            L 'cp "$USB/assets/rootadb.sh"  /system/bin/rootadb.sh  && chmod 755 /system/bin/rootadb.sh'
            L 'cp "$USB/assets/geelysu"     /system/xbin/geelysu    && chmod 755 /system/xbin/geelysu'
            L 'cp "$USB/assets/su"          /system/xbin/su         && chmod 755 /system/xbin/su'
            L 'cp "$USB/assets/geelysu.allow" /system/etc/geelysu.allow && chmod 644 /system/etc/geelysu.allow'
            L 'grep -q rootadb.sh "$IR"          || echo "/system/bin/rootadb.sh &"       >> "$IR"'
            L 'grep -q "geelysu --daemon" "$IR"  || echo "/system/xbin/geelysu --daemon &" >> "$IR"'
            L 'log "root files installed"'
        }
        if ($sel['wheel']) {
            L '# steering-wheel daemon'
            L 'cp "$USB/assets/steering.sh" /system/bin/steering.sh && chmod 755 /system/bin/steering.sh'
            L 'grep -q steering.sh "$IR" || echo "/system/bin/steering.sh &" >> "$IR"'
            L 'log "steering installed"'
        }
        L 'sys_ro'
    }

    if ($sel['setapp']) {
        L '# ---------- Settings shortcut app ----------'
        L 'pm install -r "$USB/assets/Settings_1.0.apk" >>"$USB/done.log" 2>&1 || { sys_rw; mkdir -p /system/app/CarSettingApp; cp "$USB/assets/Settings_1.0.apk" /system/app/CarSettingApp/CarSettingApp.apk; chmod 644 /system/app/CarSettingApp/CarSettingApp.apk; sys_ro; }'
        L 'log "settings app installed"'
    }

    if ($sel['apps']) {
        L '# ---------- extra APKs ----------'
        L 'for a in "$USB"/assets/apps/*.apk; do [ -f "$a" ] && pm install -r "$a" >>"$USB/done.log" 2>&1 && log "installed $a"; done'
    }

    # live starts (no reboot)
    if ($sel['root']) {
        L '# start root services live (no reboot)'
        L '/system/bin/rootadb.sh &'
        L '/system/xbin/geelysu --daemon &'
    }
    if ($sel['wheel']) {
        L '/system/bin/steering.sh &'
    }

    if ($sel['wadb']) {
        L '# ---------- wireless ADB ----------'
        L 'settings put global adb_enabled 1 2>/dev/null'
        L 'setprop persist.adb.tcp.port 5555'
        L 'setprop service.adb.tcp.port 5555'
        L 'stop adbd 2>/dev/null; start adbd 2>/dev/null'
        L 'setprop ctl.restart adbd 2>/dev/null'
        L 'log "wireless adb on"'
    }

    if ($sel['anim']) {
        L '# ---------- animation scale ----------'
        L ('settings put global window_animation_scale '     + $animVal)
        L ('settings put global transition_animation_scale ' + $animVal)
        L ('settings put global animator_duration_scale '    + $animVal)
        L 'log "animation scale set"'
    }

    if ($sel['seat']) {
        L '# ---------- seat-preset fix ----------'
        L 'sh "$USB/assets/seatfix.sh" >>"$USB/done.log" 2>&1 && log "seatfix ran"'
    }

    if ($sel['backup']) {
        L '# ---------- read-only partition backup ----------'
        L 'OUT="$USB/backup_$($BB date +%Y%m%d_%H%M%S)"; $BB mkdir -p "$OUT"'
        L 'BY=/dev/block/platform/mtk-msdc.0/by-name'
        L 'for p in lk boot recovery logo para expdb frp nvram nvdata proinfo protect1 protect2 seccfg oemkeystore keystore tee1 tee2 metadata; do'
        L '  [ -e "$BY/$p" ] && $BB dd if="$BY/$p" of="$OUT/$p.img" bs=1048576 count=64 2>>"$OUT/backup.log" && $BB md5sum "$OUT/$p.img" >> "$OUT/manifest.md5"; $BB sync'
        L 'done'
        L '$BB dd if=/dev/block/mmcblk0boot0 of="$OUT/preloader0.img" bs=1048576 count=8 2>>"$OUT/backup.log"; $BB sync'
        L 'log "backup done -> $OUT"'
    }

    L '# ---------- finish (no reboot) ----------'
    L 'sync'
    # delete the trigger folder so the hook does not re-fire on later inserts
    L ('rm -rf "$USB/' + $TrigDir + '"')
    L 'am start -n com.android.settings/.Settings >/dev/null 2>&1'
    L 'echo OK > "$USB/DONE.txt"; sync'
    L 'log "=== stick build end ==="'

    return $sb.ToString()
}

function Build-InnerSh {
    $nl = "`n"
    @(
        '#!/system/bin/sh'
        'chmod +x /mnt/media_rw/udisk1/done.sh'
        'SCRIPT_PATH="/mnt/media_rw/udisk1/done.sh"'
        '. "$SCRIPT_PATH"'
    ) -join $nl
}

function Write-Lf([string]$path, [string]$text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    $norm = $text -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($path, $norm, $enc)
}

# ---- build action -------------------------------------------------------
function Invoke-Build($drive, [hashtable]$sel, [string]$animVal, $logBox) {
    function Say($m) { $logBox.AppendText($m + "`r`n"); $logBox.ScrollToCaret() }

    $root = ($drive.Letter + '\')
    Say "Target: $($drive.Letter)  $($drive.Label)  ~$($drive.SizeGB) GB"

    # format FAT32
    Say "Formatting $($drive.Letter) as FAT32 (label GEELY)…"
    Format-Volume -DriveLetter ($drive.Letter.TrimEnd(':')) -FileSystem FAT32 -NewFileSystemLabel 'GEELY' -Force -Confirm:$false | Out-Null
    Start-Sleep -Milliseconds 800

    # trigger folder + inner launcher
    $td = Join-Path $root $TrigDir
    New-Item -ItemType Directory -Force -Path $td | Out-Null
    Write-Lf (Join-Path $td $TrigInner) (Build-InnerSh)
    Say "Wrote trigger: $TrigDir\$TrigInner"

    # done.sh
    Write-Lf (Join-Path $root 'done.sh') (Build-DoneSh $sel $animVal)
    Say "Wrote done.sh"

    # copy needed assets
    $needAssets = $false
    foreach ($f in $Features) { if ($sel[$f.Key] -and ($f.Assets.Count -gt 0 -or $f.Key -eq 'apps')) { $needAssets = $true } }
    if ($needAssets) {
        $dstA = Join-Path $root 'assets'
        New-Item -ItemType Directory -Force -Path $dstA | Out-Null
        foreach ($f in $Features) {
            if (-not $sel[$f.Key]) { continue }
            foreach ($a in $f.Assets) {
                Copy-Item (Join-Path $AssetsDir $a) (Join-Path $dstA $a) -Force
                Say "Copied asset: $a"
            }
            if ($f.Key -eq 'apps') {
                $srcApps = Join-Path $AssetsDir 'apps'
                $dstApps = Join-Path $dstA 'apps'
                New-Item -ItemType Directory -Force -Path $dstApps | Out-Null
                Get-ChildItem -Path $srcApps -Filter *.apk -File | ForEach-Object {
                    Copy-Item $_.FullName (Join-Path $dstApps $_.Name) -Force; Say "Copied app: $($_.Name)"
                }
            }
        }
    }

    # the firmware deletes the trigger after running; stash a backup copy so the
    # user can re-run later without rebuilding.
    $bk = Join-Path $root '_trigger_backup'
    New-Item -ItemType Directory -Force -Path $bk | Out-Null
    Copy-Item $td (Join-Path $bk $TrigDir) -Recurse -Force

    Say ""
    Say "DONE. Safely eject, insert in the LEFT port with the car on."
    Say "It applies everything in one pass (no reboot). Check DONE.txt / done.log on the stick after."
}

# ---- GUI ----------------------------------------------------------------
$form = New-Object System.Windows.Forms.Form
$form.Text = 'Geely Stick Builder'
$form.Size = New-Object System.Drawing.Size(620, 640)
$form.StartPosition = 'CenterScreen'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)

$lblDrive = New-Object System.Windows.Forms.Label
$lblDrive.Text = 'USB drive (removable only):'
$lblDrive.Location = '15,15'; $lblDrive.AutoSize = $true
$form.Controls.Add($lblDrive)

$cmbDrive = New-Object System.Windows.Forms.ComboBox
$cmbDrive.Location = '15,38'; $cmbDrive.Size = '460,24'
$cmbDrive.DropDownStyle = 'DropDownList'
$form.Controls.Add($cmbDrive)

$btnRefresh = New-Object System.Windows.Forms.Button
$btnRefresh.Text = 'Refresh'; $btnRefresh.Location = '485,37'; $btnRefresh.Size = '100,26'
$form.Controls.Add($btnRefresh)

$grp = New-Object System.Windows.Forms.GroupBox
$grp.Text = 'Features'; $grp.Location = '15,75'; $grp.Size = '570,300'
$form.Controls.Add($grp)

$checks = @{}
$y = 26
foreach ($f in $Features) {
    $cb = New-Object System.Windows.Forms.CheckBox
    $cb.Location = "15,$y"; $cb.Size = '520,22'; $cb.Tag = $f.Key
    $ready = Test-FeatureReady $f
    $cb.Text = $f.Label + $(if (-not $ready) { '   — asset missing' } else { '' })
    $cb.Enabled = $ready
    $tt = New-Object System.Windows.Forms.ToolTip
    $tt.SetToolTip($cb, ($f.Help + $(if ($f.Assets.Count) { "  [needs: " + ($f.Assets -join ', ') + "]" } else { '' })))
    $grp.Controls.Add($cb)
    $checks[$f.Key] = $cb
    $y += 28
}

# animation scale combo (enabled with the 'anim' checkbox)
$cmbAnim = New-Object System.Windows.Forms.ComboBox
$cmbAnim.Location = "300,$($checks['anim'].Top - 2)"; $cmbAnim.Size = '150,24'
$cmbAnim.DropDownStyle = 'DropDownList'
[void]$cmbAnim.Items.AddRange(@('Off (0)', 'Smooth (0.5)', 'Normal (1.0)'))
$cmbAnim.SelectedIndex = 1
$cmbAnim.Enabled = $false
$grp.Controls.Add($cmbAnim)
$checks['anim'].Add_CheckedChanged({ $cmbAnim.Enabled = $checks['anim'].Checked })

$lblAssets = New-Object System.Windows.Forms.Label
$lblAssets.Location = '15,385'; $lblAssets.Size = '570,34'
$lblAssets.ForeColor = 'DimGray'
$lblAssets.Text = "Assets folder: $AssetsDir`nDrop adbd_root, rootadb.sh, geelysu, su, geelysu.allow, steering.sh, Settings_1.0.apk, seatfix.sh (and apps\*.apk) there to enable the greyed-out toggles."
$form.Controls.Add($lblAssets)

$log = New-Object System.Windows.Forms.TextBox
$log.Location = '15,425'; $log.Size = '570,135'
$log.Multiline = $true; $log.ReadOnly = $true; $log.ScrollBars = 'Vertical'
$log.Font = New-Object System.Drawing.Font('Consolas', 8.5)
$form.Controls.Add($log)

$btnBuild = New-Object System.Windows.Forms.Button
$btnBuild.Text = 'Format + Build Stick'; $btnBuild.Location = '15,568'; $btnBuild.Size = '570,34'
$btnBuild.BackColor = [System.Drawing.Color]::FromArgb(220, 53, 69); $btnBuild.ForeColor = 'White'
$btnBuild.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$form.Controls.Add($btnBuild)

# admin banner
if (-not (Test-Admin)) {
    $log.AppendText("WARNING: not running as Administrator — formatting will fail. Use Run-GeelyStickBuilder.cmd.`r`n")
}

# populate drives
$script:drives = @()
function Refresh-Drives {
    $cmbDrive.Items.Clear()
    $script:drives = @(Get-UsbDrives)
    foreach ($d in $script:drives) { [void]$cmbDrive.Items.Add("$($d.Letter)  $($d.Label)  (~$($d.SizeGB) GB)") }
    if ($cmbDrive.Items.Count -gt 0) { $cmbDrive.SelectedIndex = 0 } else { $log.AppendText("No removable USB drive detected. Insert one and Refresh.`r`n") }
}
$btnRefresh.Add_Click({ Refresh-Drives })
Refresh-Drives

$btnBuild.Add_Click({
    if (-not (Test-Admin)) { [void][System.Windows.Forms.MessageBox]::Show('Run as Administrator (use the .cmd launcher) — formatting needs admin.','Admin required'); return }
    if ($cmbDrive.SelectedIndex -lt 0) { [void][System.Windows.Forms.MessageBox]::Show('Pick a USB drive first.','No drive'); return }
    $drive = $script:drives[$cmbDrive.SelectedIndex]

    $sel = @{}
    foreach ($k in $checks.Keys) { $sel[$k] = $checks[$k].Checked }
    if (-not ($sel.Values -contains $true)) { [void][System.Windows.Forms.MessageBox]::Show('Tick at least one feature.','Nothing selected'); return }

    $animMap = @{ 0 = '0'; 1 = '0.5'; 2 = '1.0' }
    $animVal = $animMap[$cmbAnim.SelectedIndex]

    $msg = "This will ERASE $($drive.Letter)  ""$($drive.Label)""  (~$($drive.SizeGB) GB) and build the stick.`n`nContinue?"
    if ([System.Windows.Forms.MessageBox]::Show($msg,'Confirm format', 'YesNo', 'Warning') -ne 'Yes') { return }

    $btnBuild.Enabled = $false
    try { Invoke-Build $drive $sel $animVal $log }
    catch { $log.AppendText("ERROR: $($_.Exception.Message)`r`n") ; [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'Build failed') }
    finally { $btnBuild.Enabled = $true }
})

[void]$form.ShowDialog()
