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
            Get-ChildItem -LiteralPath $item.Path -Recurse -File -Filter '*.ui' | ForEach-Object {
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
}

if ($documents.Count -eq 0) { throw 'No CustomUI .ui documents were found in the validation targets.' }
if ($errors.Count -gt 0) { throw "CustomUI validation failed:`n$($errors -join "`n")" }

"Validated $($documents.Count) CustomUI document(s): no invalid escapes, unterminated strings, or unbalanced delimiters."
