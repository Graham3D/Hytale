param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar"
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$localJdk = Join-Path $projectRoot '.tools\jdk-25.0.4+7\bin'
$javac = Join-Path $localJdk 'javac.exe'
$jarTool = Join-Path $localJdk 'jar.exe'

if (-not (Test-Path -LiteralPath $javac)) {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $jarCommand = Get-Command jar -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $jarCommand) {
        throw 'Java 25 JDK was not found. Keep .tools/jdk-25.0.4+7 or install Java 25 and add it to PATH.'
    }
    $javac = $javacCommand.Source
    $jarTool = $jarCommand.Source
}
if (-not (Test-Path -LiteralPath $ServerJar)) {
    throw "HytaleServer.jar was not found at: $ServerJar"
}

$classes = Join-Path $projectRoot 'build\classes'
$dist = Join-Path $projectRoot 'dist'
$outputJar = Join-Path $dist 'Taverns-0.1.0.jar'
$resolvedClasses = [IO.Path]::GetFullPath($classes)
$resolvedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
if (-not $resolvedClasses.StartsWith($resolvedBuildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe build output path: $resolvedClasses"
}
if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Recurse -File -Filter '*.java' | ForEach-Object FullName)
if ($sources.Count -eq 0) {
    throw 'No Java sources found.'
}

& $javac -encoding UTF-8 -source 25 -target 25 -classpath $ServerJar -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item -Path (Join-Path $projectRoot 'src\main\resources\*') -Destination $classes -Recurse -Force

# Patron order icons are ordinary Item Icon textures, but ParticleSystems are
# assets rather than arbitrary per-spawn payloads. Generate the finite set into
# the packaged JAR so the client receives them in its normal Init asset batch.
# Sending AddOrUpdate while playing surfaces Hytale's AssetUpdate diagnostics in
# player chat, so Tavern must never dynamically mutate these assets at runtime.
$latestRoot = Split-Path -Parent (Split-Path -Parent $ServerJar)
$assetsZip = Join-Path $latestRoot 'Assets.zip'
if (-not (Test-Path -LiteralPath $assetsZip)) {
    throw "Assets.zip was not found beside the Server directory: $assetsZip"
}

$particleRoot = Join-Path $classes 'Server\Particles\Taverns\PatronOrders'
$spawnerRoot = Join-Path $particleRoot 'Spawners'
$particleTextureRoot = Join-Path $classes 'Common\Particles\Taverns\PatronOrders\Textures'
New-Item -ItemType Directory -Force -Path $particleRoot, $spawnerRoot, $particleTextureRoot | Out-Null

