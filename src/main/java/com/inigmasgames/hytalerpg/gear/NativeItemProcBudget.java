package com.inigmasgames.hytalerpg.gear;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One item-only proc allowance per native root, shared by every accepted contact and observer. */
public final class NativeItemProcBudget {
    private record Root(UUID world,UUID actor,String id) {}
    private static final class Contact {
        final UUID target;
        final String id;
        final Object strike;
        Contact(UUID target,String id,Object strike){this.target=target;this.id=id;this.strike=strike;}
        @Override public int hashCode(){return 31*(31*target.hashCode()+id.hashCode())+System.identityHashCode(strike);}
        @Override public boolean equals(Object other){
            return other instanceof Contact c&&target.equals(c.target)&&id.equals(c.id)&&strike==c.strike;
        }
    }
    private static final class Claim {
        final double credit;
        final java.util.Set<String> channels=new java.util.HashSet<>();
        Claim(double credit){this.credit=credit;}
    }
    private static final class Account {
        final UUID item;
        final String revision;
        final double limit;
        final long expiry;
        final Map<Contact,Claim> contacts=new HashMap<>();
        final Map<String,Area> areas=new HashMap<>();
        double used;
        Account(UUID item,String revision,double limit,long expiry){
            this.item=item;this.revision=revision;this.limit=limit;this.expiry=expiry;
        }
    }
    private static final class Area {
        final java.util.List<UUID> victims;
        final double share;
        final boolean sequential;
        final java.util.Set<UUID> claimed=new java.util.HashSet<>();
        Area(java.util.List<UUID> victims,double share,boolean sequential){
            this.victims=victims;this.share=share;this.sequential=sequential;
        }
    }
    private static final int MAX_ROOTS=4096,MAX_CONTACTS=4096;
    private static final long TTL_NANOS=60_000_000_000L;
    private final Map<Root,Account> roots=new HashMap<>();

    /** Reserve a producer's accepted area set before its first damage child is submitted. */
    public synchronized void declareArea(UUID world,UUID actor,String root,UUID item,String revision,
            String contactPrefix,java.util.Collection<UUID> eligible,double authored){
        declareArea(world,actor,root,item,revision,contactPrefix,eligible,authored,1,false);
    }

    /** Explicit authored strike ordinal has its own allowance inside the unchanged cast root. */
    public synchronized void declareSequentialStrikeArea(UUID world,UUID actor,String root,UUID item,String revision,
            String contactPrefix,java.util.Collection<UUID> eligible,double authored){
        declareArea(world,actor,root,item,revision,contactPrefix,eligible,authored,1,true);
    }

    /** Declare a paid interval's exact recipients before its direct damage children. */
    public synchronized void declarePaidWindowArea(UUID world,UUID actor,String root,String interval,
            UUID item,String revision,String contactPrefix,java.util.Collection<UUID> eligible,double paidDelta){
        if(interval==null||interval.isBlank()||!Double.isFinite(paidDelta)||paidDelta<0)
            throw new IllegalArgumentException("INVALID_PAID_PROC_WINDOW");
        double limit=Math.min(1,paidDelta);
        declareArea(world,actor,root+"/paid/"+interval,item,revision,contactPrefix,eligible,limit,limit,false);
    }

    private void declareArea(UUID world,UUID actor,String root,UUID item,String revision,
            String contactPrefix,java.util.Collection<UUID> eligible,double authored,double limit,boolean sequential){
        Objects.requireNonNull(eligible);
        if(contactPrefix==null||contactPrefix.isBlank()||!Double.isFinite(authored)||authored<0||authored>1)
            throw new IllegalArgumentException("INVALID_ITEM_PROC_AREA");
        var victims=eligible.stream().map(Objects::requireNonNull).distinct().sorted().toList();
        if(victims.size()>MAX_CONTACTS)throw new IllegalStateException("ITEM_PROC_AREA_CAPACITY");
        if(victims.isEmpty()||authored==0)return;
        var account=account(world,actor,root,item,revision,limit);
        var prior=account.areas.get(contactPrefix);
        if(prior!=null){
            if(!prior.victims.equals(victims)||prior.sequential!=sequential)
                throw new IllegalStateException("ITEM_PROC_AREA_DRIFT");
            return;
        }
        if(account.areas.size()>=MAX_CONTACTS)throw new IllegalStateException("ITEM_PROC_AREA_CAPACITY");
        double allowance=sequential?authored:Math.min(authored,Math.max(0,account.limit-account.used));
        account.areas.put(contactPrefix,new Area(victims,allowance/victims.size(),sequential));
        if(!sequential)account.used+=allowance;
    }

