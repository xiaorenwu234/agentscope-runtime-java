# 保存为 Stop-Ports.ps1
$ports = 10001..10007
$stopped = @()

foreach ($port in $ports) {
    try {
        $connection = Get-NetTCPConnection -LocalPort $port -ErrorAction Stop
        $pid = $connection.OwningProcess
        $processName = (Get-Process -Id $pid).Name
        
        Write-Host "停止进程: $processName (PID: $pid) 占用端口 $port" -ForegroundColor Yellow
        Stop-Process -Id $pid -Force
        
        $stopped += [PSCustomObject]@{
            Port = $port
            PID = $pid
            ProcessName = $processName
            Status = "已停止"
        }
    } catch {
        Write-Host "端口 $port 未被占用" -ForegroundColor Gray
    }
}

# 显示结果
if ($stopped.Count -gt 0) {
    Write-Host "`n已停止以下进程:" -ForegroundColor Green
    $stopped | Format-Table -AutoSize
} else {
    Write-Host "没有发现占用端口 10001-10007 的进程" -ForegroundColor Cyan
}