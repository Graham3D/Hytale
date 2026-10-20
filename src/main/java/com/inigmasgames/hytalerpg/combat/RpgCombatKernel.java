package com.inigmasgames.hytalerpg.combat;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService;
import com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.damage.SkillScalingService;
import com.inigmasgames.hytalerpg.combat.power.BasePowerResolver;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerRegistry;
import com.inigmasgames.hytalerpg.combat.resource.HomeRestorationService;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import com.inigmasgames.hytalerpg.combat.resource.HostileCombatTracker;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshotFactory;

/** Shared Stage 02 service graph. Skill-family executors consume this later; none are implemented here. */
public final class RpgCombatKernel {
    private final CombatBalanceProfile balance;
    private final EffectiveAttributeService effectiveAttributes;
    private final DerivedStatService derivedStats;
    private final ReservationService reservations;
    private final RpgResourceService resources;
    private final HomeRestorationService homeRestoration;
    private final RpgCooldownService cooldowns;
    private final BasePowerResolver basePower;
    private final SkillScalingService scaling;
    private final DamageCalculationService damage;
    private final StatusService statuses;
    private final CombatSnapshotFactory snapshots;
    private final HostileCombatTracker hostileCombat;

    public RpgCombatKernel(CombatBalanceProfile balance, CriticalRoller criticalRoller) {
        this.balance = balance;
        effectiveAttributes = new EffectiveAttributeService(balance);
        derivedStats = new DerivedStatService(balance, effectiveAttributes);
        reservations = new ReservationService();
        resources = new RpgResourceService(balance, reservations);
        homeRestoration = new HomeRestorationService(balance);
        cooldowns = new RpgCooldownService(balance, System::nanoTime);
        basePower = new BasePowerResolver(ItemPowerRegistry.loadCanonical());
        scaling = new SkillScalingService(balance);
        damage = new DamageCalculationService(scaling, criticalRoller);
        statuses = new StatusService(balance, System::nanoTime);
        snapshots = new CombatSnapshotFactory();
        hostileCombat = new HostileCombatTracker(System::nanoTime);
    }
    public static RpgCombatKernel createProduction() {
        return new RpgCombatKernel(CombatBalanceProfile.loadCanonical(), new CriticalRoller(Math::random));
    }
    public CombatBalanceProfile balance() { return balance; }
    public EffectiveAttributeService effectiveAttributes() { return effectiveAttributes; }
    public DerivedStatService derivedStats() { return derivedStats; }
    public ReservationService reservations() { return reservations; }
    public RpgResourceService resources() { return resources; }
    public HomeRestorationService homeRestoration() { return homeRestoration; }
    public RpgCooldownService cooldowns() { return cooldowns; }
    public BasePowerResolver basePower() { return basePower; }
    public SkillScalingService scaling() { return scaling; }
    public DamageCalculationService damage() { return damage; }
    public StatusService statuses() { return statuses; }
    public CombatSnapshotFactory snapshots() { return snapshots; }
    public HostileCombatTracker hostileCombat() { return hostileCombat; }
}
