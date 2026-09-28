package com.inigmasgames.hytalerpg.difficulty;

import java.nio.file.Path;
import java.util.*;

/** Operator-authored portal locations. World ticks only read immutable snapshots. */
public final class DifficultyPortals {
    public record Portal(UUID id,UUID owner,UUID world,DifficultyId target,double x,double y,double z){
        public Portal{Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(world);Objects.requireNonNull(target);
            for(double n:new double[]{x,y,z})if(!Double.isFinite(n)||Math.abs(n)>30_000_000)throw new IllegalArgumentException("PORTAL_POSITION");}
        public String label(){return target==DifficultyId.NORMAL?"Normal Campaign — Return":(target==DifficultyId.NIGHTMARE?"Nightmare":"Hell")+" Difficulty | Recommended Level: "+target.recommendedLevel()+"+";}
        public boolean touches(double px,double py,double pz){return Math.abs(py-y)<2.5&&(px-x)*(px-x)+(pz-z)*(pz-z)<1.6;}
    }
    public record Data(List<Portal> portals){public Data{portals=List.copyOf(portals);if(portals.size()>128||portals.stream().map(Portal::id).distinct().count()!=portals.size())throw new IllegalArgumentException("PORTAL_BUDGET");}}
    private final DifficultyStorage<Data> storage;private volatile Data data;
    public DifficultyPortals(Path path){storage=new DifficultyStorage<>(path,Data.class);data=storage.read().orElse(new Data(List.of()));}
    public List<Portal> all(){return data.portals();}
    public synchronized Portal add(Portal p){
        if(data.portals().stream().anyMatch(old->old.world().equals(p.world())&&Math.pow(old.x()-p.x(),2)+Math.pow(old.z()-p.z(),2)<25))throw new IllegalStateException("PORTAL_TOO_CLOSE");
        var list=new ArrayList<>(data.portals());list.add(p);var next=new Data(list);storage.write(next);data=next;return p;
    }
    public synchronized void remove(UUID id){var list=data.portals().stream().filter(p->!p.id().equals(id)).toList();if(list.size()==data.portals().size())throw new IllegalArgumentException("UNKNOWN_PORTAL");
        var next=new Data(list);storage.write(next);data=next;}
}