function New-PatronBillboardSpawner {
    param(
        [Parameter(Mandatory = $true)][string]$Texture,
        [Parameter(Mandatory = $true)][double]$Scale,
        [Parameter(Mandatory = $true)][double]$CameraOffset
    )
    return [ordered]@{
        EmitOffset = [ordered]@{
            X = [ordered]@{ Min = 0; Max = 0 }
            Y = [ordered]@{ Min = 0; Max = 0 }
        }
        ParticleRotationInfluence = 'Billboard'
        LightInfluence = 0.8
        MaxConcurrentParticles = 1
        ParticleLifeSpan = [ordered]@{ Min = 0.55; Max = 0.55 }
        SpawnRate = [ordered]@{ Min = 1; Max = 1 }
        ParticleRotateWithSpawner = $false
        Particle = [ordered]@{
            Texture = $Texture
            ScaleRatioConstraint = 'OneToOne'
            UVOption = 'None'
            # Particle.Animation is required by the current asset validator,
            # even for a single-frame billboard. Animation scale multiplies
            # InitialAnimationFrame scale, so keep it neutral at both endpoints.
            Animation = [ordered]@{
                '0' = [ordered]@{
                    FrameIndex = [ordered]@{ Min = 0; Max = 0 }
                    Scale = [ordered]@{ X = [ordered]@{ Min = 1.0; Max = 1.0 } }
                    Opacity = 1
                    Color = '#ffffff'
                }
                '100' = [ordered]@{
                    FrameIndex = [ordered]@{ Min = 0; Max = 0 }
                    Scale = [ordered]@{ X = [ordered]@{ Min = 1.0; Max = 1.0 } }
                    Opacity = 1
                    Color = '#ffffff'
                }
            }
            InitialAnimationFrame = [ordered]@{
                Scale = [ordered]@{ X = [ordered]@{ Min = $Scale; Max = $Scale } }
                Opacity = 1
                Color = '#ffffff'
            }
            SoftParticles = 'Disable'
        }
        RenderMode = 'BlendLinear'
        SpawnBurst = $true
        TrailSpawnerPositionMultiplier = 0
        TrailSpawnerRotationMultiplier = 1
        LinearFiltering = $false
        CameraOffset = $CameraOffset
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($assetsZip)
$generatedOrderParticles = 0
try {
    function Copy-ParticleTexture {
        param(
            [Parameter(Mandatory = $true)][string]$SourceTexture,
            [Parameter(Mandatory = $true)][string]$OutputName
        )
        $sourcePath = $SourceTexture -replace '\\', '/'
        if ($sourcePath.StartsWith('Common/', [StringComparison]::OrdinalIgnoreCase)) {
            $sourcePath = $sourcePath.Substring('Common/'.Length)
        }
        $entry = $archive.GetEntry('Common/' + $sourcePath)
        if ($null -eq $entry) {
            throw "Particle source texture was not found in Assets.zip: $SourceTexture"
        }
        $extension = [IO.Path]::GetExtension($sourcePath)
        $fileName = $OutputName + $extension
        $destination = Join-Path $particleTextureRoot $fileName
        $inputStream = $entry.Open()
        $outputStream = [IO.File]::Create($destination)
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
            $inputStream.Dispose()
        }
        return 'Particles/Taverns/PatronOrders/Textures/' + $fileName
    }

    $frameSpawnerId = 'Taverns_Order_Frame'
    $frameTexture = Copy-ParticleTexture `
            -SourceTexture 'UI/ItemQualities/Slots/SlotDefault@2x.png' `
            -OutputName $frameSpawnerId
    $frameSpawner = New-PatronBillboardSpawner `
            -Texture $frameTexture `
            -Scale 0.16 `
            -CameraOffset -0.50
    [IO.File]::WriteAllText(
        (Join-Path $spawnerRoot ($frameSpawnerId + '.particlespawner')),
        ($frameSpawner | ConvertTo-Json -Depth 12))

    # Food is a runtime category, not a directory. Crops and several other
    # edible assets inherit Consumable/Items.Foods from templates elsewhere in
    # the Item tree, so resolve the same inheritance chain the Item asset map
    # uses instead of assuming Server/Item/Items/Food contains every order.
    $itemDefinitions = @{}
    $itemEntries = @($archive.Entries | Where-Object {
        $_.FullName -like 'Server/Item/Items/*.json' -and $_.Length -gt 0
    })
    foreach ($entry in $itemEntries) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try {
            $itemJson = $reader.ReadToEnd() | ConvertFrom-Json
        } finally {
            $reader.Dispose()
        }
        $itemId = [IO.Path]::GetFileNameWithoutExtension($entry.Name)
        $itemDefinitions[$itemId] = [ordered]@{
            Id = $itemId
            Parent = if ($itemJson.Parent) { [string]$itemJson.Parent } else { $null }
            HasIcon = $null -ne $itemJson.PSObject.Properties['Icon']
            Icon = if ($itemJson.Icon) { [string]$itemJson.Icon } else { $null }
            HasCategories = $null -ne $itemJson.PSObject.Properties['Categories']
            Categories = @($itemJson.Categories | ForEach-Object { [string]$_ })
            HasConsumable = $null -ne $itemJson.PSObject.Properties['Consumable']
            Consumable = if ($null -ne $itemJson.PSObject.Properties['Consumable']) {
                [bool]$itemJson.Consumable
            } else {
                $false
            }
            HasQuality = $null -ne $itemJson.PSObject.Properties['Quality']
            Quality = if ($itemJson.Quality) { [string]$itemJson.Quality } else { $null }
        }
    }

    function Get-InheritedItemValue {
        param(
            [Parameter(Mandatory = $true)][string]$ItemId,
            [Parameter(Mandatory = $true)][string]$ValueName
        )
        $currentId = $ItemId
        $visited = @{}
        while ($currentId -and $itemDefinitions.ContainsKey($currentId) -and
                -not $visited.ContainsKey($currentId)) {
            $visited[$currentId] = $true
            $definition = $itemDefinitions[$currentId]
            $hasName = 'Has' + $ValueName
            if ($definition[$hasName]) {
                return $definition[$ValueName]
            }
            $currentId = $definition.Parent
        }
        return $null
    }

    foreach ($itemId in @($itemDefinitions.Keys | Sort-Object)) {
        $consumable = Get-InheritedItemValue -ItemId $itemId -ValueName 'Consumable'
        $categories = @(Get-InheritedItemValue -ItemId $itemId -ValueName 'Categories')
        $icon = Get-InheritedItemValue -ItemId $itemId -ValueName 'Icon'
        if (-not $consumable -or $categories -notcontains 'Items.Foods' -or -not $icon) {
            continue
        }
        $safeItemId = $itemId -replace '[^A-Za-z0-9_]', '_'
        $systemId = 'Taverns_Order_' + $safeItemId
        $iconSpawnerId = $systemId + '_Icon'
        $iconSpawnerPath = Join-Path $spawnerRoot ($iconSpawnerId + '.particlespawner')
        $particleTexture = Copy-ParticleTexture `
                -SourceTexture ([string]$icon) `
                -OutputName $iconSpawnerId
        $iconSpawner = New-PatronBillboardSpawner `
                -Texture $particleTexture `
                -Scale 0.38 `
                -CameraOffset -0.53
        [IO.File]::WriteAllText(
            $iconSpawnerPath,
            ($iconSpawner | ConvertTo-Json -Depth 12))

        $system = [ordered]@{
            Spawners = @(
                [ordered]@{ SpawnerId = $frameSpawnerId; FixedRotation = $false },
                [ordered]@{ SpawnerId = $iconSpawnerId; FixedRotation = $false }
            )
            LifeSpan = 0.55
            IsImportant = $true
        }
        [IO.File]::WriteAllText(
            (Join-Path $particleRoot ($systemId + '.particlesystem')),
            ($system | ConvertTo-Json -Depth 8))
        $generatedOrderParticles++
    }
} finally {
    $archive.Dispose()
}
if ($generatedOrderParticles -eq 0) {
    throw 'No Food item icons were available for patron order particles.'
}
Write-Host "Packaged $generatedOrderParticles patron order icon ParticleSystem(s)."

$obsoleteHudIcons = @(
    (Join-Path $classes 'Common\UI\Custom\Hud\Icons\Comfort.png'),
    (Join-Path $classes 'Common\UI\Custom\Hud\Icons\Relaxed.png')
)
foreach ($obsoleteHudIcon in $obsoleteHudIcons) {
    if (Test-Path -LiteralPath $obsoleteHudIcon) {
        Remove-Item -LiteralPath $obsoleteHudIcon -Force
    }
}
if (Test-Path -LiteralPath $outputJar) {
    Remove-Item -LiteralPath $outputJar -Force
}
Push-Location $classes
try {
    & $jarTool --create --file $outputJar .
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host "Built $outputJar"
