param(
    [Parameter(Mandatory=$true)][string]$GameDirectory,
    [Parameter(Mandatory=$true)][string]$LibrariesDirectory,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$jar = if ($JavaHome) { Join-Path $JavaHome 'bin/jar.exe' } else { (Get-Command jar -ErrorAction Stop).Source }
$profile = Get-Content -LiteralPath (Join-Path $GameDirectory '1.20.4.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$classPaths = [System.Collections.Generic.List[string]]::new()
$minecraftJar = Get-ChildItem -LiteralPath (Join-Path $GameDirectory '.fabric/remappedJars') -Recurse -Filter 'client-intermediary.jar' | Where-Object { $_.FullName -match 'minecraft-1\.20\.4-' } | Sort-Object FullName | Select-Object -Last 1
if (!$minecraftJar) { throw 'Run Minecraft 1.20.4 with Fabric once to generate the remapped client cache.' }
$classPaths.Add($minecraftJar.FullName)
foreach ($library in $profile.libraries) {
    $pieces = $library.name.Split(':')
    $relative = $pieces[0].Replace('.', '/') + '/' + $pieces[1] + '/' + $pieces[2] + '/' + $pieces[1] + '-' + $pieces[2] + '.jar'
    $libraryPath = Join-Path $LibrariesDirectory $relative
    if (Test-Path -LiteralPath $libraryPath -PathType Leaf) { $classPaths.Add($libraryPath) }
}
Get-ChildItem -LiteralPath (Join-Path $GameDirectory '.fabric/processedMods') -Filter 'fabric-*.jar' | Where-Object { $_.Name -match 'api-base|item-api-v1|message-api|command-api-v2|lifecycle-events|key-binding-api|screen-api-v1' } | ForEach-Object { $classPaths.Add($_.FullName) }
$compileClassPath = $classPaths -join ';'
# Each build starts in a new directory, avoiding stale anonymous/nested class files.
$classesDirectory = Join-Path $projectDirectory ('build/classes-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 --release 17 -proc:none -classpath $compileClassPath -d $classesDirectory $sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'resources') | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $classesDirectory -Recurse -Force }
# Include the code license and attribution in the built artifact.
Copy-Item -LiteralPath (Join-Path $projectDirectory 'LICENSE'),(Join-Path $projectDirectory 'NOTICE.md') -Destination $classesDirectory
$distDirectory = Join-Path $projectDirectory 'dist'
New-Item -ItemType Directory -Path $distDirectory -Force | Out-Null
$modVersion = (Get-Content -LiteralPath (Join-Path $projectDirectory 'resources/fabric.mod.json') -Raw -Encoding UTF8 | ConvertFrom-Json).version
$artifact = Join-Path $distDirectory ('Monumenta-ZH-Supplement-1.20.4-' + $modVersion + '.jar')
$jarTemporaryDirectory=Join-Path $projectDirectory ('build/jar-tmp-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $jarTemporaryDirectory -Force | Out-Null
& $jar ('-J-Djava.io.tmpdir='+$jarTemporaryDirectory) --create --file $artifact -C $classesDirectory .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Get-Item -LiteralPath $artifact | Select-Object Name,Length
