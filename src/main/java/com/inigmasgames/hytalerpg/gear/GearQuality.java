package com.inigmasgames.hytalerpg.gear;

/** Random qualities are separate from future authored special equipment. */
public enum GearQuality {
    NORMAL, MAGIC, RARE, SET, UNIQUE;

    public boolean random(){return this==NORMAL || this==MAGIC || this==RARE;}
    public boolean fortuneEligible(){return this==MAGIC || this==RARE;}
    public String color(){return switch(this){
        case NORMAL->GearRarityPresentation.NORMAL.color;
        case MAGIC->GearRarityPresentation.RARE.color;
        case RARE->GearRarityPresentation.EPIC.color;
        case SET->GearRarityPresentation.SET.color;
        case UNIQUE->GearRarityPresentation.LEGENDARY.color;
    };}
}
