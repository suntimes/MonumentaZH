param([Parameter(Mandatory=$true)][string]$GsonJar,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$javac=if($JavaHome){Join-Path $JavaHome 'bin/javac.exe'}else{(Get-Command javac -ErrorAction Stop).Source}
$java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{(Get-Command java -ErrorAction Stop).Source}
$classes=Join-Path $PSScriptRoot ('build/tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=@('src/monumentazh/TranslationEngine.java','src/monumentazh/QuantityText.java','src/monumentazh/CaptureWorkQueue.java','tests/CaptureWorkQueueTest.java','tests/QuantityTextTest.java') | ForEach-Object {Join-Path $PSScriptRoot $_}
& $javac -encoding UTF-8 --release 17 -proc:none -classpath $GsonJar -d $classes $sources
if($LASTEXITCODE -ne 0){throw 'Test compilation failed.'}
foreach($test in @('monumentazh.CaptureWorkQueueTest','monumentazh.QuantityTextTest')){
    & $java -classpath ($classes+';'+$GsonJar) $test
    if($LASTEXITCODE -ne 0){throw ('Test failed: '+$test)}
}
