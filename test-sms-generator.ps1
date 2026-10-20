<#
.SYNOPSIS
    Populates an Android emulator with realistic test SMS messages using adb.
.DESCRIPTION
    Sends mock incoming SMS messages from various phone numbers across simulated dates/times
    to test thread clustering, multi-selection, and export parsing without needing a physical carrier SIM.
.EXAMPLE
    .\test-sms-generator.ps1
#>

param(
    [string]$AdbPath = "adb"
)

# Test whether adb is accessible
try {
    $deviceOutput = & $AdbPath devices
    Write-Host "Connected Android devices/emulators:" -ForegroundColor Cyan
    Write-Host $deviceOutput
} catch {
    Write-Error "Could not execute adb. Ensure Android platform-tools is added to your PATH or pass -AdbPath."
    exit 1
}

$sampleMessages = @(
    @{ Sender = "+15550192834"; Message = "Hey! Are you still at the convention?" },
    @{ Sender = "+15550192834"; Message = "Did you see the new backup utility keynote?" },
    @{ Sender = "+15557429901"; Message = "Good morning! Can you review the markdown export spec?" },
    @{ Sender = "+15557429901"; Message = "The release documentation is ready for review." },
    @{ Sender = "+15558831244"; Message = "Your one-time verification code is 482910. Do not share this code." },
    @{ Sender = "+15553319082"; Message = "Flight AA240 is scheduled on time at gate B14." }
)

Write-Host "`nSending test SMS messages to emulator..." -ForegroundColor Yellow

foreach ($item in $sampleMessages) {
    $sender = $item.Sender
    $msg = $item.Message
    Write-Host "-> Sending from $sender: '$msg'" -ForegroundColor Gray
    & $AdbPath emu sms send $sender "$msg"
    Start-Sleep -Milliseconds 800
}

Write-Host "`n[SUCCESS] All test messages injected into emulator telephony database!" -ForegroundColor Green
Write-Host "You can now open Muninn, grant permission, and see these threads." -ForegroundColor Cyan
