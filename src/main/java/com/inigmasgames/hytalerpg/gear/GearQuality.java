package com.inigmasgames.hytalerpg.gear;

/** Random qualities are separate from future authored special equipment. */
public enum GearQuality {
    NORMAL, MAGIC, RARE, SET, UNIQUE;

    public boolean random(){return this==NORMAL || this==MAGIC || this==RARE;}
    public boolean fortuneEligible(){return this==MAGIC || this==RARE;}
    public String color(){return switch(this){
        case NORMAL->"#ffffff";case MAGIC->"#1d4dff";case RARE->"#fff200";
        case SET->"#51c534";case UNIQUE->"#ff9100";
    };}
}
