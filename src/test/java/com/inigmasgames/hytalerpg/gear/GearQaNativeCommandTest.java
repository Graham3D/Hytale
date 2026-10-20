package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.commands.RpgGearCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GearQaNativeCommandTest {
    @Test void commandNamespaceAndPermissionAreExplicit(){
        var command=new RpgGearCommand(null);
        for(String type:GearQaRequest.typeTokens()){
            assertNotNull(command.getSubCommand(type),type);
            assertEquals(RpgGearCommand.AUTHOR_PERMISSION,command.getSubCommand(type).getPermission(),type);
        }
        assertNotNull(command.getSubCommand("spawn"));
        assertNotNull(command.getSubCommand("inspect"));
        assertNotNull(command.getSubCommand("types"));
        assertNotNull(command.getSubCommand("rarities"));
        assertEquals(RpgGearCommand.AUTHOR_PERMISSION,command.getSubCommand("odds").getPermission());
    }

}
