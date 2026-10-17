// Ajusta o projeto Android gerado pelo Capacitor: Android TV (controle remoto), celular, ícones e banner.
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..'), and = path.join(root, 'android', 'app', 'src', 'main');
const man = path.join(and, 'AndroidManifest.xml');
if (!fs.existsSync(man)) { console.error('Rode "npx cap add android" antes.'); process.exit(1); }
let x = fs.readFileSync(man, 'utf8');
if (!x.includes('android.software.leanback')) {
  x = x.replace('<application', `<uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.hardware.faketouch" android:required="false" />
    <application`);
  x = x.replace(/<application/, '<application android:banner="@drawable/banner" android:usesCleartextTraffic="true" android:hardwareAccelerated="true"');
  x = x.replace(/(<category android:name="android.intent.category.LAUNCHER"\s*\/>)/, '$1\n                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />');
  x = x.replace(/<activity\b/, '<activity android:screenOrientation="sensor" ');
  if (!x.includes('android.permission.INTERNET')) x = x.replace('</manifest>', '    <uses-permission android:name="android.permission.INTERNET" />\n</manifest>');
  fs.writeFileSync(man, x);
}
const src = path.join(root, 'resources', 'android', 'res'), dst = path.join(and, 'res');
(function cp(a, b) { fs.mkdirSync(b, { recursive: true }); for (const f of fs.readdirSync(a)) { const p = path.join(a, f), q = path.join(b, f); fs.statSync(p).isDirectory() ? cp(p, q) : fs.copyFileSync(p, q); } })(src, dst);
// remove ícones adaptativos padrão (conflitam com os PNG nossos)
for (const d of fs.readdirSync(dst)) if (d.startsWith('mipmap-anydpi')) fs.rmSync(path.join(dst, d), { recursive: true, force: true });
console.log('Android (TV + celular) ajustado.');
