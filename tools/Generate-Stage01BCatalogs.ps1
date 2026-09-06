param(
    [Parameter(Mandatory = $true)]
    [string]$MasterSpecification,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\rpg\catalog')
)

$ErrorActionPreference = 'Stop'

function Unescape-Markdown([string]$value) {
    if ($null -eq $value) { return '' }
    return ($value -replace '\\_', '_' -replace '\\-', '-' -replace '\\=', '=').Trim()
}

function Token([string]$value) {
    $expanded = (Unescape-Markdown $value) -creplace '([a-z0-9])([A-Z])', '$1_$2'
    return ($expanded.ToUpperInvariant() -replace '[^A-Z0-9]+', '_').Trim('_')
}

function Field([string]$body, [string]$label) {
    $match = [regex]::Match($body, "(?m)^\| $([regex]::Escape($label)) \| (?<value>.*?) \|\s*$")
    if ($match.Success) { return (Unescape-Markdown $match.Groups['value'].Value) }
    return ''
}

function Bold-Field([string]$body, [string]$label) {
    $match = [regex]::Match($body, "(?m)^\*\*$([regex]::Escape($label)):\*\*\s*(?<value>.*?)\s*$")
    if ($match.Success) { return (Unescape-Markdown $match.Groups['value'].Value) }
    return ''
}

function Unique-Tokens([System.Collections.IEnumerable]$values) {
    return @($values | Where-Object { $_ -and $_.Trim() } | ForEach-Object { Token $_ } | Sort-Object -Unique)
}

function Skill-Capabilities([string]$family, [string[]]$tags, [string]$resource,
                            [string]$castCooldown, [string]$rangeGeometry,
                            [string]$travelDuration, [string]$power,
                            [string]$status, [string]$description) {
    $capabilities = [System.Collections.Generic.List[string]]::new()
    foreach ($tag in $tags) { $capabilities.Add($tag) }
    $familyToken = Token $family
    $capabilities.Add($familyToken)

    if ($familyToken -eq 'PROJECTILE' -or $tags -contains 'PROJECTILE') {
        foreach ($cap in 'CAN_PIERCE','CAN_FORK','CAN_CHAIN','CAN_RICOCHET','CAN_TERRAIN_BOUNCE','CAN_RETURN',
                'SUPPORTS_MULTIPLE_PROJECTILES','SUPPORTS_STEERING','HAS_TERMINATION','ORBIT_CONVERTIBLE') { $capabilities.Add($cap) }
    }
    if ($rangeGeometry -match '(?i)radius' -and $rangeGeometry -notmatch '(?i)projectile radius|hitbox') {
        $capabilities.Add('HAS_RADIUS')
    }
    if ($rangeGeometry -match '\d' -and $rangeGeometry -notmatch '(?i)^Self') { $capabilities.Add('HAS_RANGE') }
    if ($rangeGeometry -match '(?i)width|wide line') { $capabilities.Add('HAS_WIDTH') }
    if ($travelDuration -notmatch '(?i)^\s*(N/A\s*/\s*)?Instant\s*$' -and $travelDuration -match '\d') {
        $capabilities.Add('HAS_FINITE_DURATION')
    }
    if ($resource -match '(?i)Mana|Stamina|Health') { $capabilities.Add('FINITE_RESOURCE_COST') }
    if ($resource -match '(?i)upkeep|per second|/s') { $capabilities.Add('HAS_UPKEEP') }
    if ($resource -match '(?i)reservation') { $capabilities.Add('MANA_RESERVATION') }
    if ($castCooldown -match '(?i)\d.*s\s*$') { $capabilities.Add('HAS_COOLDOWN') }
    $castPart = @($castCooldown -split '\s+/\s+', 2)[0]
    if ($castPart -match '\d' -and $castPart -notmatch '(?i)Instant') { $capabilities.Add('HAS_CAST_TIME') }
    if ($rangeGeometry -match '(?i)radius|cone|nova|area|zone|aura|explosion|burst') { $capabilities.Add('HAS_AREA_GEOMETRY') }
    if ($familyToken -eq 'GROUND_ZONE') { $capabilities.Add('GROUND_TARGETED'); $capabilities.Add('PERSISTENT_FINITE_EFFECT') }
    if ($familyToken -in @('GROUND_ZONE','AURA') -and $travelDuration -match '\d') { $capabilities.Add('PERIODIC_PULSE') }
    if ($resource -match '(?i)Mana' -and $familyToken -notin @('AURA','CHANNEL','STANCE','SUMMON','MOVEMENT','TRANSFORMATION')) {
        $capabilities.Add('SPELL_CAST')
    }
    if ($familyToken -notin @('AURA','CHANNEL','STANCE','REACTION','TRIGGERED','TRANSFORMATION')) {
        $capabilities.Add('DISCRETE_ACTIVATION')
    }
    if ($power -notmatch '(?i)^No direct damage|^None|^N/A') {
        $capabilities.Add('DAMAGE')
        $capabilities.Add('CAN_ACQUIRE_ENEMY_TARGET')
        $capabilities.Add('CAN_KILL')
        $capabilities.Add('CAN_CRIT')
        $capabilities.Add('DIRECT_HIT')
        $capabilities.Add('SCALABLE_PAYLOAD')
    }
    if (($description + ' ' + ($tags -join ' ')) -match '(?i)heal') { $capabilities.Add('HEAL'); $capabilities.Add('SCALABLE_PAYLOAD') }
    if (($description + ' ' + ($tags -join ' ')) -match '(?i)barrier|shield|guard') {
        foreach ($cap in 'BARRIER','SHIELD','SELF_TARGETED_BARRIER','ABSORBS_DAMAGE','SCALABLE_PAYLOAD') { $capabilities.Add($cap) }
    }
    if ($familyToken -eq 'SUMMON' -or $tags -contains 'SUMMON') {
        foreach ($cap in 'SUMMON','PROXY','TEMPORARY_SUMMON','TEMPORARY_COMBAT_SUMMON') { $capabilities.Add($cap) }
    }
    if ($resource -match '(?i)Mana') { $capabilities.Add('MANA_RESOURCE') }
    if ($resource -match '(?i)Stamina') { $capabilities.Add('STAMINA_RESOURCE') }
    if ($resource -match '(?i)Health') { $capabilities.Add('HEALTH_RESOURCE') }
    if ($status -match '(?i)Burn') { $capabilities.Add('APPLIES_BURN') }
    if ($status -match '(?i)Chill|Frozen') { $capabilities.Add('APPLIES_CHILL') }
    if ($status -match '(?i)Poison') { $capabilities.Add('APPLIES_POISON') }
    return @($capabilities | Sort-Object -Unique)
}

