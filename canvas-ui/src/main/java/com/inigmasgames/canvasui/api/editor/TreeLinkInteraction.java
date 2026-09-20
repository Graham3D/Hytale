package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.CanvasPoint;

/** Exact-link selection and confirmation state; popup target never follows later selection. */
public final class TreeLinkInteraction {
    public enum State { NONE, LINK_SELECTED, CONTEXT_CONFIRM_OPEN }
    private State state=State.NONE;
    private String selectedLinkId;
    private String contextTargetLinkId;
    private String contextTargetNodeId;
    private boolean resetContext;
    private boolean exitContext;
    private CanvasPoint popupAnchor;

    public void select(String linkId){selectedLinkId=linkId;contextTargetLinkId=null;contextTargetNodeId=null;resetContext=false;exitContext=false;popupAnchor=null;state=linkId==null?State.NONE:State.LINK_SELECTED;}
    public void openContext(String linkId,CanvasPoint anchor){selectedLinkId=linkId;contextTargetLinkId=linkId;contextTargetNodeId=null;resetContext=false;exitContext=false;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void openNodeContext(String nodeId,CanvasPoint anchor){contextTargetLinkId=null;contextTargetNodeId=nodeId;resetContext=false;exitContext=false;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void openResetContext(CanvasPoint anchor){contextTargetLinkId=null;contextTargetNodeId=null;resetContext=true;exitContext=false;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void openExitContext(CanvasPoint anchor){contextTargetLinkId=null;contextTargetNodeId=null;resetContext=false;exitContext=true;popupAnchor=anchor;state=State.CONTEXT_CONFIRM_OPEN;}
    public void dismissContext(){contextTargetLinkId=null;contextTargetNodeId=null;resetContext=false;exitContext=false;popupAnchor=null;state=selectedLinkId==null?State.NONE:State.LINK_SELECTED;}
    public void broken(){select(null);}
    public void clear(){select(null);}
    public State state(){return state;}
    public String selectedLinkId(){return selectedLinkId;}
    public String contextTargetLinkId(){return contextTargetLinkId;}
    public String contextTargetNodeId(){return contextTargetNodeId;}
    public boolean nodeContext(){return contextTargetNodeId!=null;}
    public boolean resetContext(){return resetContext;}
    public boolean exitContext(){return exitContext;}
    public CanvasPoint popupAnchor(){return popupAnchor;}
    public boolean contextOpen(){return state==State.CONTEXT_CONFIRM_OPEN;}
}
