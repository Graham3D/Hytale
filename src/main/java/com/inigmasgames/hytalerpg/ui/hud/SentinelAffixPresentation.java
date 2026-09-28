package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearAffixDisplay;
import java.util.ArrayList;
import java.util.List;

/** Read-only projection of the immutable bound item's compatible affix rolls. */
public final class SentinelAffixPresentation {
    private SentinelAffixPresentation() {}
    public record Lines(String title,List<String> rows){
        public Lines {rows=List.copyOf(rows);}
    }
    public static Lines of(GearInstance item){
        if(item==null)return new Lines("Iron Sentinel",List.of("No bound item"));
        var rows=new ArrayList<String>();
        for(var roll:item.affixes()){
            var policy=IronSentinelAffixes.classify(roll.familyId());
            if(!policy.adapted()||policy.disposition()==IronSentinelAffixes.Disposition.OWNER_ONLY
                    ||policy.disposition()==IronSentinelAffixes.Disposition.UNSUPPORTED)continue;
            rows.add(GearAffixDisplay.format(roll));
        }
        if(rows.isEmpty())rows.add("No inherited affixes");
        return new Lines(item.displayName(),rows);
    }
}
