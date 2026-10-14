package com.inigmasgames.hytale.patch;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** In-memory negative fixtures for the exact Chapter I patcher. Never writes a game installation. */
public final class NativePatchRegression {
    private static final String BASE="com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/";
    private static final String POPULATION="com/hypixel/hytale/server/spawning/world/system/WorldSpawningSystem.class";
    private static final String MODERN="com/hypixel/hytale/server/core/modules/projectile/interaction/ProjectileInteraction.class";

    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    private static byte[] entry(ZipFile file,String name)throws Exception {
        try(var input=file.getInputStream(file.getEntry(name))){return input.readAllBytes();}
    }

    private static byte[] removeExpectedCall(byte[] bytes,String calledMethod){
        var tree=new ClassNode();new ClassReader(bytes).accept(tree,0);
        int changed=0;
        for(var method:tree.methods)for(var instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&call.name.equals(calledMethod)){
                call.name="layoutChangedForRegression";changed++;
            }
        require(changed>0,"fixture did not locate "+calledMethod);
        var writer=new ClassWriter(0);tree.accept(writer);return writer.toByteArray();
    }

    private static void expectShapeRejection(String operation,String name,byte[] bytes,String expectedCode)throws Exception {
        var method=switch(operation){
            case "mutation" ->PatchNativeMutations.class.getDeclaredMethod("patch",String.class,byte[].class);
            case "damage" ->PatchNativeMutations.class.getDeclaredMethod("patchDamage",byte[].class);
            case "projectile" ->PatchNativeMutations.class.getDeclaredMethod("patchProjectile",byte[].class);
            case "modern" ->PatchNativeMutations.class.getDeclaredMethod("patchModernProjectile",byte[].class);
            case "population" ->PatchNativeMutations.class.getDeclaredMethod("patchPopulation",byte[].class);
            default ->throw new AssertionError(operation);
        };
        method.setAccessible(true);
        try {
            if(operation.equals("mutation"))method.invoke(null,name,bytes);
            else method.invoke(null,(Object)bytes);
            throw new AssertionError("changed "+name+" layout was accepted");
        } catch(InvocationTargetException expected){
            require(expected.getCause() instanceof IllegalStateException
                    &&expected.getCause().getMessage().startsWith(expectedCode),
                    "unexpected "+name+" rejection: "+expected.getCause());
        }
    }

    public static void main(String[] args)throws Exception {
        require(args.length==2,"usage: original.jar patched.jar");
        var original=Path.of(args[0]);var patched=Path.of(args[1]);
        try(var stock=new ZipFile(original.toFile());var output=new ZipFile(patched.toFile())){
            for(var fixture:List.of(
                    new String[]{"ClearEntityEffectInteraction","removeEffect","mutation","NATIVE_PATCH_MUTATION_CALL_COUNT"},
                    new String[]{"ChangeStatInteraction","processStatChanges","mutation","NATIVE_PATCH_MUTATION_CALL_COUNT"},
                    new String[]{"ChangeStatWithModifierInteraction","processStatChanges","mutation","NATIVE_PATCH_MUTATION_CALL_COUNT"},
                    new String[]{"DamageEntityInteraction","invoke","damage","NATIVE_DAMAGE_PATCH_INVOKE_COUNT"},
                    new String[]{"LaunchProjectileInteraction","addEntity","projectile","NATIVE_PROJECTILE_PATCH_QUEUE_COUNT"})){
                var path=BASE+fixture[0]+".class";
                require(!java.util.Arrays.equals(entry(stock,path),entry(output,path)),"class unchanged: "+path);
                expectShapeRejection(fixture[2],fixture[0],removeExpectedCall(entry(stock,path),fixture[1]),fixture[3]);
            }
            require(!java.util.Arrays.equals(entry(stock,POPULATION),entry(output,POPULATION)),"population unchanged");
            require(!java.util.Arrays.equals(entry(stock,MODERN),entry(output,MODERN)),"modern projectile unchanged");
            expectShapeRejection("modern","ProjectileInteraction",
                    removeExpectedCall(entry(stock,MODERN),"spawnProjectile"),"NATIVE_MODERN_PROJECTILE_SPAWN_SHAPE");
            expectShapeRejection("population","WorldSpawningSystem",
                    removeExpectedCall(entry(stock,POPULATION),"getActiveSpawnJobs"),"NATIVE_POPULATION_SELECTION_SHAPE");
        }
        for(var name:List.of("ClearEntityEffectInteraction","ChangeStatInteraction",
                "ChangeStatWithModifierInteraction","DamageEntityInteraction","LaunchProjectileInteraction"))
            Class.forName((BASE+name).replace('/','.'),false,NativePatchRegression.class.getClassLoader());
        Class.forName(POPULATION.substring(0,POPULATION.length()-6).replace('/','.'),false,
                NativePatchRegression.class.getClassLoader());
        Class.forName(MODERN.substring(0,MODERN.length()-6).replace('/','.'),false,
                NativePatchRegression.class.getClassLoader());
        var refused=Files.createTempFile(patched.getParent(),"duplicate-native-patch-",".jar");
        Files.delete(refused);
        try {
            try {
                PatchNativeMutations.main(new String[]{patched.toString(),patched.getParent().resolve("classes").toString(),refused.toString()});
                throw new AssertionError("unknown/previously patched server accepted");
            } catch(IllegalStateException expected){
                require(expected.getMessage().equals("NATIVE_PATCH_SERVER_HASH_MISMATCH"),"unexpected server rejection");
            }
            require(!Files.exists(refused),"rejected patch wrote output");
        } finally {Files.deleteIfExists(refused);}
        System.out.println("NATIVE_PATCH_REGRESSION_PASS classes=7 unknownServerRejected=true duplicateRejected=true");
    }
}
