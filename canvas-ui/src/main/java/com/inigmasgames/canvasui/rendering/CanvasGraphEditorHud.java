package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.editor.CursorCanvasEditor;
import com.inigmasgames.canvasui.api.editor.SkillTreeViewModel;
import com.inigmasgames.canvasui.api.editor.TreeLinkGeometry;
import com.inigmasgames.canvasui.api.editor.TreeLinkInteraction;

import javax.annotation.Nonnull;
import java.util.List;

/** Fixed-pool passive HUD renderer for the production cursor graph editor. */
public final class CanvasGraphEditorHud extends CustomUIHud {
    public static final String KEY = "inigmas:canvasui:graph-editor";
    public static final int LIBRARY_ROWS = 10;
    public static final int WORKSPACE_LEFT = 114;
    public static final int WORKSPACE_TOP = 140;
    public static final int LIBRARY_TRACK_TOP = 132;
    public static final int LIBRARY_TRACK_HEIGHT = 456;
    public static final int LIBRARY_TRACK_LEFT = 230;
    private static final String SKILL_TREE_ASSETS = "Assets/SkillTree/";
    private static final String HYTALE_ASSETS = SKILL_TREE_ASSETS + "Hytale/";
    private static final int NODE_POOL = 11;
    private static final int EDGE_SEGMENT_POOL = 96;
    private static final TreeLinkGeometry LINK_GEOMETRY = new TreeLinkGeometry();
    private final String title;

