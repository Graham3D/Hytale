package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelStatProjection;
import java.util.*;

/** Extends the encounter receipt authority. Native inventory remains the physical item owner. */
public final class GearLootService {
    /** Maximum certified base equipment count for an ME native binding; R200 profiles remain authoritative. */
    public static final int MAX_BASE_EQUIPMENT_SLOTS_PER_DEFEAT=1;
    public record Loot(EnemyRewardRegistry.LootSource source,com.inigmasgames.hytalerpg.execution.math.Vec3 position,GearClaims.Allocation allocation,double magicFind,String seed,
                       GearDropGenerator.Result result,String state,String reason,String generationRevision) {
        public Loot{Objects.requireNonNull(state);Objects.requireNonNull(reason);Objects.requireNonNull(generationRevision);if(!Double.isFinite(magicFind)||magicFind<0)throw new IllegalArgumentException("MF");}
        public Loot generated(GearDropGenerator.Result value){return new Loot(source,position,allocation,magicFind,seed,value,value.item()==null?"NO_DROP":"WORLD",value.reason(),generationRevision);}
        public Loot ownership(GearClaims.Allocation value,String status){return new Loot(source,position,value,magicFind,seed,result,status,reason,generationRevision);}
    }
    public record Cursor(long next,Loot pending){public Cursor{if(next<0)throw new IllegalArgumentException("Cursor");}}
    public record SalvageReservation(UUID player,UUID item,String sourceEventId,boolean credited){}
    /** Frozen native payload and source identity for a private-bag pickup. Recovery bytes are not spendable. */
    public record SpatialPickupReceipt(String event,UUID operationId,UUID player,UUID item,
                                       String payloadJson,long expectedBagRevision,String stage) {
        public SpatialPickupReceipt {
            Objects.requireNonNull(event);Objects.requireNonNull(operationId);Objects.requireNonNull(player);
            Objects.requireNonNull(item);Objects.requireNonNull(payloadJson);Objects.requireNonNull(stage);
            if(event.isBlank()||payloadJson.isBlank()||expectedBagRevision<0)throw new IllegalArgumentException("Spatial receipt");
        }
        public SpatialPickupReceipt stage(String next){return new SpatialPickupReceipt(event,operationId,player,item,payloadJson,expectedBagRevision,next);}
    }
    /** One player snapshot spans both the private bag and native equipment. */
    public record SpatialEquipmentReceipt(UUID operationId,UUID player,long beforeRevision,
                                          String beforeBagJson,String afterBagJson,
                                          List<EquipmentSlotChange> slots,String beforeMode,String afterMode,String stage) {
        public SpatialEquipmentReceipt(UUID operationId,UUID player,long beforeRevision,
                                       String beforeBagJson,String afterBagJson,
                                       List<EquipmentSlotChange> slots,String stage){
            this(operationId,player,beforeRevision,beforeBagJson,afterBagJson,slots,null,null,stage);
        }
        public SpatialEquipmentReceipt {
            Objects.requireNonNull(operationId);Objects.requireNonNull(player);
            Objects.requireNonNull(beforeBagJson);Objects.requireNonNull(afterBagJson);
            slots=List.copyOf(slots);Objects.requireNonNull(stage);
            if(beforeRevision<0||slots.isEmpty())throw new IllegalArgumentException("Spatial equipment receipt");
        }
        public SpatialEquipmentReceipt stage(String next){return new SpatialEquipmentReceipt(operationId,player,beforeRevision,beforeBagJson,afterBagJson,slots,beforeMode,afterMode,next);}
    }
    public record EquipmentSlotChange(String section,short slot,String beforeJson,String afterJson) {
        public EquipmentSlotChange {Objects.requireNonNull(section);}
    }
    public SpatialEquipmentReceipt prepareSpatialEquipment(SpatialEquipmentReceipt receipt){
        return store.gearTransaction("spatial-equipment",receipt.operationId().toString(),SpatialEquipmentReceipt.class,
                old->{if(old.isPresent())throw new IllegalStateException("Spatial equipment operation collision");return receipt;});
    }
    public SpatialEquipmentReceipt finishSpatialEquipment(UUID operation,String stage){
        if(!Set.of("FINALIZED","ABORTED","QUARANTINED").contains(stage))throw new IllegalArgumentException("Equipment receipt stage");
        return store.gearTransaction("spatial-equipment",operation.toString(),SpatialEquipmentReceipt.class,
                old->{var receipt=old.orElseThrow();return receipt.stage().equals("PREPARED")?receipt.stage(stage):receipt;});
    }
    public List<SpatialEquipmentReceipt> spatialEquipmentReceipts(){
        return store.gearRecords("spatial-equipment",SpatialEquipmentReceipt.class);
    }
    /** A protected stock entity remains world-owned until the bag save is durable. */
    public record SpatialStockReceipt(UUID source,UUID operationId,UUID player,UUID world,
                                      String payloadJson,long beforeRevision,String stage){
        public SpatialStockReceipt {Objects.requireNonNull(source);Objects.requireNonNull(operationId);
            Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(payloadJson);
            Objects.requireNonNull(stage);if(beforeRevision<0)throw new IllegalArgumentException("Stock revision");}
        public SpatialStockReceipt stage(String next){return new SpatialStockReceipt(source,operationId,player,world,payloadJson,beforeRevision,next);}
    }
    public SpatialStockReceipt prepareSpatialStock(SpatialStockReceipt receipt){
        return store.gearTransaction("spatial-stock",receipt.source().toString(),SpatialStockReceipt.class,old->{
            if(old.isPresent()&&!old.orElseThrow().stage().equals("ABORTED"))throw new IllegalStateException("Stock source already reserved");
            return receipt;});
    }
    public SpatialStockReceipt finishSpatialStock(UUID source,UUID operation,String stage){
        if(!Set.of("FINALIZED","ABORTED","QUARANTINED").contains(stage))throw new IllegalArgumentException("Stock stage");
        return store.gearTransaction("spatial-stock",source.toString(),SpatialStockReceipt.class,old->{
            var receipt=old.orElseThrow();if(!receipt.operationId().equals(operation))throw new IllegalStateException("Stock operation mismatch");
            return receipt.stage().equals("PREPARED")?receipt.stage(stage):receipt;});
    }
    public List<SpatialStockReceipt> spatialStockReceipts(){return store.gearRecords("spatial-stock",SpatialStockReceipt.class);}
    /** Durable intent for a player-owned stock stack moving to a protected world source. */
    public record SpatialStockDropReceipt(UUID source, UUID operationId, UUID player, UUID world,
                                          UUID entryId, String payloadJson, long beforeRevision,
                                          double x, double y, double z, String stage) {
        public SpatialStockDropReceipt {
            Objects.requireNonNull(source); Objects.requireNonNull(operationId);
            Objects.requireNonNull(player); Objects.requireNonNull(world);
            Objects.requireNonNull(entryId); Objects.requireNonNull(payloadJson);
            Objects.requireNonNull(stage);
            if (beforeRevision < 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid stock drop receipt");
        }
        public SpatialStockDropReceipt stage(String next) {
            return new SpatialStockDropReceipt(source, operationId, player, world, entryId,
                    payloadJson, beforeRevision, x, y, z, next);
        }
    }
    public SpatialStockDropReceipt prepareSpatialStockDrop(SpatialStockDropReceipt receipt) {
        return store.gearTransaction("spatial-stock-drop", receipt.source().toString(),
                SpatialStockDropReceipt.class, old -> {
                    if (old.isPresent()) throw new IllegalStateException("Stock drop source already reserved");
                    return receipt;
                });
    }
    public SpatialStockDropReceipt finishSpatialStockDrop(UUID source, UUID operation, String stage) {
        if (!Set.of("FINALIZED", "ABORTED", "QUARANTINED").contains(stage))
            throw new IllegalArgumentException("Stock drop stage");
        return store.gearTransaction("spatial-stock-drop", source.toString(),
                SpatialStockDropReceipt.class, old -> {
                    var receipt = old.orElseThrow();
                    if (!receipt.operationId().equals(operation)) throw new IllegalStateException("Stock drop operation mismatch");
                    return receipt.stage().equals("PREPARED") ? receipt.stage(stage) : receipt;
                });
    }
    public List<SpatialStockDropReceipt> spatialStockDropReceipts() {
        return store.gearRecords("spatial-stock-drop", SpatialStockDropReceipt.class);
    }
    public record MagicFindSnapshot(Map<UUID,Double> players){public MagicFindSnapshot{players=Map.copyOf(players);if(players.size()>256||players.values().stream().anyMatch(v->!Double.isFinite(v)||v<0))throw new IllegalArgumentException("Invalid MF snapshot");}}
    /** One immutable quantity decision per enemy death, committed before any individual item receipt. */
    public record DeathPicks(GearLootProfiles.Decision decision,Map<UUID,Double> magicFind) {
        public DeathPicks {Objects.requireNonNull(decision);magicFind=Map.copyOf(magicFind);}
    }
    /** Frozen ME quantity decision over R200's finalized base pick receipts. */
    public record QuantityPlan(List<UUID> baseItems,com.inigmasgames.hytalerpg.enemies.EnemyRewardContext context,
                               int bonusSlots,GearClaims.Claim claim,List<UUID> eligible,
                               Map<UUID,Double> magicFind,String generationRevision,long committedAt) {
        public QuantityPlan {baseItems=List.copyOf(baseItems);eligible=List.copyOf(eligible);
            magicFind=Map.copyOf(magicFind);Objects.requireNonNull(context);Objects.requireNonNull(claim);
            if(baseItems.isEmpty()||bonusSlots<0||bonusSlots>16)throw new IllegalArgumentException("ME quantity plan");}
    }
    public void freezeMagicFind(String event,Map<UUID,Double> values){store.gearTransaction("claims","mf/"+event,MagicFindSnapshot.class,old->old.orElse(new MagicFindSnapshot(values)));}
    public Loot deliver(EncounterContributions.DeathPlan plan){return death(plan,store.gearRead("claims","mf/"+plan.spawn().eventId(),MagicFindSnapshot.class).orElse(new MagicFindSnapshot(Map.of())).players());}
    private final FileEncounterStore store;private final GearDropGenerator generator;
    private final java.util.function.LongSupplier clock;
    public GearLootService(FileEncounterStore store,GearDropGenerator generator){this(store,generator,System::currentTimeMillis);}
    public GearLootService(FileEncounterStore store,GearDropGenerator generator,java.util.function.LongSupplier clock){this.store=store;this.generator=generator;this.clock=clock;}
    private static String encounter(UUID world,UUID enemy){return "enemy-death/"+world+"/"+enemy;}
    public void contribute(UUID world,UUID enemy,UUID actor,GearClaims.Policy policy,long at){
        store.gearTransaction("claims",encounter(world,enemy),GearClaims.Ledger.class,old->old.orElse(GearClaims.Ledger.EMPTY).contribute(actor,policy,at));
    }
    /** Production delivery: persist the count decision before creating independently owned item receipts. */
    public List<Loot> deliverPicks(EncounterContributions.DeathPlan plan){
        String parent=plan.spawn().eventId();
        var manifest=store.gearRead("loot-picks",parent,DeathPicks.class);
        if(manifest.isEmpty()&&store.gearRead("loot",parent,Loot.class).isPresent())
            return List.of(deliver(plan)); // A pre-profile death has already committed its one legacy outcome.
        if(manifest.isEmpty()){
            var eligible=new HashSet<UUID>();plan.shares().forEach(share->eligible.add(share.player()));
            if(plan.spawn().lootSource().isEmpty()||store.gearRead("claims",parent,GearClaims.Ledger.class)
                    .flatMap(ledger->ledger.select(eligible,plan.deathAtMillis())).isEmpty())
                return List.of(deliver(plan)); // No item source or beneficiary: keep the original single denial receipt.
        }
        var frozen=store.gearRead("claims","mf/"+parent,MagicFindSnapshot.class)
                .orElse(new MagicFindSnapshot(Map.of()));
        var saved=store.gearTransaction("loot-picks",parent,DeathPicks.class,old->old.orElseGet(()->
                new DeathPicks(GearLootProfiles.CURRENT.decide(plan.spawn().roleId(),plan.spawn().rank(),parent),frozen.players())));
        var successes=saved.decision().rolls().stream().filter(GearLootProfiles.Pick::opportunity).toList();
        var rows=new ArrayList<Loot>();
        for(var pick:successes)rows.add(deathOne(plan,saved.magicFind(),pick.childEventId(),pick.seed()));
        rows.addAll(appendQuantity(plan,rows,saved.magicFind()));
        traceProfile(plan,saved.decision(),saved.magicFind(),rows,manifest.isPresent());
        return List.copyOf(rows);
    }
    /** Legacy single-outcome entry point remains for pre-profile receipts and operator tests. */
    public Loot death(EncounterContributions.DeathPlan plan,Map<UUID,Double> frozenMf){
        if(plan.spawn().enemyRewards()!=null&&plan.spawn().enemyRewards().economic()){
            freezeMagicFind(plan.spawn().eventId(),frozenMf);
            frozenMf=store.gearRead("claims","mf/"+plan.spawn().eventId(),MagicFindSnapshot.class).orElseThrow().players();
        }
        var base=deathOne(plan,frozenMf,plan.spawn().eventId(),plan.spawn().eventId());
        appendQuantity(plan,List.of(base),frozenMf);
        return base;
    }
    private List<Loot> appendQuantity(EncounterContributions.DeathPlan death,List<Loot> bases,Map<UUID,Double> frozenMf){
        var context=death.spawn().enemyRewards();
        if(context==null||!context.economic()||context.quantityFactor()<=1)return List.of();
        var finalized=bases.stream().filter(row->row.result()!=null&&row.result().item()!=null).toList();
        if(finalized.isEmpty())return List.of();
        String root=death.spawn().eventId();
        var ids=finalized.stream().map(row->row.result().item().identity()).toList();
        var lead=finalized.get(0);
        var plan=store.gearTransaction("loot-quantity",root,QuantityPlan.class,old->{
            if(old.isPresent())return old.get();
            var ledger=store.gearRead("claims",root,GearClaims.Ledger.class).orElseThrow();
            var claim=ledger.claims().stream().filter(c->c.policy().group().equals(lead.allocation().group())
                    &&c.policy().revision()==lead.allocation().policyRevision()).findFirst().orElseThrow();
            return new QuantityPlan(ids,context,context.bonusEquipmentSlots(ids.size(),root),claim,
                    lead.allocation().eligible(),frozenMf,lead.generationRevision(),clock.getAsLong());
        });
        if(!plan.baseItems().equals(ids)||!plan.context().equals(context))throw new IllegalStateException("ME_QUANTITY_BASE_CHANGED");
        var children=new ArrayList<Loot>();
        for(int i=0;i<plan.bonusSlots();i++){
            String event=root+"/me-equipment/"+i;
            String seed="loot-profile/me-equipment/"+plan.context().balanceRevision()+"/"+root+"/"+i;
            var existing=store.gearRead("loot",event,Loot.class);
            if(existing.isPresent()){children.add(finish(existing.get()));continue;}
            String group=plan.claim().policy().group();
            var cursor=store.gearRead("cursors",group,Cursor.class).orElseThrow();
            if(cursor.pending()!=null){finish(cursor.pending());cursor=store.gearRead("cursors",group,Cursor.class).orElseThrow();
                existing=store.gearRead("loot",event,Loot.class);if(existing.isPresent()){children.add(finish(existing.get()));continue;}}
            var allocation=GearClaims.allocate(plan.claim(),Set.copyOf(plan.eligible()),cursor.next(),death.spawn().rank()==ProgressionMath.Rank.BOSS,
                    plan.committedAt(),new GearRandom(seed));
            var original=lead.source();var source=new EnemyRewardRegistry.LootSource(event,original.world(),original.enemy(),original.difficulty(),
                    original.sourceCombatLevel(),original.role(),original.rank(),original.rarity(),original.profileRevision());
            var prepared=new Loot(source,lead.position(),allocation,plan.magicFind().getOrDefault(allocation.sponsor(),0d),
                    seed,null,"PREPARED","",plan.generationRevision());
            long next=cursor.next();
            store.gearTransaction("cursors",group,Cursor.class,old->{var current=old.orElseThrow();
                if(current.pending()!=null||current.next()!=next)throw new IllegalStateException("ME_QUANTITY_CURSOR_CHANGED");
                return new Cursor(next,prepared);});
            children.add(finish(prepared));
        }
        return List.copyOf(children);
    }
    /** Called after the existing XP delivery cursor. A loot failure cannot remove earned XP. */
    private Loot deathOne(EncounterContributions.DeathPlan plan,Map<UUID,Double> frozenMf,String event,String seed){
        var existing=store.gearRead("loot",event,Loot.class);
        if(existing.isPresent())return finish(existing.get());
        var original=plan.spawn().lootSource();
        var source=original.map(s->new EnemyRewardRegistry.LootSource(event,s.world(),s.enemy(),s.difficulty(),
                s.sourceCombatLevel(),s.role(),s.rank(),s.rarity(),s.profileRevision()));
        Set<UUID> eligible=new HashSet<>();plan.shares().forEach(s->eligible.add(s.player()));
        var claim=store.gearRead("claims",plan.spawn().eventId(),GearClaims.Ledger.class).flatMap(c->c.select(eligible,plan.deathAtMillis()));
        if(source.isEmpty()||claim.isEmpty()){
            var denied=store.gearTransaction("loot",event,Loot.class,old->old.orElse(new Loot(source.orElse(null),plan.deathPosition(),null,0,event,null,"NO_BENEFICIARY","Frozen source or eligible claim unavailable",generator.revision())));
            for(var share:plan.shares())GearQaTrace.record(share.player(),"ENEMY_GEAR_DECISION",Map.of(
                    "enemyId",plan.spawn().enemy().toString(),"role",plan.spawn().roleId(),
                    "rank",plan.spawn().rank().name(),"result",denied.state(),"reason",denied.reason()));
            return denied;
        }
        var selected=claim.get();String group=selected.policy().group();
        var cursor=store.gearRead("cursors",group,Cursor.class).orElse(new Cursor(0,null));
        if(cursor.pending()!=null){var recovered=finish(cursor.pending());if(recovered.source().eventId().equals(event))return recovered;cursor=store.gearRead("cursors",group,Cursor.class).orElseThrow();}
        var random=new GearRandom(seed);var allocation=GearClaims.allocate(selected,eligible,cursor.next(),plan.spawn().rank()==ProgressionMath.Rank.BOSS,clock.getAsLong(),random);
        // A credited player may disconnect between their last hit and death.
        // Missing equipment at the freeze point means zero bonus, not corrupt XP/loot state.
        double mf=frozenMf.getOrDefault(allocation.sponsor(),0d);
        var prepared=new Loot(source.get(),plan.deathPosition(),allocation,mf,seed,null,"PREPARED","",generator.revision());long next=cursor.next();
        // Reservation contains the complete frozen input. A crash before publishing loot is replayable.
        store.gearTransaction("cursors",group,Cursor.class,old->{var current=old.orElse(new Cursor(0,null));if(current.pending()!=null||current.next()!=next)throw new IllegalStateException("Party cursor changed");return new Cursor(next,prepared);});
        var result=finish(prepared);
        traceDeath(plan,result);
        return result;
    }
    private static void traceDeath(EncounterContributions.DeathPlan plan,Loot row){
        var source=row.source();if(source==null||row.allocation()==null)return;
        var recipient=row.allocation().assigned()!=null?row.allocation().assigned():row.allocation().sponsor();
        if(!GearQaTrace.active(recipient))return;
        var details=new LinkedHashMap<String,Object>();
        details.put("eventId",source.eventId());details.put("enemyId",source.enemy().toString());
        details.put("role",source.role());details.put("rank",source.rank().name());
        details.put("combatLevel",source.sourceCombatLevel());details.put("era",source.difficulty().name());
        if(row.seed().startsWith("loot-profile/"))details.put("quantityOwner","loot-profile NoDrop (MF independent)");
        else {
            details.put("opportunityChance",GearMagicFind.opportunity(source.rank()));
            details.put("opportunityRoll",new GearRandom(row.seed()).stream("opportunity").nextDouble());
        }
        details.put("claimOwner",recipient.toString());details.put("magicFind",row.magicFind());
        details.put("result",row.state());details.put("reason",row.reason());
        if(row.result()!=null){
            details.put("categoryWeights",row.result().categoryDistribution());
            details.put("rarityWeights",row.result().rarityDistribution());
            var item=row.result().item();if(item!=null){
                details.put("qualityRoll",new GearRandom(row.seed()).stream("rarity").nextDouble());
                details.put("rarity",item.rarity().label);details.put("category",item.category().name());
                details.put("baseId",item.baseId());details.put("itemLevel",item.itemLevel());
                details.put("intrinsic",item.intrinsicThousandths());
                details.put("affixes",item.affixes().stream().map(a->Map.of("id",a.familyId(),"value",a.value())).toList());
                details.put("itemIdentity",item.identity().toString());
            }
        }
        GearQaTrace.record(recipient,"ENEMY_GEAR_DECISION",details);
    }
    private static void traceProfile(EncounterContributions.DeathPlan plan,GearLootProfiles.Decision decision,
                                     Map<UUID,Double> frozenMagicFind,List<Loot> rows,boolean replay){
        var details=new LinkedHashMap<String,Object>();
        details.put("sourceEventId",plan.spawn().eventId());details.put("enemyRole",plan.spawn().roleId());
        details.put("canonicalRole",decision.canonicalRole());details.put("rank",decision.rank().name());
        details.put("profile",decision.profileId());details.put("profileRevision",decision.revision());
        details.put("picks",decision.picks());details.put("guaranteedPicks",decision.guaranteedPicks());
        details.put("optionalPickChances",decision.optionalPickChances());
        details.put("maxEquipment",decision.maxEquipment());
        details.put("replay",replay);
        var mfSnapshot=new LinkedHashMap<String,Double>();
        frozenMagicFind.forEach((owner,value)->mfSnapshot.put(owner.toString(),value));
        details.put("frozenMagicFind",mfSnapshot);
        details.put("pickRolls",decision.rolls().stream().map(p->{
            var entry=new LinkedHashMap<String,Object>();entry.put("pick",p.index());
            entry.put("childReceiptId",p.childEventId());entry.put("guaranteed",p.guaranteed());
            if(!p.guaranteed()){entry.put("optionalChance",p.optionalChance());entry.put("optionalRoll",p.optionalRoll());}
            entry.put("opportunity",p.opportunity());entry.put("result",p.opportunity()?"SUCCESS":"NO_DROP");
            return entry;}).toList());
        details.put("opportunitiesSucceeded",decision.succeeded());
        details.put("itemsGenerated",rows.stream().filter(r->r.result()!=null&&r.result().item()!=null).count());
        details.put("items",rows.stream().map(row->{var entry=new LinkedHashMap<String,Object>();
            entry.put("sourceEventId",row.source()==null?"":row.source().eventId());
            entry.put("mfSnapshot",row.magicFind());entry.put("delivery",row.state());
            if(row.result()!=null&&row.result().item()!=null){entry.put("qualityRoll",new GearRandom(row.seed()).stream("rarity").nextDouble());
                entry.put("qualityWeights",row.result().rarityDistribution());
                entry.put("quality",row.result().item().rarity().label);entry.put("itemIdentity",row.result().item().identity().toString());}
            return entry;}).toList());
        for(var share:plan.shares())GearQaTrace.record(share.player(),"ENEMY_GEAR_PROFILE",details);
    }
    private Loot finish(Loot value){
        if(!value.state().equals("PREPARED")){
            if(value.allocation()!=null)advance(value);return value;
        }
        String event=value.source().eventId();
        var stored=store.gearTransaction("loot",event,Loot.class,old->old.orElse(value));
        if(stored.state().equals("PREPARED"))stored=store.gearTransaction("loot",event,Loot.class,old->{var current=old.orElseThrow();
            if(!current.state().equals("PREPARED"))return current;
            try{if(!current.generationRevision().equals(generator.revision()))throw new IllegalStateException("Pending generation definition changed; restore its original revision, never reroll");
                return current.generated(current.seed().startsWith("loot-profile/")
                        ?generator.generateGuaranteed(current.source(),current.magicFind(),current.seed(),Set.of())
                        :generator.generate(current.source(),current.magicFind(),current.seed(),Set.of()));}
            catch(IllegalArgumentException|IllegalStateException content){return new Loot(current.source(),current.position(),current.allocation(),current.magicFind(),current.seed(),null,"CONTENT_ERROR",content.getMessage(),current.generationRevision());}
        });
        advance(stored);return stored;
    }
    private void advance(Loot loot){
        store.gearTransaction("cursors",loot.allocation().group(),Cursor.class,old->{var c=old.orElseThrow();
            if(c.pending()==null||!c.pending().source().eventId().equals(loot.source().eventId()))return c;
            long next=c.next();
            if(loot.result()!=null&&loot.result().item()!=null&&loot.allocation().mode()==GearClaims.Mode.ROUND_ROBIN){
                var parent=loot.source().eventId().replaceFirst("/(?:gear-pick|me-equipment)/[0-9]+$","");
                var ledger=store.gearRead("claims",parent,GearClaims.Ledger.class).orElseThrow();
                var policy=ledger.claims().stream().map(GearClaims.Claim::policy).filter(p->p.group().equals(loot.allocation().group())).findFirst().orElseThrow();
                int count=policy.joinOrder().size(),index=policy.joinOrder().indexOf(loot.allocation().sponsor());
                next=Math.addExact(c.next(),Math.floorMod(index-Math.floorMod(c.next(),count),count)+1);
            }
            return new Cursor(next,null);
        });
    }
    public Optional<Loot> inspect(String event){return store.gearRead("loot",event,Loot.class);}
    /** Operator QA source uses the same world receipt and exact-item custody as natural loot. */
    public Loot publishSentinelQa(UUID owner,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,GearInstance item){
        if(!item.qaOnly())throw new IllegalArgumentException("QA-only source required");
        UUID eventEnemy=UUID.randomUUID();String event=encounter(world,eventEnemy);long now=clock.getAsLong();
        var source=new EnemyRewardRegistry.LootSource(event,world,eventEnemy,item.sourceEra(),item.itemLevel(),
                "QA_Iron_Sentinel",ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"sentinel-qa-v1");
        var allocation=new GearClaims.Allocation(GearClaims.Mode.SOLO,"solo/"+owner,0,List.of(owner),owner,owner,owner,
                now,Math.addExact(now,300_000),0,null,List.of("operator-sentinel-qa"));
        var result=new GearDropGenerator.Result(item,Map.of(),Map.of(),"OPERATOR_QA");
        return store.gearTransaction("loot",event,Loot.class,old->{if(old.isPresent())throw new IllegalStateException("QA event collision");
            return new Loot(source,at,allocation,0,"operator-sentinel-qa/"+item.identity(),result,"WORLD","OPERATOR_QA",generator.revision());});
    }
    /** Durable first half of a player-requested iron drop. Native inventory still owns the item. */
    public Loot beginIronDrop(String sourceEvent,UUID owner,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at,GearInstance item){
        return beginIronDrop(sourceEvent,owner,world,at,item,false);
    }
    /** The same durable source receipt also supports a private-bag withdrawal. */
    public Loot beginIronDrop(String sourceEvent,UUID owner,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at,GearInstance item,boolean spatial){
        long now=clock.getAsLong();
        var allocation=new GearClaims.Allocation(GearClaims.Mode.SOLO,"solo/"+owner,0,List.of(owner),owner,owner,owner,
                Math.addExact(now,10_000),Math.addExact(now,java.util.concurrent.TimeUnit.DAYS.toMillis(3650)),
                0,null,List.of("player-iron-drop"));
        if(sourceEvent==null){
            if(!item.qaOnly())throw new IllegalArgumentException("Committed Gear Master source receipt required");
            UUID enemy=UUID.randomUUID();String event=encounter(world,enemy);
            var source=new EnemyRewardRegistry.LootSource(event,world,enemy,item.sourceEra(),item.itemLevel(),
                    "QA_Iron_Sentinel_Player_Drop",ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"player-iron-drop-v1");
            var result=new GearDropGenerator.Result(item,Map.of(),Map.of(),"OPERATOR_QA");
            return store.gearTransaction("loot",event,Loot.class,old->{if(old.isPresent())throw new IllegalStateException("Player drop event collision");
                return new Loot(source,at,allocation,0,"player-iron-drop/"+item.identity(),result,
                        "DROP_PENDING_NATIVE_SAVE",spatial?"PLAYER_SPATIAL_DROP_QA":"PLAYER_IRON_DROP_QA",generator.revision());});
        }
        return store.gearTransaction("loot",sourceEvent,Loot.class,old->{var row=old.orElseThrow(
                ()->new IllegalArgumentException("Source gear receipt missing"));
            // Older copied-save gear can be physically in the private bag while its
            // player-owned source receipt still names the former native inventory.
            // The spatial caller proves exact bag possession and native absence.
            boolean playerOwned=spatial?Set.of("SPATIAL_BAG","INVENTORY").contains(row.state())
                    &&owner.equals(row.allocation().assigned()):row.state().equals("INVENTORY");
            if(!playerOwned||row.result()==null||!item.equals(row.result().item())
                    ||!row.source().eventId().equals(sourceEvent))throw new IllegalArgumentException("Source gear custody is not inventory-owned");
            if(consumed(item.identity()))throw new IllegalArgumentException("Source gear is reserved for salvage");
            var source=row.source();
            if(!source.world().equals(world))source=new EnemyRewardRegistry.LootSource(source.eventId(),world,source.enemy(),
                    source.difficulty(),source.sourceCombatLevel(),source.role(),source.rank(),source.rarity(),source.profileRevision());
            return new Loot(source,at,allocation,row.magicFind(),row.seed(),row.result(),
                    "DROP_PENDING_NATIVE_SAVE",spatial?"PLAYER_SPATIAL_DROP":"PLAYER_IRON_DROP",row.generationRevision());});
    }
    /** Called only after a successful native player save, or after reconnect confirms the item is absent. */
    public Loot acknowledgeIronDrop(String event,UUID owner,UUID item){
        return store.gearTransaction("loot",event,Loot.class,old->{var row=old.orElseThrow();
            if(!row.state().equals("DROP_PENDING_NATIVE_SAVE")||!owner.equals(row.allocation().assigned())
                    ||!item.equals(row.result().item().identity()))throw new IllegalArgumentException("Iron drop acknowledgement mismatch");
            return row.ownership(row.allocation(),"WORLD");});
    }
    /** Called only while the exact native item is still in inventory. */
    public Loot releaseIronDrop(String event,UUID owner,UUID item){
        return store.gearTransaction("loot",event,Loot.class,old->{var row=old.orElseThrow();
            if(!row.state().equals("DROP_PENDING_NATIVE_SAVE")||!owner.equals(row.allocation().assigned())
                    ||!item.equals(row.result().item().identity()))throw new IllegalArgumentException("Iron drop release mismatch");
            return row.ownership(row.allocation(),row.reason().endsWith("_QA")?"DROP_ABORTED":
                    row.reason().startsWith("PLAYER_SPATIAL_DROP")?"SPATIAL_BAG":"INVENTORY");});
    }
    public List<Loot> records(){return store.gearRecords("loot",Loot.class);}
    public List<SpatialPickupReceipt> spatialPickupReceipts(){return store.gearRecords("spatial-pickups",SpatialPickupReceipt.class);}
    public Optional<SpatialPickupReceipt> spatialPickupReceipt(UUID operationId){return store.gearRead("spatial-pickups",operationId.toString(),SpatialPickupReceipt.class);}
    private static final String SPATIAL_OPERATION = "spatial-pickup-operation:";
    private static UUID reservedSpatialOperation(Loot source) {
        var audit=source.allocation().audit();
        if(audit.isEmpty()||!audit.getLast().startsWith(SPATIAL_OPERATION))return null;
        return UUID.fromString(audit.getLast().substring(SPATIAL_OPERATION.length()));
    }
    private static GearClaims.Allocation withSpatialOperation(GearClaims.Allocation allocation,UUID operation) {
        var audit=new ArrayList<>(allocation.audit());audit.add(SPATIAL_OPERATION+operation);
        return new GearClaims.Allocation(allocation.mode(),allocation.group(),allocation.policyRevision(),
                allocation.eligible(),allocation.sponsor(),allocation.assigned(),allocation.frozenLeader(),
                allocation.exclusiveUntil(),allocation.expiresAt(),allocation.revision(),allocation.claimedBy(),audit);
    }
    /** Durable intent is written before the managed source is reserved. An orphan PREPARED row owns nothing. */
    public SpatialPickupReceipt prepareSpatialPickup(String event,UUID operationId,UUID actor,UUID item,
                                                     String exactPayload,long expectedBagRevision){
        var source=inspect(event).orElseThrow(()->new IllegalArgumentException("Missing managed source"));
        if(!source.state().equals("WORLD")||source.result()==null||source.result().item()==null
                ||!source.result().item().identity().equals(item))throw new IllegalArgumentException("Source not world-owned");
        var proposed=new SpatialPickupReceipt(event,operationId,actor,item,exactPayload,expectedBagRevision,"PREPARED");
        return store.gearTransaction("spatial-pickups",operationId.toString(),SpatialPickupReceipt.class,old->{
            if(old.isEmpty())return proposed;
            var prior=old.get();
            if(!prior.event().equals(event)||!prior.operationId().equals(operationId)||!prior.player().equals(actor)
                    ||!prior.item().equals(item)||!prior.payloadJson().equals(exactPayload)
                    ||prior.expectedBagRevision()!=expectedBagRevision)throw new IllegalArgumentException("Spatial pickup replay mismatch");
            return prior;
        });
    }
    /** Source CAS is the exclusive claim. The protected world projection cannot be picked up natively. */
    public Loot reserveSpatialPickup(String event,UUID actor,long revision,long now,UUID operationId){
        var prepared=spatialPickupReceipt(operationId).orElseThrow(()->new IllegalArgumentException("Missing spatial prepare"));
        if(!prepared.event().equals(event)||!prepared.player().equals(actor)||!prepared.operationId().equals(operationId)
                ||!Set.of("PREPARED","SOURCE_RESERVED").contains(prepared.stage()))
            throw new IllegalArgumentException("Spatial prepare mismatch");
        var row=store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();
            if(loot.state().equals("PICKUP_PENDING_SPATIAL_SAVE")&&actor.equals(loot.allocation().claimedBy())
                    &&loot.result().item().identity().equals(prepared.item())
                    &&operationId.equals(reservedSpatialOperation(loot)))return loot;
            if(!loot.state().equals("WORLD")||!loot.result().item().identity().equals(prepared.item()))
                throw new IllegalArgumentException("Managed source already claimed");
            return loot.ownership(withSpatialOperation(loot.allocation().pickup(actor,revision,now),operationId),
                    "PICKUP_PENDING_SPATIAL_SAVE");
        });
        store.gearTransaction("spatial-pickups",operationId.toString(),SpatialPickupReceipt.class,old->{var receipt=old.orElseThrow();
            if(!receipt.operationId().equals(operationId))throw new IllegalArgumentException("Spatial receipt changed");
            return receipt.stage("SOURCE_RESERVED");
        });
        return row;
    }
    /** Call only after the player's exact bag payload is present in a completed player save. */
    public Loot acknowledgeSpatialPickup(String event,UUID actor,UUID operationId){
        var receipt=spatialPickupReceipt(operationId).orElseThrow();
        if(!receipt.event().equals(event)||!receipt.player().equals(actor)||!receipt.operationId().equals(operationId)
                ||!Set.of("SOURCE_RESERVED","FINALIZED").contains(receipt.stage()))
            throw new IllegalArgumentException("Spatial receipt not reserved");
        var row=store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();
            if(loot.state().equals("SPATIAL_BAG")&&actor.equals(loot.allocation().claimedBy())
                    &&operationId.equals(reservedSpatialOperation(loot)))return loot;
            if(!loot.state().equals("PICKUP_PENDING_SPATIAL_SAVE")
                    ||!actor.equals(loot.allocation().claimedBy())
                    ||!operationId.equals(reservedSpatialOperation(loot))
                    ||!receipt.item().equals(loot.result().item().identity()))
                throw new IllegalArgumentException("Spatial source acknowledgement mismatch");
            return loot.ownership(loot.allocation(),"SPATIAL_BAG");
        });
        store.gearTransaction("spatial-pickups",operationId.toString(),SpatialPickupReceipt.class,old->old.orElseThrow().stage("FINALIZED"));
        return row;
    }
    /** Recovery calls this only after a loaded player save proves the bag has no matching receipt/payload. */
    public Loot releaseUncommittedSpatialPickup(String event,UUID actor,UUID operationId){
        var receipt=spatialPickupReceipt(operationId).orElseThrow();
        if(!receipt.event().equals(event)||!receipt.player().equals(actor)||!receipt.operationId().equals(operationId)
                ||receipt.stage().equals("FINALIZED"))throw new IllegalArgumentException("Spatial release mismatch");
        var row=store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();
            if(loot.state().equals("WORLD"))return loot;
            if(!loot.state().equals("PICKUP_PENDING_SPATIAL_SAVE")||!actor.equals(loot.allocation().claimedBy())
                    ||!operationId.equals(reservedSpatialOperation(loot)))
                throw new IllegalArgumentException("Spatial source no longer reserved");
            var a=loot.allocation();
            return loot.ownership(new GearClaims.Allocation(a.mode(),a.group(),a.policyRevision(),a.eligible(),a.sponsor(),
                    a.assigned(),a.frozenLeader(),a.exclusiveUntil(),a.expiresAt(),a.revision()+1,null,a.audit()),"WORLD");
        });
        store.gearTransaction("spatial-pickups",operationId.toString(),SpatialPickupReceipt.class,old->old.orElseThrow().stage("ABORTED"));
        return row;
    }
    public List<SalvageReservation> salvageReservations(){return store.gearRecords("salvage",SalvageReservation.class);}
    public Loot releaseFullPickup(String event,UUID player){return store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();var a=loot.allocation();
        if(!loot.state().equals("PICKUP_PENDING_NATIVE_SAVE")||!player.equals(a.claimedBy()))throw new IllegalArgumentException("Pickup reservation mismatch");
        return loot.ownership(new GearClaims.Allocation(a.mode(),a.group(),a.policyRevision(),a.eligible(),a.sponsor(),a.assigned(),a.frozenLeader(),a.exclusiveUntil(),a.expiresAt(),a.revision()+1,null,a.audit()),"WORLD");});}
    /** Full-inventory validation precedes CAS. Delivery adapter must persist the accepted native stack before acknowledgement. */
    public Loot reservePickup(String event,UUID actor,long revision,long now,boolean hasSpace){
        if(!hasSpace)throw new IllegalArgumentException("Inventory full; item remains on ground");
        return store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();if(!loot.state().equals("WORLD"))throw new IllegalArgumentException("Item not world-owned");
            return loot.ownership(loot.allocation().pickup(actor,revision,now),"PICKUP_PENDING_NATIVE_SAVE");});
    }
    public Loot acknowledgePickup(String event,UUID actor,UUID exactItem){
        return store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();
            if(!Set.of("PICKUP_PENDING_NATIVE_SAVE","INVENTORY").contains(loot.state())||!actor.equals(loot.allocation().claimedBy())||!loot.result().item().identity().equals(exactItem))throw new IllegalArgumentException("Pickup acknowledgement mismatch");
            return loot.ownership(loot.allocation(),"INVENTORY");});
    }
    /** Exact ground-item CAS for a future durable companion binding; never mints a native stack. */
    public Loot reserveForge(String event,UUID actor,long revision,long now,UUID exactItem){
        return store.gearTransaction("loot",event,Loot.class,old->{var row=old.orElseThrow();
            if(!row.state().equals("WORLD")||row.result()==null||row.result().item()==null
                    ||!row.result().item().identity().equals(exactItem))
                throw new IllegalArgumentException("Forge source is not the exact world item");
            return row.ownership(row.allocation().pickup(actor,revision,now),"FORGE_PENDING");
        });
    }
    /** Roll back only an uncommitted reservation. A bound source is never recoverable. */
    public Loot releaseForge(String event,UUID actor,UUID exactItem){
        return store.gearTransaction("loot",event,Loot.class,old->{var row=old.orElseThrow();
            var binding=store.gearRead("sentinels",actor.toString(),IronSentinelBinding.class).orElse(null);
            if(binding!=null&&binding.state()!=IronSentinelBinding.State.DEAD&&binding.state()!=IronSentinelBinding.State.ABORTED
                    &&binding.sourceEvent().equals(event)&&binding.boundItem().identity().equals(exactItem))
                throw new IllegalArgumentException("Durable Sentinel owns this item; forge cannot roll back");
            if(!row.state().equals("FORGE_PENDING")||!actor.equals(row.allocation().claimedBy())
                    ||row.result()==null||row.result().item()==null||!row.result().item().identity().equals(exactItem))
                throw new IllegalArgumentException("Forge reservation mismatch");
            var a=row.allocation();
            return row.ownership(new GearClaims.Allocation(a.mode(),a.group(),a.policyRevision(),a.eligible(),a.sponsor(),
                    a.assigned(),a.frozenLeader(),a.exclusiveUntil(),a.expiresAt(),a.revision()+1,null,a.audit()),"WORLD");
        });
    }
    /** Custody can leave escrow only after the exact recoverable companion is durable. */
    public Loot acknowledgeForge(String event,UUID actor,UUID exactItem,UUID sentinelInstance){
        return store.gearTransaction("loot",event,Loot.class,old->{var row=old.orElseThrow();
            var bound=store.gearRead("sentinels",actor.toString(),IronSentinelBinding.class).orElseThrow(
                    ()->new IllegalArgumentException("No durable Iron Sentinel binding"));
            if(!bound.instanceId().equals(sentinelInstance)||!bound.ownerId().equals(actor)
                    ||!bound.sourceEvent().equals(event)||!bound.boundItem().identity().equals(exactItem)
                    ||bound.state()==IronSentinelBinding.State.DEAD||bound.state()==IronSentinelBinding.State.ABORTED)
                throw new IllegalArgumentException("Durable Iron Sentinel binding mismatch");
            if(!Set.of("FORGE_PENDING","SENTINEL_BOUND").contains(row.state())||!actor.equals(row.allocation().claimedBy())
                    ||row.result()==null||row.result().item()==null||!row.result().item().identity().equals(exactItem))
                throw new IllegalArgumentException("Forge acknowledgement mismatch");
            return row.ownership(row.allocation(),"SENTINEL_BOUND");
        });
    }
    /** Reservation precedes binding; an interruption leaves the item in non-pickable escrow. */
    public IronSentinelBinding prepareSentinel(String event,UUID actor,long revision,long now,UUID exactItem,
            UUID instance,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,int effectiveLevel,double nativeInterval){
        return prepareSentinel(event,actor,revision,now,exactItem,instance,world,at,effectiveLevel,nativeInterval,1);
    }
    public IronSentinelBinding prepareSentinel(String event,UUID actor,long revision,long now,UUID exactItem,
            UUID instance,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor){
        return prepareSentinel(event,actor,revision,now,exactItem,instance,world,at,effectiveLevel,nativeInterval,powerFactor,GearEffectSnapshot.EMPTY);
    }
    public IronSentinelBinding prepareSentinel(String event,UUID actor,long revision,long now,UUID exactItem,
            UUID instance,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor,
            GearEffectSnapshot acceptedOwner){
        if(!Double.isFinite(powerFactor)||powerFactor<=0)throw new IllegalArgumentException("Invalid Sentinel power factor");
        var ownerItems=java.util.List.copyOf(java.util.Objects.requireNonNull(acceptedOwner).items());
        if(ownerItems.stream().anyMatch(item->item.identity().equals(exactItem)))
            throw new IllegalArgumentException("Bound source cannot be owner equipment");
        var existing=store.gearRead("sentinels",actor.toString(),IronSentinelBinding.class).orElse(null);
        if(existing!=null&&existing.state()!=IronSentinelBinding.State.DEAD&&existing.state()!=IronSentinelBinding.State.ABORTED)
            throw new IllegalArgumentException("Iron Sentinel already exists");
        var pending=reserveForge(event,actor,revision,now,exactItem);
        try{
            var item=pending.result().item();
            double health=IronSentinelStatProjection.project(effectiveLevel,item,nativeInterval).finalMaxHealth()*powerFactor
                    *(1+acceptedOwner.percent("WA-114"));
            return store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{
                if(old.isPresent()&&old.get().state()!=IronSentinelBinding.State.DEAD&&old.get().state()!=IronSentinelBinding.State.ABORTED)
                    throw new IllegalArgumentException("Iron Sentinel already exists");
                return new IronSentinelBinding(3,instance,actor,event,item,IronSentinelBinding.State.PREPARED,health,world,at,now,0,
                        effectiveLevel,powerFactor,nativeInterval,ownerItems);
            });
        }catch(RuntimeException failure){
            try{releaseForge(event,actor,exactItem);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
    /** The outgoing binding is saved under the incoming instance before its singleton slot is
     * replaced. A failed or interrupted preparation can restore that exact companion and return
     * the new source to WORLD; the outgoing bound source is never made pickable. */
    public IronSentinelBinding prepareReplacingSentinel(String event,UUID actor,long revision,long now,UUID exactItem,
            UUID instance,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor){
        return prepareReplacingSentinel(event,actor,revision,now,exactItem,instance,world,at,effectiveLevel,nativeInterval,powerFactor,GearEffectSnapshot.EMPTY);
    }
    public IronSentinelBinding prepareReplacingSentinel(String event,UUID actor,long revision,long now,UUID exactItem,
            UUID instance,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor,
            GearEffectSnapshot acceptedOwner){
        if(!Double.isFinite(powerFactor)||powerFactor<=0)throw new IllegalArgumentException("Invalid Sentinel power factor");
        var ownerItems=java.util.List.copyOf(java.util.Objects.requireNonNull(acceptedOwner).items());
        if(ownerItems.stream().anyMatch(item->item.identity().equals(exactItem)))
            throw new IllegalArgumentException("Bound source cannot be owner equipment");
        var outgoing=sentinel(actor).orElseThrow(()->new IllegalStateException("SENTINEL_REPLACEMENT_SOURCE_MISSING"));
        if(outgoing.state()!=IronSentinelBinding.State.ACTIVE&&outgoing.state()!=IronSentinelBinding.State.DORMANT
                &&outgoing.state()!=IronSentinelBinding.State.RESTORING)
            throw new IllegalStateException("SENTINEL_REPLACEMENT_SOURCE_NOT_RECOVERABLE");
        var pending=reserveForge(event,actor,revision,now,exactItem);
        try{
            var item=pending.result().item();
            double health=IronSentinelStatProjection.project(effectiveLevel,item,nativeInterval).finalMaxHealth()*powerFactor
                    *(1+acceptedOwner.percent("WA-114"));
            store.gearTransaction("sentinel-replacement-backups",instance.toString(),IronSentinelBinding.class,old->{
                if(old.isPresent())throw new IllegalStateException("SENTINEL_REPLACEMENT_INSTANCE_REUSED");
                return outgoing;
            });
            return store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{
                if(old.isEmpty()||!old.get().instanceId().equals(outgoing.instanceId())
                        ||old.get().state()!=outgoing.state())
                    throw new IllegalStateException("SENTINEL_REPLACEMENT_SOURCE_CHANGED");
                return new IronSentinelBinding(3,instance,actor,event,item,IronSentinelBinding.State.PREPARED,health,world,at,now,0,
                        effectiveLevel,powerFactor,nativeInterval,ownerItems);
            });
        }catch(RuntimeException failure){
            try{releaseForge(event,actor,exactItem);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
    public Optional<IronSentinelBinding> replacementBackup(UUID instance){
        return store.gearRead("sentinel-replacement-backups",instance.toString(),IronSentinelBinding.class);
    }
    /** Called only while the incoming binding is still PREPARED. */
    public void abortPreparedReplacement(UUID actor,UUID instance,boolean outgoingNativePresent){
        var backup=replacementBackup(instance).orElseThrow(()->new IllegalStateException("SENTINEL_REPLACEMENT_BACKUP_MISSING"));
        var pending=sentinel(actor).orElseThrow();
        if(!pending.instanceId().equals(instance)||pending.state()!=IronSentinelBinding.State.PREPARED)
            throw new IllegalStateException("SENTINEL_REPLACEMENT_CHANGED");
        var source=inspect(pending.sourceEvent()).orElseThrow();
        if(source.state().equals("SENTINEL_BOUND"))throw new IllegalStateException("SENTINEL_REPLACEMENT_ALREADY_BOUND");
        abortPreparedSentinel(actor,instance);
        store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{
            if(old.isEmpty()||!old.get().instanceId().equals(instance)||old.get().state()!=IronSentinelBinding.State.ABORTED)
                throw new IllegalStateException("SENTINEL_REPLACEMENT_ROLLBACK_CHANGED");
            return outgoingNativePresent?backup:backup.withState(IronSentinelBinding.State.DORMANT,
                    backup.currentHealth(),backup.worldId(),backup.position());
        });
    }
    public Optional<IronSentinelBinding> sentinel(UUID actor){return store.gearRead("sentinels",actor.toString(),IronSentinelBinding.class);}
    public List<IronSentinelBinding> sentinels(){return store.gearRecords("sentinels",IronSentinelBinding.class);}
    public IronSentinelBinding sentinelState(UUID actor,UUID instance,IronSentinelBinding.State expected,
            IronSentinelBinding.State next,double health,UUID world,com.inigmasgames.hytalerpg.execution.math.Vec3 at){
        return store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{var row=old.orElseThrow();
            if(!row.instanceId().equals(instance)||row.state()!=expected)throw new IllegalArgumentException("Sentinel state changed");
            return row.withState(next,health,world,at);
        });
    }
    /** Recover a crash at either side of the two durable forge writes before admitting a native actor. */
    public Optional<IronSentinelBinding> claimSentinelRestore(UUID actor,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at){
        var current=sentinel(actor).orElse(null);
        if(current==null||current.state()==IronSentinelBinding.State.DEAD||current.state()==IronSentinelBinding.State.ABORTED)return Optional.empty();
        var source=inspect(current.sourceEvent()).orElseThrow(()->new IllegalStateException("SENTINEL_SOURCE_RECEIPT_MISSING"));
        if(!source.result().item().identity().equals(current.boundItem().identity()))throw new IllegalStateException("SENTINEL_SOURCE_IDENTITY_CHANGED");
        if(current.state()==IronSentinelBinding.State.PREPARED){
            if(source.state().equals("FORGE_PENDING")){
                if(replacementBackup(current.instanceId()).isPresent()){
                    abortPreparedReplacement(actor,current.instanceId(),false);
                    return claimSentinelRestore(actor,world,at);
                }
                abortPreparedSentinel(actor,current.instanceId());return Optional.empty();
            }
            if(!source.state().equals("SENTINEL_BOUND"))throw new IllegalStateException("SENTINEL_PREPARED_CUSTODY_INVALID");
        }else if(!source.state().equals("SENTINEL_BOUND"))throw new IllegalStateException("SENTINEL_BOUND_CUSTODY_INVALID");
        if(current.currentHealth()<=0){endSentinel(actor,current.instanceId(),true,0,current.worldId(),current.position());return Optional.empty();}
        UUID instance=current.instanceId();
        return Optional.of(store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{
            var row=old.orElseThrow();
            if(!row.instanceId().equals(instance)||row.state()==IronSentinelBinding.State.DEAD||row.state()==IronSentinelBinding.State.ABORTED)
                throw new IllegalStateException("SENTINEL_RESTORE_IDENTITY_CHANGED");
            return row.withState(IronSentinelBinding.State.RESTORING,row.currentHealth(),world,at);
        }));
    }
    public IronSentinelBinding checkpointSentinel(UUID actor,UUID instance,double health,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at){
        return store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{var row=old.orElseThrow();
            if(!row.instanceId().equals(instance)||row.state()!=IronSentinelBinding.State.ACTIVE)return row;
            double retained=Math.min(Math.max(0,health),
                    IronSentinelStatProjection.project(row.restoredLevel(),row.boundItem(),row.restoredInterval()).finalMaxHealth()*row.restoredPowerFactor());
            if(Math.abs(row.currentHealth()-retained)<.01&&row.worldId().equals(world)&&row.position().equals(at))return row;
            return row.withState(row.state(),retained,world,at);
        });
    }
    public IronSentinelBinding finishSentinelRestore(UUID actor,UUID instance,double health,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at){
        return sentinelState(actor,instance,IronSentinelBinding.State.RESTORING,IronSentinelBinding.State.ACTIVE,health,world,at);
    }
    public IronSentinelBinding endSentinel(UUID actor,UUID instance,boolean trueDeath,double health,UUID world,
            com.inigmasgames.hytalerpg.execution.math.Vec3 at){
        var current=sentinel(actor).orElseThrow();
        if(trueDeath&&current.state()==IronSentinelBinding.State.PREPARED&&current.instanceId().equals(instance))
            acknowledgeForge(current.sourceEvent(),actor,current.boundItem().identity(),instance);
        return store.gearTransaction("sentinels",actor.toString(),IronSentinelBinding.class,old->{var row=old.orElseThrow();
            if(!row.instanceId().equals(instance))throw new IllegalStateException("SENTINEL_END_IDENTITY_CHANGED");
            if(row.state()==IronSentinelBinding.State.DEAD||row.state()==IronSentinelBinding.State.ABORTED)return row;
            if(row.state()!=IronSentinelBinding.State.ACTIVE&&row.state()!=IronSentinelBinding.State.RESTORING
                    &&!(trueDeath&&(row.state()==IronSentinelBinding.State.PREPARED||row.state()==IronSentinelBinding.State.DORMANT)))
                throw new IllegalStateException("SENTINEL_END_STATE_INVALID");
            return row.withState(trueDeath?IronSentinelBinding.State.DEAD:IronSentinelBinding.State.DORMANT,
                    Math.max(0,health),world,at);
        });
    }
    public void abortPreparedSentinel(UUID actor,UUID instance){
        var row=sentinel(actor).orElseThrow();
        sentinelState(actor,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ABORTED,
                row.currentHealth(),row.worldId(),row.position());
        releaseForge(row.sourceEvent(),actor,row.boundItem().identity());
    }
    public Loot assign(String event,UUID leader,UUID recipient,long revision,long now){return store.gearTransaction("loot",event,Loot.class,old->{var loot=old.orElseThrow();if(!loot.state().equals("WORLD"))throw new IllegalArgumentException("Not available for assignment");return loot.ownership(loot.allocation().assign(leader,recipient,revision,now),"WORLD");});}
    public SalvageReservation reserveSalvage(String event,UUID actor,GearInstance physicalItem){
        var loot=inspect(event).orElseThrow();
        if(!loot.state().equals("INVENTORY")||!loot.result().item().equals(physicalItem))throw new IllegalArgumentException("Committed source item required");
        GearEconomy.salvage(physicalItem).orElseThrow(()->new IllegalArgumentException("No RPG salvage"));
        // Caller proves current native inventory possession; original recipient need not be current owner after trade.
        return store.gearTransaction("salvage",physicalItem.identity().toString(),SalvageReservation.class,old->{
            if(old.isPresent()&&!old.get().player().equals(actor))throw new IllegalArgumentException("Item already reserved");
            return old.orElse(new SalvageReservation(actor,physicalItem.identity(),event,false));});
    }
    public void creditSalvage(SalvageReservation reservation,RpgLoadoutService players){
        var saved=store.gearRead("salvage",reservation.item().toString(),SalvageReservation.class).orElseThrow();
        if(!saved.player().equals(reservation.player())||!saved.sourceEventId().equals(reservation.sourceEventId()))throw new IllegalArgumentException("Salvage owner conflict");
        var loot=inspect(saved.sourceEventId()).orElseThrow();players.creditGearSalvage(saved.player(),loot.result().item());
        store.gearTransaction("salvage",saved.item().toString(),SalvageReservation.class,old->new SalvageReservation(saved.player(),saved.item(),saved.sourceEventId(),true));
    }
    public boolean consumed(UUID item){return store.gearRead("salvage",item.toString(),SalvageReservation.class).isPresent();}
}
