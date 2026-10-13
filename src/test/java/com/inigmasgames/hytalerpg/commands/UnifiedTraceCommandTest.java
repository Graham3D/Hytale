package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.permissions.PermissionQuery;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedTraceCommandTest {
    private static RpgTraceCommand command(){
        var root=new RpgTraceCommand(null,null,null,null,null,null,null,null,null,null);
        root.setOwner(new com.hypixel.hytale.server.core.command.system.CommandManager());
        return root;
    }
    private static AbstractCommand leaf(RpgTraceCommand root,String category,String action){
        return root.getSubCommand(category).getSubCommand(action);
    }
    private static CommandSender sender(Predicate<String> granted){
        return (CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},(proxy,method,args)->{
                    if(method.getName().equals("hasPermission")){
                        Object key=args[0];return granted.test(key instanceof PermissionQuery query?query.getId():String.valueOf(key));
                    }
                    if(method.getReturnType()==boolean.class)return false;
                    return null;
                });
    }
    @Test void categoriesHaveNativeSubcommandsAndNoLegacyModes(){
        var root=command();
        assertEquals(Set.of("status","list","help","skill","combat","skilltree","inventory","ui",
                "gear","sentinel","spawn","inventory-tab","inventory-drop","patch"),root.getSubCommands().keySet());
        for(String category: new String[]{"skill","combat","skilltree","inventory","ui","gear","sentinel","spawn",
                "inventory-tab","inventory-drop","patch"}){
            assertNotNull(leaf(root,category,"start"));
            assertNotNull(leaf(root,category,"stop"));
            assertNotNull(leaf(root,category,"status"));
        }
        assertNotNull(leaf(root,"skill","start").getSubCommand("detailed"));
        assertNotNull(leaf(root,"skill","start").getSubCommand("performance"));
        assertNotNull(leaf(root,"sentinel","snapshot"));
        assertNotNull(leaf(root,"combat","inspect"));
        assertNull(root.getSubCommand("normal"));
    }
    @Test void categoryPermissionsBypassOnlyTheParentAndRetainOldGates(){
        var root=command();
        var gear=sender(RpgGearCommand.AUTHOR_PERMISSION::equals);
        assertTrue(leaf(root,"gear","start").hasPermission(gear));
        assertTrue(leaf(root,"sentinel","snapshot").hasPermission(gear));
        for(String action:new String[]{"start","stop","status","mark"})
            assertTrue(leaf(root,"gear",action).hasPermission(gear));
        assertFalse(leaf(root,"ui","start").hasPermission(gear));
        assertFalse(leaf(root,"spawn","start").hasPermission(gear));
        assertFalse(leaf(root,"combat","start").hasPermission(gear));
        assertFalse(leaf(root,"combat","last").hasPermission(gear));
        assertFalse(leaf(root,"combat","inspect").hasPermission(gear));
        assertFalse(leaf(root,"skill","start").getSubCommand("detailed").hasPermission(gear));
        assertFalse(leaf(root,"skill","start").getSubCommand("performance").hasPermission(gear));

        var ui=sender("inigmasgames.rpg.uitrace"::equals);
        for(String category:new String[]{"ui","inventory","skilltree"})
            assertTrue(leaf(root,category,"start").hasPermission(ui));
        assertFalse(leaf(root,"gear","start").hasPermission(ui));

        var spawn=sender("inigmasgames.rpg.spawntrace"::equals);
        assertTrue(leaf(root,"spawn","start").hasPermission(spawn));
        assertFalse(leaf(root,"gear","start").hasPermission(spawn));

        var tab=leaf(root,"inventory-tab","start");
        var drop=leaf(root,"inventory-drop","stop");
        assertTrue(tab.getPermissionGroups().contains("hytale:Adventurer"));
        assertTrue(drop.getPermissionGroups().contains("hytale:Adventurer"));
        var adventurer=sender(Set.of(tab.getPermission(),drop.getPermission())::contains);
        assertTrue(tab.hasPermission(adventurer));
        assertTrue(drop.hasPermission(adventurer));
        assertFalse(leaf(root,"combat","start").hasPermission(adventurer));
        for(String action:new String[]{"start","stop","status"}) {
            assertFalse(leaf(root,"patch",action).hasPermission(adventurer));
            assertFalse(leaf(root,"patch",action).hasPermission(ui));
            assertTrue(leaf(root,"patch",action).hasPermission(sender(ignored->true)));
            assertTrue(leaf(root,"patch",action).hasPermission(sender(RpgTraceCommand.PATCH_PERMISSION::equals)));
            assertEquals(RpgTraceCommand.PATCH_PERMISSION,leaf(root,"patch",action).getPermission());
        }

        var lightQa=new RpgLightQaCommand();
        lightQa.setOwner(new com.hypixel.hytale.server.core.command.system.CommandManager());
        for(String action:new String[]{"light","lightprobe","lightcal"}){
            assertTrue(lightQa.getSubCommand(action).hasPermission(gear));
            assertFalse(lightQa.getSubCommand(action).hasPermission(ui));
        }
    }
}
