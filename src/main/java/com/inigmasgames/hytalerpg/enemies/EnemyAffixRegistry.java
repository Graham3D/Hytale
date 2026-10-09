package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed, immutable ME definitions. Loading a definition does not certify a native route. */
public final class EnemyAffixRegistry {
    public enum Capability { DIRECT_HIT, PHYSICAL_DIRECT_HIT, NATIVE_KNOCKBACK, MOBILE, RECOVERY_TIMELINE, DEFENSE_STAT,
        PACK_LEADER, MINION_ROSTER, RESOURCE_TARGETING, HOSTILE_STATUS_ADMISSION, SHIELD_OWNER, APPLIED_HIT_RECEIPTS }
    public enum Group { ELEMENTAL, LEADER, SURVIVAL, CONTROL, OFFENSE, DENIAL }
    public enum Selector { MIGHT, WARD, HASTE }
    public enum Inheritance { NONE, MOVEMENT_ONLY, PHYSICAL_INCREASE, EXTRA_ELEMENTAL_POWER_ONLY,
        POISON_PACKAGE, EMPOWERED_MINION_SNAPSHOT, PACK_AURA_MEMBERSHIP_ONLY }
    public enum Operator {
        EXTRA_FAST("movementIncrease recoveryRateIncrease"),
        EXTRA_STRONG("physicalIncrease"), MAGIC_RESISTANT("elementalResistanceAdd"), STONE_SKIN("defenseRatingScale"),
        FIRE_ENCHANTED("extraPowerFraction resistanceAdd channel"),
        COLD_ENCHANTED("extraPowerFraction resistanceAdd channel"),
        LIGHTNING_ENCHANTED("extraPowerFraction resistanceAdd channel"),
        POISON_ENCHANTED("extraPowerFraction resistanceAdd channel"),
        WIND_ENCHANTED("extraPowerFraction resistanceAdd channel"), EARTH_ENCHANTED("extraPowerFraction resistanceAdd channel"),
        VOID_ENCHANTED("extraPowerFraction resistanceAdd channel"), SPECTRAL_HIT("extraPowerFraction channels"),
        MANA_BURN("maxManaFraction damageFractionCap opportunityLockMs victimAggregateMaxManaFractionPerSecond"),
        CURSED("statusChance outgoingDirectReduction durationMs opportunityLockMs statusPackage"),
        KNOCKBACK("additionalNativeKnockbackStrength"),
        VAMPIRIC("healthLeechFraction maxOwnHealthFractionPerSecond"), UNWAVERING("statusImmunityFamilies"),
        UNSTOPPABLE("immuneMovementComponent"),
        FRENZIED("cycleMs activeWindowStartMs activeWindowDurationMs allDirectIncrease movementIncrease recoveryRateIncrease"),
        AVENGER("perDefeatedMinionDirectIncrease perDefeatedMinionMovementIncrease maxStacks"),
        EMPOWERED_MINIONS("minionMaxHealthIncrease minionDirectIncrease minionMovementIncrease"),
        HORDE("additionalInitialMinions maximumPackMembers"),
        AURA_ENCHANTED("selectorWeights radiusMeters membershipUpdateMs providerExpiryMs mightPhysicalIncrease wardElementalResistanceAdd hasteMovementIncrease hasteRecoveryRateIncrease"),
        PACKBOUND("minimumInitialGuards maximumInitialGuards guardAreaMeters allDamageBlocked allExternalStatusMutationsBlocked"),
        ARMOR_BREAKER("statusChance defenseReduction durationMs opportunityLockMs statusPackage"),
        REFLECTIVE("healthDamageReflectFraction maxVictimHealthFractionPerHit maxVictimHealthFractionPerSecond maximumOriginDistanceMeters"),
        BULWARK("initialShieldMaxHealthFraction regenerates");
        final Set<String> keys;
        Operator(String keys) { this.keys=Set.of(keys.split(" ")); }
        public String id() { return "ME-%03d".formatted(ordinal()+1); }
    }
    public static final List<String> CHANNELS=List.of("PHYSICAL","WIND","WATER","FIRE","EARTH","LIGHTNING","VOID");
    private static final Set<String> ARRAYS=Set.of("movementIncrease","recoveryRateIncrease","physicalIncrease",
            "elementalResistanceAdd","defenseRatingScale","extraPowerFraction","resistanceAdd","statusChance",
            "poisonPowerFraction","earthResistanceAdd","maxManaFraction","outgoingDirectReduction",
            "horizontalDisplacementMeters","additionalNativeKnockbackStrength","healthLeechFraction","allDirectIncrease","perDefeatedMinionDirectIncrease",
            "minionMaxHealthIncrease","minionDirectIncrease","minionMovementIncrease","mightPhysicalIncrease",
            "wardElementalResistanceAdd","hasteMovementIncrease","hasteRecoveryRateIncrease","defenseReduction",
            "healthDamageReflectFraction","initialShieldMaxHealthFraction");

