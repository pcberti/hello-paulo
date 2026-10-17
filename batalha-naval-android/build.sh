#!/usr/bin/env bash
# Gera build/batalha-naval.apk sem Android Studio nem Gradle.
#
# Precisa de: JDK (javac, keytool), python3 e as ferramentas do Android que o Ubuntu/Debian empacotam:
#   sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
#
# A chave que assina o app fica em ~/.android/batalha-naval.jks (criada na primeira vez).
# Guarde esse arquivo: o Android só aceita atualizar o app se a versão nova vier assinada pela mesma chave.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
CHAVE=${CHAVE:-$HOME/.android/batalha-naval.jks}
SENHA=${SENHA:-batalha-naval}
B=build

for f in aapt apksigner zipalign dalvik-exchange javac keytool python3; do
  command -v "$f" >/dev/null || { echo "Falta o programa '$f'. Veja as instruções no topo deste arquivo." >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "Não achei $ANDROID_JAR (pacote android-sdk-platform-23)." >&2; exit 1; }

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/assets"

echo "1/6 Página do jogo"
python3 montar_html.py ../batalha-naval/index.html "$B/assets/index.html"
cp -r assets/. "$B/assets/"

echo "2/6 Recursos (R.java)"
aapt package -f -m -J "$B/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

echo "3/6 Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')

echo "4/6 Dex"
dalvik-exchange --dex --min-sdk-version=21 --output="$B/classes.dex" "$B/classes"

echo "5/6 Pacote"
aapt package -f -M AndroidManifest.xml -S res -A "$B/assets" -I "$ANDROID_JAR" -F "$B/sem-dex.apk"
# Android 11+ exige o resources.arsc sem compressão: refaz o zip guardando esse arquivo como está
python3 - "$B/sem-dex.apk" "$B/classes.dex" "$B/sem-assinatura.apk" <<'PY'
import sys, zipfile
origem, dex, destino = sys.argv[1:]
with zipfile.ZipFile(origem) as zi, zipfile.ZipFile(destino, "w") as zo:
    for item in zi.infolist():
        modo = zipfile.ZIP_STORED if item.filename == "resources.arsc" or item.filename.endswith(".png") else zipfile.ZIP_DEFLATED
        zo.writestr(item.filename, zi.read(item.filename), compress_type=modo)
    zo.write(dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
PY
zipalign -f -p 4 "$B/sem-assinatura.apk" "$B/alinhado.apk"

echo "6/6 Assinatura"
if [ ! -f "$CHAVE" ]; then
  mkdir -p "$(dirname "$CHAVE")"
  keytool -genkeypair -keystore "$CHAVE" -storepass "$SENHA" -keypass "$SENHA" -alias batalha-naval \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Batalha Naval" >/dev/null 2>&1
  echo "   Chave nova criada em $CHAVE"
fi
apksigner sign --ks "$CHAVE" --ks-pass "pass:$SENHA" --ks-key-alias batalha-naval --out "$B/batalha-naval.apk" "$B/alinhado.apk"
apksigner verify "$B/batalha-naval.apk"

echo "Pronto: $(pwd)/$B/batalha-naval.apk"
