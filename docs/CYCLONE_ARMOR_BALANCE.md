# Cyclone Armor barrier balance (R260 QA candidate)

Cyclone Armor keeps the saved `spirit_shield` ID and its existing cost, cooldown,
duration, visuals, barrier lifetime, overflow, replacement, and link behavior.
This pass changes barrier capacity only. Projectile redirection remains outside
this pass.

## Calculation

The previous intrinsic capacity was `1.5 × (20 + flat gear Healing Power) ×
Wisdom-derived healing multiplier × [1 + 0.0125 × (level − 1)]`. The new
intrinsic capacity is:

```text
[1.5 × (20 + flat gear Healing Power)
 + 0.35 × max(0, effective WIS − starting effective WIS)
 + 0.15 × max(0, effective INT − starting effective INT)]
 × [1 + 0.0125 × (effective skill level − 1)]
```

The starting effective attribute comes from `CombatBalanceProfile` through
`EffectiveAttributeService`; the current profile gives 10. Effective WIS and
INT use the same diminishing-return curve as the rest of combat. Cyclone does
not apply the Wisdom-derived healing multiplier again. The existing combat
modifier, support barrier factor, and gear Barrier Strength factor multiply
the intrinsic capacity once each. Other healing calculations are unchanged.

## Comparison with observed Hell damage

The prior R259 combat trace reported a 596-HP character, level 64 Hell
enemies, about 74–86 **post-mitigation** HP per ordinary Skeleton Fighter
hit, about 89 for a Burnt Archer projectile, and about 25 for a weaker Burnt
Soldier hit. Six hits removed 424 HP; player mitigation was about 21.8%.
The hit equivalents below divide unmodified capacity by 74–86, so they are
an estimate rather than a promise about hit order or timing.

| Effective WIS / INT above baseline | Skill level | Intrinsic barrier HP | Skeleton Fighter hit equivalents |
| --- | ---: | ---: | ---: |
| 0 / 0 | 1 | 30.00 | 0.35–0.41 |
| 0 / 0 | 20 | 37.13 | 0.43–0.50 |
| 192 / 192 | 20 | 155.93 | 1.81–2.11 |
| 320 / 0 | 20 | 175.73 | 2.04–2.37 |
| 0 / 320 | 20 | 96.53 | 1.12–1.30 |
| 320 / 192 | 20 | 211.37 | 2.46–2.86 |
| 320 / 192, +8 flat Healing Power, +20% Barrier Strength | 20 | 271.46 final | 3.16–3.67 |

Read-only inspection of the active RPG character's saved allocation on
2026-10-11 found raw WIS 136 and INT 10. Both are below the first curve
breakpoint, so their effective values are also 136 and 10. With **no gear or
other modifiers assumed**, that allocation would give 74.10 HP at skill
level 1 or 91.70 HP at level 20. Its actual equipped gear and effective
skill-level bonuses were not established by that saved allocation; the
`BARRIER_CREATED` skill trace reports the true final capacity during QA.

This formula puts substantial effective Wisdom investment in the 150–225 HP
target before conditional defensive effects. The saved character's current
Wisdom investment is lower, so it should be expected to absorb roughly one
ordinary Skeleton Fighter hit unless gear and links raise the capacity.
