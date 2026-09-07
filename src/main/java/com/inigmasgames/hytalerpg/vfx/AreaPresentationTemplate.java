package com.inigmasgames.hytalerpg.vfx;

/** Procedural fallback palette/state, not evidence of client readability or a native asset identifier. */
public final class AreaPresentationTemplate {
    public record Color(float red,float green,float blue) { }
    public static final double GROUND_LIFT=.10;
    private AreaPresentationTemplate() { }
    public static Color color(String element,String phase) {
        Color base=switch(element) {
            case "FIRE" -> new Color(1,.35f,.08f);
            case "COLD" -> new Color(.4f,.85f,1);
            case "EARTH" -> new Color(.7f,.5f,.25f);
            case "POISON" -> new Color(.7f,.9f,.15f);
            case "NATURE" -> new Color(.25f,.8f,.3f);
            case "VOID" -> new Color(.6f,.3f,.85f);
            case "PHYSICAL" -> new Color(.85f,.7f,.4f);
            case "WIND" -> new Color(.7f,.9f,.9f);
            case "LIGHTNING" -> new Color(.85f,.9f,1);
            default -> new Color(.4f,.5f,1);
        };
        return phase.startsWith("IMPACT") ? new Color((1+base.red)/2,(1+base.green)/2,(1+base.blue)/2) : base;
    }
    public static boolean warning(String phase) { return phase.startsWith("WARNING")||phase.equals("ARMING"); }
}
