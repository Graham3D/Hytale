package com.inigmasgames.hytalerpg.execution.hytale;

/** Pure label formatting; the native Nameplate component owns camera-facing placement. */
public final class EnemyNameplateText {
    private EnemyNameplateText() {}
    public static String format(String name,int combatLevel){
        if(name==null||name.isBlank())throw new IllegalArgumentException("Missing creature display name");
        var clean=name.strip();
        return combatLevel>0?clean+"  Lv. "+combatLevel:clean;
    }
}
