package com.inigmasgames.persistentnpcs.autonomy;

/** Deterministic E8 opportunity utility; uncertainty/risk are explicit costs. */
public record AutonomousOpportunityScore(double goal, double need, double relationship,
        double economic, double curiosity, double risk, double cost, double uncertainty,
        double scheduleConflict) {
    public AutonomousOpportunityScore {
        goal=unit(goal); need=unit(need); relationship=unit(relationship); economic=unit(economic);
        curiosity=unit(curiosity); risk=unit(risk); cost=unit(cost); uncertainty=unit(uncertainty);
        scheduleConflict=unit(scheduleConflict);
    }
    public double utility() {
        return clamp(goal*.25 + need*.18 + relationship*.12 + economic*.12 + curiosity*.13
                - risk*.10 - cost*.05 - uncertainty*.10 - scheduleConflict*.10);
    }
    public boolean requiresInformationAction() { return risk >= .65 || uncertainty >= .55; }
    private static double unit(double v) { return Double.isFinite(v) ? clamp(v) : 0; }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
