$ErrorActionPreference = 'Stop'
$out = 'evidence/master-affix/equipment-simulator/classes'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$jars = Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1" -Recurse -Filter '*.jar' | ForEach-Object FullName
$gson = Get-ChildItem "$env:USERPROFILE/.gradle/wrapper/dists" -Recurse -Filter 'gson-2.13.1.jar' | Select-Object -First 1 -ExpandProperty FullName
$classpath = (@($out, 'build/classes/java/main', 'build/classes/java/test', 'build/resources/main', 'build/resources/test', $gson) + $jars) -join ';'
javac -cp $classpath -d $out `
    src/main/java/com/inigmasgames/hytalerpg/gear/GearAffixRuntime.java `
    src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java `
    src/test/java/com/inigmasgames/hytalerpg/gear/GearEquipmentLifecycleTest.java `
    src/test/java/com/inigmasgames/hytalerpg/gear/MasterAffixEquipmentSimulatorTest.java `
    evidence/master-affix/equipment-simulator/EquipmentSimulatorRunner.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp $classpath EquipmentSimulatorRunner
exit $LASTEXITCODE
