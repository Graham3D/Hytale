package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.*;

/** Offline value-only adapter seam. Never supplied a player, world, packet sink or state repository. */
public final class HroUiParity {
    private HroUiParity() { }
    public record Fixture(String id,String contract,String logicalControl,String authority,
                          Map<String,String> intent,Result expected) {
        public Fixture { intent=Map.copyOf(intent); }
    }
    public record Result(List<String> submittedCommands,String validation,Map<String,String> stateDelta,Map<String,String> projection) {
        public Result { submittedCommands=List.copyOf(submittedCommands);stateDelta=Map.copyOf(stateDelta);projection=Map.copyOf(projection); }
    }
    @FunctionalInterface public interface OfflineAdapter { Result project(Fixture fixture); }
    public record Parity(String fixture,boolean commands,boolean validation,boolean stateDelta,boolean projectedInformation,
                         String outcome,String visualEvidence) { }
    public static List<Parity> compare(List<Fixture> fixtures,OfflineAdapter oldAdapter,OfflineAdapter newAdapter) {
        if(fixtures.size()>64)throw new IllegalArgumentException("UI_FIXTURE_LIMIT");
        var result=new ArrayList<Parity>();
        for(Fixture fixture:fixtures) {
            Result old=Objects.requireNonNull(oldAdapter.project(fixture)),next=Objects.requireNonNull(newAdapter.project(fixture));
            boolean commands=old.submittedCommands.equals(next.submittedCommands),validation=old.validation.equals(next.validation),
                    delta=old.stateDelta.equals(next.stateDelta),view=old.projection.equals(next.projection);
            result.add(new Parity(fixture.id,commands,validation,delta,view,
                    !old.equals(fixture.expected)?"REFERENCE_FIXTURE_MISMATCH":commands&&validation&&delta&&view?"PARITY":"DIVERGENCE","UNVERIFIED_REQUIRES_RENDERING"));
        }
        return List.copyOf(result);
    }
    public static List<Fixture> fixtures() {
        var result=new ArrayList<Fixture>();
        add(result,"assign-skill","SKILL.ASSIGN","skill.slot.assign","progress/RpgLoadoutService.java#equipSkill","assign(slot,skill)","equipped","skill");
        add(result,"clear-skill","SKILL.ASSIGN","skill.slot.clear","progress/RpgLoadoutService.java#unequipSkill","clear(slot)","equipped","empty");
        add(result,"connect-link","SKILL.ASSIGN","link.connect","links/RpgLinkGraphService.java#candidateLink","link(source,target)","edge","connected");
        add(result,"break-link","SKILL.ASSIGN","link.break","links/RpgLinkGraphService.java#candidateUnlinkEdge","unlink(edge)","edge","absent");
        add(result,"search-library","UI.LIFECYCLE","library.search","ui/skilltree/RpgSkillTreePage.java#handleDataEvent","search(query)","query","query");
        add(result,"select-library","UI.LIFECYCLE","library.select","ui/skilltree/RpgSkillTreePage.java#handleDataEvent","select(entry)","selection","entry");
        for(String action:List.of("equip","unequip","split","merge","transfer"))
            add(result,action+"-item","INVENTORY.TRANSFER","inventory."+action,"ui/inventory/SpatialInventoryTransferCoordinator.java",action+"(item,source,destination)","custody","owner-confirmed");
        for(String action:List.of("open","close"))add(result,action+"-modal","UI.LIFECYCLE","modal."+action,"ui/inventory/InventoryProbePage.java",action+"(modal)","modal",action);
        add(result,"restore-hud","HUD.PUBLICATION","hud.restore","ui/hud/RpgHudCoordinator.java","restore(view)","hud","published");
        result.add(new Fixture("rejected-transfer","INVENTORY.TRANSFER","inventory.transfer","ui/inventory/SpatialInventoryTransferCoordinator.java",
                Map.of("prerequisite","destination invalid"),new Result(List.of("transfer(item,source,destination)"),"REJECTED",Map.of(),Map.of("error","destination invalid"))));
        return List.copyOf(result);
    }
    private static void add(List<Fixture> fixtures,String id,String contract,String control,String authority,String command,String key,String value) {
        fixtures.add(new Fixture(id,contract,control,"src/main/java/com/inigmasgames/hytalerpg/"+authority,
                Map.of("prerequisite","valid immutable owner result","semanticCommand",command),
                new Result(List.of(command),"ACCEPTED",Map.of(key,value),Map.of(key,value))));
    }
}
