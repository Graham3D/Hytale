package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.*;

/** Context exists only during an actual owner calculation, never a tooltip/preview evaluation. */
public final class HroGear {
    private HroGear() { }
    private record Context(UUID owner,String root) { }
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    public static final class Scope implements AutoCloseable {
        private static final Scope OFF=new Scope(null,false);
        private final Context prior;private final boolean on;
        private Scope(Context prior,boolean on){this.prior=prior;this.on=on;}
        @Override public void close(){if(on){if(prior==null)CURRENT.remove();else CURRENT.set(prior);}}
    }
    public static Scope calculation(UUID owner,String root){if(!HroRuntime.active(owner))return Scope.OFF;
        var prior=CURRENT.get();CURRENT.set(new Context(owner,root));return new Scope(prior,true);}
    public static void percent(GearEffectSnapshot snapshot,GearEffectSnapshot.Operator operator,double total,double applied){
        if(!HroRuntime.recording())return;
        var c=CURRENT.get();if(c==null)return;var sources=snapshot.sources(operator);
        // Aggregate buckets do not identify an individual source contribution. Never assign the whole total to every affix.
        if(sources.size()==1)contribution(c,snapshot,sources.getFirst(),operator.name(),total,applied);
    }
    public static void percent(GearEffectSnapshot snapshot,String affix,double total,double applied){
        if(!HroRuntime.recording())return;
        var c=CURRENT.get();if(c==null)return;
        int visited=0;for(var operator:GearEffectSnapshot.Operator.values())for(var source:snapshot.sources(operator)){
            if(++visited>128)return;
            if(source.affixId().equals(affix)&&Double.compare(source.value(),total)==0){contribution(c,snapshot,source,operator.name(),total,applied);return;}
        }
    }
    private static void contribution(Context c,GearEffectSnapshot snapshot,GearEffectSnapshot.Source source,String bucket,double value,double applied){
        HroRuntime.observe(c.owner,"AFFIX.MODIFIER","GearEffectSnapshot.percent","CONTRIBUTION",c.root+":"+source.affixId(),null,null,null,null,
                Map.of("itemId",source.itemId(),"affixId",source.affixId(),"equipmentRevision",snapshot.revision(),
                        "rootId",c.root,"bucket",bucket,"value",value,"applied",applied,"units","PERCENT_POINTS_TO_FRACTION"));
    }
    public static void roll(UUID player,UUID world,UUID item,String root,String contact,String affix,double sample,double chance,boolean selected){
        if(HroRuntime.active(player))HroRuntime.observe(player,"AFFIX.PROC","GearSignatureProcRuntime","ROLL",contact+":"+affix,world,null,null,null,
                Map.of("itemId",item,"affixId",affix,"rootId",root,"roll",sample,"chance",chance,"selected",selected));
    }
}
