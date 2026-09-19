package com.inigmasgames.hytalerpg.combat.power;

import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Immutable authored reference, not a native attack instance or a recipient damage result. */
public record WeaponLightAttackProfile(String weaponId,String weaponClass,String sourceRevision,
        double normalDuration,double contactTime,List<Component> components) {
    public record Component(String id,String channel,double minimum,double maximum,String randomGroup,
            WeaponDamageExecution.Provenance provenance,boolean critEligible) {
        public Component {
            if(id==null||id.isBlank()||id.length()>256||channel==null||!channel.matches("[A-Z][A-Z0-9_]*")
                    ||randomGroup==null||randomGroup.length()>256||provenance==null
                    ||!Set.of(WeaponDamageExecution.Provenance.WEAPON,WeaponDamageExecution.Provenance.WEAPON_AFFIX,
                        WeaponDamageExecution.Provenance.FLAME_WEAPON).contains(provenance)
                    ||!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<0||maximum<minimum||maximum>Float.MAX_VALUE)
                throw new IllegalArgumentException("UNSUPPORTED_LIGHT_COMPONENT");
        }
    }
    public WeaponLightAttackProfile {
        if(weaponId==null||weaponId.isBlank()||weaponClass==null||sourceRevision==null||sourceRevision.isBlank()
                ||!Double.isFinite(normalDuration)||normalDuration<=0||normalDuration>5
                ||!Double.isFinite(contactTime)||contactTime<0||contactTime>=normalDuration
                ||components==null||components.isEmpty()||components.size()>32)
            throw new IllegalArgumentException("UNSUPPORTED_LIGHT_PROFILE");
        components=List.copyOf(components);
        if(components.stream().map(Component::id).distinct().count()!=components.size())
            throw new IllegalArgumentException("DUPLICATE_LIGHT_COMPONENT");
    }
    /** Future authoritative affix/imbue owners contribute at commit, never per victim. */
    @FunctionalInterface public interface Provider { List<Component> capture(WeaponLightAttackProfile base); }
    public WeaponLightAttackProfile assemble(List<Provider> providers){
        if(providers.size()>8)throw new IllegalArgumentException("LIGHT_PROVIDER_BUDGET");
        var all=new ArrayList<>(components);for(var provider:providers)all.addAll(provider.capture(this));
        return new WeaponLightAttackProfile(weaponId,weaponClass,sourceRevision,normalDuration,contactTime,all);
    }
    public List<WeaponDamageExecution.Component> sample(double multiplier,DoubleSupplier random,boolean canProc){
        if(!Double.isFinite(multiplier)||multiplier<0)throw new IllegalArgumentException("INVALID_LIGHT_MULTIPLIER");
        var rolls=new HashMap<String,Double>();var out=new ArrayList<WeaponDamageExecution.Component>();
        for(var c:components){
            double roll=rolls.computeIfAbsent(c.randomGroup().isEmpty()?c.id():c.randomGroup(),ignored->{
                double value=random.getAsDouble();if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("INVALID_LIGHT_RANDOM");return value;});
            out.add(new WeaponDamageExecution.Component(c.id(),c.channel(),(c.minimum()+(c.maximum()-c.minimum())*roll)*multiplier,c.provenance(),true,canProc));
        }
        return List.copyOf(out);
    }
}