function Passive-Priority([string]$id) {
    switch ($id) {
        { $_ -in @('mobile_domain','orbit') } { return 100 }
        { $_ -in @('long_reach','homing','phantom_reach') } { return 200 }
        { $_ -in @('expanded_radius','lingering','accelerant','ballistics','rapid_pulse','vacuum','repulsion','widening','focused_channel') } { return 300 }
        { $_ -in @('echo','volley','barrage','multistrike','swarm') } { return 400 }
        'ricochet' { return 510 }
        'piercing' { return 520 }
        'fork' { return 530 }
        'chain' { return 540 }
        'return' { return 550 }
        { $_ -in @('efficiency','rapid_invocation','second_wind','conservation','lifeblood','attunement') } { return 600 }
        { $_ -in @('retaliation','critical_trigger','kill_trigger') } { return 750 }
        default { return 700 }
    }
}

function Required-Gate-Tokens([string]$gate, [string]$label) {
    $match = [regex]::Match($gate, "(?i)${label}:\s*(?<value>.*?)(?:;|$)")
    if (-not $match.Success) { return @() }
    $value = $match.Groups['value'].Value
    if ($label -eq 'Payload') { $value = ($value -split '(?i)\s+AND\s+', 2)[0] }
    $value = ($value -split '(?i)\s+(?:capability|tag):', 2)[0]
    $value = $value -replace '(?i)\band\b', '+' -replace '(?i)\bor\b', ','
    return Unique-Tokens ($value -split '[+,/]')
}

if (-not (Test-Path -LiteralPath $MasterSpecification -PathType Leaf)) {
    throw "Master specification not found: $MasterSpecification"
}

