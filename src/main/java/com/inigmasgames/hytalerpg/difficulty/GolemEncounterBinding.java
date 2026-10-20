package com.inigmasgames.hytalerpg.difficulty;

import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.util.*;

/** Pure source-policy boundary shared by the native observer and adversarial fixtures. */
public final class GolemEncounterBinding {
    private final WorldDifficultyRegistry worlds;
    private final GolemMilestones catalog=GolemMilestones.load();
    public GolemEncounterBinding(WorldDifficultyRegistry worlds){this.worlds=worlds;}
    public Optional<EnemyRewardRegistry.Spawn> classify(UUID world,UUID enemy,String role,String marker,UUID markerId,
                                                      CampaignEncounterProjection placement,long now){
        var binding=worlds.find(world).orElse(null);var golem=catalog.role(role).orElse(null);
        if(binding==null||!binding.enabled()||binding.kind()!=WorldDifficultyRegistry.Kind.CAMPAIGN||golem==null)return Optional.empty();
        GolemEncounter evidence;
        if(placement!=null&&world.equals(placement.world)&&role.equals(placement.role)&&placement.placement!=null)
            evidence=new GolemEncounter(binding.difficulty(),golem.id(),catalog.profileId(),placement.placement.toString(),GolemEncounter.Source.OPERATOR_CAMPAIGN_PLACEMENT);
        else if(markerId!=null&&!golem.nativeMarker().isEmpty()&&golem.nativeMarker().equals(marker))
            evidence=new GolemEncounter(binding.difficulty(),golem.id(),catalog.profileId(),markerId.toString(),GolemEncounter.Source.NATIVE_SPAWN_MARKER);
        else return Optional.empty();
        return Optional.of(new EnemyRewardRegistry.Spawn(world,enemy,role,role,"milestone/"+golem.id(),0,
                com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank.BOSS,com.inigmasgames.hytalerpg.progress.ProgressionMath.Rarity.ORDINARY,catalog.profileId(),now,evidence));
    }
}
