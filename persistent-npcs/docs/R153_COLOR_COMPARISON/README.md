# R153 native-material color comparison

[Purple / Green Overtop sheet](OVERTOP-Purple-Green.png) uses the same compact
base/mask/palette reconstruction as the runtime PNG renderer. These are real
cosmetic textures and native gradient LUTs, not generated artwork or whole-card tint.

Six representative shirts share the R152 fixed torso camera. Fabric changes;
skin, underwear, black leather, gold buckle and colored embroidery remain fixed.
The Python test also compares these pixels to separately rendered tinted source
textures: at most one RGB level difference from byte-quantized lighting.

Reproduce with the installed pinned `Assets.zip`:

```
python tools/test_appearance_color_sources.py --assets <Assets.zip>
```

This verifies material reconstruction, **not connected Hytale asset refresh**.
