param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("19", "21")]
    [string]$Version
)

if ($Version -eq "19") {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
    $env:PATH = "C:\Program Files\Java\jdk-19\bin;$env:PATH"
    Write-Host "Java 19로 전환되었습니다." -ForegroundColor Green
} elseif ($Version -eq "21") {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
    $env:PATH = "C:\Program Files\Java\jdk-21\bin;$env:PATH"
    Write-Host "Java 21로 전환되었습니다." -ForegroundColor Green
}

Write-Host "현재 Java 버전:" -ForegroundColor Yellow
java -version
Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Yellow






