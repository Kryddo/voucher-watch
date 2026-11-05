#!/bin/bash
# Construieste VoucherWatch.apk fara Gradle (aapt2 + javac + d8 + apksigner).
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME=/opt/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$HOME/Android/Sdk"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-37.0/android.jar"

rm -rf build
mkdir -p build/classes build/dex build/gen

"$BT/aapt2" compile --dir res -o build/res.zip

"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" build/res.zip --java build/gen \
  --min-sdk-version 27 --target-sdk-version 34 \
  --version-code 7 --version-name 3.4 -o build/unsigned.apk

javac -encoding UTF-8 --release 11 -cp "$JAR" -d build/classes $(find src build/gen -name '*.java')

"$BT/d8" --release --min-api 27 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

if [ ! -f release.keystore ]; then
  keytool -genkeypair -keystore release.keystore -storepass voucherwatch -keypass voucherwatch \
    -alias voucherwatch -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Voucher Watch" >/dev/null
fi
"$BT/apksigner" sign --ks release.keystore --ks-pass pass:voucherwatch --key-pass pass:voucherwatch \
  --out VoucherWatch.apk build/aligned.apk

echo "OK: $(pwd)/VoucherWatch.apk"
