package com.inigmasgames.hytalerpg.gear;

import com.google.gson.GsonBuilder;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.*;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.bson.BsonDocument;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installed registry/codec evidence. Explicitly not a connected-player acceptance claim. */
public final class GearNativeAudit {
    private GearNativeAudit() {}
    /** Real PlayerRef and runtime entry points, with persistence deliberately not hydrated. */
    @SuppressWarnings("unchecked")
    private static Map<String,Object> coldPlayerJoinChecks() {
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();
        var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
        var repository=new com.inigmasgames.hytalerpg.progress.RpgPlayerStateRepository(){
            public LoadResult load(UUID player){throw new AssertionError("World thread attempted player hydration");}
            public void save(com.inigmasgames.hytalerpg.progress.RpgPlayerState state){throw new AssertionError("Unexpected player write");}
        };
        try(var players=new com.inigmasgames.hytalerpg.progress.RpgLoadoutService(catalog,repository,graph,
                new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),
                new com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy(true),ignored->{})) {
            players.enableNonblockingReads();var equipment=new HytaleGearEquipment(players);
            var player=new com.hypixel.hytale.server.core.universe.PlayerRef(null,UUID.randomUUID(),"cold-join-audit","en-US",null,null);
            var actor=new com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>(null,1);
            var accessor=(com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>)java.lang.reflect.Proxy.newProxyInstance(
                GearNativeAudit.class.getClassLoader(),new Class<?>[]{com.hypixel.hytale.component.ComponentAccessor.class},(proxy,method,args)->{
                    if(method.getName().equals("getComponent")&&args[1]==com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())return player;
                    throw new AssertionError("Unready player reached native equipment: "+method.getName());
                });
            for(int i=0;i<1000;i++) {
                equipment.tickEquipment(actor,accessor);equipment.project(actor,accessor);
                if(!equipment.effects(actor,accessor).equals(GearAffixRuntime.Effects.NONE)||equipment.magicFind(actor,accessor)!=0)
                    throw new AssertionError("Unready player received gear bonuses");
            }
            return Map.of("result","PASS","iterations",1000,"entryPoints",List.of("tickEquipment","projectBeforeArmor","effects","magicFind"),"connectedPlayer",false);
        }
    }
    public static String run() throws java.io.IOException {
        String configured=System.getProperty("rpg.gear.auditRoot","");
        if(configured.isBlank()) throw new IllegalStateException("Gear audit requires an explicit isolated audit root");
        var root=Path.of(configured).toAbsolutePath().normalize();
        if(!root.equals(Path.of("").toAbsolutePath().normalize()) || !root.toString().contains("gear-stage-1-smoke"))
            throw new IllegalStateException("Gear audit is restricted to its isolated save");
        var catalog=GearCatalog.load();var bindings=new GearBindings();
        var evidence=new LinkedHashMap<String,Object>();var nativeRows=new ArrayList<Map<String,Object>>();
        int codecChecks=0,armorChecks=0;
        for(var binding:bindings.all()) {
            var row=new LinkedHashMap<String,Object>();row.put("baseId",binding.baseId());row.put("binding",binding.disposition());
            if(!binding.mapped()) { row.put("reason",binding.reason());nativeRows.add(row);continue; }
            var item=Item.getAssetMap().getAsset(binding.managedItemId());
            if(item==null) throw new IllegalStateException("Missing managed native asset "+binding.managedItemId());
            for(var rarity:GearRarity.values()) {
                var carrier=Item.getAssetMap().getAsset(binding.carrier(rarity));
                var quality=ItemQuality.getAssetMap().getAsset(rarity.qualityAsset());
                if(carrier==null || quality==null)
                    throw new IllegalStateException("Missing carrier or quality: "+binding.carrier(rarity));
            }
            var base=catalog.base(binding.baseId());
            if(base.category()==GearCatalog.Category.ARMOR) {
                var armor=item.getArmor();
                if(armor==null || armor.getBaseDamageResistance()!=0
                        || armor.getDamageResistanceValues()!=null&&!armor.getDamageResistanceValues().isEmpty()
                        || armor.getStatModifiers()!=null&&!armor.getStatModifiers().isEmpty()
                        || armor.getDamageEnhancementValues()!=null&&!armor.getDamageEnhancementValues().isEmpty())
                    throw new IllegalStateException("Inherited native armor fields escaped neutralization "+base.id());
                armorChecks++;
            }
            if(base.category()==GearCatalog.Category.HELD) {
                if(base.family().startsWith("gm.shortbow_") || base.family().startsWith("gm.crossbow_"))
                    row.put("nativeCoefficientParity",GearRouteParity.verify(Item.getAssetMap().getAsset(binding.nativeItemId()),item));
                var routes=new TreeMap<String,Object>();
                for(var entry:item.getInteractions().entrySet()) {
                    var context=InteractionContext.withoutEntity();context.setInteractionVarsGetter(ignored->item.getInteractionVars());
                    var route=GearInteractionAudit.inspect(entry.getKey(),context,RootInteraction.getAssetMap().getAsset(entry.getValue()));
                    if(!route.supported()) throw new IllegalStateException("Mapped route requires an adapter: "+base.id()+" "+route.blockers());
                    routes.put(entry.getKey().name(),route);
                }
                row.put("nativeRoutes",routes);
            }
            for(int scalar:new int[]{900,950,1000}) {
                var identity=UUID.nameUUIDFromBytes((base.id()+"/"+scalar).getBytes(StandardCharsets.UTF_8));
                int level=base.sourceWindow()==null?base.requiredLevel():base.sourceWindow().getFirst();
                var instance=GearInstance.authoredQa(base,identity,level,scalar,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
                var stack=GearNativeItems.create(instance,99,Map.of(RpgAttribute.STR,999,RpgAttribute.DEX,999,RpgAttribute.INT,999,RpgAttribute.WIS,999,RpgAttribute.LUCK,999));
                String encoded=ItemStack.CODEC.encode(stack,new ExtraInfo()).asDocument().toJson();
                var file=root.resolve("gear-codec-fixtures").resolve(identity+".json");Files.createDirectories(file.getParent());
                if(Files.exists(file)) {
                    var persisted=ItemStack.CODEC.decode(BsonDocument.parse(Files.readString(file)),new ExtraInfo());
                    if(!instance.equals(GearNativeItems.read(persisted))) throw new IllegalStateException("Gear changed across server restart "+identity);
                } else Files.writeString(file,encoded,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                var decoded=ItemStack.CODEC.decode(BsonDocument.parse(encoded),new ExtraInfo());
                if(!instance.equals(GearNativeItems.read(decoded))) throw new IllegalStateException("Native ItemStack codec changed gear");
                codecChecks++;
            }
            row.put("codec","PASS");nativeRows.add(row);
        }
        var rarityBase=catalog.base(bindings.all().stream().filter(b->b.mapped()
                && catalog.base(b.baseId()).category()==GearCatalog.Category.ARMOR
                && catalog.base(b.baseId()).era()==com.inigmasgames.hytalerpg.difficulty.DifficultyId.HELL)
                .sorted(Comparator.comparing(GearBindings.Binding::baseId)).findFirst().orElseThrow().baseId());
        for(var rarity:GearRarity.values()) {
            var identity=UUID.nameUUIDFromBytes((rarityBase.id()+"/rarity-v3/"+rarity).getBytes(StandardCharsets.UTF_8));
            var instance=GearQaFixtures.create(catalog,rarityBase,identity,rarityBase.sourceWindow().getLast(),950,rarity);
            var stack=GearNativeItems.create(instance,99,Map.of());
            if(stack.getQualityIndex()!=ItemQuality.getAssetMap().getIndex(rarity.qualityAsset()))
                throw new IllegalStateException("Wrong stack quality presentation "+rarity);
            if(!stack.equals(GearNativeItems.present(stack,instance,99,Map.of())))
                throw new IllegalStateException("Unstable gear presentation would replace a held stack each tick: "+rarity);
            var encoded=ItemStack.CODEC.encode(stack,new ExtraInfo()).asDocument().toJson();
            var file=root.resolve("gear-codec-fixtures").resolve(identity+".json");
            if(Files.exists(file)) {
                if(!instance.equals(GearNativeItems.read(ItemStack.CODEC.decode(BsonDocument.parse(Files.readString(file)),new ExtraInfo()))))
                    throw new IllegalStateException("Rarity fixture changed across restart");
            } else Files.writeString(file,encoded,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var decoded=ItemStack.CODEC.decode(BsonDocument.parse(encoded),new ExtraInfo());
            if(!instance.equals(GearNativeItems.read(decoded)))
                throw new IllegalStateException("Rarity codec lost frozen fields");
            var display=decoded.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
            if(display==null||!instance.displayName().equals(display.getName().getRawText()))
                throw new IllegalStateException("Generated gear title changed across native codec");
            String body=display.getDescription().getChildren().stream().map(com.hypixel.hytale.server.core.Message::getRawText)
                    .filter(Objects::nonNull).collect(java.util.stream.Collectors.joining());
            for(var affix:instance.affixes())if(!body.contains(GearAffixDisplay.format(affix)))
                throw new IllegalStateException("Frozen affix roll missing from native tooltip: "+affix.familyId());
            if(body.contains("Intrinsic base roll")||body.contains("Source era")||body.contains("Base variant:"))
                throw new IllegalStateException("Debug detail leaked into native tooltip");
            codecChecks++;
        }
        var qualities=new TreeMap<String,Object>();
        for(var rarity:GearRarity.values()) {
            var q=ItemQuality.getAssetMap().getAsset(rarity.qualityAsset());
            if(q==null) throw new IllegalStateException("Missing managed rarity "+rarity);
            var color=q.getTextColor();String actual=String.format(Locale.ROOT,"#%02x%02x%02x",color.red&255,color.green&255,color.blue&255);
            if(!actual.equals(rarity.color)) throw new IllegalStateException("Wrong native rarity color "+rarity);
            qualities.put(rarity.name(),Map.of("color",actual,"qualityAsset",rarity.qualityAsset()));
        }
        var physical=DamageCause.getAssetMap().getAsset("Physical");
        var projectile=DamageCause.getAssetMap().getAsset("Projectile");
        if(physical.getInherits()!=null || projectile.getInherits()!=null) throw new IllegalStateException("Native armor cause hierarchy changed; re-audit one-pass mapping");
        for(int i=1;i<=600;i++) {
            var effect=EntityEffect.getAssetMap().getAsset("RPG_Gear_Protection_"+i);
            if(effect==null || effect.getDamageResistanceValues().size()!=2 || !effect.getDamageResistanceValues().containsKey(physical)
                    || !effect.getDamageResistanceValues().containsKey(projectile))
                throw new IllegalStateException("Managed protection must cover the two independent native roots");
        }
        evidence.put("result","PASS");evidence.put("nativeRegistryCount",Item.getAssetMap().getAssetMap().size());
        evidence.put("nativeCodecChecks",codecChecks);evidence.put("neutralArmorChecks",armorChecks);
        evidence.put("qualities",qualities);evidence.put("bindings",nativeRows);evidence.put("connectedProof",false);
        evidence.put("coldPlayerJoin",coldPlayerJoinChecks());
        evidence.put("nativeCapacity",capacityChecks());
        evidence.put("nativeArmor",armorChecks());
        evidence.put("affixRuntime",affixChecks(catalog));
        Files.writeString(root.resolve("gear-native-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        return "RPG_GEAR_NATIVE_AUDIT PASS codecChecks="+codecChecks+" neutralArmor="+armorChecks+" connectedProof=false";
    }
    private static Map<String,Object> affixChecks(GearCatalog catalog){
        var gear=GearQaFixtures.create(catalog,catalog.base("gm.sword_mithril.h"),UUID.randomUUID(),99,950,GearRarity.LEGENDARY);
        var stack=GearNativeItems.create(gear,99,Map.of());
        long started=System.nanoTime();
        for(int i=0;i<10000;i++)if(!GearNativeItems.read(stack).equals(gear))throw new IllegalStateException("Frozen gear read changed");
        double micros=(System.nanoTime()-started)/1000d/10000;
        // Cached decoding must not cache a custody decision.
        var production=new GearInstance(gear.schemaVersion(),gear.identity(),gear.definitionRevision(),gear.baseId(),gear.baseName(),gear.category(),gear.sourceEra(),gear.itemLevel(),gear.rarity(),gear.intrinsicThousandths(),gear.intrinsicStats(),gear.requirements(),gear.affixes(),GearRandom.VERSION,false);
        var unknown=GearNativeItems.create(production,99,Map.of());boolean denied=false;
        try{GearNativeItems.read(unknown);}catch(IllegalArgumentException expected){denied=true;}
        if(!denied)throw new IllegalStateException("Unreceipted production instance became usable");
        return Map.of("implementedOperators",GearAffixRuntime.ENABLED.size(),"repeatedNativeReads",10000,"meanReadMicros",micros,"unknownCustodyDenied",true,"connectedProof",false);
    }
    private static Map<String,Object> capacityChecks() {
        var stats=new com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap();stats.update();
        var balance=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical();
        var service=new com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService(balance,new com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService(balance));
        var derived=service.derive(Map.of(RpgAttribute.STR,10,RpgAttribute.DEX,10,RpgAttribute.INT,10,RpgAttribute.WIS,10,RpgAttribute.LUCK,10));
        var adapter=new com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter();adapter.apply(stats,derived);
        int hp=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();
        int mana=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getMana();
        float hpMaximum=stats.get(hp).getMax(),manaMaximum=stats.get(mana).getMax();
        stats.setStatValue(hp,40);stats.setStatValue(mana,10);
        for(int pass=0;pass<3;pass++) {
            com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter.applyGearCapacity(stats,50,30,20);
            near(hpMaximum+50,stats.get(hp).getMax(),"gear HP capacity");near(40,stats.get(hp).get(),"gear must not heal");
            near(manaMaximum+30,stats.get(mana).getMax(),"gear Mana capacity");near(10,stats.get(mana).get(),"gear must not refill Mana");
        }
        com.inigmasgames.hytalerpg.combat.hytale.NativeManaReservationProjection.project(stats,20);
        adapter.apply(stats,derived);
        near(manaMaximum+10,stats.get(mana).getMax(),"derived refresh preserves gear and reservation");
        com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter.applyGearCapacity(stats,0,0,0);
        near(hpMaximum,stats.get(hp).getMax(),"gear removed");near(40,stats.get(hp).get(),"removal current HP");
        near(manaMaximum-20,stats.get(mana).getMax(),"removal preserves reservation");near(10,stats.get(mana).get(),"removal current Mana");
        return Map.of("result","PASS","capacityReapplyChecks",3,"currentHealth",40,"currentMana",10,"reservation",20);
    }
    private static Map<String,Object> armorChecks() {
        var world=com.hypixel.hytale.server.core.universe.Universe.get().getDefaultWorld();
        var effects=new com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent();
        String id="RPG_Gear_Protection_200";int index=EntityEffect.getAssetMap().getIndex(id);
        effects.getActiveEffects().put(index,new com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect(id,index,2,false,null,false));
        var armor=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)4);
        var modifiers=com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction.getResistanceModifiers(world,armor,false,effects);
        for(String cause:List.of("Physical","Projectile")) {
            var value=modifiers.get(DamageCause.getAssetMap().getAsset(cause));
            if(value==null || value.inheritedParentId!=null) throw new IllegalStateException("Unexpected managed armor cause path "+cause);
            near(.20,value.multiplierModifier,"native armor contribution "+cause);near(0,value.flatModifier,"native flat reduction");
        }
        return Map.of("result","PASS","owner","DamageSystems.ArmorDamageReduction","physical",.20,"projectile",.20,"independentNativeRoots",true);
    }
    private static void near(double expected,double actual,String boundary) {
        if(Math.abs(expected-actual)>.001) throw new IllegalStateException(boundary+": expected "+expected+", actual "+actual);
    }
}
