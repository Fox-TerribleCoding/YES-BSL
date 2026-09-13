# Standalone build script - no Gradle, no network access required.
#
# Everything needed to compile ships with the game installation itself:
#   - Minecraft jar with official (Mojang) mappings
#   - NeoForge 21.1.248
#   - Sponge Mixin
#   - Iris, inside the game version's mods/ folder
# So plain javac + jar is enough.

[CmdletBinding()]
param(
    # Target game version folder name under versions\.
    [string]$GameVersion = 'T',
    [switch]$Clean,
    # Copy the built jar into that version's mods\ folder.
    [switch]$Install,
    # JDK to compile with; defaults to JAVA_HOME / auto-discovery / PATH.
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'

# This project lives at <game>\.minecraft\versions\<ProjectFolder>\<name>,
# so walk up three levels to reach .minecraft.
$ProjectRoot   = $PSScriptRoot                          # ...\versions\<ProjectFolder>\<name>
$ProjectParent = Split-Path $ProjectRoot -Parent        # ...\versions\<ProjectFolder>
$VersionsDir   = Split-Path $ProjectParent -Parent      # ...\versions
$MinecraftRoot = Split-Path $VersionsDir -Parent        # ...\.minecraft
$LibRoot       = Join-Path $MinecraftRoot 'libraries'
$GameRoot      = Join-Path $VersionsDir $GameVersion
$ModsDir       = Join-Path $GameRoot 'mods'

if (-not (Test-Path -LiteralPath $LibRoot)) {
    throw "libraries folder not found: $LibRoot (use -GameVersion to pick the right version folder)"
}

$BuildDir    = Join-Path $ProjectRoot 'build'
$ClassesDir  = Join-Path $BuildDir 'classes'
$SrcDir      = Join-Path $ProjectRoot 'src\main\java'
$ResDir      = Join-Path $ProjectRoot 'src\main\resources'
$JarName     = 'YES_BSL-1.0.0.jar'
$OutJar      = Join-Path $BuildDir $JarName

if ($Clean -and (Test-Path -LiteralPath $BuildDir)) {
    Remove-Item -Recurse -Force -LiteralPath $BuildDir
}
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

# ---------------------------------------------------------------- dependencies
$jars = New-Object System.Collections.Generic.List[string]

$explicit = @(
    (Join-Path $LibRoot 'net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar'),
    (Join-Path $LibRoot 'net\neoforged\neoforge\21.1.248\neoforge-21.1.248-universal.jar'),
    (Join-Path $LibRoot 'net\neoforged\neoforge\21.1.248\neoforge-21.1.248-client.jar'),
    (Join-Path $LibRoot 'net\neoforged\fancymodloader\loader\4.0.43\loader-4.0.43.jar'),
    (Join-Path $LibRoot 'net\fabricmc\sponge-mixin\0.15.2+mixin.0.8.7\sponge-mixin-0.15.2+mixin.0.8.7.jar'),
    # Pinned versions: the libraries tree contains several fastutil/joml releases
    # and picking an old one breaks compilation (Object2IntFunction differs).
    (Join-Path $LibRoot 'it\unimi\dsi\fastutil\8.5.18\fastutil-8.5.18.jar'),
    (Join-Path $LibRoot 'org\joml\joml\1.10.8\joml-1.10.8.jar')
)
foreach ($j in $explicit) {
    if (Test-Path -LiteralPath $j) { $jars.Add($j) }
    else { Write-Warning "missing dependency: $j" }
}

# Iris, shipped inside the game version's mods folder
Get-ChildItem -LiteralPath $ModsDir -Filter 'iris*.jar' -File -ErrorAction SilentlyContinue |
    ForEach-Object { $jars.Add($_.FullName) }

$patterns = @(
    'annotations-*.jar','slf4j-api-*.jar','log4j-api-*.jar','bus-*.jar',
    'coremods-*.jar','guava-*.jar','datafixerupper-*.jar','brigadier-*.jar',
    'gson-*.jar','commons-lang3-*.jar','authlib-*.jar','nightconfig-*.jar',
    'client-extra-*.jar','jarjar-*.jar'
)
foreach ($p in $patterns) {
    Get-ChildItem -Path $LibRoot -Recurse -Filter $p -File -ErrorAction SilentlyContinue |
        ForEach-Object {
            if ($_.FullName -notmatch 'sources|javadoc') { $jars.Add($_.FullName) }
        }
}

$uniqueJars = $jars | Select-Object -Unique
$classpath = $uniqueJars -join ';'
Write-Host "dependency jars: $($uniqueJars.Count)"

# ---------------------------------------------------------------- compile
$sources = Get-ChildItem -Path $SrcDir -Recurse -Filter '*.java' -File |
           Select-Object -ExpandProperty FullName
Write-Host "source files: $($sources.Count)"

# Locate a JDK 21. Portability matters here: this script is published, so it
# must not depend on one machine's install path. Order: -JavaHome, JAVA_HOME,
# the usual install roots, then whatever is on PATH.
$jdkBin = $null
$candidates = New-Object System.Collections.Generic.List[string]

if ($JavaHome) {
    $candidates.Add((Join-Path $JavaHome 'bin'))
}
if ($env:JAVA_HOME) {
    $candidates.Add((Join-Path $env:JAVA_HOME 'bin'))
}
foreach ($root in @("$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium")) {
    if (Test-Path -LiteralPath $root) {
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'jdk' } |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add((Join-Path $_.FullName 'bin')) }
    }
}

foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath (Join-Path $candidate 'javac.exe')) {
        $jdkBin = $candidate
        break
    }
}

$javac = if ($jdkBin) { Join-Path $jdkBin 'javac.exe' } else { $null }
if (-not $javac) {
    $javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
    if ($javac) { $jdkBin = Split-Path $javac -Parent }
}
if (-not $javac) {
    throw 'javac not found. Install JDK 21, or pass -JavaHome <path-to-jdk>.'
}
Write-Host "javac: $javac"

$javacArgs = @(
    '-J-Duser.language=en',
    '-J-Duser.country=US',
    '-encoding', 'UTF-8',
    '-source', '21',
    '-target', '21',
    '-nowarn',
    '-proc:none',
    '-d', $ClassesDir,
    '-cp', $classpath
) + $sources

Write-Host "compiling ... (classpath chars: $($classpath.Length))"
& $javac $javacArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# ---------------------------------------------------------------- resources
Copy-Item -Path (Join-Path $ResDir '*') -Destination $ClassesDir -Recurse -Force

# Ship the licence text inside the jar too.
#
# MIT requires the copyright notice to be included in all copies or substantial
# portions of the Software - and a built jar is a copy.
$metaInf = Join-Path $ClassesDir 'META-INF'
New-Item -ItemType Directory -Force -Path $metaInf | Out-Null
foreach ($doc in @('LICENSE', 'NOTICE')) {
    $docPath = Join-Path $ProjectRoot $doc
    if (Test-Path -LiteralPath $docPath) {
        Copy-Item -LiteralPath $docPath -Destination (Join-Path $metaInf $doc) -Force
    }
}

# ---------------------------------------------------------------- package
if (Test-Path -LiteralPath $OutJar) { Remove-Item -LiteralPath $OutJar -Force }
$jar = Join-Path $jdkBin 'jar.exe'
if (-not (Test-Path -LiteralPath $jar)) {
    $jar = (Get-Command jar -ErrorAction SilentlyContinue).Source
}

& $jar --create --file $OutJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

# Also publish a copy next to the project folder for easy access.
$PublishedJar = Join-Path $ProjectParent $JarName

# Publish atomically.
#
# Copy-Item truncates the destination and then writes into it. If anything is
# reading that jar during the window (a running game loading a jar it has not
# read yet, or you copying it into mods\ at the same moment) it can see a
# PARTIAL jar - the mixin config is there but the classes are not, which
# surfaces later as NoClassDefFoundError. Writing a temp file and then moving
# it into place swaps the file in one step, so a reader sees either the old jar
# or the new one - never a half one.
function Publish-Jar {
    param([string]$Source, [string]$Destination)
    $dir = Split-Path $Destination -Parent
    $tmp = Join-Path $dir ($JarName + '.tmp')
    Copy-Item -LiteralPath $Source -Destination $tmp -Force
    Move-Item -LiteralPath $tmp -Destination $Destination -Force
}

Publish-Jar -Source $OutJar -Destination $PublishedJar

Write-Host ""
Write-Host "BUILD OK: $OutJar" -ForegroundColor Green
Write-Host "published: $PublishedJar" -ForegroundColor Green
Write-Host ""

if ($Install) {
    # Refuse to touch the live jar while the game is running: even an atomic
    # replace can leave a running JVM unable to open a class it has not loaded yet.
    $running = Get-Process java, javaw -ErrorAction SilentlyContinue
    if ($running) {
        Write-Warning "Java is running (PID: $($running.Id -join ', '))."
        Write-Warning "Not installing to $ModsDir - close the game first, then re-run with -Install."
    } else {
        Publish-Jar -Source $OutJar -Destination (Join-Path $ModsDir $JarName)
        Write-Host "installed to: $(Join-Path $ModsDir $JarName)" -ForegroundColor Green
    }
} else {
    Write-Host "Copy the published jar into: $ModsDir (or pass -Install)" -ForegroundColor Green
}
