package com.inigmasgames.hytalerpg.difficulty;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class DifficultyNativeCommandTest {
    @Test void authoringAndForcedTravelUseSeparateExplicitPermissions(){
        var command=new com.inigmasgames.hytalerpg.commands.RpgDifficultyCommand(null,null,null,null,null,Runnable::run,null);
        for(String action:List.of("portal","remove","encounter","prepare"))assertEquals(com.inigmasgames.hytalerpg.commands.RpgDifficultyCommand.AUTHOR_PERMISSION,command.getSubCommand(action).getPermission());
        assertEquals(com.inigmasgames.hytalerpg.commands.RpgDifficultyCommand.FORCE_PERMISSION,command.getSubCommand("force").getPermission());
        assertNotEquals(command.getSubCommand("force").getPermission(),command.getSubCommand("status").getPermission());
    }
}
