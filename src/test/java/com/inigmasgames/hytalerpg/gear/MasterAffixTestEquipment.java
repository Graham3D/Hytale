package com.inigmasgames.hytalerpg.gear;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.util.*;
/** Test-only candidate admission; the production capability gate is never modified. */
public final class MasterAffixTestEquipment {
    public static final Map<RpgAttribute,Integer> BASELINE=Map.of(RpgAttribute.STR,500,RpgAttribute.DEX,500,
            RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final Set<String> CANDIDATES=CATALOG.affixes().stream().map(GearCatalog.Affix::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);
    public static GearInstance fixture(String id,boolean control) {
        var name="ab-"+id.toLowerCase(Locale.ROOT)+(control?"-control":"-affixed");
        return QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals(name)).findFirst().orElseThrow());
    }
    public static GearAffixRuntime.Effects accepted(GearInstance item) {
        var result=GearEquipmentResolution.resolve(99,BASELINE,item==null?List.of():
                List.of(new GearEquipmentResolution.Candidate(item,true,true,true)),CANDIDATES);
        if(item!=null&&!result.validItems().equals(List.of(item)))throw new AssertionError(result.rejected());
        return result.effects();
    }
}
