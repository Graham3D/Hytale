package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.power.WeaponLightAttackProfile;
import java.util.*;
import java.util.function.DoubleSupplier;

/** One sampled authored strike. Shared across all its victims, never resampled per component/contact. */
public record WeaponLightHit(WeaponLightAttackProfile profile,List<WeaponDamageExecution.Component> sampled,
        boolean critical,double multiplier,String executionId) {
    public static WeaponLightHit create(WeaponLightAttackProfile profile,double multiplier,String executionId,
            DoubleSupplier random,boolean critical,boolean canProc){
        return new WeaponLightHit(profile,profile.sample(1,random,canProc),critical,multiplier,executionId);
    }
    public DamageCalculationService.Result calculate(DamageCalculationService kernel,WeaponDamageExecution.Component component,
            double attribute,ModifierBuckets modifiers,double criticalMultiplier){
        var base=kernel.calculate(new DamageCalculationService.Request(component.sourceAmount(),attribute,multiplier,modifiers,false,0,criticalMultiplier));
        boolean crit=critical&&profile.components().stream().filter(c->c.id().equals(component.componentId())).findFirst().orElseThrow().critEligible();
        return new DamageCalculationService.Result(base.basePower(),base.attributeMultiplier(),base.scaledBasePower(),base.skillRawDamage(),
                base.modifierFactor(),base.preCritDamage(),crit,base.preCritDamage()*(crit?criticalMultiplier:1));
    }
    public WeaponDamageExecution envelope(WeaponDamageExecution.Identity identity,DamageCalculationService kernel,
            double attribute,ModifierBuckets modifiers,double criticalMultiplier,boolean derived){
        var components=sampled.stream().map(c->new WeaponDamageExecution.Component(c.componentId(),c.channel(),
            calculate(kernel,c,attribute,modifiers,criticalMultiplier).preMitigationDamage(),c.provenance(),true,c.canProc())).toList();
        return new WeaponDamageExecution(identity,profile.weaponId(),WeaponDamageExecution.Delivery.RPG_WEAPON,derived,!derived,critical,components);
    }
}
