param([string]$AssetsZip = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip")
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$resources = Join-Path $PSScriptRoot '../src/main/resources'
$target = Join-Path $resources 'rpg/gear/action-templates/native-primary'
$expectedAssetsSha256='C02E69A0FA4088B965D0CA9D01C6A55610229EFA3F78F91FB45463108F07D022'
if((Get-FileHash -LiteralPath $AssetsZip -Algorithm SHA256).Hash -ne $expectedAssetsSha256){
  throw 'Installed Assets.zip changed; re-audit every native attack graph before regenerating variants'
}
$families = [ordered]@{
  battleaxe='Root_Weapon_Battleaxe_Primary'; mace='Root_Weapon_Mace_Primary'
  daggers='Root_Weapon_Daggers_Primary'; longsword='Longsword_Attack'
  shortbow='RPG_GearRoute_R_Root_Weapon_Shortbow_Primary_Shoot'
  crossbow='RPG_GearRoute_R_Root_Weapon_Crossbow_Primary_Signature'
  staff='Staff_Primary'; wand='Wand_Primary'; book='Spellbook_Primary'
  shield='RPG_Carrier_Shield_Primary'; bomb='RPG_Carrier_Bomb_Throw_Root'
  twin='RPG_Twin_Daggers_Root'
}
Add-Type -AssemblyName System.IO.Compression
$zip=[IO.Compression.ZipFile]::OpenRead($AssetsZip)
try {
  $index=@{}
  foreach($entry in $zip.Entries) {
    if($entry.FullName -match '^Server/Item/(Interactions|RootInteractions|Animations)/.*\.json$') {
      $kind=$Matches[1];$id=[IO.Path]::GetFileNameWithoutExtension($entry.Name)
      $key="$kind/$id"
      if($index.ContainsKey($key)) { throw "Duplicate native action asset $key" }
      $index[$key]=$entry
    }
  }
  $local=@{}
  foreach($kind in @('Interactions','RootInteractions','Animations')) {
    Get-ChildItem (Join-Path $resources "Server/Item/$kind") -Filter '*.json' -Recurse | ForEach-Object {
      $key="$kind/$($_.BaseName)"
      if($local.ContainsKey($key)) { throw "Duplicate packaged action asset $key" }
      $local[$key]=$_.FullName
    }
  }
  Get-ChildItem (Join-Path $resources 'rpg/gear/action-templates/twin-daggers') -Filter '*.json' | ForEach-Object {
    $kind=if($_.BaseName -eq 'RPG_Twin_Daggers_Root'){'RootInteractions'}else{'Interactions'}
    $local["$kind/$($_.BaseName)"]=$_.FullName
  }
  function Read-Asset([string]$key) {
    if($local.ContainsKey($key)){return [IO.File]::ReadAllText($local[$key])}
    if(!$index.ContainsKey($key)){throw "Missing native action asset $key"}
    $reader=[IO.StreamReader]::new($index[$key].Open())
    try{return $reader.ReadToEnd()}finally{$reader.Dispose()}
  }
  function Collect-Strings($node,[Collections.Generic.List[string]]$values,[string]$field='') {
    if($node -is [string]){if($field -in @('Interactions','Next','Failed')){$values.Add($node)};return}
    if($node -is [System.Collections.IDictionary]) {
      foreach($key in $node.Keys){
        $nextField=if($field -eq 'Next' -and [string]$key -match '^\d+(\.\d+)?$'){'Next'}else{[string]$key}
        Collect-Strings $node[$key] $values $nextField
      }
    }elseif($node -is [System.Collections.IList]) {
      foreach($value in $node){Collect-Strings $value $values $field}
    }
  }
  function Resolve-Animation([string]$id,[Collections.Generic.HashSet[string]]$visited) {
    if(!$visited.Add($id)){throw "Animation inheritance cycle $id"}
    $asset=(Read-Asset "Animations/$id")|ConvertFrom-Json -AsHashtable
    $merged=[ordered]@{}
    if($asset.ContainsKey('Parent') -and $asset['Parent']) {
      $parent=Resolve-Animation ([string]$asset['Parent']) $visited
      foreach($key in $parent.Keys){$merged[$key]=$parent[$key]}
    }
    foreach($key in $asset['Animations'].Keys){$merged[$key]=$asset['Animations'][$key]}
    return $merged
  }
  function Clone-Reference([string]$family,[string]$id) {
    if($id -match 'No_Ammo|StatAmmoReload'){return $false}
    return $index.ContainsKey("Interactions/$id") -or $local.ContainsKey("Interactions/$id")
  }
  New-Item -ItemType Directory -Force -Path $target | Out-Null
  $manifest=[ordered]@{}
  foreach($family in $families.Keys) {
    $root=$families[$family]
    $queue=[Collections.Generic.Queue[string]]::new()
    $found=[Collections.Generic.HashSet[string]]::new()
    $animations=[Collections.Generic.HashSet[string]]::new()
    $familyDir=Join-Path $target $family
    New-Item -ItemType Directory -Force -Path $familyDir | Out-Null
    $rootText=Read-Asset "RootInteractions/$root"
    [IO.File]::WriteAllText((Join-Path $familyDir 'root.json'),$rootText,[Text.UTF8Encoding]::new($false))
    $rootJson=$rootText|ConvertFrom-Json -AsHashtable
    $rootValues=[Collections.Generic.List[string]]::new();Collect-Strings $rootJson $rootValues
    foreach($value in $rootValues){if(Clone-Reference $family $value){$queue.Enqueue($value)}}
    $launchRoot=$null
    if($family -in @('staff','wand','book')) {
      $launchRoot='RPG_Carrier_{0}_Launch_Root' -f (Get-Culture).TextInfo.ToTitleCase($family)
      $launchText=Read-Asset "RootInteractions/$launchRoot"
      [IO.File]::WriteAllText((Join-Path $familyDir 'launch-root.json'),$launchText,[Text.UTF8Encoding]::new($false))
      $launchValues=[Collections.Generic.List[string]]::new()
      Collect-Strings ($launchText|ConvertFrom-Json -AsHashtable) $launchValues
      foreach($value in $launchValues){if(Clone-Reference $family $value){$queue.Enqueue($value)}}
    }
    $carrierFamily=if($family -eq 'twin'){'daggers'}else{$family}
    $carrier=Get-ChildItem (Join-Path $resources 'Server/Item/Items/RPG/Gear') -Filter "RPG_Gear_${carrierFamily}_*.json" | Select-Object -First 1
    if($null -eq $carrier){throw "No carrier for $family"}
    $carrierJson=[IO.File]::ReadAllText($carrier.FullName)|ConvertFrom-Json -AsHashtable
    $selectedVars=[Collections.Generic.HashSet[string]]::new()
    while($true) {
      while($queue.Count -gt 0) {
        $id=$queue.Dequeue()
        if(!$found.Add($id)){continue}
        $json=Read-Asset "Interactions/$id"
        [IO.File]::WriteAllText((Join-Path $familyDir "$id.json"),$json,[Text.UTF8Encoding]::new($false))
        foreach($match in [regex]::Matches($json,'"ItemAnimationId"\s*:\s*"([^"]+)"')){[void]$animations.Add($match.Groups[1].Value)}
        $values=[Collections.Generic.List[string]]::new();Collect-Strings ($json|ConvertFrom-Json -AsHashtable) $values
        foreach($value in $values) {
          if(Clone-Reference $family $value){$queue.Enqueue($value)}
        }
      }
      foreach($id in @($found)) {
        $json=Read-Asset "Interactions/$id"
        foreach($match in [regex]::Matches($json,'"Var"\s*:\s*"([^"]+)"')) {
          $key=$match.Groups[1].Value
          if(!$selectedVars.Add($key) -or !$carrierJson['InteractionVars'].ContainsKey($key)){continue}
          $value=$carrierJson['InteractionVars'][$key]
          $values=[Collections.Generic.List[string]]::new()
          if($value -is [string]){$values.Add($value)}else{Collect-Strings $value $values}
          foreach($reference in $values){
            if(Clone-Reference $family $reference){$queue.Enqueue($reference)}
          }
        }
      }
      if($queue.Count -eq 0){break}
    }
    $animationId=[string]$carrierJson['PlayerAnimationsId']
    if(!$animationId){throw "No item animation profile for $family"}
    $animationJson=[ordered]@{Animations=(Resolve-Animation $animationId ([Collections.Generic.HashSet[string]]::new()))}
    foreach($animation in $animations){if(!$animationJson['Animations'].Contains($animation)){throw "Missing $family animation $animation in $animationId"}}
    [IO.File]::WriteAllText((Join-Path $familyDir 'animations.json'),($animationJson|ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
    $keep=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    [void]$keep.Add('root.json');[void]$keep.Add('animations.json')
    if($launchRoot){[void]$keep.Add('launch-root.json')}
    foreach($node in $found){[void]$keep.Add("$node.json")}
    Get-ChildItem -LiteralPath $familyDir -File -Filter '*.json' | ForEach-Object {
      if(!$keep.Contains($_.Name)){Remove-Item -LiteralPath $_.FullName -Force}
    }
    $manifest[$family]=[ordered]@{root=$root;launchRoot=$launchRoot;nodes=@($found|Sort-Object);actionVariables=@($selectedVars|Sort-Object);animation=$animationId;attackAnimations=@($animations|Sort-Object)}
    Write-Output "$family root=$root interactions=$($found.Count) animationProfiles=$($animations.Count)"
  }
  [IO.File]::WriteAllText((Join-Path $target 'manifest.json'),($manifest|ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
} finally {$zip.Dispose()}
