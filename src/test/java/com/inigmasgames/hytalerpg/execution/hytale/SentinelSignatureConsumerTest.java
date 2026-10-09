package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelSignatureConsumerTest {
    private static final UUID WORLD=UUID.randomUUID(),ACTOR=UUID.randomUUID(),VICTIM=UUID.randomUUID();
    private final GearCatalog catalog=GearCatalog.load();
    private static final class Port implements GearSignatureProcRuntime.Port {
        final List<GearSignatureProcRuntime.Child> children=new ArrayList<>();
        int executes;
        public void enqueue(GearSignatureProcRuntime.Child child){children.add(child);}
        public boolean execute(GearSignatureProcRuntime.Contact contact){executes++;return true;}
        public List<UUID> burstTargets(GearSignatureProcRuntime.Contact contact,double radius,int maximum){
            return List.of(UUID.randomUUID());
        }
    }
    private GearInstance item(String id){
        var base=catalog.base("gm.sword_iron.n");
        if(id==null)return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        var affix=catalog.affix(id);
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,100,
                new GearRequirements.Gate(1,Map.of()),"Signature",affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    private static GearSignatureProcRuntime.Contact contact(GearInstance item,String root,double health,
            boolean noProc,boolean credited){
        return new GearSignatureProcRuntime.Contact(WORLD,ACTOR,item.identity(),VICTIM,root,root,
                new GearEffectSnapshot(List.of(item)),true,true,true,noProc,false,false,20,health,1000,
                GearSignatureProcRuntime.Kind.COMMON,100,
                Map.of(GearCombatEffects.Channel.PHYSICAL,100d),1,false,false,credited);
    }
    @Test void outgoingSignatureGroupsConsumeActualAcceptedContactFacts(){
        for(String id:List.of("WA-135","WA-137","WA-138","WA-139","WA-140")){
            var source=item(id);var control=item(null);var runtime=new GearSignatureProcRuntime(()->0);
            var port=new Port();var plain=new Port();var ignored=new Port();
            double remaining=id.equals("WA-139")?40:500;
            runtime.applied(contact(source,id+"/positive",remaining,false,false),1,port);
            new GearSignatureProcRuntime(()->0).applied(contact(control,id+"/control",remaining,false,false),1,plain);
            new GearSignatureProcRuntime(()->0).applied(contact(source,id+"/child",remaining,true,false),1,ignored);
            assertTrue(plain.children.isEmpty()&&plain.executes==0,id+" control");
            assertTrue(ignored.children.isEmpty()&&ignored.executes==0,id+" no-proc");
            switch(id){
                case "WA-135"->assertEquals(GearSignatureProcRuntime.ChildKind.CRUSHING,port.children.getFirst().kind());
                case "WA-137"->{
                    assertTrue(port.children.isEmpty());
                    runtime.applied(contact(source,id+"/next",remaining,false,false),2,port);
                    assertEquals(GearSignatureProcRuntime.ChildKind.BARBED,port.children.getFirst().kind());
                }
                case "WA-138"->{assertEquals(.85,runtime.armorRatingFactor(VICTIM,2),1e-9);
                    assertEquals(1,new GearSignatureProcRuntime(()->0).armorRatingFactor(VICTIM,2),1e-9);}
                case "WA-139"->assertEquals(1,port.executes);
                case "WA-140"->{assertEquals(.95,runtime.incomingHitFactor(ACTOR,2,true,false),1e-9);
                    assertEquals(1,runtime.incomingHitFactor(ACTOR,2,false,true),1e-9);}
                default->throw new AssertionError(id);
            }
        }
    }
    @Test void deadlyReflectionAndCreditedBurstUseTheirDistinctOwners(){
        var deadly=item("WA-136");var noAffix=item(null);
        var d=new GearSignatureProcRuntime(()->0);
        assertEquals(200,d.deadly(new GearEffectSnapshot(List.of(deadly)),deadly.identity(),"strike",100,false,true,false,1));
        assertEquals(100,d.deadly(new GearEffectSnapshot(List.of(noAffix)),noAffix.identity(),"control",100,false,true,false,1));
        assertEquals(100,d.deadly(new GearEffectSnapshot(List.of(deadly)),deadly.identity(),"critical",100,true,true,false,1));
        var reflect=item("WA-142");var port=new Port();
        d.received(WORLD,ACTOR,VICTIM,"incoming","contact",new GearEffectSnapshot(List.of(reflect)),
                true,true,false,false,20,false,3,port);
        assertEquals(GearSignatureProcRuntime.ChildKind.RETRIBUTION,port.children.getFirst().kind());
        int count=port.children.size();
        d.received(WORLD,ACTOR,VICTIM,"child","child",new GearEffectSnapshot(List.of(reflect)),
                true,true,true,false,20,false,4,port);
        assertEquals(count,port.children.size());
        var burst=item("WA-143");var burstPort=new Port();
        d.creditedKill(contact(burst,"not-credited",0,false,false),5,burstPort);
        assertTrue(burstPort.children.isEmpty());
        d.creditedKill(contact(burst,"credited",0,false,true),5,burstPort);
        assertEquals(GearSignatureProcRuntime.ChildKind.KILL_BURST,burstPort.children.getFirst().kind());
        assertTrue(burstPort.children.getFirst().noProc());
        d.creditedKill(contact(burst,"credited",0,false,true),5,burstPort);
        assertEquals(1,burstPort.children.size());
    }
}
