<#
  Puts every binary file QVoice's build needs in place, each one checked by
  SHA-256. They stay out of git (see .gitignore): about 110 MB that comes from
  its publishers and rarely changes.

    app\libs\sherpa-onnx-1.13.8.aar
        the speech runtime, from sherpa-onnx's release (48 MB)
    app\src\main\assets\bundled\kitten-nano-en\
        the built-in voice: LICENSE, model.fp32.onnx, tokens.txt, voices.bin
    app\src\main\assets\bundled\espeak-ng-data.zip
        eSpeak NG's data, which the app unpacks on first run

  The voice and the eSpeak NG data both come from sherpa-onnx's
  kitten-nano-en-v0_8-fp32.tar.bz2 (64 MB), downloaded at most once, and only
  if something is missing or wrong. Files already in place and correct are
  left alone, so running this again is quick and harmless. A file is only
  replaced by one whose checksum matches.

  Run from the QVoice project folder:

    powershell -ExecutionPolicy Bypass -File tools\get-binaries.ps1

  GitHub runs the same script (PowerShell 7 on Linux) before every build:
  .github/workflows/build.yml. It replaces tools\get-bundled-voice.ps1 of
  slices 7 to 10, which only handled the voice.

  A download needs Windows 10 (version 1803) or newer for tar.exe, an
  internet connection and about 250 MB free in the temp folder.

  (ASCII only on purpose: Windows PowerShell 5.1 reads a script without a
  byte-order mark in the system code page.)
#>
$ErrorActionPreference = 'Stop'
# The progress bar makes Invoke-WebRequest many times slower on PowerShell 5.1.
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# ---- What goes where (the only place these URLs and checksums live) ----

$aarName   = 'sherpa-onnx-1.13.8.aar'
$aarUrl    = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/$aarName"
$aarSha256 = '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'

$archiveName   = 'kitten-nano-en-v0_8-fp32'
$archiveUrl    = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName.tar.bz2"
# Same value as BundledVoices.KITTEN_NANO_EN.sourceSha256 in the app.
$archiveSha256 = '16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e'

# The built-in voice's files, as they are in that archive.
$voiceFiles = [ordered]@{
    'LICENSE'         = 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30'
    'model.fp32.onnx' = '2174dbf67b58b7b50d7b65294f89c2c53c172834533519b853c579879a04cc22'
    'tokens.txt'      = '934a4188addc7665dd3410256bb622169242357fbb99d840d9351209b486dabb'
    'voices.bin'      = 'd520519c4a3519d44fcfcd943ed0b1e3c5da5cee0eea501d922fac1a93cd24dc'
}

# The zip's CONTENT, not its bytes (zip tools compress differently): SHA-256
# of the lines "<SHA-256 of the file>  <path in the zip>\n", sorted by path
# (ordinal), the same lines sha256sum prints. The files are the archive's
# espeak-ng-data folder, 355 of them. Its app-side counterpart is
# BundledVoices.ESPEAK_DATA_VERSION: change both together.
$espeakTreeSha256 = '71a159a95ee8e26c4110582ec69093f0e39eb453c3a33882f6e971510ff5c47a'

# ---- Paths ----

$project = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $project 'app/build.gradle.kts'))) {
    throw 'Run this from the QVoice project folder (get-binaries.ps1 must sit in its tools folder).'
}
$libs      = Join-Path $project 'app/libs'
$bundled   = Join-Path $project 'app/src/main/assets/bundled'
$aar       = Join-Path $libs $aarName
$voiceDir  = Join-Path $bundled 'kitten-nano-en'
$espeakZip = Join-Path $bundled 'espeak-ng-data.zip'
# Zips of the built-in voice from earlier versions: the build refuses to run
# while they are there (they would be packed into the APK unused, 55 MB).
$oldZip    = Join-Path $bundled "$archiveName.zip"
$oldZips   = @($oldZip, (Join-Path $bundled 'kitten-nano-en-v0_8-int8.zip'))
$work      = Join-Path ([IO.Path]::GetTempPath()) 'qvoice-binaries'