    public static final class Definition {
        private final String revision, displayName, tagTemplate;
        private final Operator operator;
        private final List<Integer> weights;
        private final Set<Capability> capabilities;
        private final Set<Group> groups;
        private final Set<EnemyRarity> rarities;
        private final JsonObject parameters, inheritance;
        private Definition(String revision,JsonObject json) {
            exact(json,Set.of("id","key","operator","displayName","tagTemplate","randomWeights","requiredCapabilities",
                    "constraintGroups","allowedRarities","parameters","inheritance","positiveTestId","negativeTestId"));
            this.revision=revision; operator=Operator.valueOf(string(json,"operator"));
            require(string(json,"id").equals(operator.id()),"AFFIX_ID_OPERATOR_MISMATCH");
            require(string(json,"key").equals(operator.name().toLowerCase(Locale.ROOT)),"AFFIX_KEY_MISMATCH");
            displayName=string(json,"displayName"); tagTemplate=string(json,"tagTemplate");
            require(string(json,"positiveTestId").equals(id()+"-P")&&string(json,"negativeTestId").equals(id()+"-N"),"AFFIX_TEST_ID");
            weights=numbers(json.get("randomWeights"),true,3,100000).stream().map(Double::intValue).toList();
            capabilities=enums(json.get("requiredCapabilities"),Capability.class);
            require(capabilities.containsAll(minimumCapabilities(operator)),"MISSING_REQUIRED_CAPABILITY");
            groups=enums(json.get("constraintGroups"),Group.class);
            rarities=enums(json.get("allowedRarities"),EnemyRarity.class);
            require(!rarities.isEmpty()&&!rarities.contains(EnemyRarity.NORMAL),"AFFIX_RARITY");
            parameters=json.getAsJsonObject("parameters").deepCopy(); exact(parameters,operator.keys);
            for(var entry:parameters.entrySet()) {
                String key=entry.getKey();var value=entry.getValue();
                if(ARRAYS.contains(key)) numbers(value,false,3,parameterMaximum(key));
                else if(key.equals("selectorWeights")) {
                    exact(value.getAsJsonObject(),Set.of("MIGHT","WARD","HASTE"));
                    double sum=0;for(var e:value.getAsJsonObject().entrySet())sum+=number(e.getValue(),true,100000);
                    require(sum>0,"EMPTY_AURA_SELECTORS");
                } else if(key.equals("channels")) require(strings(value).equals(CHANNELS.subList(1,7)),"SPECTRAL_CHANNELS");
                else if(key.equals("statusImmunityFamilies")) require(strings(value).equals(List.of("STUN_STAGGER")),"STATUS_IMMUNITY_FAMILY");
                else if(Set.of("channel","status","statusPackage","immuneMovementComponent").contains(key)) string(parameters,key);
                else if(Set.of("regenerates","allDamageBlocked","allExternalStatusMutationsBlocked").contains(key))
                    require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean(),"BOOLEAN_REQUIRED:"+key);
                else {
                    double n=number(value,key.endsWith("Ms")||Set.of("maxStacks","stacksPerAcceptedPackage","additionalInitialMinions","maximumPackMembers","minimumInitialGuards","maximumInitialGuards").contains(key),
                            key.endsWith("Ms")?60000:key.endsWith("Meters")?32:8);
                    if(key.endsWith("Ms"))require(n>=1,"ZERO_DURATION_OR_LOCK");
                    if(key.endsWith("Meters")&&!key.equals("verticalDisplacementMeters"))require(n>=.1,"DISTANCE_BELOW_MINIMUM");
                    if(key.contains("Fraction")||key.equals("perDefeatedMinionMovementIncrease"))require(n<=parameterMaximum(key),"FRACTION_OUT_OF_RANGE");
                }
            }
            validateIdentities();
            inheritance=json.getAsJsonObject("inheritance").deepCopy();
            var mode=Inheritance.valueOf(string(inheritance,"mode"));
            exact(inheritance,switch(mode) {
                case MOVEMENT_ONLY,PHYSICAL_INCREASE,EXTRA_ELEMENTAL_POWER_ONLY -> Set.of("mode","parameterScale");
                case POISON_PACKAGE -> Set.of("mode","statusChance","potencyScale","opportunityLockMs");
                default -> Set.of("mode");
            });
            var expected=switch(operator) {
                case EXTRA_FAST -> Inheritance.MOVEMENT_ONLY;
                case EXTRA_STRONG -> Inheritance.PHYSICAL_INCREASE;
                case FIRE_ENCHANTED,COLD_ENCHANTED,LIGHTNING_ENCHANTED,POISON_ENCHANTED,WIND_ENCHANTED,EARTH_ENCHANTED,VOID_ENCHANTED,SPECTRAL_HIT -> Inheritance.EXTRA_ELEMENTAL_POWER_ONLY;
                case EMPOWERED_MINIONS -> Inheritance.EMPOWERED_MINION_SNAPSHOT;
                case AURA_ENCHANTED -> Inheritance.PACK_AURA_MEMBERSHIP_ONLY;
                default -> Inheritance.NONE;
            };
            require(mode==expected,"INHERITANCE_NOT_ADMITTED");
            for(var e:inheritance.entrySet())if(!e.getKey().equals("mode")){
                boolean duration=e.getKey().endsWith("Ms");double value=number(e.getValue(),duration,duration?60000:1);
                require(!duration||value>=1,"INHERITED_DURATION_BOUNDS");
            }
        }
        private void validateIdentities() {
            String channel=switch(operator) { case FIRE_ENCHANTED->"FIRE";case COLD_ENCHANTED->"WATER";
                case LIGHTNING_ENCHANTED->"LIGHTNING";case POISON_ENCHANTED,EARTH_ENCHANTED->"EARTH";case WIND_ENCHANTED->"WIND";case VOID_ENCHANTED->"VOID";default->null;};
            if(channel!=null)require(text("channel").equals(channel),"AFFIX_CHANNEL");
            if(operator==Operator.CURSED)require(text("statusPackage").equals("ME_WEAKENED"),"AFFIX_PACKAGE");
            if(operator==Operator.ARMOR_BREAKER)require(text("statusPackage").equals("ME_ARMOR_BROKEN"),"AFFIX_PACKAGE");
            if(operator==Operator.UNSTOPPABLE)require(text("immuneMovementComponent").equals("SLOW_ONLY"),"AFFIX_MOVEMENT_IMMUNITY");
            if(operator==Operator.BULWARK)require(!parameters.get("regenerates").getAsBoolean(),"BULWARK_CANNOT_REGENERATE");
            if(operator==Operator.PACKBOUND)require(parameters.get("allDamageBlocked").getAsBoolean()&&parameters.get("allExternalStatusMutationsBlocked").getAsBoolean(),"PACKBOUND_INCOMPLETE_GATE");
            if(operator==Operator.FRENZIED)require(constant("activeWindowStartMs")+constant("activeWindowDurationMs")<=constant("cycleMs"),"FRENZY_WINDOW_EXCEEDS_CYCLE");
            if(operator==Operator.PACKBOUND)require(constant("minimumInitialGuards")>=2&&constant("maximumInitialGuards")<=5
                    &&constant("minimumInitialGuards")<=constant("maximumInitialGuards"),"PACKBOUND_GUARD_RANGE");
            if(operator==Operator.HORDE)require(constant("additionalInitialMinions")==2&&constant("maximumPackMembers")==8,"HORDE_SEALED_ROSTER_RULE");
            if(operator==Operator.AURA_ENCHANTED)require(constant("membershipUpdateMs")<=250&&constant("providerExpiryMs")<=500
                    &&constant("providerExpiryMs")>=constant("membershipUpdateMs"),"AURA_UPDATE_OR_EXPIRY_BOUNDS");
        }
        public String id(){return operator.id();}
        public String revision(){return revision;}
        public String displayName(){return displayName;}
        public String tagTemplate(){return tagTemplate;}
        public Operator operator(){return operator;}
        public int weight(DifficultyId mode){return weights.get(mode.ordinal());}
        public Set<Capability> capabilities(){return capabilities;}
        public Set<Group> groups(){return groups;}
        public Set<EnemyRarity> rarities(){return rarities;}
        public double magnitude(String key,DifficultyId mode){return parameters.getAsJsonArray(key).get(mode.ordinal()).getAsDouble();}
        public double constant(String key){return parameters.get(key).getAsDouble();}
        public String text(String key){return string(parameters,key);}
        public double selectorWeight(Selector selector){return parameters.getAsJsonObject("selectorWeights").get(selector.name()).getAsDouble();}
        public Inheritance inheritance(){return Inheritance.valueOf(string(inheritance,"mode"));}
        public double inherited(String key){return inheritance.get(key).getAsDouble();}
        public Map<String,Double> resolvedNumbers(DifficultyId mode) {
            var values=new TreeMap<String,Double>();
            parameters.entrySet().forEach(e->{if(ARRAYS.contains(e.getKey()))values.put(e.getKey(),magnitude(e.getKey(),mode));
                else if(e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isNumber())values.put(e.getKey(),e.getValue().getAsDouble());});
            return Collections.unmodifiableMap(values);
        }
    }
    private final String revision;
    private static double parameterMaximum(String key){
        if(key.equals("extraPowerFraction"))return .30;
        if(key.toLowerCase(Locale.ROOT).contains("resistance"))return .75;
        if(key.toLowerCase(Locale.ROOT).contains("movement")||key.toLowerCase(Locale.ROOT).contains("recovery"))return .50;
        return 1;
    }
    private static Set<Capability> minimumCapabilities(Operator operator){return switch(operator){
        case EXTRA_FAST,UNSTOPPABLE->Set.of(Capability.MOBILE);
        case EXTRA_STRONG->Set.of(Capability.PHYSICAL_DIRECT_HIT);
        case STONE_SKIN->Set.of(Capability.DEFENSE_STAT);
        case FIRE_ENCHANTED,COLD_ENCHANTED,LIGHTNING_ENCHANTED,POISON_ENCHANTED,WIND_ENCHANTED,EARTH_ENCHANTED,VOID_ENCHANTED,SPECTRAL_HIT,FRENZIED,KNOCKBACK->Set.of(Capability.DIRECT_HIT);
        case CURSED,ARMOR_BREAKER->Set.of(Capability.DIRECT_HIT,Capability.HOSTILE_STATUS_ADMISSION);
        case MANA_BURN,VAMPIRIC->Set.of(Capability.DIRECT_HIT,Capability.APPLIED_HIT_RECEIPTS);
        case AVENGER->Set.of(Capability.PACK_LEADER,Capability.MINION_ROSTER,Capability.DIRECT_HIT);
        case EMPOWERED_MINIONS,HORDE,AURA_ENCHANTED,PACKBOUND->Set.of(Capability.PACK_LEADER,Capability.MINION_ROSTER);
        case REFLECTIVE->Set.of(Capability.APPLIED_HIT_RECEIPTS);
        case BULWARK->Set.of(Capability.SHIELD_OWNER);
        case MAGIC_RESISTANT,UNWAVERING->Set.of();
    };}
    private final Map<String,Definition> definitions;
    public EnemyAffixRegistry(JsonObject json) {
        exact(json,Set.of("schemaVersion","revision","difficultyOrder","definitions"));
        require(number(json.get("schemaVersion"),true,1)==1,"AFFIX_SCHEMA");
        revision=string(json,"revision");
        require(strings(json.get("difficultyOrder")).equals(List.of("NORMAL","NIGHTMARE","HELL")),"DIFFICULTY_ORDER");
        var entries=new TreeMap<String,Definition>();
        for(var entry:json.getAsJsonArray("definitions")){var definition=new Definition(revision,entry.getAsJsonObject());
            require(entries.putIfAbsent(definition.id(),definition)==null,"DUPLICATE_AFFIX");}
        require(entries.size()==27,"CATALOG_MUST_CONTAIN_ALL_27");definitions=Collections.unmodifiableMap(entries);
    }
    public String revision(){return revision;}
    public Collection<Definition> definitions(){return definitions.values();}
    public Definition require(String id){var value=definitions.get(id);if(value==null)throw new IllegalArgumentException("UNKNOWN_AFFIX:"+id);return value;}
    public Definition require(Operator operator){return require(operator.id());}
    public static EnemyAffixRegistry canonical(){return new EnemyAffixRegistry(resource("monster-affixes-v1.json"));}
    static JsonObject resource(String name) {
        try(var stream=EnemyAffixRegistry.class.getResourceAsStream("/rpg/enemies/"+name)) {
            if(stream==null)throw new IllegalStateException("MISSING_ENEMY_RESOURCE:"+name);
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    static void exact(JsonObject object,Set<String> keys){require(object.keySet().equals(keys),"UNKNOWN_OR_MISSING_KEYS:"+object.keySet()+" expected "+keys);}
    static String string(JsonObject object,String key){var value=object.get(key);
        require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString(),"STRING_REQUIRED:"+key);
        String text=value.getAsString();require(!text.isBlank()&&text.length()<=256&&text.chars().noneMatch(Character::isISOControl),"INVALID_STRING:"+key);return text;}
    static double number(JsonElement value,boolean integer,double maximum){
        require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber(),"NUMBER_REQUIRED");
        double n=value.getAsDouble();require(Double.isFinite(n)&&n>=0&&n<=maximum&&(!integer||n==Math.rint(n)),"INVALID_NUMBER");return n;}
    static List<Double> numbers(JsonElement value,boolean integer,int size,double maximum){
        require(value!=null&&value.isJsonArray()&&value.getAsJsonArray().size()==size,"ARRAY_LENGTH");
        var result=new ArrayList<Double>();for(var n:value.getAsJsonArray())result.add(number(n,integer,maximum));return List.copyOf(result);}
    static List<String> strings(JsonElement value){require(value!=null&&value.isJsonArray(),"STRING_ARRAY_REQUIRED");var result=new ArrayList<String>();
        for(var v:value.getAsJsonArray()){require(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString(),"STRING_ARRAY_REQUIRED");result.add(v.getAsString());}return List.copyOf(result);}
    static <T extends Enum<T>> Set<T> enums(JsonElement value,Class<T> type){var names=strings(value);var result=EnumSet.noneOf(type);
        for(var name:names)require(result.add(Enum.valueOf(type,name)),"DUPLICATE_ENUM");return Collections.unmodifiableSet(result);}
    static void require(boolean test,String message){if(!test)throw new IllegalArgumentException(message);}
}
