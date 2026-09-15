[CmdletBinding()]
param(
    # Fichier de cle, dossier ou racine d'un support (ex. G:\). Vide : recherche automatique.
    [string]$KeystorePath = $env:COURSES_RELEASE_KEYSTORE,
    [string]$KeyAlias = "courses",
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
$adb = "C:\platform-tools\adb.exe"
$applicationId = "org.opensources.courses"
# La cle doit s'appeler courses.<extension> ; seules les extensions de keystore Android sont acceptees.
$keyFileNames = @("courses.jks", "courses.keystore", "courses.p12")
$searchDepth = 3

function Find-CoursesKeys([string[]]$roots) {
    @(
        foreach ($root in $roots) {
            if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
            Get-ChildItem -LiteralPath $root -File -Recurse -Depth $searchDepth -ErrorAction SilentlyContinue |
                Where-Object { $keyFileNames -contains $_.Name.ToLowerInvariant() }
        }
    ) | Sort-Object FullName -Unique
}

function Get-ExternalDriveRoots {
    $roots = @()
    # Cles USB classiques (lecteurs amovibles).
    $roots += Get-CimInstance Win32_LogicalDisk -Filter "DriveType=2" -ErrorAction SilentlyContinue |
        ForEach-Object { "$($_.DeviceID)\" }
    # Disques et SSD USB, que Windows declare comme disques fixes.
    try {
        $roots += Get-Disk -ErrorAction Stop |
            Where-Object { $_.BusType -eq "USB" } |
            Get-Partition -ErrorAction SilentlyContinue |
            Where-Object { $_.DriveLetter } |
            ForEach-Object { "$($_.DriveLetter):\" }
    } catch {
        Write-Host "Detection des disques USB indisponible, seuls les lecteurs amovibles sont parcourus."
    }
    $roots | Sort-Object -Unique
}

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
    $driveRoots = @(Get-ExternalDriveRoots)
    Write-Host "Recherche de $($keyFileNames -join ', ') sur : $(if ($driveRoots) { $driveRoots -join ', ' } else { 'aucun support externe' })"
    $keyCandidates = @(Find-CoursesKeys $driveRoots)
    if ($keyCandidates.Count -eq 0) {
        $KeystorePath = Read-Host "Aucune cle courses trouvee sur un support USB. Chemin du fichier ou du support qui la contient"
    }
} else {
    $keyCandidates = @()
}

if ($keyCandidates.Count -eq 0) {
    if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
        throw "Aucune cle de signature indiquee."
    }
    $keystoreItem = Get-Item -LiteralPath $KeystorePath -ErrorAction Stop
    if ($keystoreItem.PSIsContainer) {
        $keyCandidates = @(Find-CoursesKeys @($keystoreItem.FullName))
        if ($keyCandidates.Count -eq 0) {
            throw "Aucune cle $($keyFileNames -join ', ') trouvee dans '$($keystoreItem.FullName)'."
        }
    } else {
        $keyCandidates = @($keystoreItem)
    }
}

if ($keyCandidates.Count -gt 1) {
    $choices = $keyCandidates.FullName -join [Environment]::NewLine
    throw "Plusieurs cles ont ete trouvees. Relance avec -KeystorePath et le chemin complet de celle a utiliser :`n$choices"
}

$keystoreItem = $keyCandidates[0]
if ($keystoreItem.Extension.ToLowerInvariant() -notin ".jks", ".keystore", ".p12") {
    throw "Le fichier indique n'est pas une cle Android prise en charge : $($keystoreItem.FullName)"
}

$resolvedKeystore = $keystoreItem.FullName
$repositoryPrefix = $repositoryRoot.TrimEnd("\") + "\"
if ($resolvedKeystore.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "La cle de signature doit rester hors du depot : $repositoryRoot"
}

Write-Host "Cle selectionnee : $resolvedKeystore"

if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw "ADB est introuvable a l'emplacement attendu : $adb"
}

$sdkCandidates = @(
    $env:ANDROID_SDK_ROOT
    $env:ANDROID_HOME
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

$sdkRoot = $sdkCandidates |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ "build-tools") -PathType Container } |
    Select-Object -First 1