# ---- Helpers ----

function Get-Hex([byte[]]$bytes) {
    -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Get-Sha256([string]$path) {
    (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
}

function Test-File([string]$path, [string]$sha256) {
    (Test-Path -LiteralPath $path -PathType Leaf) -and ((Get-Sha256 $path) -eq $sha256)
}

# True when every voice file is in $dir with the right checksum.
function Test-VoiceFiles([string]$dir) {
    foreach ($file in $voiceFiles.Keys) {
        if (-not (Test-File (Join-Path $dir $file) $voiceFiles[$file])) { return $false }
    }
    return $true
}

# The zip's content checksum (see $espeakTreeSha256); $null if it can't be read.
function Get-ZipTreeSha256([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    try {
        $zip = [IO.Compression.ZipFile]::OpenRead($path)
    } catch {
        return $null
    }
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        $sums = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
        foreach ($entry in $zip.Entries) {
            if ($entry.Name -eq '') { continue } # a folder
            $stream = $entry.Open()
            try { $sums[$entry.FullName] = Get-Hex ($sha.ComputeHash($stream)) } finally { $stream.Dispose() }
        }
        $names = [string[]]@($sums.Keys)
        [Array]::Sort($names, [StringComparer]::Ordinal)
        $text = -join ($names | ForEach-Object { "$($sums[$_])  $_`n" })
        return Get-Hex ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text)))
    } catch {
        return $null
    } finally {
        $zip.Dispose()
    }
}

