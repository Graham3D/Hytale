package com.inigmasgames.hytale.patch;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Exact-bytecode Packbound, damage-receipt and projectile-holder patcher for 0.7.0-pre.5.1. */
public final class PatchNativeMutations {
    private static final String ORIGINAL="35a34a32175cd92ce5e2a51310953db3a4a64cac89d995cbd4830c52a9b1b904";
    private static final String BASE="com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/";
    private static final String HOOK="com/inigmasgames/hytale/patch/NativeMutationHook";
    private static final String DAMAGE_HOOK="com/inigmasgames/hytale/patch/NativeDamageReceiptHook";
    private static final String PROJECTILE_HOOK="com/inigmasgames/hytale/patch/NativeProjectileReceiptHook";
    private static final String DAMAGE_LEAF=BASE+"DamageEntityInteraction.class";
    private static final String PROJECTILE_LEAF=BASE+"LaunchProjectileInteraction.class";
    private static final String DAMAGE_LEAF_PIN="6be3b2c00e3364373cb22b622e07677e14e31dcfa2cf1df9b5e7185340836e73";
    private static final String PROJECTILE_LEAF_PIN="973c5e280b171f59c575b9e3cd4b4d0741e538eb40c8699867d16360eb13938e";
    private static final String CONTEXT="Lcom/hypixel/hytale/server/core/entity/InteractionContext;";
    private static final String REF="Lcom/hypixel/hytale/component/Ref;";
    private static final String STATS="Lit/unimi/dsi/fastutil/ints/Int2FloatMap;";
    private static final String VALUE="Lcom/hypixel/hytale/protocol/ValueType;";
    private static final String BEHAVIOR="Lcom/hypixel/hytale/protocol/ChangeStatBehaviour;";
    private static final Map<String,String> PINNED=Map.of(
            "ClearEntityEffectInteraction","20f5765988dcb491a865a19ca591966f78c19d8c46cbf2174e0d46d1b6ec235f",
            "ChangeStatInteraction","d3b7ef2eec083689ead816ba485c7aba52abb00b41dde029e9acf2bd1fb2da07",
            "ChangeStatWithModifierInteraction","7c3e0fe4fb601940b0e5fec214edcdd228ed441593e645b471b90597542595f2");
    private PatchNativeMutations(){}
    private static void require(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hash(Path path)throws Exception{try(var in=Files.newInputStream(path)){
        var digest=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[65536];int n;
        while((n=in.read(buf))!=-1)digest.update(buf,0,n);return HexFormat.of().formatHex(digest.digest());}}
    private static AbstractInsnNode previous(AbstractInsnNode node){do{node=node.getPrevious();}while(node!=null&&node.getOpcode()<0);return node;}
    private static AbstractInsnNode next(AbstractInsnNode node){do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;}
    private static AbstractInsnNode start(AbstractInsnNode call,int count){
        var node=call;for(int i=0;i<count;i++)node=previous(node);return node;
    }
    private static void shape(AbstractInsnNode node,int opcode,int local){
        require(node instanceof VarInsnNode var&&node.getOpcode()==opcode&&var.var==local,
                "NATIVE_PATCH_BYTECODE_SHAPE");
    }
    private static byte[] patch(String name,byte[] input){
        var owner=BASE+name;var tree=new ClassNode();new ClassReader(input).accept(tree,0);
        require(tree.name.equals(owner),"NATIVE_PATCH_CLASS_ID");
        var methods=tree.methods.stream().filter(m->m.name.equals("firstRun")&&m.desc.startsWith("(Lcom/hypixel/hytale/protocol/InteractionType;"))
                .toList();require(methods.size()==1,"NATIVE_PATCH_FIRST_RUN_COUNT");
        var method=methods.getFirst();var matches=new ArrayList<MethodInsnNode>();
        for(var node:method.instructions.toArray())if(node instanceof MethodInsnNode call){
            if(name.equals("ClearEntityEffectInteraction")&&call.owner.equals("com/hypixel/hytale/server/core/entity/effect/EffectControllerComponent")
                    &&call.name.equals("removeEffect")&&call.desc.equals("("+REF+"ILcom/hypixel/hytale/component/ComponentAccessor;)V"))matches.add(call);
            if(!name.equals("ClearEntityEffectInteraction")&&call.owner.equals("com/hypixel/hytale/server/core/modules/entitystats/EntityStatMap")
                    &&call.name.equals("processStatChanges")&&call.desc.equals("(Lcom/hypixel/hytale/server/core/modules/entitystats/EntityStatMap$Predictable;"+STATS+VALUE+BEHAVIOR+")V"))matches.add(call);
        }
        require(matches.size()==1,"NATIVE_PATCH_MUTATION_CALL_COUNT:"+name);
        var call=matches.getFirst();var guard=new InsnList();var allowed=new LabelNode();
        AbstractInsnNode insertion;
        if(name.equals("ClearEntityEffectInteraction")){
            insertion=start(call,7);shape(insertion,Opcodes.ALOAD,8);
            guard.add(new VarInsnNode(Opcodes.ALOAD,2));guard.add(new VarInsnNode(Opcodes.ALOAD,5));
            guard.add(new VarInsnNode(Opcodes.ALOAD,0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD,owner,"entityEffectId","Ljava/lang/String;"));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"allowClear","("+CONTEXT+REF+"Ljava/lang/String;)Z",false));
        }else{
            int target=name.equals("ChangeStatInteraction")?5:4;
            int map=7;
            insertion=start(call,7);shape(insertion,Opcodes.ALOAD,6);
            guard.add(new VarInsnNode(Opcodes.ALOAD,2));guard.add(new VarInsnNode(Opcodes.ALOAD,target));
            guard.add(new VarInsnNode(Opcodes.ALOAD,map));guard.add(new VarInsnNode(Opcodes.ALOAD,0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD,BASE+"ChangeStatBaseInteraction","valueType",VALUE));
            guard.add(new VarInsnNode(Opcodes.ALOAD,0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD,BASE+"ChangeStatBaseInteraction","changeStatBehaviour",BEHAVIOR));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,
                    name.equals("ChangeStatInteraction")?"allowStat":"allowStatWithModifier",
                    "("+CONTEXT+REF+STATS+VALUE+BEHAVIOR+")Z",false));
        }
        guard.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        // A plain return from firstRun lets SimpleInstantInteraction.tick0 advance
        // to its Next branch. Native chain cancellation prevents that follow-on
        // branch (and any Failed branch) from mutating a guarded target.
        guard.add(new TypeInsnNode(Opcodes.NEW,
                "com/hypixel/hytale/server/core/entity/InteractionManager$ChainCancelledException"));
        guard.add(new InsnNode(Opcodes.DUP));
        guard.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "com/hypixel/hytale/protocol/InteractionState","Failed",
                "Lcom/hypixel/hytale/protocol/InteractionState;"));
        guard.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "com/hypixel/hytale/server/core/entity/InteractionManager$ChainCancelledException",
                "<init>","(Lcom/hypixel/hytale/protocol/InteractionState;)V",false));
        guard.add(new InsnNode(Opcodes.ATHROW));
        guard.add(allowed);
        method.instructions.insertBefore(insertion,guard);
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        tree.accept(writer);return writer.toByteArray();
    }
    private static byte[] patchDamage(byte[] input){
        var tree=new ClassNode();new ClassReader(input).accept(tree,0);
        require(tree.name.equals(BASE+"DamageEntityInteraction"),"NATIVE_DAMAGE_PATCH_CLASS_ID");
        var methods=tree.methods.stream().filter(m->m.name.equals("attemptEntityDamage0")).toList();
        require(methods.size()==1,"NATIVE_DAMAGE_PATCH_METHOD_COUNT");
        var method=methods.getFirst();var calls=new ArrayList<MethodInsnNode>();
        for(var node:method.instructions.toArray())if(node instanceof MethodInsnNode call
                &&call.owner.equals("com/hypixel/hytale/component/CommandBuffer")
                &&call.name.equals("invoke")
                &&call.desc.equals("(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/system/EcsEvent;)V"))calls.add(call);
        require(calls.size()==1,"NATIVE_DAMAGE_PATCH_INVOKE_COUNT");
        var call=calls.getFirst();var insertion=start(call,3);
        shape(insertion,Opcodes.ALOAD,7);
        shape(next(insertion),Opcodes.ALOAD,4);
        shape(next(next(insertion)),Opcodes.ALOAD,33);
        var stamp=new InsnList();
        stamp.add(new VarInsnNode(Opcodes.ALOAD,2));
        stamp.add(new VarInsnNode(Opcodes.ALOAD,4));
        stamp.add(new VarInsnNode(Opcodes.ALOAD,33));
        stamp.add(new VarInsnNode(Opcodes.ILOAD,32));
        stamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC,DAMAGE_HOOK,"stamp",
                "(Lcom/hypixel/hytale/server/core/entity/InteractionContext;"
                        +"Lcom/hypixel/hytale/component/Ref;"
                        +"Lcom/hypixel/hytale/server/core/modules/entity/damage/Damage;I)V",false));
        method.instructions.insertBefore(insertion,stamp);
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        tree.accept(writer);return writer.toByteArray();
    }
    private static byte[] patchProjectile(byte[] input){
        var tree=new ClassNode();new ClassReader(input).accept(tree,0);
        require(tree.name.equals(BASE+"LaunchProjectileInteraction"),"NATIVE_PROJECTILE_PATCH_CLASS_ID");
        var methods=tree.methods.stream().filter(m->m.name.equals("firstRun")
                &&m.desc.startsWith("(Lcom/hypixel/hytale/protocol/InteractionType;")).toList();
        require(methods.size()==1,"NATIVE_PROJECTILE_PATCH_METHOD_COUNT");
        var method=methods.getFirst();var calls=new ArrayList<MethodInsnNode>();
        for(var node:method.instructions.toArray())if(node instanceof MethodInsnNode call
                &&call.owner.equals("com/hypixel/hytale/component/CommandBuffer")
                &&call.name.equals("addEntity")
                &&call.desc.equals("(Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/AddReason;)Lcom/hypixel/hytale/component/Ref;"))calls.add(call);
        require(calls.size()==1,"NATIVE_PROJECTILE_PATCH_QUEUE_COUNT");
        var insertion=start(calls.getFirst(),3);
        shape(insertion,Opcodes.ALOAD,4);
        shape(next(insertion),Opcodes.ALOAD,13);
        var spawn=next(next(insertion));
        require(spawn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC
                &&field.owner.equals("com/hypixel/hytale/component/AddReason")&&field.name.equals("SPAWN"),
                "NATIVE_PROJECTILE_PATCH_QUEUE_SHAPE");
        var stamp=new InsnList();
        stamp.add(new VarInsnNode(Opcodes.ALOAD,2));
        stamp.add(new VarInsnNode(Opcodes.ALOAD,13));
        stamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC,PROJECTILE_HOOK,"beforeQueue",
                "(Lcom/hypixel/hytale/server/core/entity/InteractionContext;Lcom/hypixel/hytale/component/Holder;)V",false));
        method.instructions.insertBefore(insertion,stamp);
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        tree.accept(writer);return writer.toByteArray();
    }
    private static List<String> instructionShape(MethodNode method,String injectedHook,List<String> prefix){
        var result=new ArrayList<String>();
        for(var instruction:method.instructions.toArray()){
            if(instruction.getOpcode()<0)continue;
            if(injectedHook!=null&&instruction instanceof MethodInsnNode call&&call.owner.equals(injectedHook)){
                require(result.size()>=prefix.size(),"NATIVE_RECEIPT_PATCH_PREFIX_MISSING");
                require(result.subList(result.size()-prefix.size(),result.size()).equals(prefix),
                        "NATIVE_RECEIPT_PATCH_PREFIX_CHANGED");
                result.subList(result.size()-prefix.size(),result.size()).clear();
                continue;
            }
            String operand=switch(instruction){
                case VarInsnNode v->"var:"+v.var;
                case FieldInsnNode f->"field:"+f.owner+":"+f.name+":"+f.desc;
                case MethodInsnNode m->"method:"+m.owner+":"+m.name+":"+m.desc;
                case TypeInsnNode t->"type:"+t.desc;
                case LdcInsnNode l->"ldc:"+l.cst;
                case IntInsnNode i->"int:"+i.operand;
                case IincInsnNode i->"iinc:"+i.var+":"+i.incr;
                case InvokeDynamicInsnNode d->"dynamic:"+d.name+":"+d.desc;
                case MultiANewArrayInsnNode a->"array:"+a.desc+":"+a.dims;
                case TableSwitchInsnNode t->"table:"+t.min+":"+t.max+":"+t.labels.size();
                case LookupSwitchInsnNode l->"lookup:"+l.keys;
                default->"";
            };
            result.add(instruction.getOpcode()+":"+operand);
        }
        return List.copyOf(result);
    }
    private static void verify(Path original,Path output,Set<String> hooks,Set<String> changed)throws Exception{
        try(var old=new ZipFile(original.toFile());var current=new ZipFile(output.toFile())){
            var oldEntries=new HashSet<String>();var all=old.entries();
            while(all.hasMoreElements())oldEntries.add(all.nextElement().getName());
            var currentEntries=new HashSet<String>();all=current.entries();
            while(all.hasMoreElements())currentEntries.add(all.nextElement().getName());
            var added=new HashSet<>(currentEntries);added.removeAll(oldEntries);
            require(added.equals(hooks),"NATIVE_PATCH_ADDED_ENTRIES");
            var removed=new HashSet<>(oldEntries);removed.removeAll(currentEntries);
            require(removed.isEmpty(),"NATIVE_PATCH_REMOVED_ENTRIES");
            for(var path:oldEntries){
                byte[] before,after;
                try(var in=old.getInputStream(old.getEntry(path))){before=in.readAllBytes();}
                try(var in=current.getInputStream(current.getEntry(path))){after=in.readAllBytes();}
                require(changed.contains(path)||Arrays.equals(before,after),"NATIVE_PATCH_UNRELATED_ENTRY_CHANGED:"+path);
                if(changed.contains(path)){
                    require(!Arrays.equals(before,after),"NATIVE_PATCH_EXPECTED_ENTRY_UNCHANGED:"+path);
                    var tree=new ClassNode();new ClassReader(after).accept(tree,0);
                    if(path.equals(DAMAGE_LEAF)){
                        var method=tree.methods.stream().filter(m->m.name.equals("attemptEntityDamage0")).findFirst().orElseThrow();
                        var originalTree=new ClassNode();new ClassReader(before).accept(originalTree,0);
                        var originalMethod=originalTree.methods.stream().filter(m->m.name.equals("attemptEntityDamage0")).findFirst().orElseThrow();
                        require(instructionShape(originalMethod,null,List.of()).equals(instructionShape(method,DAMAGE_HOOK,List.of(
                                Opcodes.ALOAD+":var:2",Opcodes.ALOAD+":var:4",
                                Opcodes.ALOAD+":var:33",Opcodes.ILOAD+":var:32"))),
                                "NATIVE_DAMAGE_PATCH_ORIGINAL_INSTRUCTIONS_CHANGED");
                        var calls=new ArrayList<MethodInsnNode>();
                        for(var instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call
                                &&call.owner.equals(DAMAGE_HOOK)&&call.name.equals("stamp"))calls.add(call);
                        require(calls.size()==1,"NATIVE_DAMAGE_PATCH_HOOK_COUNT");
                        var following=next(calls.getFirst());
                        require(following instanceof VarInsnNode var&&var.getOpcode()==Opcodes.ALOAD&&var.var==7,
                                "NATIVE_DAMAGE_PATCH_BEFORE_INVOKE");
                        continue;
                    }
                    if(path.equals(PROJECTILE_LEAF)){
                        var method=tree.methods.stream().filter(m->m.name.equals("firstRun")).findFirst().orElseThrow();
                        var originalTree=new ClassNode();new ClassReader(before).accept(originalTree,0);
                        var originalMethod=originalTree.methods.stream().filter(m->m.name.equals("firstRun")).findFirst().orElseThrow();
                        require(instructionShape(originalMethod,null,List.of()).equals(instructionShape(method,PROJECTILE_HOOK,List.of(
                                Opcodes.ALOAD+":var:2",Opcodes.ALOAD+":var:13"))),
                                "NATIVE_PROJECTILE_PATCH_ORIGINAL_INSTRUCTIONS_CHANGED");
                        var calls=new ArrayList<MethodInsnNode>();
                        for(var instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call
                                &&call.owner.equals(PROJECTILE_HOOK)&&call.name.equals("beforeQueue"))calls.add(call);
                        require(calls.size()==1,"NATIVE_PROJECTILE_PATCH_HOOK_COUNT");
                        var following=next(calls.getFirst());
                        require(following instanceof VarInsnNode var&&var.getOpcode()==Opcodes.ALOAD&&var.var==4,
                                "NATIVE_PROJECTILE_PATCH_BEFORE_QUEUE");
                        continue;
                    }
                    var method=tree.methods.stream().filter(m->m.name.equals("firstRun")).findFirst().orElseThrow();
                    var calls=new ArrayList<MethodInsnNode>();
                    for(var instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call
                            &&call.owner.equals(HOOK))calls.add(call);
                    require(calls.size()==1,"NATIVE_PATCH_HOOK_COUNT:"+path);
                    var branch=calls.getFirst().getNext();
                    require(branch instanceof JumpInsnNode jump&&branch.getOpcode()==Opcodes.IFNE
                            &&branch.getNext() instanceof TypeInsnNode create
                            &&create.getOpcode()==Opcodes.NEW
                            &&create.desc.equals("com/hypixel/hytale/server/core/entity/InteractionManager$ChainCancelledException")
                            &&previous(jump.label) instanceof InsnNode denied
                            &&denied.getOpcode()==Opcodes.ATHROW,
                            "NATIVE_PATCH_DENY_CANCELS_CHAIN_BEFORE_WRITER:"+path);
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3,"USAGE: PatchNativeMutations original.jar hook-classes-dir patched.jar");
        var original=Path.of(args[0]).toAbsolutePath().normalize();var classes=Path.of(args[1]).toAbsolutePath().normalize();
        var output=Path.of(args[2]).toAbsolutePath().normalize();
        require(!original.equals(output)&&Files.isRegularFile(original)&&Files.isDirectory(classes),"NATIVE_PATCH_PATHS");
        require(hash(original).equals(ORIGINAL),"NATIVE_PATCH_SERVER_HASH_MISMATCH");
        var hookClasses=List.of(HOOK+".class",HOOK+"$Kind.class",HOOK+"$Mutation.class",HOOK+"$Policy.class",
                DAMAGE_HOOK+".class",DAMAGE_HOOK+"$Context.class",DAMAGE_HOOK+"$Provider.class",
                PROJECTILE_HOOK+".class",PROJECTILE_HOOK+"$Context.class",PROJECTILE_HOOK+"$Provider.class");
        var hooks=new LinkedHashMap<String,byte[]>();
        for(var hookClass:hookClasses)hooks.put(hookClass,Files.readAllBytes(classes.resolve(hookClass)));
        var patches=new HashMap<String,byte[]>();
        try(var source=new ZipFile(original.toFile())){
            for(var hookClass:hookClasses)require(source.getEntry(hookClass)==null,"NATIVE_PATCH_ALREADY_PRESENT");
            for(var entry:PINNED.entrySet()){
                var path=BASE+entry.getKey()+".class";var zip=source.getEntry(path);
                require(zip!=null,"NATIVE_PATCH_CLASS_MISSING:"+path);
                byte[] bytes;try(var in=source.getInputStream(zip)){bytes=in.readAllBytes();}
                require(hash(bytes).equals(entry.getValue()),"NATIVE_PATCH_CLASS_HASH_MISMATCH:"+path);
                patches.put(path,patch(entry.getKey(),bytes));
            }
            var damageEntry=source.getEntry(DAMAGE_LEAF);
            require(damageEntry!=null,"NATIVE_DAMAGE_PATCH_CLASS_MISSING");
            byte[] damageBytes;try(var in=source.getInputStream(damageEntry)){damageBytes=in.readAllBytes();}
            require(hash(damageBytes).equals(DAMAGE_LEAF_PIN),"NATIVE_DAMAGE_PATCH_CLASS_HASH_MISMATCH");
            patches.put(DAMAGE_LEAF,patchDamage(damageBytes));
            var projectileEntry=source.getEntry(PROJECTILE_LEAF);
            require(projectileEntry!=null,"NATIVE_PROJECTILE_PATCH_CLASS_MISSING");
            byte[] projectileBytes;try(var in=source.getInputStream(projectileEntry)){projectileBytes=in.readAllBytes();}
            require(hash(projectileBytes).equals(PROJECTILE_LEAF_PIN),"NATIVE_PROJECTILE_PATCH_CLASS_HASH_MISMATCH");
            patches.put(PROJECTILE_LEAF,patchProjectile(projectileBytes));
            Files.createDirectories(output.getParent());
            var temp=Files.createTempFile(output.getParent(),"native-packbound-",".jar");
            try{
                try(var out=new ZipOutputStream(Files.newOutputStream(temp))){
                    var all=source.entries();byte[] buffer=new byte[65536];
                    while(all.hasMoreElements()){
                        var old=all.nextElement();var next=new ZipEntry(old.getName());next.setTime(old.getTime());
                        out.putNextEntry(next);var replacement=patches.get(old.getName());
                        if(replacement!=null)out.write(replacement);
                        else try(var in=source.getInputStream(old)){int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                        out.closeEntry();
                    }
                    for(var hook:hooks.entrySet()){
                        var added=new ZipEntry(hook.getKey());added.setTime(0L);
                        out.putNextEntry(added);out.write(hook.getValue());out.closeEntry();
                    }
                }
                Files.move(temp,output,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temp);}
        }
        verify(original,output,hooks.keySet(),patches.keySet());
        System.out.println("NATIVE_PATCH_ORIGINAL_SHA256="+ORIGINAL);
        System.out.println("NATIVE_PATCH_OUTPUT_SHA256="+hash(output));
        System.out.println("NATIVE_PACKBOUND_HOOK_ID="+NativeMutationHook.PATCH_ID);
        System.out.println("NATIVE_DAMAGE_RECEIPT_HOOK_ID="+NativeDamageReceiptHook.PATCH_ID);
        System.out.println("NATIVE_PROJECTILE_RECEIPT_HOOK_ID="+NativeProjectileReceiptHook.PATCH_ID);
    }
}
