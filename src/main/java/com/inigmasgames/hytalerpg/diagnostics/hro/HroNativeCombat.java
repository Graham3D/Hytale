package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.diagnostics.CombatDamageTraceService;
import java.util.*;

/** Small packet-local observer on the EXISTING native callbacks; never starts the combat recorder. */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="NP_NONNULL_PARAM_VIOLATION",
        justification="Native transient metadata uses a null codec, as the existing combat observer does.")
public final class HroNativeCombat {
    private HroNativeCombat() { }
    private static final MetaKey<Hit> KEY=Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"Hywind:HroHit",null);
    private static final class Hit {
        final UUID player,world;final String token,id;final double attempted;
        Double before;double accepted=Double.NaN;boolean finished;int stages;
        Hit(UUID player,UUID world,String token,String id,double attempted){this.player=player;this.world=world;this.token=token;this.id=id;this.attempted=attempted;}
    }
    public static boolean tracked(Damage damage){return damage.getIfPresentMetaObject(KEY)!=null;}
    public static void begin(Damage damage,UUID world,UUID source,UUID target) {
        UUID player=HroRuntime.ownerInWorld(world);if(player==null||tracked(damage))return;
        var metadata=HytaleDamageAdapter.metadata(damage);
        if(!player.equals(source)&&!player.equals(target)&&(metadata==null||!player.equals(metadata.actorId())))return;
        String id=HroRuntime.packet(player);if(id==null)return;
        var hit=new Hit(player,world,HroRuntime.token(player),id,damage.getAmount());damage.putMetaObject(KEY,hit);
        emit(hit,"GATHER",Map.of("attempted",hit.attempted,"targetId",target==null?"UNKNOWN":target,
                "cause",damage.getCause()==null?"UNKNOWN":damage.getCause().getId(),
                "origin",metadata==null?"NATIVE_UNWRAPPED":metadata.origin(),"rootId",metadata==null?id:metadata.rootCastId()));
        var offense=HytaleDamageAdapter.offenseResult(damage);
        if(offense!=null) {
            stage(damage,"ATTRIBUTE_SCALING",offense.basePower(),offense.scaledBasePower());
            stage(damage,"SKILL_COEFFICIENT",offense.scaledBasePower(),offense.skillRawDamage());
            stage(damage,"MODIFIER_BUCKETS",offense.skillRawDamage(),offense.preCritDamage());
            stage(damage,"CRITICAL",offense.preCritDamage(),offense.preMitigationDamage());
        }
    }
    private static void emit(Hit hit,String boundary,Map<String,?> facts){
        if(Objects.equals(hit.token,HroRuntime.token(hit.player)))HroRuntime.observe(hit.player,"COMBAT.RECONCILE",
                "CombatDamageTraceService",boundary,hit.id,hit.world,null,null,null,facts);
    }
    public static void stage(Damage damage,String operation,double before,double after){var h=damage.getIfPresentMetaObject(KEY);
        if(h!=null&&!h.finished&&h.stages++<16)emit(h,operation,Map.of("before",before,"after",after));}
    public static void accepted(Damage damage){var h=damage.getIfPresentMetaObject(KEY);if(h!=null)h.accepted=damage.isCancelled()?0:Math.max(0,damage.getAmount());}
    public static void before(Damage damage,Double health){var h=damage.getIfPresentMetaObject(KEY);if(h!=null)h.before=health;}
    public static void applied(Damage damage,Double after){var h=damage.getIfPresentMetaObject(KEY);if(h==null||h.finished)return;h.finished=true;
        double finalAmount=damage.isCancelled()?0:Math.max(0,damage.getAmount());
        emit(h,"NATIVE_RESULT",Map.of("healthBefore",h.before==null?"UNOBSERVABLE":h.before,
                "healthAfter",after==null?"UNOBSERVABLE":after,"attempted",h.attempted,"accepted",h.accepted,
                "postAbsorption",finalAmount,"reconciliation",CombatDamageTraceService.reconcile(h.before,after,finalAmount)));
    }
}