if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw "Android SDK introuvable. Definis ANDROID_SDK_ROOT puis relance la tache."
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "apksigner.bat") -PathType Leaf } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

if ($null -eq $buildTools) {
    throw "apksigner.bat est introuvable dans $sdkRoot\build-tools."
}

$apkSigner = Join-Path $buildTools.FullName "apksigner.bat"

$connectedDevices = @(
    & $adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "^(?<serial>\S+)\s+device$" } |
        ForEach-Object { $Matches.serial }
)

if ($connectedDevices.Count -eq 0) {
    throw "Aucun telephone autorise detecte par ADB."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($connectedDevices.Count -gt 1) {
        throw "Plusieurs appareils sont connectes. Relance avec -Serial <numero>."
    }
    $Serial = $connectedDevices[0]
} elseif ($Serial -notin $connectedDevices) {
    throw "L'appareil '$Serial' n'est pas connecte ou autorise."
}

if (-not $env:JAVA_HOME) {
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path -LiteralPath $studioJbr -PathType Container) { $env:JAVA_HOME = $studioJbr }
}

Write-Host "1/5 Compilation de l'APK release non signe..."
& $gradleWrapper -p $repositoryRoot :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "La compilation Gradle a echoue."
}

$unsignedApk = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "app\build\outputs\apk\release") `
        -Filter "*-release-unsigned.apk" -File |
    Select-Object -First 1

if ($null -eq $unsignedApk) {
    throw "L'APK release non signe est introuvable."
}

$signedApk = Join-Path $unsignedApk.DirectoryName "courses-release-signed.apk"
if (Test-Path -LiteralPath $signedApk -PathType Leaf) {
    Remove-Item -LiteralPath $signedApk -Force
}

Write-Host "2/5 Signature de l'APK."
Write-Host "Le mot de passe sera demande directement par apksigner et ne sera pas stocke."
& $apkSigner sign `
    --ks $resolvedKeystore `
    --ks-key-alias $KeyAlias `
    --out $signedApk `
    $unsignedApk.FullName
if ($LASTEXITCODE -ne 0) {
    throw "La signature de l'APK a echoue."
}

Write-Host "3/5 Verification cryptographique de l'APK..."
& $apkSigner verify --verbose --print-certs $signedApk
if ($LASTEXITCODE -ne 0) {
    throw "La verification de la signature a echoue."
}

Write-Host "4/5 Installation de la mise a jour sur $Serial..."
# Windows PowerShell 5.1 turns a native command's redirected stderr into error records: with
# ErrorActionPreference=Stop, adb's first error line would abort the script before the explicit
# messages below could explain the failure.
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $installOutput = @(& $adb -s $Serial install -r $signedApk 2>&1 | ForEach-Object { "$_" })
    $installExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorAction
}
$installOutput | ForEach-Object { Write-Host $_ }
if ($installExitCode -ne 0) {
    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        throw "La version deja installee utilise une autre signature. Une desinstallation manuelle est necessaire une seule fois, mais elle effacera les donnees de l'application."
    }
    if ($installOutput -match "INSTALL_FAILED_VERSION_DOWNGRADE") {
        throw "Le versionCode de cet APK est inferieur a celui installe. Recupere la derniere version de main (app/version.properties) avant de relancer."
    }
    throw "L'installation ADB a echoue."
}

Write-Host "5/5 Lancement de courses..."
& $adb -s $Serial shell am start -n "$applicationId/.MainActivity"
if ($LASTEXITCODE -ne 0) {
    throw "L'APK est installe, mais le lancement de l'activite a echoue."
}

Write-Host ""
Write-Host "Termine : $signedApk"
Write-Host "Tu peux debrancher le support qui contient la cle."
