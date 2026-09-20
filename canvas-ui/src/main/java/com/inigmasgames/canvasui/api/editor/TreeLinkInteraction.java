package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.CanvasPoint;

/** Exact-link selection and confirmation state; popup target never follows later selection. */
public final class TreeLinkInteraction {
    public enum State { NONE, LINK_SELECTED, CONTEXT_CONFIRM_OPEN }
    private State state=State.NONE;
    private String selectedLinkId;
    private String contextTargetLinkId;
    private String contextTargetNodeId;
    private CanvasPoint popupAnchor;

    public void select(String linkId){selectedLinkId=linkId;contextTargetLinkId=null;contextTargetNodeId=null;popupAnchor=null;state=linkId==null?State.NONE:State.LINK_SELECTED;}
    public void openContext(String linkId,CanvasPoint anchor){selectedLinkId=linkId;contextTargetLinkId=linkId;contextTargetNodeId=null;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void openNodeContext(String nodeId,CanvasPoint anchor){contextTargetLinkId=null;contextTargetNodeId=nodeId;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void dismissContext(){contextTargetLinkId=null;contextTargetNodeId=null;popupAnchor=null;state=selectedLinkId==null?State.NONE:State.LINK_SELECTED;}
    public void broken(){select(null);}
    public void clear(){select(null);}
    public State state(){return state;}
    public String selectedLinkId(){return selectedLinkId;}
    public String contextTargetLinkId(){return contextTargetLinkId;}
    public String contextTargetNodeId(){return contextTargetNodeId;}
    public boolean nodeContext(){return contextTargetNodeId!=null;}
    public CanvasPoint popupAnchor(){return popupAnchor;}
    public boolean contextOpen(){return state==State.CONTEXT_CONFIRM_OPEN;}
}
