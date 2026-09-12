// Rasterizes repository-owned SVG source at its authored dimensions. No runtime dependency.
const path = require('node:path');
const sharp = require(process.env.RPG_SHARP_PATH || 'sharp');
const root = path.resolve(__dirname, '..');
(async () => {
  for (const [source, target] of [['HealingCore', 'RPG_Healing_Core'], ['HealingFlow', 'RPG_Healing_Flow']]) {
    const output = path.join(root, 'src/main/resources/Common/Trails', target + '.png');
    await sharp(path.join(root, 'art/Generated', source + '.svg')).png().toFile(output);
    const metadata = await sharp(output).metadata();
    if (metadata.width !== 128 || metadata.height !== 16 || !metadata.hasAlpha) throw Error('Healing strip dimensions/alpha mismatch');
    console.log(target + ': 128x16 RGBA');
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
