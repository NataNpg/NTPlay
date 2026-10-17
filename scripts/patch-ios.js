// Ajusta o projeto iOS gerado pelo Capacitor: permite HTTP (listas IPTV), ícone e orientação.
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..'), app = path.join(root, 'ios', 'App', 'App');
const plist = path.join(app, 'Info.plist');
if (!fs.existsSync(plist)) { console.error('Rode "npx cap add ios" antes.'); process.exit(1); }
let x = fs.readFileSync(plist, 'utf8');
if (!x.includes('NSAllowsArbitraryLoads')) x = x.replace('</dict>\n</plist>', '\t<key>NSAppTransportSecurity</key>\n\t<dict><key>NSAllowsArbitraryLoads</key><true/></dict>\n\t<key>UIBackgroundModes</key>\n\t<array><string>audio</string></array>\n</dict>\n</plist>');
fs.writeFileSync(plist, x);
const set = path.join(app, 'Assets.xcassets', 'AppIcon.appiconset');
if (fs.existsSync(set)) { for (const f of fs.readdirSync(set)) if (f.endsWith('.png')) fs.unlinkSync(path.join(set, f)); fs.copyFileSync(path.join(root, 'resources', 'icon-1024.png'), path.join(set, 'AppIcon-512@2x.png'));
  fs.writeFileSync(path.join(set, 'Contents.json'), JSON.stringify({ images: [{ filename: 'AppIcon-512@2x.png', idiom: 'universal', platform: 'ios', size: '1024x1024' }], info: { author: 'xcode', version: 1 } }, null, 2)); }
console.log('iOS ajustado.');