    public CanvasGraphEditorHud(PlayerRef playerRef, String title) {
        super(playerRef, KEY, 860);
        this.title = title == null || title.isBlank() ? "RPG SKILL TREE" : title;
    }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("CanvasGraphEditorHud.ui");
        commands.set("#GraphEditorTitle.TextSpans", Message.raw(title));
        commands.set("#GraphEditorRevision.TextSpans", Message.raw(CanvasUI.REVISION));
    }

    public void render(SkillTreeViewModel model, DragVisual drag, TreeLinkInteraction links) {
        UICommandBuilder commands = new UICommandBuilder();
        writeFrame(commands, model, drag, links);
        update(false, commands);
    }

    public static void writeFrame(UICommandBuilder commands, SkillTreeViewModel model,
                                  DragVisual drag, TreeLinkInteraction links) {
        CursorCanvasEditor.LibraryKind tab = model.libraryKind();
        List<CursorCanvasEditor.LibraryEntry> page = model.entries();
        commands.set("#GraphEditorTitle.TextSpans", Message.raw(model.title()));
        commands.set("#GraphEditorSubtitle.TextSpans", Message.raw(model.subtitle()));
        commands.setObject("#GraphEditorSkillsTab.Background", texture(HYTALE_ASSETS
                + (tab == CursorCanvasEditor.LibraryKind.SKILL
                ? "HeaderTabSelectedBackground.png" : "HeaderTabBackground.png"), 9));
        commands.setObject("#GraphEditorPassivesTab.Background", texture(HYTALE_ASSETS
                + (tab == CursorCanvasEditor.LibraryKind.PASSIVE
                ? "HeaderTabSelectedBackground.png" : "HeaderTabBackground.png"), 9));
        commands.set("#GraphEditorSearch.TextSpans", Message.raw(model.query().isBlank()
                ? (tab == CursorCanvasEditor.LibraryKind.SKILL ? "Search skills..." : "Search passives...") : model.query()));
        commands.set("#GraphEditorNoMatches.Visible", page.isEmpty());
        commands.set("#GraphEditorStatus.TextSpans", Message.raw(model.status()));
        commands.set("#GraphEditorDrag.TextSpans", Message.raw(drag == null
                ? "Drag a library entry onto a matching node. Drag ports to create links."
                : drag.state() + ": " + drag.entry().name()));
        for (int i = 0; i < LIBRARY_ROWS; i++) {
            boolean visible = i < page.size();
            commands.set("#GraphLibraryRow" + i + ".Visible", visible);
            if (visible) {
                CursorCanvasEditor.LibraryEntry entry = page.get(i);
                commands.set("#GraphLibraryRow" + i + " #Name.TextSpans", Message.raw(entry.name()));
                commands.set("#GraphLibraryRow" + i + " #Category.TextSpans", Message.raw(entry.category()));
                commands.setObject("#GraphLibraryRow" + i + " #Icon.Background", texture(entry.iconPath()));
                commands.setObject("#GraphLibraryRow" + i + ".Background",
                        texture(HYTALE_ASSETS + "ContainerPanelLightPatch.png", 4));
            }
        }
        renderScrollbar(commands, model.scrollOffset(), model.maximumScrollOffset(), model.totalMatches(), page.size());
        renderNodes(commands, model.nodes());
        renderContinuousEdges(commands, model.links());
        renderInspector(commands,model.inspector());
        renderDrag(commands, drag);
        commands.set("#GraphPreview0.Visible", false);
        commands.set("#GraphPreview1.Visible", false);
        commands.set("#GraphPreview2.Visible", false);
        commands.set("#GraphTargetHighlight.Visible", false);
        renderContext(commands, links);
    }

    public PatchMetrics renderDragOnly(DragVisual drag) {
        return renderDragOnly(drag, null);
    }

    public PatchMetrics renderDragOnly(DragVisual drag, CanvasPoint candidateCenter) {
        UICommandBuilder commands = new UICommandBuilder();
        renderDrag(commands, drag);
        commands.set("#GraphTargetHighlight.Visible", candidateCenter != null);
        if(candidateCenter!=null)commands.setObject("#GraphTargetHighlight.Anchor",
                anchor((int)Math.round(candidateCenter.x())-11,(int)Math.round(candidateCenter.y())-11,22,22));
        update(false, commands);
        return new PatchMetrics(drag == null ? 2 : 5, drag == null ? 48 : 220);
    }

    public PatchMetrics renderPreview(PreviewVisual preview) {
        UICommandBuilder commands = new UICommandBuilder();
        int index = 0;
        if (preview != null) {
            for (TreeLinkGeometry.Segment segment : LINK_GEOMETRY.route(preview.source(), preview.target())) {
                if (index >= 3) break;
                int thickness = 3;
                int left=(int)Math.round(segment.left()-(segment.width()==0?thickness/2.0:0));
                int top=(int)Math.round(segment.top()-(segment.height()==0?thickness/2.0:0));
                commands.set("#GraphPreview" + index + ".Visible", true);
                commands.setObject("#GraphPreview" + index + ".Anchor", anchor(left, top,
                        Math.max(thickness,(int)Math.round(segment.width())),
                        Math.max(thickness,(int)Math.round(segment.height()))));
                index++;
            }
        }
        while (index < 3) commands.set("#GraphPreview" + index++ + ".Visible", false);
        commands.set("#GraphTargetHighlight.Visible", preview != null && preview.valid());
        if (preview != null && preview.valid()) commands.setObject("#GraphTargetHighlight.Anchor",
                anchor((int)Math.round(preview.target().x())-9, (int)Math.round(preview.target().y())-9,18,18));
        update(false, commands);
        return new PatchMetrics(preview == null ? 4 : 9, preview == null ? 96 : 420);
    }

    private static void renderNodes(UICommandBuilder commands, List<SkillTreeViewModel.Node> nodes) {
        int index = 0;
        for (SkillTreeViewModel.Node node : nodes) {
            if (index >= NODE_POOL) break;
            String selector = "#GraphNode" + index;
            CanvasPoint point = node.point();
            commands.set(selector + ".Visible", true);
            commands.setObject(selector + ".Anchor", anchor((int)Math.round(point.x()), (int)Math.round(point.y()),
                    node.width(), node.height()));
            commands.setObject(selector + ".Background", color("#00000000"));
            commands.set(selector + " #Title.TextSpans", Message.raw(node.label()));
            commands.setObject(selector+" #Title.Anchor",anchor(0,-25,node.width(),22));
            commands.set(selector + " #Subtitle.Visible", false);
            commands.set(selector + " #Triangle.Visible", "joint".equals(node.type()));
            commands.set(selector + " #Circle.Visible", "passive".equals(node.type())||"skill".equals(node.type()));
            commands.set(selector + " #Triangle.TextSpans",Message.raw(""));
            commands.set(selector + " #Circle.TextSpans",Message.raw(""));
            if("joint".equals(node.type()))commands.setObject(selector+" #Triangle.Background",
                    texture(SKILL_TREE_ASSETS + "skilltree_joint.png"));
            if("passive".equals(node.type()))commands.setObject(selector+" #Circle.Background",
                    texture(node.occupied()?SKILL_TREE_ASSETS + "skilltree_passive_occupied.png"
                            :SKILL_TREE_ASSETS + "skilltree_passive_unoccupied.png"));
            if("skill".equals(node.type()))commands.setObject(selector+" #Circle.Background",
                    texture(node.occupied()?SKILL_TREE_ASSETS + "Slot.png"
                            :SKILL_TREE_ASSETS + "SpecialSlotTemporary.png"));
            if("skill".equals(node.type()))commands.setObject(selector+" #Circle.Anchor",anchor(35,0,62,62));
            if("passive".equals(node.type()))commands.setObject(selector+" #Circle.Anchor",anchor(35,0,54,54));
            if("joint".equals(node.type()))commands.setObject(selector+" #Triangle.Anchor",anchor(0,0,72,72));
            boolean icon = !"joint".equals(node.type()) && node.occupied();
            commands.set(selector + " #Icon.Visible", icon);
            if (icon) {commands.setObject(selector + " #Icon.Background", texture(node.icon()));
                commands.setObject(selector+" #Icon.Anchor",anchor((node.width()-40)/2,(node.height()-40)/2,40,40));}
            commands.set(selector + " #Port0.Visible", false);
            commands.set(selector + " #Port1.Visible", false);
            commands.set(selector + " #Port2.Visible", false);
            int portIndex = 0;
            for (SkillTreeViewModel.Port port : node.ports()) {
                if (portIndex >= 3) break;
                commands.set(selector + " #Port" + portIndex + ".Visible", true);
                commands.setObject(selector + " #Port" + portIndex + ".Anchor",
                        anchor((int)Math.round(port.point().x() - point.x()) - 6,
                                (int)Math.round(port.point().y() - point.y()) - 6, 12, 12));
                commands.setObject(selector+" #Port"+portIndex+".Background",
                        texture(SKILL_TREE_ASSETS + "StructuralCraftingArrowUp.png"));
                portIndex++;
            }
            index++;
        }
        while (index < NODE_POOL) commands.set("#GraphNode" + index++ + ".Visible", false);
    }

    private static void renderContinuousEdges(UICommandBuilder commands, List<SkillTreeViewModel.Link> links) {
        int index=0;
        for (SkillTreeViewModel.Link edge : links) {
            boolean selected=edge.selected();
            for(TreeLinkGeometry.Segment segment:edge.segments()){
                if(index>=EDGE_SEGMENT_POOL)break;
                int thickness=selected?5:3;
                int left=(int)Math.round(segment.left()-(segment.width()==0?thickness/2.0:0));
                int top=(int)Math.round(segment.top()-(segment.height()==0?thickness/2.0:0));
                int width=Math.max(thickness,(int)Math.round(segment.width()));
                int height=Math.max(thickness,(int)Math.round(segment.height()));
                commands.set("#GraphEdgeDot"+index+".Visible",true);
                commands.setObject("#GraphEdgeDot"+index+".Anchor",anchor(left,top,width,height));
                commands.setObject("#GraphEdgeDot"+index+".Background",color(selected?"#ffffffff":"#e8f4ffff"));
                index++;
            }
        }
        while(index<EDGE_SEGMENT_POOL)commands.set("#GraphEdgeDot"+index++ + ".Visible",false);
    }

    private static void renderDrag(UICommandBuilder commands,DragVisual drag){
        commands.set("#GraphDragGhost.Visible",drag!=null);
        if(drag==null)return;
        commands.setObject("#GraphDragGhost.Anchor",anchor((int)Math.round(drag.point().x())-24,(int)Math.round(drag.point().y())-24,48,48));
        commands.setObject("#GraphDragGhost.Background",texture(drag.entry().iconPath()));
    }

    private static void renderScrollbar(UICommandBuilder commands, int offset, int maximumOffset,
                                        int totalMatches, int visibleCount) {
        int thumbHeight = maximumOffset == 0 ? LIBRARY_TRACK_HEIGHT - 4
                : Math.max(34, (int)Math.round((LIBRARY_TRACK_HEIGHT - 4)
                * Math.min(1.0, LIBRARY_ROWS / (double)Math.max(1,totalMatches))));
        int travel = Math.max(0, LIBRARY_TRACK_HEIGHT - 4 - thumbHeight);
        int top = LIBRARY_TRACK_TOP + 2 + (maximumOffset == 0 ? 0
                : (int)Math.round(travel * offset / (double)maximumOffset));
        commands.setObject("#GraphLibraryScrollbarThumb.Anchor", anchor(LIBRARY_TRACK_LEFT + 2, top, 6, thumbHeight));
        int first = totalMatches == 0 ? 0 : offset + 1;
        int last = totalMatches == 0 ? 0 : offset + visibleCount;
        commands.set("#GraphLibraryPosition.TextSpans", Message.raw(first + "-" + last + " / " + totalMatches));
    }

    private static void renderContext(UICommandBuilder commands,TreeLinkInteraction links){
        boolean visible=links!=null&&links.contextOpen()&&links.popupAnchor()!=null;
        commands.set("#GraphLinkContext.Visible",visible);
        if(visible)commands.setObject("#GraphLinkContext.Anchor",anchor(
                (int)Math.round(Math.min(690,Math.max(208,links.popupAnchor().x()))),
                (int)Math.round(Math.min(372,Math.max(4,links.popupAnchor().y()))),126,88));
        commands.set("#GraphContextPrompt.TextSpans",Message.raw(links!=null&&links.nodeContext()?"Unequip Skill?":"Break Link?"));
        commands.set("#GraphDeleteLink.Visible",links!=null&&links.selectedLinkId()!=null);
        commands.set("#GraphDeleteLinkLabel.Visible",links!=null&&links.selectedLinkId()!=null);
    }

    private static void renderInspector(UICommandBuilder commands,CursorCanvasEditor.Inspector inspector){
        CursorCanvasEditor.Inspector value=inspector==null?CursorCanvasEditor.Inspector.neutral():inspector;
        commands.set("#GraphInspectorName.TextSpans",Message.raw(value.name()));
        commands.set("#GraphInspectorDescriptor.TextSpans",Message.raw(value.descriptor()));
        commands.set("#GraphInspectorDescription.TextSpans",Message.raw(value.description()));
        commands.set("#GraphInspectorIcon.Visible",!value.iconPath().isBlank());
        if(!value.iconPath().isBlank())commands.setObject("#GraphInspectorIcon.Background",texture(value.iconPath()));
        commands.set("#GraphInspectorFooter.TextSpans",Message.raw(value.footer()));
        for(int i=0;i<6;i++){
            boolean visible=i<value.rows().size();String selector="#GraphInspectorRow"+i;
            commands.set(selector+".Visible",visible);
            if(visible){CursorCanvasEditor.DetailRow row=value.rows().get(i);
                commands.set(selector+" #Label.TextSpans",Message.raw(row.label()));
                commands.set(selector+" #Value.TextSpans",Message.raw(row.value()));
            }
        }
    }

    private static PatchStyle color(String value) { return new PatchStyle().setColor(Value.of(value)); }
    private static PatchStyle texture(String value) { return new PatchStyle().setTexturePath(Value.of(value)); }
    private static PatchStyle texture(String value, int border) {
        return texture(value).setBorder(Value.of(border));
    }
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }

    public record DragVisual(CursorCanvasEditor.LibraryEntry entry, CanvasPoint point, String state) { }
    public record PreviewVisual(CanvasPoint source, CanvasPoint target, boolean valid) { }
    public record PatchMetrics(int elementsUpdated, int estimatedBytes) { }
}
