package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Tunable weights for random quality and affix breadth; special authored types have no roll path. */
public final class GearQualityProfile {
    private record Data(String revision,double[] normalWeights,double[] nightmareWeights,double[] hellWeights,
                        double[] commonRank,double[] specialistRank,double[] eliteRank,double[] minibossRank,
                        double[] bossRank,double mfKnee,double magicMfGain,double rareMfGain,
                        int[] magicAffixWeights,int[] rareAffixWeights) {}
    public static final GearQualityProfile CURRENT=load();
    private final Data data;
    private GearQualityProfile(Data data){this.data=data;validate();}
    private static GearQualityProfile load(){
        try(var stream=GearQualityProfile.class.getResourceAsStream("/rpg/gear/quality-profile-v1.json")){
            if(stream==null)throw new IllegalStateException("Missing quality profile");
            return new GearQualityProfile(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class));
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private void validate(){
        Objects.requireNonNull(data.revision());
        for(var row:new double[][]{data.normalWeights(),data.nightmareWeights(),data.hellWeights(),
                data.commonRank(),data.specialistRank(),data.eliteRank(),data.minibossRank(),data.bossRank()})
            if(row==null||row.length!=3||Arrays.stream(row).anyMatch(v->!Double.isFinite(v)||v<=0))throw new IllegalStateException("Invalid quality weights");
        if(data.magicAffixWeights()==null||data.magicAffixWeights().length!=2||data.rareAffixWeights()==null
                ||data.rareAffixWeights().length!=4||Arrays.stream(data.magicAffixWeights()).anyMatch(v->v<=0)
                ||Arrays.stream(data.rareAffixWeights()).anyMatch(v->v<=0)||data.mfKnee()<=0
                ||data.magicMfGain()<0||data.rareMfGain()<0)throw new IllegalStateException("Invalid quality profile");
    }
    public String revision(){return data.revision();}
    public double[] base(DifficultyId era){return switch(era){case NORMAL->data.normalWeights().clone();case NIGHTMARE->data.nightmareWeights().clone();case HELL->data.hellWeights().clone();};}
    public double[] rank(ProgressionMath.Rank rank){return switch(rank){case COMMON->data.commonRank().clone();case SPECIALIST->data.specialistRank().clone();case ELITE->data.eliteRank().clone();case MINIBOSS->data.minibossRank().clone();case BOSS->data.bossRank().clone();};}
    public double[] magicFind(double mf){double saturated=mf/(mf+data.mfKnee());return new double[]{1,1+data.magicMfGain()*saturated,1+data.rareMfGain()*saturated};}
    public int[] affixWeights(GearRarity rarity){return switch(rarity){case MAGIC->data.magicAffixWeights().clone();case RARE->data.rareAffixWeights().clone();default->throw new IllegalArgumentException("No new random affix budget for "+rarity);};}
}
