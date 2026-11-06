# Stops the MOMENTUM local Java service if it is running.
# Covers both a live port listener and leftover "zombie" java processes
# that still hold the jar but no longer serve the port.
$ErrorActionPreference = 'SilentlyContinue'

$stopped = 0

# 1. Kill whatever is listening on the MOMENTUM ports.
foreach ($port in @(8787, 8799)) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $port
    foreach ($listener in $listeners) {
        $proc = Get-Process -Id $listener.OwningProcess
        if ($proc -and ($proc.Name -like 'java*')) {
            Stop-Process -Id $proc.Id -Force
            Write-Host "Stopped MOMENTUM service on port $port (PID $($proc.Id))."
            $stopped++
        }
    }
}

# 2. Kill leftover java processes that run the momentum jar but own no port.
$zombies = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*momentum-api.jar*' }
foreach ($zombie in $zombies) {
    Stop-Process -Id $zombie.ProcessId -Force
    Write-Host "Stopped leftover MOMENTUM service process (PID $($zombie.ProcessId))."
    $stopped++
}

if ($stopped -eq 0) {
    Write-Host 'MOMENTUM service is not running.'
}
