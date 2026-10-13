package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger;
import java.util.*;

/** Copies the state already committed or replayed by the journal owner; no I/O or extra durability work. */
public final class HroDurable {
    private HroDurable() { }
    public static void repository(UUID player,String boundary,int schema,long revision,String checksum) {
        if(!HroRuntime.active(player))return;
        HroRuntime.observe(player,"PERSISTENCE.RECOVERY","FileRpgPlayerStateRepository",boundary,"rpg-state",null,null,null,revision,
                Map.of("durableSubject",player.toString(),"schema",schema,"revision",revision,"fingerprint",checksum));
    }
    public static void state(CycloneBarrierLedger.State state,String boundary) {
        if(!HroRuntime.active(state.player()))return;
        var f=new LinkedHashMap<String,Object>();
        f.put("durableSubject",state.player().toString());f.put("schema",state.schema());f.put("revision",state.revision());
        f.put("generation",String.valueOf(state.generation()));f.put("activation",String.valueOf(state.activation()));
        f.put("phase",state.phase());f.put("capacity",state.capacity());f.put("remaining",state.remaining());
        f.put("manaReceipt",String.valueOf(state.manaReceipt()));f.put("cooldownReceipt",String.valueOf(state.cooldownReceipt()));f.put("manaPaid",state.manaPaid());
        HroRuntime.observe(state.player(),"PERSISTENCE.RECOVERY","CycloneBarrierLedger",boundary,"cyclone-ledger",null,null,null,state.revision(),f);
    }
    public static void payment(CycloneBarrierLedger.State prior,CycloneBarrierLedger.State next) {
        if(!HroRuntime.active(next.player()))return;
        if(next.manaReceipt()!=null&&!Objects.equals(prior.manaReceipt(),next.manaReceipt()))
            HroRuntime.observe(next.player(),"CYCLONE.ACCOUNTING","CycloneBarrierLedger","PAYMENT",String.valueOf(next.generation()),null,null,null,next.revision(),
                    Map.of("generation",String.valueOf(next.generation()),"receiptId",next.manaReceipt(),"manaPaid",next.manaPaid()));
    }
}
