#!/usr/bin/env bash
# Compilar en el VPS: solo el motor; la firma del APK queda en la PC.
set -euo pipefail
export PATH=/root/.cargo/bin:$PATH
: "${ANDROID_NDK_HOME:?Indicá el NDK r27d}"
native_dir=$(cd "$(dirname "$0")" && pwd)
llvm="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export LIBCLANG_PATH="${LIBCLANG_PATH:-/usr/lib/llvm-18/lib}"
export CLANG_PATH="$llvm/bin/clang"
export CARGO_BUILD_JOBS=2
cd "$native_dir"
for manifest in quantic-testnet-v2-network.v1.json quantic-testnet-genesis.v1.json quantic-mainnet-genesis.v1.json; do
  test -f "../../docs/$manifest" || { echo "Falta el manifiesto público docs/$manifest" >&2; exit 1; }
done
for abi in armeabi-v7a arm64-v8a; do
  cargo ndk -t "$abi" -P 26 build --release --locked --features quantic-pq
  case "$abi" in
    armeabi-v7a) target=armv7-linux-androideabi; sysroot=arm-linux-androideabi ;;
    arm64-v8a) target=aarch64-linux-android; sysroot=aarch64-linux-android ;;
  esac
  mkdir -p "jniLibs/$abi"
  cp "target/$target/release/libzoka_mobile.so" "jniLibs/$abi/"
  "$llvm/bin/llvm-strip" --strip-debug "jniLibs/$abi/libzoka_mobile.so"
  cp "$llvm/sysroot/usr/lib/$sysroot/libc++_shared.so" "jniLibs/$abi/"
  symbols=$("$llvm/bin/llvm-readelf" --dyn-syms --wide "jniLibs/$abi/libzoka_mobile.so")
  for symbol in JNI_OnLoad Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_confirmCheckpoint0 Java_com_zoka_zkapriv_network_nativebridge_ZokaStark_sendPhase0; do
    grep -F "$symbol" <<< "$symbols"
  done
  sha256sum "jniLibs/$abi/"*.so
done
