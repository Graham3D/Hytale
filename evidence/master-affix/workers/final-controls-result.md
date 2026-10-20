# Final affix controls (offline)

Owned files: `src/test/java/com/inigmasgames/hytalerpg/gear/MasterAffixFinalControlsTest.java`, test-only `src/test/java/com/inigmasgames/hytalerpg/MasterAffixFinalAuraHelper.java`, and this note. No production, registry, gate, proof-map, or existing test file was edited.

Every positive uses `MasterAffixTestEquipment.fixture(id, false)` and the corresponding unrolled control uses `fixture(id, true)`. The test asserts production `GearDropGenerator.eligible`, then admits both through public `GearEquipmentResolution.resolve(level, baseline, candidates)`, which uses `GearAffixRuntime.ENABLED`. It asserts the accepted item and frozen value, and checks that a nonequipped candidate is rejected as `NOT_EQUIPPED` with no effects. It does not use `MasterAffixTestEquipment.accepted`, whose current overload supplies candidate capabilities. All IDs below are enabled and none is WA-155.

| ID | New method / actual owner proof | Remaining boundary |
| --- | --- | --- |
| WA-008 | `wa008NativeGearActionProfilesPrimaryChangesRenderedAnimationFromAcceptedUnrolled`: `NativeGearActionProfiles.primary` selects the accepted frozen rate; the actual family `NativePrimaryActionAssets.renderAnimations` changes attack animation speed; the accepted unrolled item keeps its original root (`primary` rejects variant selection). | Native asset publication, client animation, and contact were not run. |
| WA-014 | `wa014NativeGearActionProfilesPrimaryChangesRenderedReachFromAcceptedUnrolled`: `NativeGearActionProfiles.primary` selects frozen reach; the actual family `NativePrimaryActionAssets.renderNode` extends an authored melee selector while the zero-reach render is unchanged. | Native selector collision, wall and victim traces were not run. |
| WA-016 | `wa016NativeAffixProjectileTravelLaunchExtendsSamePathOnlyForAcceptedRoll`: same launch inputs return no override for the unrolled item and a longer `NativeAffixProjectileTravel.Launch` for the rolled item; the real `Path` admits the new endpoint and terminates there. Instant-line is excluded. | Native projectile spawn, physics tick and impact were not run. |
| WA-053 | `gearStatusRuntimeAdmitSameHitUnrolledAndProtectedOrChanceMiss(WA-053)`: real `GearStatusRuntime.admit` enters `PeriodicStatusRuntime` for Bleed once. | Native hit producer and Sentinel inheritance were not run. |
| WA-054 | Same parameterized method `(WA-054)`: Burn enters `PeriodicStatusRuntime` once. | Same boundary. |
| WA-055 | Same parameterized method `(WA-055)`: Poison enters `PeriodicStatusRuntime` once. | Same boundary. |
| WA-057 | Same parameterized method `(WA-057)`: Electrified enters `StatusService`. | Native target projection and Sentinel inheritance were not run. |
| WA-058 | Same parameterized method `(WA-058)`: Slow enters `StatusService`. | Same boundary. |
| WA-059 | Same parameterized method `(WA-059)`: Stun enters `StatusService`. | Same boundary. |
| WA-060 | Same parameterized method `(WA-060)`: Silence enters `StatusService`. | Same boundary. |
| WA-061 | Same parameterized method `(WA-061)`: Blind enters `StatusService`. | Same boundary. |
| WA-062 | Same parameterized method `(WA-062)`: Fear enters `StatusService`. | Same boundary. |
| WA-063 | Same parameterized method `(WA-063)`: Root enters `StatusService`. | Same boundary. |
| WA-144 | `wa144SkillExecutionServiceRequestSameActorUnrolledRejectedAndAcceptedGrantExecutes`: fixture grants its authored `axe_toss` selector; the same actor/request is rejected with the unrolled item, committed through `SkillExecutionService.request` after accepted publication, and rejected with `EMPTY_SLOT` after unrolled publication. | Native item transaction and connected projectile release were not run. |
| WA-149 | `wa149SupportRuntimeThornsRecipientEffectRemovalAndNoAuraActor`: accepted item context activates real `SupportRuntime`; an ally receives positive `thorns`, an unrelated actor receives none, and removal clears the ally effect. | `SupportRuntime.activateItemAura` accepts a caller-supplied item context; this test does not prove the native Hytale support selector suppresses unrolled activation. Native reflection delivery was not run. |
| WA-150 | `wa150SupportRuntimePedanticismRecipientEffectRemovalAndNoAuraActor`: accepted item context activates real `SupportRuntime`; an ally gets 0.15 cooldown recovery, an unrelated actor gets zero, and removal clears the ally effect. | Same selector boundary; native cooldown use was not run. |

For each parameterized status case, the exact same `AppliedHit` (actor, root, strike, victim, channel and draw) is supplied to separate positive and unrolled owner instances. The positive is `ADMITTED`, the unrolled control is `CHANCE_MISS`, a high draw on the rolled source is `CHANCE_MISS`, and a protected hit is `INELIGIBLE_HIT`. Periodic cases assert one package in `PeriodicStatusRuntime` and zero in the control; control cases assert target status in `StatusService` only for the positive.

Direct `javac --release 25 -sourcepath ''` compiled the two new test sources against current local classes/resources, cached dependency JARs and the installed public Hytale server JAR. Direct JUnit Platform Launcher via the existing `NativeActionJUnitRunner` found **16 tests, 16 passed, 0 failed**. This was an isolated JVM run; no Gradle, game, server, authentication, deployment, commit, worker, JUnit XML, or connected validation was run. No cases were skipped as already proved but unregistered.
