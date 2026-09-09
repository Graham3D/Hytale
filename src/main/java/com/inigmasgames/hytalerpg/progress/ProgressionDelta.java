package com.inigmasgames.hytalerpg.progress;

import java.util.Objects;

/** Internal typed operation, authorized against catalog/eligible source before intent creation. */
public record ProgressionDelta(Kind kind,String subject,String source,long insightCost) {
    public enum Kind { MEANINGFUL_USE, LEARNING_FAILURE, LEARNING_SUCCESS, PASSIVE_PURCHASE }
    public ProgressionDelta {
        Objects.requireNonNull(kind);AcquisitionProgress.id(subject);Objects.requireNonNull(source);
        boolean learning=kind==Kind.LEARNING_FAILURE||kind==Kind.LEARNING_SUCCESS;
        if(learning)AcquisitionProgress.id(source);else if(!source.isEmpty())throw new IllegalArgumentException("UNEXPECTED_PROGRESSION_SOURCE");
        if(kind==Kind.PASSIVE_PURCHASE){if(insightCost!=20&&insightCost!=40&&insightCost!=80)throw new IllegalArgumentException("INVALID_INSIGHT_PRICE");}
        else if(insightCost!=0)throw new IllegalArgumentException("UNEXPECTED_INSIGHT_COST");
    }
    public static ProgressionDelta meaningful(String skill){return new ProgressionDelta(Kind.MEANINGFUL_USE,skill,"",0);}
}