$text = Get-Content -Raw -LiteralPath $MasterSpecification
$skills = [System.Collections.Generic.List[object]]::new()
$skillPattern = '(?ms)^## SK-(?<number>\d+)\. (?<name>[^\r\n]+)\r?\n\r?\n(?<body>.*?)(?=^## SK-\d+\.|^# 10\\\.)'
foreach ($match in [regex]::Matches($text, $skillPattern)) {
    $name = Unescape-Markdown $match.Groups['name'].Value
    $body = $match.Groups['body'].Value
    $identity = [regex]::Match($body, '(?m)^rpg\.skill\.(?<id>[^ •\r\n]+) • Tier (?<tier>[^•]+) • Phase (?<phase>\d+) • (?<family>[^•]+) • (?<element>[^•]+) •')
    if (-not $identity.Success) { throw "Unable to parse skill identity: $name" }
    $id = Unescape-Markdown $identity.Groups['id'].Value
    $family = Unescape-Markdown $identity.Groups['family'].Value
    $element = Unescape-Markdown $identity.Groups['element'].Value
    $descriptionMatch = [regex]::Match($body.Substring($identity.Index + $identity.Length), '(?m)^\s*\r?\n(?<description>[^\r\n|*#][^\r\n]*)')
    $description = if ($descriptionMatch.Success) { Unescape-Markdown $descriptionMatch.Groups['description'].Value } else { $name }
    $familyRow = Field $body 'Family / weapon / scaling'
    $resource = Field $body 'Resource'
    $castCooldown = Field $body 'Cast / cooldown'
    $rangeGeometry = Field $body 'Range / geometry'
    $travelDuration = Field $body 'Travel / duration'
    $power = Field $body 'Power'
    $status = Field $body 'Status / control'
    $source = Field $body 'Signature source'
    $familyTagsRaw = if ($familyRow -match '^(?<tags>.*?)\s+/\s+Requirement:') { $Matches['tags'] } else { $family }
    $tags = @(Unique-Tokens (($familyTagsRaw -split '\s*/\s*') + $element + $family))
    $weapon = if ($familyRow -match '(?i)Requirement:\s*(?<weapon>.*?)\s+/\s+Scaling:') { Unescape-Markdown $Matches['weapon'] } else { 'None' }
    $scaling = if ($familyRow -match '(?i)Scaling:\s*(?<scaling>.*)$') { Unescape-Markdown $Matches['scaling'] } else { 'Utility' }
    $castParts = @($castCooldown -split '\s+/\s+', 2)
    $travelParts = @($travelDuration -split '\s+/\s+', 2)
    $vfx = if ($body -match '(?m)\*\*Icon / VFX keys:\*\*\s*[^/]+/\s*(?<vfx>rpg\.vfx\.[^\.\s]+\.[^\.\s]+)') { Unescape-Markdown $Matches['vfx'] } else { "rpg.vfx.skill.$id" }
    $basePower = if ($power -match '(?i)Magic Power') { 'MAGIC_WEAPON' } elseif ($power -match '(?i)Weapon Power|Light Power|Heavy Power|scaling power') { 'WEAPON' } elseif ($power -match '(?i)No direct damage|None|N/A') { 'NONE' } else { 'INNATE' }
    $sourceParts = @($source -split ';', 2)
    $capabilities = @(Skill-Capabilities $family $tags $resource $castCooldown $rangeGeometry $travelDuration $power $status $description)
    [string[]]$statusApplications = if ($status -match '(?i)^None') { @() } else { @($status) }
    $skills.Add([ordered]@{
        schemaVersion = 1
        id = $id
        skillId = "rpg.skill.$id"
        name = $name
        description = $description
        tier = (Unescape-Markdown $identity.Groups['tier'].Value)
        phase = [int]$identity.Groups['phase'].Value
        tags = $tags
        weaponRequirement = $weapon
        scalingClass = $scaling
        basePowerSource = $basePower
        innateBasePower = $null
        resourceType = if ($resource -match '(?i)Mana') { 'MANA' } elseif ($resource -match '(?i)Stamina') { 'STAMINA' } elseif ($resource -match '(?i)Health') { 'HEALTH' } else { 'NONE' }
        castCost = $resource
        upkeep = if ($resource -match '(?i)upkeep|reservation|per second|/s') { $resource } else { '' }
        castTime = if ($castParts.Count -gt 0) { $castParts[0] } else { '' }
        cooldown = if ($castParts.Count -gt 1) { $castParts[1] } else { '' }
        targetMode = $rangeGeometry
        maxRange = if ($rangeGeometry -match '^(?<range>.*?)\s+/') { $Matches['range'].Trim() } else { $rangeGeometry }
        geometry = if ($rangeGeometry -match '/\s*(?<geometry>.*)$') { $Matches['geometry'].Trim() } else { $rangeGeometry }
        travelSpeed = if ($travelParts.Count -gt 0) { $travelParts[0] } else { '' }
        lifetime = if ($travelParts.Count -gt 1) { $travelParts[1] } else { '' }
        powerCoefficient = $power
        canCrit = ($power -notmatch '(?i)^No direct damage|^None|^N/A')
        statusApplications = $statusApplications
        vfxRecipeId = $vfx
        soundRecipeId = "rpg.sound.skill.$id"
        linkCompatibilityTags = $capabilities
        sourceAcquisition = [ordered]@{
            signatureEnemyId = if ($sourceParts.Count -gt 0) { $sourceParts[0].Trim() } else { '' }
            validationState = if ($sourceParts.Count -gt 1) { (Token $sourceParts[1]) } else { 'UNASSIGNED' }
            learnChance = $null
            acquisitionRarity = 'NORMAL'
            difficultyVariant = 'NORMAL'
        }
        aliases = @($name, $id, "rpg.skill.$id")
    })
}

