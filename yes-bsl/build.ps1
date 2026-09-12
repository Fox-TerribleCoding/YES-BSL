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
    [switch]$Clean
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

$jdkBin = 'C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin'
$javac = Join-Path $jdkBin 'javac.exe'
if (-not (Test-Path -LiteralPath $javac)) {
    $javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
}
if (-not $javac) { throw 'javac not found; please install JDK 21' }

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
Copy-Item -LiteralPath $OutJar -Destination $PublishedJar -Force

Write-Host ""
Write-Host "BUILD OK: $OutJar" -ForegroundColor Green
Write-Host "published: $PublishedJar" -ForegroundColor Green
Write-Host ""
Write-Host "Copy the published jar into: $ModsDir" -ForegroundColor Green
