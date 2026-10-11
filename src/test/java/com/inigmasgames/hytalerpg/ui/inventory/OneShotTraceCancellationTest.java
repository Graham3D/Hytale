package com.inigmasgames.hytalerpg.ui.inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Seeds a recorder session directly so cancellation can be tested without a native server or player. */
class OneShotTraceCancellationTest {
    @TempDir Path directory;

    @SuppressWarnings("unchecked")
    private static void seed(Object probe,UUID player,Object session) throws Exception {
        var field=probe.getClass().getDeclaredField("sessions");field.setAccessible(true);
        ((Map<UUID,Object>)field.get(probe)).put(player,session);
    }
    @Test void tabCancellationWritesPartialEvidenceAndIsIdempotent() throws Exception {
        UUID player=UUID.randomUUID();Path file=directory.resolve("tab-cancel.jsonl");
        try(var probe=new TabTraceProbe(directory)){
            var session=new TabTraceProbe.Session(player,file);
            session.note("ARMED",Map.of("marker","before-tab"));
            seed(probe,player,session);
            assertTrue(probe.status(player).contains("armed"));
            assertTrue(probe.cancel(player).contains(file.toString()));
            assertTrue(probe.cancel(player).contains("not armed"));
            String rows=Files.readString(file);
            assertTrue(rows.contains("before-tab"));
            assertTrue(rows.contains("OPERATOR_CANCELLED"));
        }
    }
    @Test void nativeDropCancellationWritesPartialEvidenceAndRestoresState() throws Exception {
        UUID player=UUID.randomUUID();Path file=directory.resolve("drop-cancel.jsonl");
        try(var probe=new NativeInventoryDropTraceProbe(directory,null)){
            var type=Class.forName(NativeInventoryDropTraceProbe.class.getName()+"$Session");
            var ctor=type.getDeclaredConstructor(UUID.class,Path.class);ctor.setAccessible(true);
            Object session=ctor.newInstance(player,file);
            var note=type.getDeclaredMethod("note",String.class,Map.class);note.setAccessible(true);
            note.invoke(session,"ARMED",Map.of("marker","before-drop"));
            seed(probe,player,session);
            assertTrue(probe.status(player).contains("armed"));
            assertTrue(probe.cancel(player).contains(file.toString()));
            assertTrue(probe.cancel(player).contains("stopped"));
            String rows=Files.readString(file);
            assertTrue(rows.contains("before-drop"));
            assertTrue(rows.contains("OPERATOR_CANCELLED"));
        }
    }
}
