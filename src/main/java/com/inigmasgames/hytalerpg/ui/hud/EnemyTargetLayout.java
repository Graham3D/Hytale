package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto;
import java.util.*;

/** One selected-target details surface. Explicit line heights avoid the old two-line truncation. */
public final class EnemyTargetLayout {
    public static final int WIDTH=480,FONT=12,LINE=16,PADDING=10;
    public record Layout(String name,String identity,List<String> lines,int height){public Layout{lines=List.copyOf(lines);}}
    private EnemyTargetLayout(){}
    public static Layout of(EnemyDisplayDto target){
        var lines=new ArrayList<String>();
        for(var tag:target.orderedTags()){
            String text=tag.fallbackText();
            if(tag.labelParameters().containsKey("signature"))text+=" ["+tag.labelParameters().get("signature")+"]";
            var description=tag.labelParameters().get("description");if(description!=null)text+=" — "+description;
            wrap(text,60,lines);
        }
        return new Layout(target.name(),target.rarityLabel()+" · "+target.baseRoleDisplayName()+" · Lv "+target.combatLevel(),lines,72+LINE*lines.size());
    }
    static void wrap(String text,int columns,List<String> lines){
        String remaining=text;
        while(remaining.length()>columns){int at=remaining.lastIndexOf(' ',columns);if(at<1)at=columns;
            lines.add(remaining.substring(0,at));remaining=remaining.substring(at).stripLeading();}
        if(!remaining.isEmpty())lines.add(remaining);
    }
}
