package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.enemies.EnemyRewardContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** File adapter for the existing learning registry. No ownership, pity, RNG or reward ledger. */
public final class LearningSourceConfiguration {
    public static final int MAX_BYTES=262144;
    public static final String EMPTY="{\"schemaVersion\":2,\"assignments\":[]}";
    public static final class Hold extends IllegalStateException{public Hold(Throwable cause){super("WAITING_FOR_LEARNING_ASSIGNMENT_ARCHIVE",cause);}}
    public record Entry(String sourceId,String canonicalRoleId,String combatIdentity,Set<String> nativeRoleIds,
            LearningSources.AbilityKind kind,String abilityId,boolean enabled,ProgressionMath.AcquisitionRarity rarity,
            ProgressionMath.Rank minimumRank,Set<DifficultyId> difficulties,Set<ProgressionMath.Rank> encounterRanks,
            boolean allowInitialMinions,boolean sourceAccepted,boolean abilityReady,String approvalEvidence){
        public Entry {nativeRoleIds=Set.copyOf(nativeRoleIds);difficulties=Set.copyOf(difficulties);encounterRanks=Set.copyOf(encounterRanks);}
    }
    public record Snapshot(String revision,List<Entry> assignments,LearningSources registry){
        public Snapshot{assignments=List.copyOf(assignments);}
        public Optional<LearningSources.Opportunity> resolve(EnemyRewardRegistry.Spawn spawn,double wisdom){
            if(spawn.milestone()!=null||spawn.combat()==null||spawn.enemyRewards()!=null&&!spawn.enemyRewards().economic())return Optional.empty();
            var entry=assignments.stream().filter(e->e.enabled()&&e.combatIdentity().equals(spawn.combatIdentity())
                    &&e.nativeRoleIds().contains(spawn.roleId())&&e.difficulties().contains(spawn.combat().difficulty())
                    &&e.encounterRanks().contains(spawn.rank())
                    &&(e.allowInitialMinions()||spawn.enemyRewards()==null||!spawn.enemyRewards().minion())).findFirst();
            if(entry.isEmpty())return Optional.empty();
            return registry.resolve(spawn.combatIdentity(),spawn.rank(),wisdom).map(o->new LearningSources.Opportunity(
                    o.skill(),o.source(),o.rarity(),o.effectiveWisdom(),spawn.enemyRewards(),o.kind(),revision));
        }
    }
    private final Path file,archives;
    private final RpgCatalog catalog;
    private final EnemyRewardRegistry sources;
    private final java.util.function.Predicate<String> implementedSkill;
    private volatile Snapshot active;
    public LearningSourceConfiguration(Path directory,RpgCatalog catalog,EnemyRewardRegistry sources,
                                      java.util.function.Predicate<String> implementedSkill)throws IOException{
        this.file=directory.resolve("learning-sources.json");this.archives=directory.resolve("learning-source-archives");
        this.catalog=Objects.requireNonNull(catalog);this.sources=Objects.requireNonNull(sources);this.implementedSkill=Objects.requireNonNull(implementedSkill);
        Files.createDirectories(directory);Files.createDirectories(archives);
        if(!Files.exists(file))try{Files.writeString(file,EMPTY,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException concurrent){if(!Files.isRegularFile(file))throw concurrent;/* Read the concurrently created owner's file. */}
        active=parse(EMPTY);
        Path pointer=directory.resolve("learning-sources.active");
        if(Files.exists(pointer)){
            String revision=Files.readString(pointer,StandardCharsets.UTF_8);
            if(!revision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("LEARNING_ACTIVE_REVISION");
            String previous=read(archives.resolve(revision+".json"));
            if(!RewardIntent.digest(previous).equals(revision))throw new IllegalArgumentException("LEARNING_ARCHIVE_HASH");
            active=parse(previous,true);
        }
        // A bad startup edit cannot activate mappings. Historical archives remain available to pending rewards.
        try{reload();}catch(IOException|RuntimeException invalid){startupRejection=invalid.getMessage();}
    }
    private volatile String startupRejection;
    public Path file(){return file;}
    public Snapshot snapshot(){return active;}
    public String status(){var snapshot=active;return "revision="+snapshot.revision()+" enabled="+snapshot.registry().verifiedBindings()+" file="+file+" startupRejection="+startupRejection;}
    public synchronized Snapshot validate()throws IOException{return checked(read(file));}
    public synchronized Snapshot reload()throws IOException{
        String bytes=read(file);Snapshot next=checked(bytes);Path archive=archives.resolve(next.revision()+".json");
        if(Files.exists(archive)){if(!read(archive).equals(bytes))throw new IllegalArgumentException("LEARNING_ARCHIVE_CONFLICT");}
        else {
            Path temporary=Files.createTempFile(archives,"pending-",".tmp");
            try{Files.writeString(temporary,bytes,StandardCharsets.UTF_8);try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
                Files.move(temporary,archive,StandardCopyOption.ATOMIC_MOVE);
            }finally{Files.deleteIfExists(temporary);}
        }
        Path pointer=Files.createTempFile(Objects.requireNonNull(file.getParent(),"LEARNING_DIRECTORY_REQUIRED"),"learning-active-",".tmp");
        try{Files.writeString(pointer,next.revision(),StandardCharsets.UTF_8);try(var channel=java.nio.channels.FileChannel.open(pointer,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(pointer,file.resolveSibling("learning-sources.active"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(pointer);}
        active=next;startupRejection=null;return next;
    }
    /** A source's historical ability and rarity are stable pity identities, even when disabled. */
    private Snapshot checked(String bytes)throws IOException{
        Snapshot next=parse(bytes);
        int count=0;long total=0;
        try(var paths=Files.newDirectoryStream(archives,"*.json")){
            for(Path path:paths){if(++count>4096||(total+=Files.size(path))>64L*1024*1024)throw new IllegalArgumentException("LEARNING_ARCHIVE_REVIEW_LIMIT");
                String oldBytes=read(path);if(!Objects.requireNonNull(path.getFileName(),"LEARNING_ARCHIVE_NAME_REQUIRED").toString().equals(RewardIntent.digest(oldBytes)+".json"))throw new IllegalArgumentException("LEARNING_ARCHIVE_HASH");
                var old=parse(oldBytes,true);
                var bySource=new HashMap<String,Entry>();var byCombat=new HashMap<String,Entry>();var byAbility=new HashMap<String,Entry>();
                for(var prior:old.assignments())if(prior.enabled()){bySource.put(prior.sourceId(),prior);byCombat.put(prior.combatIdentity(),prior);byAbility.put(prior.kind()+"/"+prior.abilityId(),prior);}
                for(var entry:next.assignments()){
                    var prior=bySource.get(entry.sourceId());
                    if(prior!=null&&(!prior.abilityId().equals(entry.abilityId())||prior.kind()!=entry.kind()||prior.rarity()!=entry.rarity()
                            ||!prior.canonicalRoleId().equals(entry.canonicalRoleId())||!prior.combatIdentity().equals(entry.combatIdentity())))throw new IllegalArgumentException("LEARNING_HISTORICAL_SOURCE_IDENTITY");
                    var combat=byCombat.get(entry.combatIdentity());var ability=byAbility.get(entry.kind()+"/"+entry.abilityId());
                    if(combat!=null&&!combat.sourceId().equals(entry.sourceId())||ability!=null&&!ability.sourceId().equals(entry.sourceId()))throw new IllegalArgumentException("LEARNING_HISTORICAL_SOURCE_IDENTITY");
                }
            }
        }return next;
    }
    /** The frozen death plan names its immutable assignment archive, never the live mapping. */
    public boolean accepts(LearningSources.Opportunity opportunity){
        String revision=opportunity.assignmentRevision();if(revision==null||!revision.matches("[a-f0-9]{64}"))return false;
        try{String bytes=read(archives.resolve(revision+".json"));if(!RewardIntent.digest(bytes).equals(revision))throw new IllegalArgumentException("LEARNING_ARCHIVE_HASH");
            return parse(bytes,true).assignments().stream().anyMatch(e->e.enabled()&&e.sourceId().equals(opportunity.source())&&e.kind()==opportunity.abilityKind()
                    &&e.abilityId().equals(opportunity.skill())&&e.rarity()==opportunity.rarity());
        }catch(IOException|IllegalArgumentException missing){throw new Hold(missing);}
    }
    public boolean accepts(ProgressionDelta delta){
        String revision=delta.assignmentRevision();if(revision==null||!revision.matches("[a-f0-9]{64}"))return false;
        var kind=switch(delta.kind()){
            case LEARNING_SUCCESS,LEARNING_FAILURE->LearningSources.AbilityKind.SKILL;
            case PASSIVE_LEARNING_SUCCESS,PASSIVE_LEARNING_FAILURE->LearningSources.AbilityKind.FIRST_COPY_PASSIVE;
            default->null;
        };
        if(kind==null)return false;
        try{String bytes=read(archives.resolve(revision+".json"));if(!RewardIntent.digest(bytes).equals(revision))throw new IllegalArgumentException("LEARNING_ARCHIVE_HASH");
            return parse(bytes,true).assignments().stream().anyMatch(e->e.enabled()&&e.sourceId().equals(delta.source())&&e.kind()==kind&&e.abilityId().equals(delta.subject()));
        }catch(IOException|IllegalArgumentException missing){throw new Hold(missing);}
    }
    private Snapshot parse(String bytes){
        return parse(bytes,false);
    }
    private Snapshot parse(String bytes,boolean historical){
        if(bytes.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("LEARNING_CONFIG_SIZE");
        try(var reader=new JsonReader(new StringReader(bytes))){reader.setStrictness(Strictness.STRICT);tokens(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("LEARNING_CONFIG_TRAILING");
        }catch(IOException error){throw new IllegalArgumentException("LEARNING_CONFIG_JSON",error);}
        var root=JsonParser.parseString(bytes).getAsJsonObject();
        if(!root.keySet().equals(Set.of("schemaVersion","assignments"))||!root.get("schemaVersion").toString().equals("2"))throw new IllegalArgumentException("LEARNING_CONFIG_SCHEMA");
        var entries=new ArrayList<Entry>();var bindings=new ArrayList<LearningSources.Binding>();var identities=new HashSet<String>();var sourceIds=new HashSet<String>();var abilities=new HashSet<String>();
        var array=root.getAsJsonArray("assignments");if(array.size()>2048)throw new IllegalArgumentException("LEARNING_BINDING_BUDGET");
        for(var item:array){var object=item.getAsJsonObject();
            if(!object.keySet().equals(Set.of("sourceId","canonicalRoleId","combatIdentity","nativeRoleIds","kind","abilityId","enabled","rarity","minimumRank","difficulties","encounterRanks","allowInitialMinions","sourceAccepted","abilityReady","approvalEvidence")))throw new IllegalArgumentException("LEARNING_ENTRY_SCHEMA");
            for(String field:List.of("enabled","allowInitialMinions","sourceAccepted","abilityReady"))if(!object.get(field).isJsonPrimitive()||!object.getAsJsonPrimitive(field).isBoolean())throw new IllegalArgumentException("LEARNING_BOOLEAN");
            var entry=new Gson().fromJson(object,Entry.class);AcquisitionProgress.id(entry.sourceId());
            if(!identities.add(entry.combatIdentity())||!sourceIds.add(entry.sourceId())||!abilities.add(entry.kind()+"/"+entry.abilityId()))throw new IllegalArgumentException("LEARNING_ONE_SOURCE_ONE_ABILITY");
            if(entry.nativeRoleIds().isEmpty()||entry.difficulties().isEmpty()||entry.encounterRanks().isEmpty()||entry.minimumRank()==null||entry.rarity()==null||entry.kind()==null)throw new IllegalArgumentException("LEARNING_RESTRICTIONS_REQUIRED");
            if(!historical)for(String alias:entry.nativeRoleIds()){
                var nativeRole=sources.resolveRole(alias).orElseThrow(()->new IllegalArgumentException("LEARNING_UNKNOWN_NATIVE_SOURCE:"+alias));
                if(!nativeRole.canonical().roleId().equals(entry.canonicalRoleId())||!nativeRole.canonical().combatIdentity().equals(entry.combatIdentity()))throw new IllegalArgumentException("LEARNING_CANONICAL_SOURCE_MISMATCH");
            }
            if(entry.kind()==LearningSources.AbilityKind.SKILL){if(catalog.skill(new SkillId(entry.abilityId())).isEmpty())throw new IllegalArgumentException("UNKNOWN_SKILL_ASSIGNMENT");
                if(!historical&&entry.enabled()&&!implementedSkill.test(entry.abilityId()))throw new IllegalArgumentException("LEARNING_SKILL_NOT_IMPLEMENTED");
            }else if(catalog.passive(new PassiveId(entry.abilityId())).isEmpty())throw new IllegalArgumentException("UNKNOWN_PASSIVE_ASSIGNMENT");
            if(entry.enabled()&&(!entry.sourceAccepted()||!entry.abilityReady()||entry.approvalEvidence()==null||entry.approvalEvidence().isBlank()))throw new IllegalArgumentException("LEARNING_OWNER_READINESS_REQUIRED");
            entries.add(entry);
            if(entry.enabled())bindings.add(new LearningSources.Binding(entry.combatIdentity(),entry.sourceId(),entry.rarity(),new LearningSources.Assignment(entry.kind(),entry.abilityId(),"VERIFIED_CONNECTED",entry.approvalEvidence(),entry.minimumRank())));
        }
        return new Snapshot(RewardIntent.digest(bytes),entries,new LearningSources(catalog,bindings,true));
    }
    private static String read(Path path)throws IOException{if(Files.size(path)>MAX_BYTES)throw new IllegalArgumentException("LEARNING_CONFIG_SIZE");return Files.readString(path,StandardCharsets.UTF_8);}
    private static void tokens(JsonReader reader,int depth)throws IOException{
        if(depth>16)throw new IllegalArgumentException("LEARNING_CONFIG_DEPTH");
        switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();var keys=new HashSet<String>();while(reader.hasNext()){if(!keys.add(reader.nextName()))throw new IllegalArgumentException("LEARNING_DUPLICATE_KEY");tokens(reader,depth+1);}reader.endObject();}
            case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())tokens(reader,depth+1);reader.endArray();}
            case STRING,NUMBER->reader.nextString();case BOOLEAN->reader.nextBoolean();case NULL->throw new IllegalArgumentException("LEARNING_NULL");default->throw new IllegalArgumentException("LEARNING_CONFIG_TOKEN");
        }
    }
}