    /** -1 means this selector has not declared a set yet; 0 means the victim is outside it. */
    public synchronized double areaShare(UUID world,UUID actor,String root,UUID item,String revision,
            String contactPrefix,UUID target){
        var account=roots.get(new Root(world,actor,root));
        if(account==null||account.expiry<=System.nanoTime())return -1;
        if(!account.item.equals(item)||!account.revision.equals(revision))
            throw new IllegalStateException("ITEM_PROC_ROOT_SOURCE_DRIFT");
        var area=account.areas.get(contactPrefix);
        return area==null?-1:area.victims.contains(target)?area.share:0;
    }
    public synchronized boolean areaDeclared(UUID world,UUID actor,String root,UUID item,String revision,
            String contactPrefix){
        var account=roots.get(new Root(world,actor,root));
        if(account==null||account.expiry<=System.nanoTime())return false;
        if(!account.item.equals(item)||!account.revision.equals(revision))
            throw new IllegalStateException("ITEM_PROC_ROOT_SOURCE_DRIFT");
        return account.areas.containsKey(contactPrefix);
    }

    /** Invalid native contacts cannot take allowance from a later admitted channel or victim. */
    public synchronized double nativeCredit(UUID world,UUID actor,String root,UUID item,String revision,
            Object strike,UUID target,String contact,String channel,double authored,boolean admitted){
        return credit(world,actor,root,item,revision,strike,target,contact,channel,authored,1,admitted);
    }

    /** A paid continuous interval has its own root/interval ledger. Call only with an audited paidDelta. */
    public synchronized double paidWindowCredit(UUID world,UUID actor,String root,String interval,
            UUID item,String revision,Object strike,UUID target,String contact,String channel,
            double paidDelta,boolean admitted){
        if(interval==null||interval.isBlank()||!Double.isFinite(paidDelta)||paidDelta<0)
            throw new IllegalArgumentException("INVALID_PAID_PROC_WINDOW");
        double limit=Math.min(1,paidDelta);
        return credit(world,actor,root+"/paid/"+interval,item,revision,strike,target,contact,channel,
                limit,limit,admitted);
    }

    private double credit(UUID world,UUID actor,String root,UUID item,String revision,Object strike,
            UUID target,String contact,String channel,double authored,double limit,boolean admitted){
        Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(item);
        Objects.requireNonNull(strike);Objects.requireNonNull(target);
        if(root==null||root.isBlank()||revision==null||revision.isBlank()
                ||contact==null||contact.isBlank()||channel==null||channel.isBlank()
                ||!Double.isFinite(authored)||authored<0||authored>1
                ||!Double.isFinite(limit)||limit<0||limit>1)
            throw new IllegalArgumentException("INVALID_ITEM_PROC_CREDIT");
        if(!admitted||authored==0||limit==0)return 0;
        var account=account(world,actor,root,item,revision,limit);
        var contactKey=new Contact(target,contact,strike);
        var claim=account.contacts.get(contactKey);
        if(claim!=null){
            return claim.channels.add(channel)?claim.credit:0;
        }
        if(account.contacts.size()>=MAX_CONTACTS)throw new IllegalStateException("ITEM_PROC_CONTACT_CAPACITY");
        Area area=null;
        for(var row:account.areas.entrySet())if(contact.startsWith(row.getKey())){
            if(area!=null)throw new IllegalStateException("ITEM_PROC_AREA_AMBIGUOUS");
            area=row.getValue();
        }
        double allowed=area==null?Math.max(0,Math.min(authored,account.limit-account.used)):
                area.victims.contains(target)&&area.claimed.add(target)?Math.min(authored,area.share):0;
        claim=new Claim(allowed);claim.channels.add(channel);
        account.contacts.put(contactKey,claim);if(area==null)account.used+=allowed;
        return allowed;
    }
    private Account account(UUID world,UUID actor,String root,UUID item,String revision,double limit){
        Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(item);
        if(root==null||root.isBlank()||revision==null||revision.isBlank())
            throw new IllegalArgumentException("INVALID_ITEM_PROC_ROOT");
        long now=System.nanoTime();roots.entrySet().removeIf(row->row.getValue().expiry<=now);
        var key=new Root(world,actor,root);var account=roots.get(key);
        if(account==null){
            if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("ITEM_PROC_ROOT_CAPACITY");
            account=new Account(item,revision,limit,now+TTL_NANOS);roots.put(key,account);
        }else if(!account.item.equals(item)||!account.revision.equals(revision)||account.limit!=limit)
            throw new IllegalStateException("ITEM_PROC_ROOT_SOURCE_DRIFT");
        return account;
    }
    public synchronized void clearActor(UUID actor){roots.keySet().removeIf(key->key.actor.equals(actor));}
    public synchronized void clearWorld(UUID world){roots.keySet().removeIf(key->key.world.equals(world));}
    public synchronized void clear(){roots.clear();}
    public synchronized int size(){return roots.size();}
}
