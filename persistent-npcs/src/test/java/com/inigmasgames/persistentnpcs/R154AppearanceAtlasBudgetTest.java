package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.*;
import java.nio.file.*;
import java.util.*;

/** R155 turns the R154 reduced atlas budget into a zero-runtime-image admission policy. */
public final class R154AppearanceAtlasBudgetTest {
    public static void main(String[] args) throws Exception {
        AppearanceUiAssetBudget budget=AppearanceUiAssetBudget.PRODUCTION;
        budget.requireUsage(20,0,0,0);
        try { budget.requireUsage(21,0,0,0); assert false; } catch(IllegalStateException expected) { }
        try { budget.requireUsage(1,1,92L*100,1024); assert false; } catch(IllegalStateException expected) { }

        List<Runnable> queue=new ArrayList<>(); List<Long> applied=new ArrayList<>();
        try(var gate=new AppearancePreviewGate()) {
            assert !gate.request(queue::add, applied::add);
            for(int i=0;i<100;i++) assert gate.request(queue::add, applied::add);
            assert queue.size()==1 && gate.pending()==1 && gate.cancelled()==100;
            queue.removeFirst().run();
            assert applied.equals(List.of(101L)) && gate.active()==0 && gate.pending()==0;
            gate.request(queue::add, applied::add); gate.cancel(); queue.removeFirst().run();
            assert applied.size()==1 : "Cancelled preview applied";
        }

        AppearanceUiState state=new AppearanceUiState();
        assert !state.degraded();
        assert state.observeFailure("Texture atlas needs 4096x32768; dropping 194 images");
        assert state.degraded() && !state.observeFailure("Texture atlas needs 4096x32768");
        assert state.degradedMessage().contains("restart Hytale") && state.degradedMessage().contains("draft is retained");

        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        for(String event:List.of("APPEARANCE_UI_ASSET_REUSED", "APPEARANCE_UI_ASSET_RELEASE_REQUESTED",
                "APPEARANCE_PAGE_RENDERED", "APPEARANCE_PAGE_UNLOADED", "APPEARANCE_PREVIEW_SCHEDULED",
                "APPEARANCE_PREVIEW_COALESCED", "APPEARANCE_PREVIEW_APPLIED", "APPEARANCE_FULL_REBUILD_SUPPRESSED"))
            assert source.contains(event):event;
        assert source.contains("dynamicImages=0 dynamicPixels=0 dynamicEncodedBytes=0")
                && source.contains("assetsUploaded=0");
        System.out.println("R154 superseded safely: zero dynamic images, hard 20-card admission, coalesced one-preview gate and latched degradation sentinel.");
    }
}
