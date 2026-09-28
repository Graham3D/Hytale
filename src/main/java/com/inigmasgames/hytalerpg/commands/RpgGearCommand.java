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
    public RpgGearCommand(HytaleGearEquipment equipment) {
        super("gear","Inspect gear and create protected generated QA equipment.");
        var catalog=GearCatalog.load();var bindings=new GearBindings();
        var generator=new GearDropGenerator(catalog,bindings,GearAffixRuntime.ENABLED);
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
                    var result=generator.generate(source,mf,seed,Set.of());var random=new GearRandom(seed);
                    c.sendMessage(Message.raw("Gear odds source="+era+"/"+level+"/"+rank+" rawLuck="+rawLuck
                            +" effectiveLuck="+effective+" gearMF="+(mf-baseMf)+" totalMF="+mf
                            +" equipmentChance="+GearMagicFind.opportunity(rank)));
                    c.sendMessage(Message.raw("Eligible="+weights.base().entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey().label).toList()
                            +" base="+weights.base()+" rank="+weights.rank()+" mfFactor="+weights.magicFind()
                            +" probability="+weights.normalized()));
                    c.sendMessage(Message.raw("seed="+seed+" opportunityRoll="+random.stream("opportunity").nextDouble()
                            +" qualityRoll="+random.stream("rarity").nextDouble()+" result="
                            +(result.item()==null?"NO_EQUIPMENT":result.item().quality()+" affixes="+result.item().affixes().size()
                                    +" identity="+result.item().identity())));
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
