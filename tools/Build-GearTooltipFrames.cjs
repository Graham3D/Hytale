/* Render code-authored native UI geometry; no item/grid/rarity-footprint image is edited. */
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.HYWIND_NODE_MODULES
  ? path.join(process.env.HYWIND_NODE_MODULES, 'sharp') : 'sharp');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'src/main/resources/Common/UI/ItemQualities/Tooltips/Hywind');
const qualities = { Common:'Common', Rare:'Magic', RandomRare:'Rare', Legendary:'Legendary', Uncommon:'Uncommon', Epic:'Epic' };
async function main() {
  fs.mkdirSync(output, { recursive:true });
  for (const [asset,label] of Object.entries(qualities)) {
    const quality=JSON.parse(fs.readFileSync(path.join(root,`src/main/resources/Server/Item/Qualities/RPG_Gear_${asset}.json`),'utf8'));
    for (const [source,suffix] of [['Frame',''],['Arrow','Arrow']]) {
      const svg=fs.readFileSync(path.join(root,`art/UI/tooltip/${source}.svg`),'utf8').replaceAll('ACCENT',quality.TextColor);
      await sharp(Buffer.from(svg)).png().toFile(path.join(output,`ItemTooltip${label}${suffix}@2x.png`));
    }
  }
  console.log('Rendered 6 dedicated quality frames and pointers using one shared native nine-slice geometry.');
}
main().catch(error=>{ console.error(error); process.exitCode=1; });
