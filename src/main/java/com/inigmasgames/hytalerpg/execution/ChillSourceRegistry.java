package com.inigmasgames.hytalerpg.execution;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.*;
/** Provenance of contributed live Chill stacks, not a second status/slow authority. */
public final class ChillSourceRegistry {
    private record Key(UUID owner,String skill,UUID victim){}
    public record Source(SkillExecutionContext context,int stacks,double ends){}
    private final Map<Key,Source> sources=new HashMap<>();
    public synchronized String observed(SkillExecutionContext context,UUID victim,StatusService.StatusView before,StatusService.StatusView after,double now){
        if(context==null||victim==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid Chill provenance");
        sources.values().removeIf(source->source.ends<=now);
        if(before==null||after==null)sources.keySet().removeIf(key->key.victim.equals(victim));
        if(after==null)return "NO_TRANSFERABLE_CHILL"; // Threshold consumes ALL contributors; Frozen is never a source.
        // Native shared-stack refresh extends the existing stack contributors too.
        sources.replaceAll((key,value)->key.victim.equals(victim)?new Source(value.context,value.stacks,now+after.remainingSeconds()):value);
        int added=after.stacks()-(before==null?0:before.stacks());
        if(added<=0||!context.compiledPlan().proliferation()||context.derivedRelease())return "NO_NEW_SOURCE_CONTRIBUTION";
        var key=new Key(context.request().actorId(),context.profile().skillId(),victim);var old=sources.get(key);
        if(old==null&&(sources.size()>=4096||sources.keySet().stream().filter(k->k.owner.equals(key.owner)).count()>=256))return "CHILL_PROVENANCE_BUDGET";
        int stacks=Math.min(after.stacks(),added+(old==null?0:old.stacks));
        sources.put(key,new Source(context,stacks,now+after.remainingSeconds()));return "CHILL_SOURCE_RECORDED";
    }
    public synchronized List<Source> takeForDeath(UUID victim,StatusService.StatusView actual,double now){
        var result=new ArrayList<Source>();
        for(var entry:new ArrayList<>(sources.entrySet()))if(entry.getKey().victim.equals(victim)){
            sources.remove(entry.getKey());var value=entry.getValue();
            if(actual!=null&&value.ends>now)result.add(new Source(value.context,Math.min(value.stacks,actual.stacks()),Math.min(value.ends,now+actual.remainingSeconds())));
        }
        return List.copyOf(result);
    }
    public synchronized void cancel(UUID owner){sources.keySet().removeIf(key->key.owner.equals(owner)||key.victim.equals(owner));}
    public synchronized void forget(UUID victim){sources.keySet().removeIf(key->key.victim.equals(victim));}
    public synchronized int size(){return sources.size();}
}
