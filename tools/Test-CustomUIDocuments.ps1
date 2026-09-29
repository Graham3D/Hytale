[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0, ValueFromRemainingArguments)]
    [string[]]$Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$documents = [System.Collections.Generic.List[object]]::new()
foreach ($candidate in $Path) {
    $resolved = Resolve-Path -LiteralPath $candidate -ErrorAction Stop
    foreach ($item in $resolved) {
        if (Test-Path -LiteralPath $item.Path -PathType Container) {
            $scanRoot = [IO.Path]::GetFullPath($item.Path).TrimEnd([IO.Path]::DirectorySeparatorChar)
            Get-ChildItem -LiteralPath $item.Path -Recurse -File -Filter '*.ui' | Where-Object {
                $relative = $_.FullName.Substring($scanRoot.Length).TrimStart([char]'\',[char]'/')
                $segments = $relative -split '[\\/]'
                -not @($segments | Where-Object { $_ -in @('.git','build','gradle-build','dist','run','evidence') }).Count
            } | ForEach-Object {
                $documents.Add([pscustomobject]@{ Name = $_.FullName; Text = Get-Content -LiteralPath $_.FullName -Raw })
            }
            continue
        }

        if ([IO.Path]::GetExtension($item.Path) -ieq '.jar') {
            $archive = [IO.Compression.ZipFile]::OpenRead($item.Path)
            try {
                foreach ($entry in $archive.Entries | Where-Object { $_.FullName -like '*.ui' }) {
                    $reader = [IO.StreamReader]::new($entry.Open())
                    try {
                        $documents.Add([pscustomobject]@{ Name = "$($item.Path)!/$($entry.FullName)"; Text = $reader.ReadToEnd() })
                    }
                    finally { $reader.Dispose() }
                }
            }
            finally { $archive.Dispose() }
            continue
        }

        if ([IO.Path]::GetExtension($item.Path) -ieq '.ui') {
            $documents.Add([pscustomobject]@{ Name = $item.Path; Text = Get-Content -LiteralPath $item.Path -Raw })
            continue
        }

        throw "Unsupported CustomUI validation target: $($item.Path)"
    }
}

$errors = [System.Collections.Generic.List[string]]::new()
$gameAssets = Join-Path $env:APPDATA 'Hytale/install/pre-release/package/game/latest/Assets.zip'
if (Test-Path -LiteralPath $gameAssets) {
    $gameArchive = [IO.Compression.ZipFile]::OpenRead($gameAssets)
    try {
        $commonEntry = $gameArchive.GetEntry('Common/UI/Custom/Common.ui')
        if ($null -eq $commonEntry) { throw 'Installed Hytale assets lack Common.ui.' }
        $commonReader = [IO.StreamReader]::new($commonEntry.Open())
        try { $commonText = $commonReader.ReadToEnd() } finally { $commonReader.Dispose() }
    }
    finally { $gameArchive.Dispose() }
    $commonMacros = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($definition in [regex]::Matches($commonText, '(?m)^\s*@(?<name>\w+)\s*(?:=|\{)')) {
        [void]$commonMacros.Add($definition.Groups['name'].Value)
    }
    foreach ($document in $documents) {
        if ($document.Text -notmatch '\$C\s*=\s*"Common\.ui"') { continue }
        foreach ($reference in [regex]::Matches($document.Text, '\$C\.@(?<name>\w+)')) {
            if (-not $commonMacros.Contains($reference.Groups['name'].Value)) {
                $referenceLine = 1 + ([regex]::Matches($document.Text.Substring(0, $reference.Index), "`n")).Count
                $errors.Add("$($document.Name) ($referenceLine): Common.ui has no macro $($reference.Value) in installed Hytale assets.")
            }
        }
    }
}
foreach ($document in $documents) {
    $text = $document.Text
    $line = 1
    $column = 0
    $inString = $false
    $stringLine = 0
    $stringColumn = 0
    $delimiters = [System.Collections.Generic.Stack[object]]::new()

    for ($index = 0; $index -lt $text.Length; $index++) {
        $character = $text[$index]
        if ($character -eq "`n") { $line++; $column = 0; continue }
        $column++

        if ($inString) {
            if ($character -eq '\') {
                if ($index + 1 -ge $text.Length -or ($text[$index + 1] -ne '\' -and $text[$index + 1] -ne '"')) {
                    $next = if ($index + 1 -lt $text.Length) { $text[$index + 1] } else { '<eof>' }
                    $errors.Add(('{0} ({1}:{2}): invalid string escape \{3}; CustomUI permits only \\ and \".' -f $document.Name, $line, $column, $next))
                }
                else { $index++; $column++ }
                continue
            }
            if ($character -eq '"') { $inString = $false }
            continue
        }

        if ($character -eq '"') {
            $inString = $true
            $stringLine = $line
            $stringColumn = $column
            continue
        }

        if ($character -in @('{', '(', '[')) {
            $delimiters.Push([pscustomobject]@{ Character = $character; Line = $line; Column = $column })
            continue
        }
        if ($character -in @('}', ')', ']')) {
            $expected = switch ($character) { '}' { '{' } ')' { '(' } ']' { '[' } }
            if ($delimiters.Count -eq 0) {
                $errors.Add("$($document.Name) ($line`:$column): unexpected closing delimiter $character.")
            }
            else {
                $opening = $delimiters.Pop()
                if ($opening.Character -ne $expected) {
                    $errors.Add("$($document.Name) ($line`:$column): $character closes $($opening.Character) from $($opening.Line):$($opening.Column).")
                }
            }
        }
    }

    if ($inString) { $errors.Add("$($document.Name) ($stringLine`:$stringColumn): unterminated string.") }
    while ($delimiters.Count -gt 0) {
        $opening = $delimiters.Pop()
        $errors.Add("$($document.Name) ($($opening.Line)`:$($opening.Column)): unclosed delimiter $($opening.Character).")
    }

    foreach ($match in [regex]::Matches($text, '(?ms)\bButton(?:\s+#\w+)?\s*\{(?<body>[^{}]*)\}')) {
        if ($match.Groups['body'].Value -match '(?m)^\s*Text\s*:') {
            $matchLine = 1 + ([regex]::Matches($text.Substring(0, $match.Index), "`n")).Count
            $errors.Add("$($document.Name) ($matchLine): Button does not accept Text; use TextButton for a labeled control.")
        }
    }

    # Imported-control macro arguments must precede concrete properties. Scope the
    # ordering check to the imported control's own leaf body. A parent Group may
    # legally declare concrete properties before containing an imported control on
    # the same line; the previous line-wide regex incorrectly rejected that shape.
    foreach ($control in [regex]::Matches($text,
            '(?ms)(?:\$\w+\.)?@\w+(?:\s+#\w+)?\s*\{(?<body>[^{}]*)\}')) {
        $body = $control.Groups['body'].Value
        $segments = [System.Collections.Generic.List[string]]::new()
        $segmentStart = 0
        $nestedDepth = 0
        $bodyInString = $false
        $bodyEscaped = $false
        for ($bodyIndex = 0; $bodyIndex -lt $body.Length; $bodyIndex++) {
            $bodyCharacter = $body[$bodyIndex]
            if ($bodyInString) {
                if ($bodyEscaped) { $bodyEscaped = $false; continue }
                if ($bodyCharacter -eq '\') { $bodyEscaped = $true; continue }
                if ($bodyCharacter -eq '"') { $bodyInString = $false }
                continue
            }
            if ($bodyCharacter -eq '"') { $bodyInString = $true; continue }
            if ($bodyCharacter -in @('(', '[')) { $nestedDepth++; continue }
            if ($bodyCharacter -in @(')', ']')) { $nestedDepth--; continue }
            if ($bodyCharacter -eq ';' -and $nestedDepth -eq 0) {
                $segments.Add($body.Substring($segmentStart, $bodyIndex - $segmentStart))
                $segmentStart = $bodyIndex + 1
            }
        }
        if ($segmentStart -lt $body.Length) { $segments.Add($body.Substring($segmentStart)) }

        $concretePropertySeen = $false
        $lateMacroArgument = $false
        foreach ($segment in $segments) {
            $trimmed = $segment.Trim()
            if ($trimmed -match '^@\w+\s*=') {
                if ($concretePropertySeen) { $lateMacroArgument = $true; break }
            }
            elseif ($trimmed -match '^\w+\s*:') { $concretePropertySeen = $true }
        }
        if ($lateMacroArgument) {
            $matchLine = 1 + ([regex]::Matches($text.Substring(0, $control.Index), "`n")).Count
            $errors.Add("$($document.Name) ($matchLine): imported-control macro arguments must precede concrete properties; use Text: or move the macro argument before the property.")
        }
    }

    # LabelAlignment is a client enum. The installed 0.7.0-pre.3.1 documents use
    # Start/Center/End; CSS-like Left/Right values make the client reject the
    # complete CustomUI document during connection.
    foreach ($match in [regex]::Matches($text, '(?m)\b(?:Horizontal|Vertical)Alignment\s*:\s*(?<value>Left|Right)\b')) {
        $matchLine = 1 + ([regex]::Matches($text.Substring(0, $match.Index), "`n")).Count
        $replacement = if ($match.Groups['value'].Value -eq 'Left') { 'Start' } else { 'End' }
        $errors.Add("$($document.Name) ($matchLine): unsupported LabelAlignment '$($match.Groups['value'].Value)'; use '$replacement'.")
    }

    # The pre.4 client Anchor codec rejects MaxHeight at document load time.
    foreach ($match in [regex]::Matches($text, '(?m)\bMaxHeight\s*:')) {
        $matchLine = 1 + ([regex]::Matches($text.Substring(0, $match.Index), "`n")).Count
        $errors.Add("$($document.Name) ($matchLine): unsupported Anchor field MaxHeight in 0.7.0-pre.4.")
    }

    # Hytale UI references the logical .png name; @2x names are physical
    # density variants. Addressing the variant directly renders a red X.
    foreach ($match in [regex]::Matches($text, 'TexturePath\s*:\s*"Icons/Hytale/[^"\r\n]+@2x\.png"')) {
        $matchLine = 1 + ([regex]::Matches($text.Substring(0, $match.Index), "`n")).Count
        $errors.Add("$($document.Name) ($matchLine): refer to the logical Icons/Hytale/*.png path, not its @2x asset variant.")
    }
}

if ($documents.Count -eq 0) { throw 'No CustomUI .ui documents were found in the validation targets.' }
if ($errors.Count -gt 0) { throw "CustomUI validation failed:`n$($errors -join "`n")" }

"Validated $($documents.Count) CustomUI document(s): no invalid escapes, unterminated strings, unbalanced delimiters, labeled Button misuse, invalid LabelAlignment values, unsupported MaxHeight anchors, or late imported-control macro arguments."
