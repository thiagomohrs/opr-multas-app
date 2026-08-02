# Script para iniciar o OPR Multas App (Spring Boot + Thymeleaf)
Write-Host "=== OPR Multas App ===" -ForegroundColor Cyan
Write-Host "Aplicação full-stack: Java + Spring Boot + Thymeleaf" -ForegroundColor Gray

# ── Localizar o Java ─────────────────────────────────
$javaExe = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $candidate) {
        Write-Host "Java encontrado via JAVA_HOME: $candidate" -ForegroundColor Gray
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
        $javaExe = Get-Command java
    }
}
if (-not $javaExe) {
    # Procura JDKs 21+ em locais comuns (IntelliJ: ~/.jdks, ~/sdkman)
    $candidates = @()
    foreach ($dir in @("$env:USERPROFILE\.jdks", "$env:USERPROFILE\sdkman\candidates\java", "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium")) {
        if (Test-Path -LiteralPath $dir) {
            $candidates += Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '21|22|23|24' } |
                ForEach-Object { Join-Path $_.FullName "bin\java.exe" }
        }
    }
    $javaPath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($javaPath) {
        Write-Host "Java encontrado em: $javaPath" -ForegroundColor Gray
        $env:Path = (Split-Path $javaPath) + ";" + $env:Path
        $javaExe = Get-Command java
    }
}
if (-not $javaExe) {
    Write-Host "ERRO: Java 21+ não encontrado. Instale o Java 21 e defina JAVA_HOME." -ForegroundColor Red
    Write-Host "Download: https://adoptium.net" -ForegroundColor Yellow
    exit 1
}

# ── Localizar o Maven ─────────────────────────────────
$mvnCmd = $null
$mvnFromPath = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnFromPath) {
    $mvnCmd = $mvnFromPath.Source
}
if (-not $mvnCmd -and $env:MAVEN_HOME) {
    $cand = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
    if (Test-Path -LiteralPath $cand) { $mvnCmd = $cand }
}
if (-not $mvnCmd) {
    $mvnDirs = @(
        "$env:ProgramFiles\Apache\maven-*",
        "$env:ProgramFiles\apache-maven-*",
        "C:\apache-maven-*",
        "$env:LOCALAPPDATA\Temp\opencode\maven\apache-maven-*",
        "$env:USERPROFILE\scoop\apps\maven\*\bin"
    )
    foreach ($pat in $mvnDirs) {
        $found = Get-ChildItem -Path $pat -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\mvn.cmd") } |
            Select-Object -First 1
        if ($found) {
            $mvnCmd = Join-Path $found.FullName "bin\mvn.cmd"
            break
        }
    }
}
if (-not $mvnCmd) {
    Write-Host "ERRO: Maven não encontrado. Instale via: choco install maven  ou  https://maven.apache.org/download.cgi" -ForegroundColor Red
    exit 1
}
Write-Host "Maven encontrado em: $mvnCmd" -ForegroundColor Gray
$env:Path = (Split-Path $mvnCmd) + ";" + $env:Path

Write-Host ""
Write-Host "Iniciando Spring Boot (perfil dev)..." -ForegroundColor Cyan
Write-Host "Acesse: http://localhost:8080" -ForegroundColor Green
Write-Host "Console H2: http://localhost:8080/h2-console" -ForegroundColor Green
Write-Host "Pressione Ctrl+C para parar." -ForegroundColor Yellow
Write-Host ""

$env:SPRING_PROFILES_ACTIVE = "dev"

# Se existir um maven-settings.xml na raiz do projeto, usa-o
# (útil quando o settings global (~/.m2/settings.xml) aponta para um mirror inacessível)
$mavenSettings = Join-Path $PSScriptRoot "maven-settings.xml"
$mvnArgs = @("spring-boot:run")
if (Test-Path -LiteralPath $mavenSettings) {
    Write-Host "Usando maven-settings.xml local." -ForegroundColor Yellow
    $mvnArgs = @("-s", $mavenSettings) + $mvnArgs
}

Push-Location (Join-Path $PSScriptRoot "backend")
try {
    & mvn @mvnArgs
} finally {
    Pop-Location
}
