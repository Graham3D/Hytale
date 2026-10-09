package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.Objects;

/** Context union for the one shared periodic package table and its global Poison cap. */
public sealed interface PeriodicContext {
    record Skill(SkillExecutionContext context) implements PeriodicContext {
        public Skill {Objects.requireNonNull(context);}
    }
    /** Power is already resolved at the original strike. Ticks must never apply offensive scaling again. */
    record Monster(MonsterAffixSource source,double resolvedSourcePower,java.util.function.BooleanSupplier bindingCurrent) implements PeriodicContext {
        public Monster {
            Objects.requireNonNull(source);Objects.requireNonNull(bindingCurrent);
            if(!source.affixId().equals("ME-008")||!Double.isFinite(resolvedSourcePower)||resolvedSourcePower<=0||resolvedSourcePower>Float.MAX_VALUE)
                throw new IllegalArgumentException("INVALID_MONSTER_POISON_POWER");
        }
        public double tickDamage(double integratedCoefficient){
            double amount=resolvedSourcePower*integratedCoefficient;
            if(!Double.isFinite(integratedCoefficient)||integratedCoefficient<0||!Double.isFinite(amount)||amount>Float.MAX_VALUE)
                throw new IllegalArgumentException("INVALID_MONSTER_PERIODIC_AMOUNT");return amount;
        }
    }
}
