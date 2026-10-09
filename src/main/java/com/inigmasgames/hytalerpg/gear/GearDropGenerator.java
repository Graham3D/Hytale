package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source-driven candidate generation. Capability selection is explicit; no GATED family enters implicitly. */
public final class GearDropGenerator {
    private final GearCatalog catalog;private final GearBindings bindings;private final Set<String> capabilities;
    private volatile String fingerprint;
    public String revision(){
        if(fingerprint!=null)return fingerprint;
        var definition=new StringBuilder(GearRandom.VERSION).append(new TreeSet<>(capabilities));
        for(String file:List.of("bases-v1.json","affixes-v1.json","native-bindings-v1.json","quality-profile-v1.json"))try(var input=getClass().getResourceAsStream("/rpg/gear/"+file)){
            byte[] bytes=Objects.requireNonNull(input).readAllBytes();definition.append(bytes.length).append(new String(bytes,StandardCharsets.UTF_8));
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        return fingerprint=com.inigmasgames.hytalerpg.progress.RewardIntent.digest(definition.toString());
    }
    /** Compatibility name; every generation caller now uses the implemented capability set. */
    public static final Set<String> STAGE_TWO_CANDIDATES=GearAffixRuntime.ENABLED;
    public GearDropGenerator(GearCatalog catalog,GearBindings bindings,Set<String> capabilities){this.catalog=catalog;this.bindings=bindings;this.capabilities=Set.copyOf(capabilities);
        for(String id:capabilities){catalog.affix(id);if(!GearAffixRuntime.ENABLED.contains(id))throw new IllegalArgumentException("Unverified generation adapter "+id);}}
    public record Result(GearInstance item,Map<String,Double> categoryDistribution,Map<GearRarity,Double> rarityDistribution,String reason) {
        public Result{categoryDistribution=Map.copyOf(categoryDistribution);rarityDistribution=Map.copyOf(rarityDistribution);Objects.requireNonNull(reason);}
    }
    public boolean opportunity(EnemyRewardRegistry.LootSource source,String seed){return new GearRandom(seed).stream("opportunity").nextDouble()<GearMagicFind.opportunity(source.rank());}
    public List<GearCatalog.Base> eligibleBases(EnemyRewardRegistry.LootSource source,Set<String> allowedFamilies){
        return catalog.eligible(source.difficulty(),source.sourceCombatLevel()).stream()
                .filter(b->bindings.require(b.id()).mapped()&&b.category()!=GearCatalog.Category.TOOL)
                .filter(b->allowedFamilies.isEmpty()||allowedFamilies.contains(b.family()))
                .sorted(Comparator.comparing(GearCatalog.Base::id)).toList();
    }
    public Result generate(EnemyRewardRegistry.LootSource source,double mf,String seed,Set<String> allowedFamilies){
        var random=new GearRandom(seed);
        if(!opportunity(source,seed))return new Result(null,Map.of(),Map.of(),"NO_DROP");
        return generateGuaranteed(source,mf,seed,allowedFamilies);
    }
    /** Called only after a durable loot-profile pick has already passed NoDrop. */
    public Result generateGuaranteed(EnemyRewardRegistry.LootSource source,double mf,String seed,Set<String> allowedFamilies){
        var random=new GearRandom(seed);
        var bases=eligibleBases(source,allowedFamilies);
        if(bases.isEmpty())throw new IllegalStateException("No legal same-era base for source "+source.profileRevision()+"/"+source.sourceCombatLevel());
        var categories=new LinkedHashMap<String,Double>();for(var b:bases)categories.putIfAbsent(category(b),category(b).equals("ARMOR")?40.0:category(b).equals("SHIELD")?5.0:55.0);
        double total=categories.values().stream().mapToDouble(Double::doubleValue).sum();categories.replaceAll((k,v)->v/total);
        String category=random.weighted(categories,"category");var pool=bases.stream().filter(b->category(b).equals(category)).toList();
        if(category.equals("ARMOR")){
            var slots=new TreeMap<GearCatalog.Slot,Double>();pool.forEach(b->slots.put(b.slot(),1.0));var slot=random.weighted(slots,"armor-slot");pool=pool.stream().filter(b->b.slot()==slot).toList();
            var roles=new TreeMap<com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute,Double>();pool.forEach(b->roles.put(b.primaryAttribute(),switch(b.primaryAttribute()){case STR->35.;case DEX->25.;case INT->20.;case WIS->15.;case LUCK->5.;}));
            var role=random.weighted(roles,"armor-role");pool=pool.stream().filter(b->b.primaryAttribute()==role).toList();
        }else{
            var actions=new TreeMap<String,Double>();pool.forEach(b->actions.put(action(b),1.));var action=random.weighted(actions,"weapon-category");pool=pool.stream().filter(b->action(b).equals(action)).toList();
        }
        var steps=pool.stream().map(b->b.sourceWindow().getFirst()).distinct().sorted(Comparator.reverseOrder()).limit(2).toList();
        var stepWeights=new LinkedHashMap<Integer,Double>();stepWeights.put(steps.getFirst(),75.);if(steps.size()>1)stepWeights.put(steps.getLast(),25.);
        int step=random.weighted(stepWeights,"step");pool=pool.stream().filter(b->b.sourceWindow().getFirst()==step).toList();
        var base=pool.get(random.stream("family").nextInt(pool.size()));
        var weights=GearMagicFind.distribution(source.difficulty(),source.sourceCombatLevel(),source.rank(),mf);var rarity=random.weighted(weights,"rarity");
        var affixes=affixes(base,source.sourceCombatLevel(),rarity,random,false);
        BigDecimal ease=affixes.stream().filter(a->a.familyId().equals("WA-154")).map(a->BigDecimal.valueOf(a.value()).movePointLeft(2)).findFirst().orElse(BigDecimal.ZERO);
        UUID identity=UUID.nameUUIDFromBytes((GearRandom.VERSION+"/"+source.eventId()).getBytes(StandardCharsets.UTF_8));
        var authored=GearInstance.authoredQa(base,identity,source.sourceCombatLevel(),random.stream("intrinsic").nextInt(900,1001),rarity,affixes,ease);
        var item=new GearInstance(1,identity,authored.definitionRevision(),base.id(),base.name(),base.category(),base.era(),authored.itemLevel(),rarity,authored.intrinsicThousandths(),authored.intrinsicStats(),authored.requirements(),affixes,GearRandom.VERSION,false);
        return new Result(item,categories,weights,"GENERATED_CANDIDATE");
    }
    /** Admin entry point: use production affix, roll and requirement logic with explicit QA constraints. */
    public GearInstance generateQa(GearQaRequest request){
        Objects.requireNonNull(request);
        var random=new GearRandom("admin-qa/"+request.seed());
        var candidates=catalog.bases().stream().filter(request::matches)
                .filter(base->base.era()==request.era()&&bindings.require(base.id()).mapped())
                .filter(base->base.worldDropCandidate()&&base.sourceWindow()!=null)
                .filter(base->request.itemLevel()==null
                        ?request.rarity().eligible(base.sourceWindow().getLast(),request.era())
                        :base.eligible(request.era(),request.itemLevel())&&request.rarity().eligible(request.itemLevel(),request.era()))
                .sorted(Comparator.comparing(GearCatalog.Base::id)).toList();
        if(candidates.isEmpty())throw new IllegalArgumentException("No valid "+request.era()+"-era "+request.type()
                +" base is currently available"+(request.itemLevel()==null?"":" at item level "+request.itemLevel())+".");
        var ordered=new ArrayList<>(candidates);
        // A seeded permutation varies the authored base and can be replayed from the command output.
        var shuffle=random.stream("qa-base");for(int i=ordered.size()-1;i>0;i--)Collections.swap(ordered,i,shuffle.nextInt(i+1));
        if(request.max())ordered.sort(Comparator.comparingInt((GearCatalog.Base base)->base.sourceWindow().getFirst()).reversed());
        for(var base:ordered){
            int level=request.itemLevel()==null?base.sourceWindow().getLast():request.itemLevel();
            try{
                var affixes=affixes(base,level,request.rarity(),random,request.max());
                BigDecimal ease=affixes.stream().filter(a->a.familyId().equals("WA-154"))
                        .map(a->BigDecimal.valueOf(a.value()).movePointLeft(2)).findFirst().orElse(BigDecimal.ZERO);
                UUID identity=UUID.nameUUIDFromBytes((GearRandom.VERSION+"/admin-qa/"+request.seed()+"/"+request.type()
                        +"/"+request.rarity()+"/"+request.era()+"/"+level+"/"+request.max()).getBytes(StandardCharsets.UTF_8));
                int intrinsic=request.max()?1000:random.stream("intrinsic").nextInt(900,1001);
                var frozen=GearInstance.authoredQa(base,identity,level,intrinsic,request.rarity(),affixes,ease);
                return new GearInstance(frozen.schemaVersion(),identity,frozen.definitionRevision(),frozen.baseId(),frozen.baseName(),
                        frozen.category(),frozen.sourceEra(),frozen.itemLevel(),frozen.rarity(),frozen.intrinsicThousandths(),
                        frozen.intrinsicStats(),frozen.requirements(),frozen.affixes(),"admin-qa/"+GearRandom.VERSION+"/"+request.seed(),true);
            }catch(IllegalStateException noCompletion){
                if(!noCompletion.getMessage().startsWith("No legal affix completion "))throw noCompletion;
            }
        }
        throw new IllegalArgumentException("No legal affix completion for "+request.rarity()+" "+request.era()+" "+request.type()+".");
    }
    private static String action(GearCatalog.Base base){return base.family().substring(3).split("_")[0];}
    private static String category(GearCatalog.Base b){return b.category()==GearCatalog.Category.ARMOR?"ARMOR":action(b).equals("shield")?"SHIELD":"WEAPON";}
    private record Choice(GearCatalog.Affix affix,GearAffixTiers.Tier tier,GearRequirements.Gate gate){}
    private List<GearInstance.AffixRoll> affixes(GearCatalog.Base base,int level,GearRarity rarity,GearRandom random,boolean max){
        if(rarity==GearRarity.NORMAL)return List.of();
        if(rarity==GearRarity.COMMON||rarity==GearRarity.UNCOMMON)throw new IllegalArgumentException("Legacy quality cannot be generated");
        var options=new ArrayList<Choice>();
        for(var affix:catalog.affixes())if(capabilities.contains(affix.id())&&eligible(affix,base))for(var tier:GearAffixTiers.compile(affix))
            if(tier.minimumItemLevel()<=level&&GearAffixTiers.rarityAllows(affix,tier,rarity)
                    &&rollable(affix,base,tier))
                options.add(new Choice(affix,tier,new GearRequirements.Gate(tier.requiredLevel(),Map.of(affix.requirementAttribute(base),affix.attributeFloor(5-tier.tier(),tier.minimumItemLevel())))));
        options.sort(Comparator.comparing((Choice c)->c.affix().id()).thenComparingInt(c->c.tier().tier()));
        var selected=new ArrayList<Choice>();var baseGate=new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes());
        int[] weights=GearQualityProfile.CURRENT.affixWeights(rarity);
        var budgets=new TreeMap<Integer,Double>();var splits=new HashMap<Integer,List<int[]>>();
        for(int n=1;n<=weights.length;n++){
            int count=switch(rarity){case RARE->n+1;case VERY_RARE->n+3;case LEGENDARY->6;default->n;};
            var legal=new ArrayList<int[]>();
            for(int p=0;p<=Math.min(3,count);p++){
                int s=count-p;
                if(s>3||!rarity.legalNewBudget(p,s))continue;
                if(completion(options,new ArrayList<>(),p,s,baseGate,new int[]{0}))legal.add(new int[]{p,s});
            }
            if(!legal.isEmpty()){budgets.put(count,(double)weights[n-1]);splits.put(count,legal);}
        }
        if(budgets.isEmpty())throw new IllegalStateException("No legal affix completion "+base.id()+"/"+rarity);
        int count=random.weighted(budgets,"budget");var split=splits.get(count).get(random.stream("budget-sides").nextInt(splits.get(count).size()));
        int p=split[0],s=split[1];
        while(p+s>0){var side=p>0?GearCatalog.Side.PREFIX:GearCatalog.Side.SUFFIX;int np=p-(side==GearCatalog.Side.PREFIX?1:0),ns=s-(side==GearCatalog.Side.SUFFIX?1:0);
            var feasible=new ArrayList<Choice>();for(var c:options)if(c.affix().side()==side&&legal(selected,c,baseGate)){
                selected.add(c);boolean possible=completion(options,selected,np,ns,baseGate,new int[]{0});selected.removeLast();if(possible)feasible.add(c);}
            var familyWeights=new TreeMap<String,Double>();feasible.forEach(c->familyWeights.put(c.affix().id(),(double)c.affix().weight()));String family=random.weighted(familyWeights,"affix-family/"+selected.size());
            var tierWeights=new LinkedHashMap<Choice,Double>();feasible.stream().filter(c->c.affix().id().equals(family)).forEach(c->tierWeights.put(c,(double)c.tier().weight()));
            if(max){int strongest=tierWeights.keySet().stream().mapToInt(c->c.tier().tier()).min().orElseThrow();tierWeights.keySet().removeIf(c->c.tier().tier()!=strongest);}
            selected.add(random.weighted(tierWeights,"affix-tier/"+selected.size()));p=np;s=ns;
        }
        var result=new ArrayList<GearInstance.AffixRoll>();for(var c:selected){double grid=c.tier().grid();
            if(!c.affix().tierModel().equals("Q")){
                double value=c.tier().low();
                String selector=c.affix().eligibility().equals("MATCH")
                        ?randomChoice(matchingSkillIds(base),random.stream("affix-selector/"+result.size())):null;
                result.add(new GearInstance.AffixRoll(c.affix().id(),c.affix().side(),c.affix().exclusionGroup(),c.tier().tier(),value,c.gate(),
                        c.affix().tierModel().equals("ALL")?"+"+(int)value+" to all learned active skills":c.affix().effectContract(),c.affix().name(),selector));continue;
            }
            long[] interval=scaledInterval(c.affix(),base,c.tier());long low=interval[0],high=interval[1];
            if(high<low)throw new IllegalStateException("Empty scaled affix interval");double value=BigDecimal.valueOf(max?high:random.stream("affix-value/"+result.size()).nextLong(low,high+1)).multiply(BigDecimal.valueOf(grid)).doubleValue();
            result.add(new GearInstance.AffixRoll(c.affix().id(),c.affix().side(),c.affix().exclusionGroup(),c.tier().tier(),value,c.gate(),c.affix().effectContract().split(" Require:")[0].replaceAll("\\bV\\b",Double.toString(value)),c.affix().name()));}
        return List.copyOf(result);
    }
    private static boolean completion(List<Choice> options,List<Choice> chosen,int p,int s,GearRequirements.Gate base,int[] work){
        if(++work[0]>100_000)throw new IllegalStateException("Affix feasibility budget exceeded");if(p+s==0)return true;
        var side=p>0?GearCatalog.Side.PREFIX:GearCatalog.Side.SUFFIX;
        for(var c:options)if(c.affix().side()==side&&legal(chosen,c,base)){chosen.add(c);boolean yes=completion(options,chosen,p-(p>0?1:0),s-(p>0?0:1),base,work);chosen.removeLast();if(yes)return true;}return false;
    }
    private static boolean legal(List<Choice> chosen,Choice next,GearRequirements.Gate base){
        if(chosen.stream().anyMatch(c->c.affix().exclusionGroup().equals(next.affix().exclusionGroup())))return false;
        var gates=new ArrayList<>(chosen.stream().map(Choice::gate).toList());gates.add(next.gate());
        try{GearRequirements.combine(base,gates,BigDecimal.ZERO);return true;}catch(IllegalArgumentException invalid){return false;}
    }
    static boolean eligible(GearCatalog.Affix a,GearCatalog.Base b){
        if(b.category()==GearCatalog.Category.TOOL)return false;
        if(b.category()==GearCatalog.Category.ARMOR){
            if(a.id().startsWith("GA-"))return true;
            String extension=a.armorExtension();
            if(extension.startsWith("none."))return false;
            boolean slot=extension.startsWith("All armor") || Arrays.stream(extension.split(";",2)[0].split(",\\s*"))
                    .anyMatch(token->token.equalsIgnoreCase(b.slot().name()));
            if(!slot)return false;
            if(extension.contains("INT/WIS armor"))return Set.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.INT,
                    com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS).contains(b.primaryAttribute());
            if(extension.contains("INT armor"))return b.primaryAttribute()==com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.INT;
            if(extension.contains("WIS armor"))return b.primaryAttribute()==com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS;
            return true;
        }
        if(a.id().startsWith("GA-"))return false;
        String action=action(b);
        String code=action.equals("shield")?"H":Set.of("staff","wand","book").contains(action)?"C"
                :Set.of("shortbow","longbow","crossbow","rifle","blunderbuss").contains(action)?"R":action.equals("bomb")?"B":"M";
        if(a.eligibility().equals("ALL"))return true;
        if(a.eligibility().equals("MATCH"))return !matchingSkillIds(b).isEmpty();
        for(String token:a.eligibility().split(",\\s*"))if(token.equals(code))return true;
        // Focus quick contact and charged projectile both use the managed local snapshot.
        // Their elemental flat is legal once the capability gate admits the family.
        if(code.equals("C") && Set.of("WA-008","WA-017","WA-018","WA-019","WA-020","WA-021","WA-022").contains(a.id())
                && Arrays.asList(a.eligibility().split(",\\s*")).contains("C*"))return true;
        // Twin Assault has a real two-instance dagger contact profile. The runtime still
        // requires a distinct compatible offhand; owning the affix never fabricates one.
        if(a.id().equals("WA-141") && action.equals("daggers")
                && Arrays.asList(a.eligibility().split(",\\s*")).contains("M*"))return true;
        // Other stars still require their native timing/block/dual-wield profile.
        return false;
    }
    private static String randomChoice(List<String> choices,java.util.random.RandomGenerator random) {
        if(choices.isEmpty())throw new IllegalStateException("No legal skill selector");
        return choices.get(random.nextInt(choices.size()));
    }
    private static final class Selectors {
        static final com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles PROFILES=
                com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles.loadCanonical(
                        com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
        static final Map<String,List<String>> BY_KIND=new java.util.concurrent.ConcurrentHashMap<>();
    }
    /** Uses the executable skill profile's allowed weapon kinds, not skill-name guesses. */
    public static List<String> matchingSkillIds(GearCatalog.Base base) {
        if(base.category()!=GearCatalog.Category.HELD)return List.of();
        String kind=switch(action(base)) {
            case "daggers"->"DAGGER";case "shortbow","longbow"->"BOW";
            case "rifle","blunderbuss"->"GUN";case "book"->"SPELLBOOK";
            default->action(base).toUpperCase(Locale.ROOT);
        };
        return Selectors.BY_KIND.computeIfAbsent(kind,key->Selectors.PROFILES.all().values().stream()
                .filter(p->p.allowedMainHandKinds().contains(key)
                        ||key.equals("SHIELD")&&p.requiredOffHandKinds().contains("SHIELD"))
                .map(com.inigmasgames.hytalerpg.execution.Stage04SkillProfile::skillId).sorted().toList());
    }
    static double armorFactor(GearCatalog.Affix a,GearCatalog.Base b){
        if(b.category()!=GearCatalog.Category.ARMOR)return 1;
        if(a.id().equals("GA-159"))return switch(b.slot()){case HEAD->.20/.36;case HANDS->.16/.36;case LEGS->.28/.36;default->1;};
        if(a.operator().equals("ATTRIBUTE") && !a.id().equals("WA-090"))return .5;
        if(Set.of("WA-091","WA-092","WA-093").contains(a.id()))return b.slot()==GearCatalog.Slot.CHEST||b.slot()==GearCatalog.Slot.LEGS?.5:.35;
        String extension=a.armorExtension();
        if(extension.contains("0.6 × V"))return .6;
        if(extension.contains("0.5 × V"))return .5;
        return 1;
    }
    static long[] scaledInterval(GearCatalog.Affix a,GearCatalog.Base base,GearAffixTiers.Tier tier) {
        if(!a.tierModel().equals("Q"))throw new IllegalArgumentException("Not a Q tier");
        double factor=new double[]{1,.85,.70,.55,.40}[tier.tier()-1];
        var multiplier=BigDecimal.valueOf(factor).multiply(BigDecimal.valueOf(armorFactor(a,base)));
        var grid=BigDecimal.valueOf(tier.grid());
        long low=BigDecimal.valueOf(a.topRange().getFirst()).multiply(multiplier).divide(grid,0,RoundingMode.CEILING).longValueExact();
        long high=BigDecimal.valueOf(a.topRange().getLast()).multiply(multiplier).divide(grid,0,RoundingMode.FLOOR).longValueExact();
        return new long[]{low,high};
    }
    static boolean rollable(GearCatalog.Affix a,GearCatalog.Base base,GearAffixTiers.Tier tier) {
        if(!a.tierModel().equals("Q"))return true;
        long[] interval=scaledInterval(a,base,tier);return interval[0]<=interval[1];
    }
}