$passives = [System.Collections.Generic.List[object]]::new()
$passivePattern = '(?ms)^## LP-(?<number>\d+)\. (?<name>[^\r\n]+)\r?\n\r?\n(?<body>.*?)(?=^## LP-\d+\.|^# 11\\\.)'
foreach ($match in [regex]::Matches($text, $passivePattern)) {
    $name = Unescape-Markdown $match.Groups['name'].Value
    $body = $match.Groups['body'].Value
    $identity = [regex]::Match($body, '(?m)^rpg\.passive\.(?<id>[^ •\r\n]+) • Phase (?<phase>\d+) • (?<tier>[^•]+) •')
    if (-not $identity.Success) { throw "Unable to parse passive identity: $name" }
    $id = Unescape-Markdown $identity.Groups['id'].Value
    $gate = Bold-Field $body 'Compatibility gate'
    $effect = Bold-Field $body 'Exact baseline effect'
    $execution = Bold-Field $body 'Execution / interactions'
    $safety = Bold-Field $body 'Master parameters / safety'
    $fixture = Bold-Field $body 'Fixture'
    $requiredFamilies = @(Required-Gate-Tokens $gate 'Family')
    $requiredCapabilities = @(Required-Gate-Tokens $gate 'capability')
    $requiredPayloads = @(Required-Gate-Tokens $gate 'Payload')
    $excluded = @()
    if (($gate + ' ' + $execution + ' ' + $safety) -match '(?i)excludes?\s+(?<excluded>[^.;]+)') {
        $excluded = @(Unique-Tokens ($Matches['excluded'] -split '[,/]|\bor\b|\band\b'))
    }
    $added = @()
    if ($execution -match '(?i)gains?\s+(?<tags>[^.;]+?)\s+tags?') { $added = @(Unique-Tokens ($Matches['tags'] -split '[,/]|\band\b')) }
    $removed = @()
    if ($execution -match '(?i)removes?\s+(?<tags>[^.;]+?)\s+(?:tags?|capabilities?)') { $removed = @(Unique-Tokens ($Matches['tags'] -split '[,/]|\band\b')) }
    $stacking = if (($execution + ' ' + $safety) -match '(?i)conflicts? with\s+(?<group>[^.;]+)') { Token $Matches['group'] } else { '' }
    if ($id -in @('echo','barrage','multistrike','retaliation','critical_trigger','kill_trigger')) { $stacking = 'EXCLUSIVE_REPEAT_CONTROLLER' }
    $trigger = switch ($id) {
        'retaliation' { 'ON_DAMAGE_TAKEN' }
        'critical_trigger' { 'ON_CRITICAL_HIT' }
        'kill_trigger' { 'ON_KILL' }
        default { '' }
    }
    $conversion = switch ($id) {
        'mobile_domain' { 'MOBILE_ZONE' }
        'orbit' { 'ORBIT' }
        default { '' }
    }
    $aliases = @($name, $id, "rpg.passive.$id")
    if ($id -eq 'expanded_radius') { $aliases += @('Expanded Area','expanded_area') }
    $passives.Add([ordered]@{
        schemaVersion = 1
        id = $id
        passiveId = "rpg.passive.$id"
        name = $name
        description = $effect
        tier = (Unescape-Markdown $identity.Groups['tier'].Value)
        phase = [int]$identity.Groups['phase'].Value
        compatibleTags = @($requiredFamilies + $requiredCapabilities + $requiredPayloads | Sort-Object -Unique)
        requiredFamilies = $requiredFamilies
        requiredCapabilities = $requiredCapabilities
        compatibleAnyPayloads = $requiredPayloads
        incompatibleTags = $excluded
        compatibilityExpression = $gate
        modifierOps = @($effect)
        addedTags = $added
        removedTags = $removed
        familyConversion = $conversion
        triggerHook = $trigger
        priority = Passive-Priority $id
        stackingGroup = $stacking
        maxCopies = 1
        spawnBudgetCost = if ($id -eq 'fork') { 2 } elseif ($id -in @('echo','volley','barrage','multistrike','swarm')) { 1 } else { 0 }
        childProjectileCount = if ($id -eq 'fork') { 2 } else { 0 }
        executionNotes = $execution
        safetyNotes = $safety
        fixture = $fixture
        aliases = $aliases
    })
}

if ($skills.Count -ne 87) { throw "Expected 87 skills, parsed $($skills.Count)" }
if ($passives.Count -ne 66) { throw "Expected 66 passives, parsed $($passives.Count)" }

$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$skillPath = Join-Path $OutputDirectory 'skills.json'
$passivePath = Join-Path $OutputDirectory 'passives.json'
$skills | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $skillPath -Encoding utf8NoBOM
$passives | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $passivePath -Encoding utf8NoBOM

Write-Output "Generated $($skills.Count) skills: $skillPath"
Write-Output "Generated $($passives.Count) passives: $passivePath"
