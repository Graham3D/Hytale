package com.inigmasgames.hytalerpg.domain;
import java.util.List;
public record HitProcModifiers(boolean hemorrhage,boolean terror,boolean shatter){
    public static HitProcModifiers from(List<PassiveId> order){var ids=order.stream().map(PassiveId::value).toList();return new HitProcModifiers(ids.contains("hemorrhage"),ids.contains("terror"),ids.contains("shatter"));}
    public boolean active(){return hemorrhage||terror||shatter;}
}