# Zips $sourceDir's files, paths sorted and one fixed date, so the same files
# always give the same zip on one machine.
function New-Zip([string]$sourceDir, [string]$zipPath) {
    # Both from Get-Item's spelling of the path, so cutting the root off each
    # file's path works even where Windows hands out short (8.3) temp paths.
    $root = (Get-Item -LiteralPath $sourceDir).FullName.TrimEnd('\', '/')
    $names = [string[]]@(Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object {
        $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    })
    [Array]::Sort($names, [StringComparer]::Ordinal)
    $stamp = [DateTimeOffset]::new(2026, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    $file = [IO.File]::Open($zipPath, [IO.FileMode]::Create)
    try {
        $zip = [IO.Compression.ZipArchive]::new($file, [IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($name in $names) {
                $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime = $stamp
                $out = $entry.Open()
                try {
                    $in = [IO.File]::OpenRead((Join-Path $root $name))
                    try { $in.CopyTo($out) } finally { $in.Dispose() }
                } finally {
                    $out.Dispose()
                }
            }
        } finally {
            $zip.Dispose()
        }
    } finally {
        $file.Dispose()
    }
}

# Downloads $url to $path and refuses it unless its checksum matches.
function Get-CheckedDownload([string]$url, [string]$path, [string]$sha256, [string]$what) {
    Write-Host "Downloading $what..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $path -UseBasicParsing
    $actual = Get-Sha256 $path
    if ($actual -ne $sha256) {
        Remove-Item -Force -LiteralPath $path
        throw "Checksum mismatch for ${what}: got $actual, expected $sha256. The download is damaged or the file changed upstream; nothing was changed."
    }
}

# Copies $source over $target and checks the copy (a full disk shows here).
function Copy-Checked([string]$source, [string]$target, [string]$sha256) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -Force -LiteralPath $source -Destination $target
    if (-not (Test-File $target $sha256)) { throw "Copying to $target failed (is the disk full?). Run this again." }
}

# ---- The work ----

if (Test-Path -LiteralPath $work) { Remove-Item -Recurse -Force -LiteralPath $work }
New-Item -ItemType Directory -Force -Path $work | Out-Null
try {
    # 1. sherpa-onnx
    if (Test-File $aar $aarSha256) {
        Write-Host "sherpa-onnx: in place"
    } else {
        $download = Join-Path $work $aarName
        Get-CheckedDownload $aarUrl $download $aarSha256 "$aarName (48 MB)"
        Copy-Checked $download $aar $aarSha256
        Write-Host "sherpa-onnx: downloaded and checked"
    }

    # 2. The built-in voice and the eSpeak NG data
    $needVoice  = -not (Test-VoiceFiles $voiceDir)
    $needEspeak = (Get-ZipTreeSha256 $espeakZip) -ne $espeakTreeSha256
    $voiceSource = $null

    # The zip versions 0.3.0 to slice 6 used holds the voice: no download for
    # it (it has no eSpeak NG data, though).
    if ($needVoice -and (Test-Path -LiteralPath $oldZip)) {
        $fromZip = Join-Path $work 'old-zip'
        Expand-Archive -LiteralPath $oldZip -DestinationPath $fromZip
        if (-not (Test-VoiceFiles $fromZip)) {
            throw "The files in $archiveName.zip don't match their checksums, so the zip is damaged. Nothing was changed. Delete $oldZip and run this again to download the voice afresh."
        }
        $voiceSource = $fromZip
    }

    $unpacked = $null
    if ($needEspeak -or ($needVoice -and -not $voiceSource)) {
        $archive = Join-Path $work "$archiveName.tar.bz2"
        Get-CheckedDownload $archiveUrl $archive $archiveSha256 "$archiveName.tar.bz2 (64 MB)"
        Write-Host 'Checksum OK. Unpacking...'
        tar -xjf $archive -C $work
        if ($LASTEXITCODE -ne 0) { throw "tar failed with exit code $LASTEXITCODE" }
        $unpacked = Join-Path $work $archiveName
        if (-not $voiceSource) { $voiceSource = $unpacked }
    }

    if ($needVoice) {
        if (-not (Test-VoiceFiles $voiceSource)) { throw "The unpacked voice files don't match their checksums; nothing was changed. Run this again." }
        foreach ($file in $voiceFiles.Keys) {
            Copy-Checked (Join-Path $voiceSource $file) (Join-Path $voiceDir $file) $voiceFiles[$file]
        }
        Write-Host "Built-in voice: put in place and checked"
    } else {
        Write-Host "Built-in voice: in place"
    }
    # Anything else in the voice folder would be packed into the APK too.
    Get-ChildItem -LiteralPath $voiceDir -Force | Where-Object { -not $voiceFiles.Contains($_.Name) } | ForEach-Object {
        Remove-Item -Recurse -Force -LiteralPath $_.FullName
        Write-Host "Removed $($_.Name) from the voice folder (the app doesn't use it)."
    }

    if ($needEspeak) {
        $newZip = Join-Path $work 'espeak-ng-data.zip'
        New-Zip (Join-Path $unpacked 'espeak-ng-data') $newZip
        if ((Get-ZipTreeSha256 $newZip) -ne $espeakTreeSha256) {
            throw "The eSpeak NG data in the archive isn't the expected one; nothing was changed."
        }
        Copy-Item -Force -LiteralPath $newZip -Destination $espeakZip
        if ((Get-ZipTreeSha256 $espeakZip) -ne $espeakTreeSha256) { throw "Copying to $espeakZip failed (is the disk full?). Run this again." }
        Write-Host "eSpeak NG data: zipped from the archive and checked"
    } else {
        Write-Host "eSpeak NG data: in place"
    }

    foreach ($zip in $oldZips) {
        if (Test-Path -LiteralPath $zip) {
            Remove-Item -Force -LiteralPath $zip
            Write-Host "Removed the old $(Split-Path -Leaf $zip)."
        }
    }
} finally {
    Remove-Item -Recurse -Force -LiteralPath $work -ErrorAction SilentlyContinue
}
Write-Host 'All binary files are in place.'
