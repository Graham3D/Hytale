package com.inigmasgames.hytalerpg.gear;

import java.util.*;

/** Checkpoint within the existing player save. Receipts are retained; capacity fails closed. */
public record GearEconomyProgress(Map<String,Long> materials,Map<String,Integer> baseRanks,Map<String,Receipt> receipts) {
    public static final GearEconomyProgress INITIAL=new GearEconomyProgress(Map.of(),Map.of(),Map.of());
    public static final int MAX_RECEIPTS=4096;
    public record Receipt(String operation,String subject,int beforeRank,int afterRank,int roll,Map<String,Long> amounts) {
        public Receipt {Objects.requireNonNull(operation);Objects.requireNonNull(subject);amounts=Map.copyOf(amounts);
            if(!Set.of("SALVAGE","UPGRADE").contains(operation)||beforeRank<0||beforeRank>20||afterRank<0||afterRank>20||roll< -1||roll>=10000)throw new IllegalArgumentException("Invalid economy receipt");}
    }
    public GearEconomyProgress {
        materials=Map.copyOf(materials);baseRanks=Map.copyOf(baseRanks);receipts=Map.copyOf(receipts);
        if(materials.size()>12||baseRanks.size()>256||receipts.size()>MAX_RECEIPTS)throw new IllegalArgumentException("Economy checkpoint capacity");
        materials.forEach((key,count)->{GearEconomy.Material.parse(key);if(count<0)throw new IllegalArgumentException("Negative components");});
        baseRanks.forEach((key,rank)->{if(!key.matches("[a-z][a-z0-9_]{0,95}")||rank<1||rank>20)throw new IllegalArgumentException("Invalid base skill rank");});
    }
    /** Operator QA changes only the saved base rank, leaving materials and economy receipts intact. */
    public GearEconomyProgress withBaseRank(String skill,int rank){
        var ranks=new TreeMap<>(baseRanks);ranks.put(skill,rank);
        return new GearEconomyProgress(materials,ranks,receipts);
    }
    public GearEconomyProgress salvage(String operation,GearInstance item){
        var prior=receipts.get(operation);if(prior!=null){if(!prior.operation().equals("SALVAGE")||!prior.subject().equals(item.identity().toString()))throw new IllegalArgumentException("Receipt conflict");return this;}
        if(receipts.values().stream().anyMatch(r->r.operation().equals("SALVAGE")&&r.subject().equals(item.identity().toString())))throw new IllegalArgumentException("Item already salvaged");
        var payout=GearEconomy.salvage(item).orElseThrow(()->new IllegalArgumentException("This item grants no RPG salvage"));
        var balance=new TreeMap<>(materials);balance.merge(payout.material().key(),(long)payout.quantity(),Math::addExact);
        return with(operation,new Receipt("SALVAGE",item.identity().toString(),0,0,-1,Map.of(payout.material().key(),(long)payout.quantity())),balance,baseRanks);
    }
    public GearEconomyProgress upgrade(String operation,String skill,int before,int roll){
        if(roll<0||roll>=10000)throw new IllegalArgumentException("Upgrade RNG range");
        var prior=receipts.get(operation);if(prior!=null){if(!prior.operation().equals("UPGRADE")||!prior.subject().equals(skill))throw new IllegalArgumentException("Receipt conflict");return this;}
        var recipe=GearEconomy.recipe(before+1);var payment=GearEconomy.debit(materials,recipe);
        int after=roll<recipe.resetPercent()*100?1:before+1;
        var balance=new TreeMap<>(materials);payment.forEach((key,count)->balance.put(key,Math.subtractExact(balance.get(key),count)));
        var ranks=new TreeMap<>(baseRanks);ranks.put(skill,after);
        return with(operation,new Receipt("UPGRADE",skill,before,after,roll,payment),balance,ranks);
    }
    private GearEconomyProgress with(String id,Receipt receipt,Map<String,Long> balance,Map<String,Integer> ranks){
        if(id==null||id.isBlank()||id.length()>200||receipts.size()>=MAX_RECEIPTS)throw new IllegalStateException("Economy receipt capacity");
        var next=new TreeMap<>(receipts);next.put(id,receipt);return new GearEconomyProgress(balance,ranks,next);
    }
}
