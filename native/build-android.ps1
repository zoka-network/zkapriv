$ErrorActionPreference = "Stop"

$nativeRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$ndkRoot = Join-Path $sdkRoot "ndk\27.3.13750724"

if (-not (Test-Path -LiteralPath $ndkRoot)) {
    throw "Android NDK 27.3.13750724 is not installed under $sdkRoot"
}
if (-not (Get-Command cargo-ndk -ErrorAction SilentlyContinue)) {
    throw "cargo-ndk is not installed. Run: cargo install cargo-ndk --locked"
}

$llvmRoot = Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64"
$clangResourceInclude = Get-ChildItem -LiteralPath (Join-Path $llvmRoot "lib\clang") -Directory |
    Sort-Object -Property Name -Descending |
    Select-Object -First 1 |
    ForEach-Object { Join-Path $_.FullName "include" }
$libclangDir = @(
    (Join-Path $llvmRoot "bin"),
    "C:\Program Files\LLVM\bin"
) | Where-Object {
    Test-Path -LiteralPath (Join-Path $_ "libclang.dll")
} | Select-Object -First 1

if (-not $libclangDir) {
    throw "libclang.dll was not found. Install LLVM for Windows and rerun this script."
}

$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_NDK_HOME = $ndkRoot
$env:LIBCLANG_PATH = $libclangDir
$env:CLANG_PATH = Join-Path $llvmRoot "bin\clang.exe"
$env:BINDGEN_EXTRA_CLANG_ARGS = "-I$($clangResourceInclude.Replace('\', '/'))"
$env:CPATH = $clangResourceInclude

Push-Location $nativeRoot
try {
    cargo ndk -t arm64-v8a -P 26 -o jniLibs build --release
    cargo ndk -t armeabi-v7a -P 26 -o jniLibs build --release -j 1

    # libzoka_mobile.so links the shared C++ runtime (libc++_shared.so), so it
    # must be bundled alongside it for each ABI — otherwise dlopen fails at
    # runtime with: library "libc++_shared.so" not found. cargo-ndk does NOT
    # copy the STL by default, so we copy it from the NDK sysroot here.
    $sysrootLib = Join-Path $llvmRoot "sysroot\usr\lib"
    $stlByAbi = @{
        "arm64-v8a"   = Join-Path $sysrootLib "aarch64-linux-android\libc++_shared.so"
        "armeabi-v7a" = Join-Path $sysrootLib "arm-linux-androideabi\libc++_shared.so"
    }
    foreach ($abi in $stlByAbi.Keys) {
        $src = $stlByAbi[$abi]
        $destDir = Join-Path $nativeRoot "jniLibs\$abi"
        if (-not (Test-Path -LiteralPath $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
        Copy-Item -LiteralPath $src -Destination (Join-Path $destDir "libc++_shared.so") -Force
        Write-Host "Bundled libc++_shared.so for $abi"
    }

    # cargo-ndk copia a jniLibs cualquier .so que encuentre en el directorio de
    # salida, incluidos los artefactos intermedios de dependencias como iroh o
    # if_watch. Nadie los carga -- la app solo abre libzoka_mobile -- pero
    # Gradle empaqueta todo lo que hay en jniLibs, asi que viajaban en el APK
    # como varios megas de peso muerto. Se borran aca en vez de listarlos en
    # .gitignore: no alcanza con no versionarlos, hay que no enviarlos.
    $seQuedan = @("libzoka_mobile.so", "libc++_shared.so")
    foreach ($abi in $stlByAbi.Keys) {
        $destDir = Join-Path $nativeRoot "jniLibs\$abi"
        Get-ChildItem -LiteralPath $destDir -Filter *.so -File |
            Where-Object { $seQuedan -notcontains $_.Name } |
            ForEach-Object {
                Remove-Item -LiteralPath $_.FullName -Force
                Write-Host "Descartado artefacto intermedio $($_.Name) de $abi"
            }
    }
} finally {
    Pop-Location
}
