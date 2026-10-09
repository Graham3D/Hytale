package com.inigmasgames.hytalerpg.commands;

import com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Player-facing wording for the existing QA planner's results and rejections. */
final class QaSpawnFeedback {
    private QaSpawnFeedback() { }

    static String success(String role, List<String> affixIds) {
        String monster=monster(role);
        return affixIds.isEmpty()?monster+" spawned.":monster+" spawned with "+
                joined(affixIds.stream().map(QaSpawnFeedback::affix).toList())+".";
    }

    static String failure(Throwable error, String role, String era) {
        while(error.getCause()!=null&&(error instanceof CompletionException||error instanceof ExecutionException))
            error=error.getCause();
        String message=error.getMessage();
        String monster=monster(role);
        if(message==null)return "Could not spawn "+monster+". Check the server log for details.";
        if(message.startsWith("QA_AFFIX_CAPABILITY_UNSUPPORTED:")) {
            String[] fields=message.split(":",3);
            List<String> names=new ArrayList<>();
            if(fields.length>1)for(String id:fields[1].split(","))names.add(affix(id));
            if(!names.isEmpty())return joined(names)+(names.size()==1?" is":" are")+
                    " incompatible with "+monster+".";
        }
        if(message.equals("QA_NO_SUPPORTED_RANDOM_AFFIX_SET"))
            return "No compatible affix combination is available for "+monster+" in "+title(era)+".";
        if(message.startsWith("QA_SPAWN_NO_VALID_POSITION"))
            return "Could not find a safe place to spawn "+monster+" nearby.";
        if(message.equals("ENEMY_QA_WORLD_UNAVAILABLE"))
            return "This world is not ready to spawn "+monster+". Try again shortly.";
        if(message.equals("QA_ROLE_UNAVAILABLE_POST_SPAWN"))
            return monster+" could not finish spawning. Check the server log for details.";
        if(message.startsWith("Use a concrete role")||message.startsWith("Unknown concrete monster role")
                ||message.startsWith("Unknown affix")||message.startsWith("Duplicate affix")
                ||message.startsWith("Duplicate Master Enemies affix")
                ||message.startsWith("Family must")||message.startsWith("Era must")
                ||message.matches("(?i)^(normal|nightmare|hell) (champion|unique|super unique) requires .*"))
            return message;
        return "Could not spawn "+monster+". Check the server log for details.";
    }

    static String diagnostic(Throwable error) {
        while(error.getCause()!=null&&(error instanceof CompletionException||error instanceof ExecutionException))
            error=error.getCause();
        return error.getClass().getSimpleName()+":"+error.getMessage();
    }

    private static String monster(String role) {
        return role==null||role.isBlank()?"monster":role.replace('_',' ');
    }

    private static String affix(String id) {
        try{return EnemyAffixRegistry.canonical().require(id).displayName();}
        catch(RuntimeException unknown){return "Unknown affix";}
    }

    private static String title(String value) {
        if(value==null||value.isBlank())return "this era";
        String lower=value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0))+lower.substring(1);
    }

    private static String joined(List<String> names) {
        if(names.size()==1)return names.getFirst();
        if(names.size()==2)return names.getFirst()+" and "+names.getLast();
        return String.join(", ",names.subList(0,names.size()-1))+", and "+names.getLast();
    }
}
