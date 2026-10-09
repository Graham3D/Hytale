package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialInventoryTransferCoordinator;
import java.math.BigDecimal;
import java.util.*;

/** Operator QA commands; generated items retain production rolls and protected QA provenance. */
public final class RpgGearCommand extends AbstractCommandCollection {
    public static final String AUTHOR_PERMISSION="inigmasgames.rpg.gear.author";
    private final Map<String,Integer> affixQaCursor=new java.util.concurrent.ConcurrentHashMap<>();
    public RpgGearCommand(HytaleGearEquipment equipment) {
        super("gear","Inspect gear and create protected generated QA equipment.");
        var catalog=GearCatalog.load();var bindings=new GearBindings();
        var generator=new GearDropGenerator(catalog,bindings,GearAffixRuntime.ENABLED);
        var affixQa=new GearAffixQaSuite(catalog);
        var affixQaCommands=new AbstractCommandCollection("affixqa","List or issue protected, mechanically qualified affix fixtures.") {};
        var qa159=new AbstractCommandCollection("qa159","159 functional affixes on 17 distinct legal QA carriers.") {};
        qa159.requirePermission(AUTHOR_PERMISSION);
        qa159.addSubCommand(new AbstractAsyncCommand("list","List the compact pack, carriers, footprints and Sentinel eligibility.") {
            @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext c){
                affixQa.fixtures().stream().filter(f->f.group().equals("qa159"))
                        .forEach(f->c.sendMessage(Message.raw(Qa159Pack.listing(f,catalog))));
                c.sendMessage(Message.raw("17 fixtures / 159 unique functional affixes; WA-155 excluded. Normal requirements apply. QA affixes function at runtime."));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
        affixQaCommands.addSubCommand(qa159);
        affixQaCommands.addSubCommand(new AbstractAsyncCommand("list","List affix QA fixtures and their exact affix IDs.") {
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext c){
                for(var row:affixQa.fixtures()) {
                    var preview=affixQa.preview(row);
                    var base=catalog.base(row.itemBaseId());
                    String family=base.category()==GearCatalog.Category.ARMOR?base.slot().name():base.family();
                    var rolls=preview.affixes().stream().map(roll->roll.familyId()+" "+roll.name()+"="+roll.value()
                            +(roll.selector()==null?"":" selector="+roll.selector())).toList();
                    c.sendMessage(Message.raw(row.fixtureId()+" "+row.itemBaseId()+" "+family+" "+row.group()+" "+row.rarity()
                            +" "+rolls+" "+(affixQa.spawnable(row)?"SPAWNABLE":"PENDING_ADAPTER")));
                }
                c.sendMessage(Message.raw("A/B catalog: 160 affixed and 160 controls. Group spawn issues at most 8 per call; repeat to resume."));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
        affixQaCommands.addSubCommand(new AbstractPlayerCommand("spawn","Issue one fixture ID, armor, weapons, support, summons, or all.") {
            final RequiredArg<String> fixtureArg=withRequiredArg("fixtureId","fixture ID, armor, weapons, support, summons, or all",ArgTypes.STRING);
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                String target=c.get(fixtureArg);
                var rows=affixQa.fixtures().stream().filter(row->target.equals("all")||target.equals(row.group())||target.equals(row.fixtureId()))
                        .filter(affixQa::spawnable).toList();
                if(rows.isEmpty()){
                    boolean known=Set.of("all","armor","weapons","support","summons").contains(target)
                            ||affixQa.fixtures().stream().anyMatch(row->row.fixtureId().equals(target));
                    c.sendMessage(Message.raw((known?"No currently spawnable affix QA fixture for: ":"Unknown affix QA fixture/group: ")+target));
                    return;
                }
                String cursorKey=player.getUuid()+"/"+target;
                issueAffixFixtures(rows,affixQaCursor.getOrDefault(cursorKey,0),8,cursorKey,affixQaCursor,affixQa,equipment,store,actor,player,world);
            }
        });
        addSubCommand(affixQaCommands);
        for(String token:GearQaRequest.typeTokens())addSubCommand(new AbstractPlayerCommand(token,
                "Generate protected gear: /rpg gear "+token+" <rarity> <era> [itemLevel|max|seed:<seed>] [seed].") {
            final RequiredArg<String> rarityArg=withRequiredArg("quality","normal, magic or rare",ArgTypes.STRING);
            final RequiredArg<String> eraArg=withRequiredArg("era","normal, nightmare or hell",ArgTypes.STRING);
            final OptionalArg<String> optionArg=withOptionalArg("itemLevelOrMaxOrSeed","1–99, max, or seed:<seed>",ArgTypes.STRING);
            final OptionalArg<String> seedArg=withOptionalArg("seed","Reproducible QA seed (requires itemLevel or max)",ArgTypes.STRING);
            { requirePermission(AUTHOR_PERMISSION); }
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world) {
                try {
                    var request=GearQaRequest.parse(token,c.get(rarityArg),c.get(eraArg),
                            c.provided(optionArg)?c.get(optionArg):null,c.provided(seedArg)?c.get(seedArg):null);
                    var gear=generator.generateQa(request);
                    var view=equipment.view(actor,store);
                    var stack=GearNativeItems.create(gear,view.level(),view.baseline());
                    String result="Generated "+gear.rarity().label+" "+gear.sourceEra()+" "+token+" gear: "
                            +gear.displayName()+". Base: "+gear.baseName()+". Item Level: "+gear.itemLevel()
                            +". Affixes: "+gear.affixes().size()+". Instance: "+gear.identity()
                            +". QA provenance: true. Seed: "+request.seed()+".";
                    deliver(stack,store,actor,player,world,message->player.sendMessage(Message.raw(
                            message.startsWith("Generated gear saved") || message.startsWith("Delivered")
                                    ? result+" "+message : message)));
                }catch(RuntimeException failure){c.sendMessage(Message.raw("Gear QA: "+failure.getMessage()));}
            }
        });
        addSubCommand(new AbstractPlayerCommand("odds","Inspect the production loot calculation for a supplied source.") {
            final RequiredArg<String> eraArg=withRequiredArg("era","normal, nightmare or hell",ArgTypes.STRING);
            final RequiredArg<Integer> levelArg=withRequiredArg("sourceLevel","Enemy item level 1–99",ArgTypes.INTEGER);
            final RequiredArg<String> rankArg=withRequiredArg("rank","common, specialist, elite, miniboss or boss",ArgTypes.STRING);
            final OptionalArg<String> seedArg=withOptionalArg("seed","Optional deterministic diagnostic seed",ArgTypes.STRING);
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                try {
                    var era=DifficultyId.valueOf(c.get(eraArg).toUpperCase(Locale.ROOT));
                    int level=c.get(levelArg);if(level<1||level>99)throw new IllegalArgumentException("Source level must be 1–99");
                    var rank=ProgressionMath.Rank.valueOf(c.get(rankArg).toUpperCase(Locale.ROOT));
                    String seed=c.provided(seedArg)?c.get(seedArg):"gear-odds/"+UUID.randomUUID();
                    var view=equipment.view(actor,store);int rawLuck=view.validity().permanentAttributes().getOrDefault(RpgAttribute.LUCK,10);
                    double effective=GearMagicFind.effectiveLuck(rawLuck),baseMf=GearMagicFind.snapshot(rawLuck,0),mf=equipment.magicFind(actor,store);
                    var weights=GearMagicFind.weights(era,level,rank,mf);
                    var source=new EnemyRewardRegistry.LootSource(seed,UUID.randomUUID(),UUID.randomUUID(),era,level,
                            "operator-diagnostic",rank,ProgressionMath.Rarity.ORDINARY,GearQualityProfile.CURRENT.revision());
                    var decision=GearLootProfiles.CURRENT.decide(source.role(),rank,seed);
                    c.sendMessage(Message.raw("Gear odds source="+era+"/"+level+"/"+rank+" rawLuck="+rawLuck
                            +" effectiveLuck="+effective+" gearMF="+(mf-baseMf)+" totalMF="+mf
                            +" profile="+decision.profileId()+" guaranteedPicks="+decision.guaranteedPicks()
                            +" optionalPickChances="+decision.optionalPickChances()
                            +" maxEquipment="+decision.maxEquipment()));
                    c.sendMessage(Message.raw("Eligible="+weights.base().entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey().label).toList()
                            +" base="+weights.base()+" rank="+weights.rank()+" mfFactor="+weights.magicFind()
                            +" probability="+weights.normalized()));
                    int generated=0;
                    for(var pick:decision.rolls()){
                        String pickDetails="pick="+pick.index()+" childReceiptId="+pick.childEventId()
                                +(pick.guaranteed()?" guaranteed=true":" optionalChance="+pick.optionalChance()
                                +" optionalRoll="+pick.optionalRoll());
                        if(!pick.opportunity()){
                            c.sendMessage(Message.raw(pickDetails+" result=NO_DROP"));
                            continue;
                        }
                        var pickSource=new EnemyRewardRegistry.LootSource(pick.childEventId(),source.world(),
                                source.enemy(),era,level,source.role(),rank,source.rarity(),source.profileRevision());
                        var result=generator.generateGuaranteed(pickSource,mf,pick.seed(),Set.of());generated++;
                        c.sendMessage(Message.raw(pickDetails
                                +" qualityRoll="+new GearRandom(pick.seed()).stream("rarity").nextDouble()
                                +" qualityWeights="+result.rarityDistribution()
                                +" result="+result.item().quality()+" identity="+result.item().identity()
                                +" delivery=DIAGNOSTIC_ONLY"));
                    }
                    c.sendMessage(Message.raw("seed="+seed+" opportunities="+decision.succeeded()
                            +" itemsGenerated="+generated+" worldSpawn=NOT_ATTEMPTED"));
                }catch(RuntimeException failure){c.sendMessage(Message.raw("Gear odds: "+failure.getMessage()));}
            }
        });
        addSubCommand(new AbstractAsyncCommand("types","List supported generated gear type tokens.") {
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext c){
                c.sendMessage(Message.raw("Gear types: "+String.join(", ",GearQaRequest.typeTokens())));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
        addSubCommand(new AbstractAsyncCommand("rarities","List supported generated gear rarities.") {
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext c){
                c.sendMessage(Message.raw("Random gear qualities: normal, magic, rare. Authored set and unique gear are not available. Eras: normal, nightmare, hell."));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
        addSubCommand(new AbstractPlayerCommand("ring", "Create one Copper Ring for two-slot equipment QA.") {
            { requirePermission(AUTHOR_PERMISSION); }
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world) {
                try {
                    SpatialInventoryTransferCoordinator.grantRingQa(store, actor, player, world,
                            message -> player.sendMessage(Message.raw(message)));
                } catch (RuntimeException failure) {
                    c.sendMessage(Message.raw("Ring QA: " + failure.getMessage()));
                }
            }
        });
        if(!System.getProperty("rpg.gear.auditRoot","").isBlank()) addSubCommand(new AbstractAsyncCommand("audit","Audit the installed gear registry and native metadata codecs.") {
            { requirePermission(AUTHOR_PERMISSION); }
            @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext c) {
                try { c.sendMessage(Message.raw(GearNativeAudit.run())); }
                catch(Exception error) { c.sendMessage(Message.raw("RPG_GEAR_NATIVE_AUDIT FAIL "+error)); }
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
        addSubCommand(new AbstractPlayerCommand("spawn","Create a QA base with an explicit intrinsic roll and rarity.") {
            final RequiredArg<String> baseArg=withRequiredArg("base","Stable gm.* base ID",ArgTypes.STRING);
            final RequiredArg<Integer> rollArg=withRequiredArg("roll","Intrinsic thousandths, 900 through 1000",ArgTypes.INTEGER);
            final RequiredArg<String> rarityArg=withRequiredArg("quality","NORMAL, MAGIC or RARE",ArgTypes.STRING);
            { requirePermission(AUTHOR_PERMISSION); }
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world) {
                try {
                    var base=catalog.base(c.get(baseArg));var binding=bindings.require(base.id());
                    if(!binding.mapped()) throw new IllegalArgumentException("BLOCKED_WITH_REASON: "+binding.reason());
                    int level=base.sourceWindow()==null?base.requiredLevel():base.sourceWindow().getLast();
                    var quality=GearRarity.valueOf(c.get(rarityArg).toUpperCase(Locale.ROOT));
                    if(quality!=GearRarity.NORMAL&&quality!=GearRarity.MAGIC&&quality!=GearRarity.RARE)throw new IllegalArgumentException("Only normal, magic and rare QA qualities may be generated");
                    var gear=GearQaFixtures.create(catalog,base,UUID.randomUUID(),level,c.get(rollArg),quality);
                    var view=equipment.view(actor,store);
                    var stack=GearNativeItems.create(gear,view.level(),view.baseline());
                    String result="QA gear created: "+gear.baseName()+" / "+gear.identity()
                            +"; intrinsic "+gear.intrinsicThousandths()+"/1000.";
                    deliver(stack,store,actor,player,world,message->player.sendMessage(Message.raw(
                            message.startsWith("Generated gear saved") || message.startsWith("Delivered")
                                    ? result+" "+message : message)));
                } catch(RuntimeException failure) { c.sendMessage(Message.raw("Gear QA: "+failure.getMessage())); }
            }
        });
        addSubCommand(new AbstractPlayerCommand("inspect","Inspect the held gear's frozen identity and every requirement.") {
            { setPermissionGroup(GameMode.Adventure); }
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world) {
                try {
                    var gear=GearNativeItems.read(InventoryComponent.getItemInHand(store,actor));
                    if(gear==null) { c.sendMessage(Message.raw("Held item is not managed Gear Master equipment.")); return; }
                    var view=equipment.view(actor,store);
                    for(var line:GearTooltip.describe(gear,view.level(),HytaleGearEquipment.requirementAttributes(view,gear.identity()))) {
                        var message=Message.raw(line.text());if(line.color()!=null) message.color(line.color());c.sendMessage(message);
                    }
                } catch(RuntimeException failure) { c.sendMessage(Message.raw("Gear inspection failed: "+failure.getMessage())); }
            }
        });
        addSubCommand(new AbstractPlayerCommand("inspect-debug","Inspect frozen gear IDs, mechanics and resolved tooltip templates.") {
            { requirePermission(AUTHOR_PERMISSION); }
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world) {
                try {
                    var gear=GearNativeItems.read(InventoryComponent.getItemInHand(store,actor));
                    if(gear==null){c.sendMessage(Message.raw("Held item is not managed Gear Master equipment."));return;}
                    c.sendMessage(Message.raw("item="+gear.identity()+" base="+gear.baseId()+" era="+gear.sourceEra()
                            +" intrinsic="+gear.intrinsicThousandths()+"/1000 requirements="+gear.requirements()
                            +" qaOnly="+gear.qaOnly()+" rng="+gear.rngVersion()));
                    for(var roll:gear.affixes()){
                        var definition=catalog.affix(roll.familyId());
                        c.sendMessage(Message.raw("affix="+roll.familyId()+" tier="+roll.tier()+" roll="+roll.value()
                                +" operator="+definition.operator()+" scope="+definition.scopeContract()
                                +" template=\""+definition.playerTooltipTemplate()+"\" resolved=\""+GearAffixDisplay.format(roll)+"\""));
                    }
                }catch(RuntimeException failure){c.sendMessage(Message.raw("Gear debug inspection failed: "+failure.getMessage()));}
            }
        });
    }

    private static void issueAffixFixtures(List<GearAffixQaSuite.Fixture> rows,int index,int remaining,String cursorKey,Map<String,Integer> cursors,GearAffixQaSuite suite,
                                           HytaleGearEquipment equipment,Store<EntityStore> store,Ref<EntityStore> actor,
                                           PlayerRef player,World world){
        if(index>=rows.size()){cursors.remove(cursorKey);player.sendMessage(Message.raw("Affix QA issuance complete: "+rows.size()+" fixture(s)."));return;}
        if(remaining<=0){player.sendMessage(Message.raw("Affix QA paused at "+index+"/"+rows.size()+"; repeat the same spawn command to resume."));return;}
        var row=rows.get(index);
        try{
            var gear=suite.create(row.fixtureId(),player.getUuid());var view=equipment.view(actor,store);
            var stack=GearNativeItems.create(gear,view.level(),view.baseline());
            deliver(stack,store,actor,player,world,message->{
                if(message.startsWith("Generated gear saved")||message.startsWith("Delivered")){
                    player.sendMessage(Message.raw("Affix QA "+row.fixtureId()+" saved: "+gear.identity()));
                    cursors.put(cursorKey,index+1);
                    issueAffixFixtures(rows,index+1,remaining-1,cursorKey,cursors,suite,equipment,store,actor,player,world);
                }else player.sendMessage(Message.raw("Affix QA stopped at "+row.fixtureId()+": "+message));
            });
        }catch(RuntimeException failure){player.sendMessage(Message.raw("Affix QA stopped at "+row.fixtureId()+": "+failure.getMessage()));}
    }

    private static void deliver(com.hypixel.hytale.server.core.inventory.ItemStack stack,
                                Store<EntityStore> store, Ref<EntityStore> actor, PlayerRef player,
                                World world, java.util.function.Consumer<String> reply) {
        var type = SpatialBagComponent.getComponentType();
        var bag = type == null ? null : store.getComponent(actor, type);
        if (bag != null && bag.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE) {
            SpatialInventoryTransferCoordinator.grantGeneratedQaItem(stack,store,actor,player,world,reply);
            return;
        }
        var inventory=InventoryComponent.getCombined(store,actor,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if(inventory==null||!inventory.canAddItemStack(stack))
            throw new IllegalArgumentException("Inventory full; QA item was not delivered.");
        if(!inventory.addItemStack(stack,true,false,true).succeeded())
            throw new IllegalStateException("Inventory insertion failed; QA item was not delivered.");
        reply.accept("Delivered to native inventory.");
    }
}
