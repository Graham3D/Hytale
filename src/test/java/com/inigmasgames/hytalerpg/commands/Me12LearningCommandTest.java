package com.inigmasgames.hytalerpg.commands;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12LearningCommandTest {
    @Test void registeredAuthoringTreeRequiresExplicitPermissionAtEveryLevel(){
        var command=new RpgLearningCommand(null,()->Runnable::run);
        command.setOwner(new com.hypixel.hytale.server.core.command.system.CommandManager());
        assertEquals(Set.of("status","validate","reload"),command.getSubCommands().keySet());
        assertEquals(RpgLearningCommand.PERMISSION,command.getPermission());
        assertFalse(command.getPermissionGroups().contains("hytale:Adventurer"));
        for(var child:command.getSubCommands().values()){
            assertEquals(RpgLearningCommand.PERMISSION,child.getPermission());
            assertFalse(child.getPermissionGroups().contains("hytale:Adventurer"));
        }
    }
}
