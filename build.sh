#!/bin/sh
# build.sh builds build/tvask.apk.
# It needs JDK 17 and the Android SDK packages
# "platforms;android-30" and "build-tools;34.0.0".
#
# By default it makes a debug build, signed with the Android debug key.
# For a release build, set RELEASE_KEYSTORE, RELEASE_ALIAS and
# RELEASE_PASS. An app signed with one key cannot be updated with the other.
set -eu

cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
TOOLS=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-30/android.jar
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}
export PATH=$JAVA_HOME/bin:$PATH

OUT=build
if [ -n "${RELEASE_KEYSTORE:-}" ]; then
	KEYSTORE=$RELEASE_KEYSTORE
	ALIAS=$RELEASE_ALIAS
	PASS=$RELEASE_PASS
	DEBUG=""
else
	KEYSTORE=$HOME/.android/debug.keystore
	ALIAS=androiddebugkey
	PASS=android
	DEBUG="--debug-mode"
fi
rm -rf "$OUT/classes" "$OUT/dex" "$OUT/gen"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

# aapt2 builds the resource table and writes R.java for the Java code.
"$TOOLS/aapt2" compile --dir android/res -o "$OUT/res.zip"
"$TOOLS/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" $DEBUG \
	--manifest android/AndroidManifest.xml --java "$OUT/gen" \
	--min-sdk-version 30 --target-sdk-version 30 "$OUT/res.zip"

# The platform jar has no LambdaMetafactory, so the code uses no lambdas.
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options -bootclasspath "$JAR" \
	-d "$OUT/classes" $(find android/src "$OUT/gen" -name '*.java')
"$TOOLS/d8" --release --min-api 30 --lib "$JAR" --output "$OUT/dex" \
	$(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)

"$TOOLS/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ -n "$DEBUG" ] && [ ! -f "$KEYSTORE" ]; then
	mkdir -p "$(dirname "$KEYSTORE")"
	keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
		-alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
		-dname "CN=Android Debug,O=Android,C=US"
fi
export SIGN_PASS="$PASS"
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass env:SIGN_PASS \
	--out "$OUT/tvask.apk" "$OUT/aligned.apk"

echo "built $OUT/tvask.apk"
