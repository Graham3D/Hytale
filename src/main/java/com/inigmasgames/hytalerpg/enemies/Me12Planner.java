package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.math.BigInteger;
import java.util.*;
import java.util.function.LongConsumer;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Exact distinct-set decision graphs. Pure immutable inputs; no native, file, reward or world calls. */
public final class Me12Planner {
    public static final int MAX_STATES=100000;
    public static final long DEADLINE_NANOS=500_000_000L, STATE_BYTES=512;
    public enum Evidence { CERTIFIED_STATIC_FIXTURE, UNCERTIFIED_PRODUCTION }
    public enum Owner { PREFIX_SIGNATURE, SUFFIX_SIGNATURE, PREFIX_FILLER, SUFFIX_FILLER, DIFFICULTY }
    public record Member(String role,String source,EnemyAffixSelection.Binding binding,Set<String> immunities,boolean reachable,String nativeRole) {
        public Member(String role,String source,EnemyAffixSelection.Binding binding,Set<String> immunities,boolean reachable){this(role,source,binding,immunities,reachable,role);}
        public Member {require(role!=null&&!role.isBlank()&&role.length()<=128&&source!=null&&!source.isBlank()&&source.length()<=512,"ME12_MEMBER_IDENTITY");Objects.requireNonNull(binding);
            require(nativeRole!=null&&!nativeRole.isBlank()&&nativeRole.length()<=128,"ME12_NATIVE_ALIAS");
            immunities=Collections.unmodifiableSet(new TreeSet<>(immunities));require(CHANNELS.containsAll(immunities),"ME12_NATIVE_IMMUNITY");}
    }
    public record Context(String nativeSnapshotDigest,String sourceReceipt,long generation,DifficultyId difficulty,
            Evidence evidence,Member leader,List<Member> minions,List<Member> hordeSlots,Map<String,Long> effectiveWeights) {
        public Context {
            require(nativeSnapshotDigest!=null&&nativeSnapshotDigest.matches("[a-f0-9]{64}")&&sourceReceipt!=null&&!sourceReceipt.isBlank()&&sourceReceipt.length()<=512&&generation>=0,"ME12_CONTEXT_IDENTITY");
            Objects.requireNonNull(difficulty);Objects.requireNonNull(evidence);Objects.requireNonNull(leader);
            minions=List.copyOf(minions);hordeSlots=List.copyOf(hordeSlots);
            require(minions.size()>=2&&minions.size()<=5&&(hordeSlots.isEmpty()||hordeSlots.size()==2)&&minions.size()+hordeSlots.size()<=7,"ME12_ROSTER_BOUNDS");
            require(leader.binding().initialMinions()==minions.size(),"ME12_BASE_ROSTER_BINDING");
            var nativeRoles=new HashSet<String>();nativeRoles.add(leader.role());minions.forEach(m->nativeRoles.add(m.role()));
            require(hordeSlots.stream().allMatch(m->nativeRoles.contains(m.role())),"ME12_HORDE_UNAPPROVED_ROLE_MIX");
            var expected=new TreeSet<String>();for(var op:Operator.values())if(op==Operator.AURA_ENCHANTED)for(var s:Selector.values())expected.add(op.id()+"/"+s);else expected.add(op.id());
            require(effectiveWeights.keySet().equals(expected),"ME12_EFFECTIVE_WEIGHT_KEYS");
            var weights=new TreeMap<String,Long>(effectiveWeights);for(var e:weights.entrySet())require(e.getValue()>=0&&e.getValue()<=10000000000L,"ME12_EFFECTIVE_WEIGHT");
            effectiveWeights=Collections.unmodifiableMap(weights);
        }
        public String key(Me12Policies policy){var json=new GsonBuilder().disableHtmlEscaping().create().toJsonTree(this);canonicalSets(json);return RewardIntent.digest(policy.digest()+"/"+json);}
    }
    public record Owned(EnemyAffixSelection.Choice choice,Owner owner) { public Owned {Objects.requireNonNull(choice);Objects.requireNonNull(owner);} }
    public record Plan(String contextKey,Me12Policies.Name prefix,Me12Policies.Name suffix,int difficultySlots,List<Owned> affixes) {
        public Plan {affixes=List.copyOf(affixes);require(affixes.size()==prefix.tier()+suffix.tier()+difficultySlots,"ME12_PLAN_COUNT");}
        public String displayName(){return prefix.text()+" "+suffix.text();}
        public List<EnemyAffixSelection.Choice> choices(){return affixes.stream().map(Owned::choice).toList();}
    }
    public static final class CompileFailure extends IllegalStateException {
        public CompileFailure(String reason){super("ME12_ENGINEERING_FAILURE:"+reason);}
    }
    public static final class Budget {
        private final long deadline;private final LongConsumer account;private int states;private long bytes;
        public Budget(LongConsumer account){this(System.nanoTime()+DEADLINE_NANOS,account);}
        Budget(long deadline,LongConsumer account){this.deadline=deadline;this.account=Objects.requireNonNull(account);}
        void check(){if(Thread.currentThread().isInterrupted())throw new CompileFailure("CANCELLED");if(System.nanoTime()>deadline)throw new CompileFailure("COLD_DEADLINE");}
        void state(){check();if(++states>MAX_STATES)throw new CompileFailure("MEMO_STATES");charge(STATE_BYTES);}
        void charge(long value){account.accept(value);bytes+=value;}
        public int states(){return states;}public long bytes(){return bytes;}
    }
    private record Option(EnemyAffixSelection.Choice choice,BigInteger weight,int groups,int flags) {}
    private record State(int index,int count,int groups,int flags,int allocations) {}
    private record Edge(Option option,State state,BigInteger mass) {}
    private record Node(BigInteger mass,List<Edge> edges) {}
    private static final BigInteger ZERO=BigInteger.ZERO,ONE=BigInteger.ONE;
    // Flags carry only sufficient future restrictions, never the full selected-set identity.
    private static final int FRENZY=1,AVENGER=2,REFLECT=4,VAMPIRIC=8,PACKBOUND=16,HORDE=32,BLOCKS_HORDE=64;
    public static final class Compiled {
        private final Context context;private final Me12Policies policies;private final String key;
        private final List<Graph> graphs;private final long[] tiers;private final int states;private final long bytes,compileNanos;
        private Compiled(Context context,Me12Policies policies,List<Graph> graphs,Budget budget,long start) {
            this.context=context;this.policies=policies;this.key=context.key(policies);this.graphs=List.copyOf(graphs);
            tiers=new long[9];for(var graph:graphs)tiers[(graph.prefix.tier()-1)*3+graph.suffix.tier()-1]=tierWeight(graph.prefix.tier())*tierWeight(graph.suffix.tier());
            states=budget.states();bytes=budget.bytes();compileNanos=System.nanoTime()-start;
        }
        public long[] tierMasses(){return tiers.clone();}public int states(){return states;}public long accountedBytes(){return bytes;}public long compileNanos(){return compileNanos;}
        public String key(){return key;}public Context context(){return context;}public Me12Policies policies(){return policies;}
        public boolean accepts(Plan plan){return plan.contextKey().equals(key)&&graphs.stream().anyMatch(g->g.prefix.equals(plan.prefix())&&g.suffix.equals(plan.suffix())&&g.accepts(plan));}
        public Map<String,BigInteger> completionMasses(){var result=new TreeMap<String,BigInteger>();for(var g:graphs)result.put(g.prefix.id()+"/"+g.suffix.id(),g.root.mass());return Collections.unmodifiableMap(result);}
        public Map<String,Long> nameMasses(){var result=new TreeMap<String,Long>();for(var g:graphs)result.put(g.prefix.id()+"/"+g.suffix.id(),g.nameWeight());return Collections.unmodifiableMap(result);}
        public Plan sample(String seed) {
            long sum=Arrays.stream(tiers).sum();if(sum==0)throw new IllegalStateException("ME12_PROVEN_NO_COMPLETE_PLAN");
            long tierDraw=new Me12Random(Me12Random.TIER,seed).uniformBelow(BigInteger.valueOf(sum)).longValueExact();int tier=0;
            for(;tier<9;tier++){if(tierDraw<tiers[tier])break;tierDraw-=tiers[tier];}
            long names=0;for(var g:graphs)if(g.tierIndex()==tier)names+=g.nameWeight();
            long nameDraw=new Me12Random(Me12Random.NAME,seed).uniformBelow(BigInteger.valueOf(names)).longValueExact();Graph chosen=null;
            for(var g:graphs)if(g.tierIndex()==tier){if(nameDraw<g.nameWeight()){chosen=g;break;}nameDraw-=g.nameWeight();}
            if(chosen==null)throw new AssertionError("ME12_NAME_RANGE");
            var draw=new Me12Random(Me12Random.SET,seed).uniformBelow(chosen.root.mass());
            return chosen.sample(key,draw);
        }
    }
    public static Compiled compile(Context context,Me12Policies policies,Budget budget) {
        long start=System.nanoTime();budget.check();
        if(context.evidence()!=Evidence.CERTIFIED_STATIC_FIXTURE)throw new IllegalStateException("ME12_DEPENDENCY_UNCERTIFIED_PRODUCTION_CONTEXT");
        var registry=policies.registry();var balance=policies.balance();var eligibility=new EnemyAffixSelection(registry);
        var options=new ArrayList<List<Option>>();
        boolean nativeSafe=nativeSafe(context.leader(),context.difficulty(),balance,EnemyRarity.UNIQUE);
        for(var d:registry.definitions().stream().sorted(Comparator.comparing(Definition::id)).toList()) {
            var alternatives=new ArrayList<Option>();
            var choices=d.operator()==Operator.AURA_ENCHANTED?Arrays.stream(Selector.values()).map(s->new EnemyAffixSelection.Choice(d.id(),s)).toList():List.of(new EnemyAffixSelection.Choice(d.id(),null));
            for(var choice:choices) {
                Long weight=context.effectiveWeights().get(choice.key());if(weight==null)throw new IllegalArgumentException("ME12_UNFROZEN_WEIGHT:"+choice.key());
                if(weight==0||!nativeSafe||eligibility.rejectionReason(context.leader().binding(),EnemyRarity.UNIQUE,choice).isPresent())continue;
                if(context.minions().stream().anyMatch(m->!nativeSafe(m,context.difficulty(),balance,EnemyRarity.NORMAL)||!inherits(m,d)))continue;
                if(Set.of(Operator.VAMPIRIC,Operator.PACKBOUND,Operator.BULWARK).contains(d.operator())&&elementCount(context.leader())>1)continue;
                if(d.operator()==Operator.PACKBOUND&&context.minions().stream().anyMatch(m->!m.reachable()||m.immunities().contains("PHYSICAL")))continue;
                if(d.operator()==Operator.HORDE&&(context.hordeSlots().size()!=2||context.hordeSlots().stream().anyMatch(m->!m.reachable()||!nativeSafe(m,context.difficulty(),balance,EnemyRarity.NORMAL))))continue;
                int flags=switch(d.operator()){case FRENZIED->FRENZY;case AVENGER->AVENGER;case REFLECTIVE->REFLECT;case VAMPIRIC->VAMPIRIC;case PACKBOUND->PACKBOUND;case HORDE->HORDE;default->0;};
                if(context.hordeSlots().stream().anyMatch(m->!inherits(m,d)))flags|=BLOCKS_HORDE;
                int groups=0;for(var g:EnemyAffixSelection.semanticGroups(d.operator(),choice.selector()))groups+=1<<(g.ordinal()*2);
                alternatives.add(new Option(choice,BigInteger.valueOf(weight),groups,flags));
            }
            options.add(List.copyOf(alternatives));
        }
        var graphs=new ArrayList<Graph>();var shared=new HashMap<String,Graph>();
        for(var prefix:policies.prefixes())for(var suffix:policies.suffixes()) {
            budget.check();if(prefix.signature().equals(suffix.signature()))continue;
            budget.charge(512);
            String shape=prefix.tier()+"/"+prefix.signature()+"/"+prefix.fillers()+"/"+suffix.tier()+"/"+suffix.signature()+"/"+suffix.fillers();
            var prior=shared.get(shape);var graph=prior==null?new Graph(prefix,suffix,context.difficulty().ordinal(),options,budget):new Graph(prefix,suffix,prior);
            shared.putIfAbsent(shape,graph);
            if(graph.root.mass().signum()>0)graphs.add(graph);
        }
        budget.check();var compiled=new Compiled(context,policies,graphs,budget,start);budget.check();return compiled;
    }
    public static Map<String,Long> weights(EnemyAffixRegistry registry,DifficultyId mode,EnemyAffixSelection.WeightPolicy policy) {
        var result=new TreeMap<String,Long>();for(var d:registry.definitions()) {
            long weight=policy.weight(d.id(),d.weight(mode));
            if(d.operator()==Operator.AURA_ENCHANTED)for(var s:Selector.values())result.put(new EnemyAffixSelection.Choice(d.id(),s).key(),Math.multiplyExact(weight,(long)d.selectorWeight(s)));
            else result.put(d.id(),weight);
        }return Collections.unmodifiableMap(result);
    }
    private static boolean nativeSafe(Member member,DifficultyId mode,EnemyBalance balance,EnemyRarity rarity){return !member.immunities().contains("PHYSICAL")&&elementCount(member)<=balance.immunityCap(rarity,mode);}
    private static void canonicalSets(com.google.gson.JsonElement value){
        if(value.isJsonArray()){value.getAsJsonArray().forEach(Me12Planner::canonicalSets);return;}
        if(!value.isJsonObject())return;var object=value.getAsJsonObject();
        for(String field:List.of("capabilities","supportedAffixIds"))if(object.has(field)){
            var values=new TreeSet<String>();object.getAsJsonArray(field).forEach(e->values.add(e.getAsString()));
            var array=new com.google.gson.JsonArray();values.forEach(array::add);object.add(field,array);
        }
        object.entrySet().forEach(e->canonicalSets(e.getValue()));
    }
    private static long elementCount(Member member){return member.immunities().stream().filter(c->!c.equals("PHYSICAL")).count();}
    private static boolean inherits(Member member,Definition d) {
        var b=member.binding();var required=switch(d.inheritance()) {
            case MOVEMENT_ONLY->Set.of(Capability.MOBILE);case PHYSICAL_INCREASE->Set.of(Capability.PHYSICAL_DIRECT_HIT);
            case EXTRA_ELEMENTAL_POWER_ONLY->Set.of(Capability.DIRECT_HIT);
            case POISON_PACKAGE->Set.of(Capability.DIRECT_HIT,Capability.HOSTILE_STATUS_ADMISSION,Capability.APPLIED_HIT_RECEIPTS);
            case EMPOWERED_MINION_SNAPSHOT->Set.of(Capability.DIRECT_HIT,Capability.MOBILE);default->Set.<Capability>of();};
        return required.isEmpty()||b.supportedAffixIds().contains(d.id())&&b.capabilities().containsAll(required);
    }
    public static long tierWeight(int tier){return switch(tier){case 1->70;case 2->25;case 3->5;default->throw new IllegalArgumentException("ME12_TIER");};}
    public static long[] countFeasibleMasses(int maximum,int difficulty){long[] result=new long[9];for(int p=1;p<=3;p++)for(int s=1;s<=3;s++)if(p+s+difficulty<=maximum)result[(p-1)*3+s-1]=tierWeight(p)*tierWeight(s);return result;}
    private static boolean legal(int groups,int flags) {
        for(var group:Group.values())if((groups>>(group.ordinal()*2)&3)>(group==Group.OFFENSE?2:1))return false;
        return (flags&(FRENZY|AVENGER))!=(FRENZY|AVENGER)&&(flags&(REFLECT|VAMPIRIC))!=(REFLECT|VAMPIRIC)
                &&(flags&(REFLECT|PACKBOUND))!=(REFLECT|PACKBOUND)&&(flags&(HORDE|BLOCKS_HORDE))!=(HORDE|BLOCKS_HORDE);
    }
    private static final class Graph {
        final Me12Policies.Name prefix,suffix;final int difficulty,target,goal;
        final List<List<Option>> options;final List<Option> signatures;final Map<State,Node> memo;final Budget budget;final Node root;
        Graph(Me12Policies.Name prefix,Me12Policies.Name suffix,Graph shared){this.prefix=prefix;this.suffix=suffix;difficulty=shared.difficulty;target=shared.target;goal=shared.goal;options=shared.options;signatures=shared.signatures;memo=shared.memo;budget=shared.budget;root=shared.root;}
        Graph(Me12Policies.Name prefix,Me12Policies.Name suffix,int difficulty,List<List<Option>> options,Budget budget) {
            this.prefix=prefix;this.suffix=suffix;this.difficulty=difficulty;this.options=options;this.budget=budget;
            memo=new HashMap<>();
            target=prefix.tier()+suffix.tier()+difficulty;goal=1<<((prefix.tier()-1)*9+(suffix.tier()-1)*3+difficulty);
            var fixed=new ArrayList<Option>();for(var id:List.of(prefix.signature(),suffix.signature()))for(var choices:options)for(var choice:choices)if(choice.choice().affixId().equals(id))fixed.add(choice);
            signatures=List.copyOf(fixed);int groups=0,flags=0;for(var o:fixed){groups+=o.groups();flags|=o.flags();}
            root=fixed.size()!=2||!legal(groups,flags)?new Node(ZERO,List.of()):visit(new State(0,2,groups,flags,1));
        }
        int tierIndex(){return (prefix.tier()-1)*3+suffix.tier()-1;}long nameWeight(){return (long)prefix.nameWeight()*suffix.nameWeight();}
        Node visit(State state) {
            var old=memo.get(state);if(old!=null)return old;
            budget.state();
            if(state.count()==target){var node=new Node((state.allocations()&goal)!=0?ONE:ZERO,List.of());memo.put(state,node);return node;}
            if(state.index()>=options.size()||state.count()+options.size()-state.index()<target){var node=new Node(ZERO,List.of());memo.put(state,node);return node;}
            var edges=new ArrayList<Edge>();
            var skip=new State(state.index()+1,state.count(),state.groups(),state.flags(),state.allocations());var skipped=visit(skip);
            if(skipped.mass().signum()>0)edges.add(new Edge(null,skip,skipped.mass()));
            for(var option:options.get(state.index())) {
                String id=option.choice().affixId();if(id.equals(prefix.signature())||id.equals(suffix.signature()))continue;
                int groups=state.groups()+option.groups(),flags=state.flags()|option.flags();if(!legal(groups,flags))continue;
                int allocation=advance(state.allocations(),id);if(allocation==0)continue;
                var next=new State(state.index()+1,state.count()+1,groups,flags,allocation);var child=visit(next);
                if(child.mass().signum()>0)edges.add(new Edge(option,next,child.mass().multiply(option.weight())));
            }
            BigInteger mass=ZERO;for(var edge:edges)mass=mass.add(edge.mass());var node=new Node(mass,List.copyOf(edges));memo.put(state,node);return node;
        }
        int advance(int mask,String id) {
            int result=0;for(int bit=0;bit<27;bit++)if((mask&(1<<bit))!=0) {
                int p=bit/9,s=bit/3%3,d=bit%3;
                if(p<prefix.tier()-1&&prefix.fillers().contains(id))result|=1<<(bit+9);
                if(s<suffix.tier()-1&&suffix.fillers().contains(id))result|=1<<(bit+3);
                if(d<difficulty)result|=1<<(bit+1);
            }return result;
        }
        Plan sample(String key,BigInteger draw) {
            var selected=new ArrayList<EnemyAffixSelection.Choice>();signatures.forEach(o->selected.add(o.choice()));Node node=root;
            while(!node.edges().isEmpty()) {
                Edge chosen=null;for(var edge:node.edges()){if(draw.compareTo(edge.mass())<0){chosen=edge;break;}draw=draw.subtract(edge.mass());}
                if(chosen==null)throw new AssertionError("ME12_GRAPH_RANGE");
                if(chosen.option()!=null){selected.add(chosen.option().choice());draw=draw.divide(chosen.option().weight());}
                node=memo.get(chosen.state());
            }
            return new Plan(key,prefix,suffix,difficulty,canonicalOwnership(prefix,suffix,difficulty,selected));
        }
        boolean accepts(Plan plan){
            if(plan.difficultySlots()!=difficulty||plan.affixes().size()!=target||root.mass().signum()==0)return false;
            var choices=new HashSet<>(plan.choices());if(choices.size()!=target||signatures.stream().anyMatch(s->!choices.contains(s.choice())))return false;
            int groups=0,flags=0;for(var s:signatures){groups+=s.groups();flags|=s.flags();}
            var state=new State(0,2,groups,flags,1);Node node=root;
            while(!node.edges().isEmpty()){
                var alternatives=options.get(state.index());boolean take=alternatives.stream().anyMatch(o->choices.contains(o.choice())&&!signatures.contains(o));
                Edge next=null;for(var edge:node.edges())if(take?edge.option()!=null&&choices.contains(edge.option().choice()):edge.option()==null){next=edge;break;}
                if(next==null)return false;state=next.state();node=memo.get(state);
            }
            return state.count()==target&&plan.affixes().equals(canonicalOwnership(prefix,suffix,difficulty,plan.choices()));
        }
    }
    public static List<Owned> canonicalOwnership(Me12Policies.Name prefix,Me12Policies.Name suffix,int difficulty,List<EnemyAffixSelection.Choice> choices) {
        var sorted=choices.stream().sorted(Comparator.comparing(EnemyAffixSelection.Choice::key)).toList();var result=new ArrayList<Owned>();
        require(sorted.stream().map(EnemyAffixSelection.Choice::affixId).distinct().count()==sorted.size(),"ME12_DUPLICATE_ID");
        require(match(sorted,0,prefix,suffix,new int[]{prefix.tier()-1,suffix.tier()-1,difficulty},result),"ME12_NO_OWNERSHIP");return List.copyOf(result);
    }
    private static boolean match(List<EnemyAffixSelection.Choice> sorted,int index,Me12Policies.Name p,Me12Policies.Name s,int[] remaining,List<Owned> result) {
        if(index==sorted.size())return Arrays.stream(remaining).sum()==0;
        var choice=sorted.get(index);var id=choice.affixId();
        if(id.equals(p.signature())||id.equals(s.signature())) {result.add(new Owned(choice,id.equals(p.signature())?Owner.PREFIX_SIGNATURE:Owner.SUFFIX_SIGNATURE));if(match(sorted,index+1,p,s,remaining,result))return true;result.removeLast();return false;}
        for(int slot=0;slot<3;slot++)if(remaining[slot]>0&&(slot==2||slot==0&&p.fillers().contains(id)||slot==1&&s.fillers().contains(id))) {
            remaining[slot]--;result.add(new Owned(choice,Owner.values()[slot+2]));if(match(sorted,index+1,p,s,remaining,result))return true;result.removeLast();remaining[slot]++;
        }return false;
    }
}
