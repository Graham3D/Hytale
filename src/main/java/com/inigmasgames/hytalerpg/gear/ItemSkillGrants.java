package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Validated equipment projection for WA-144. The caller supplies the live accepted gear snapshot. */
public final class ItemSkillGrants {
    private static final GearCatalog GEAR=GearCatalog.load();
    private static final RpgCatalog SKILLS=RpgCatalog.loadCanonical();
    private static final Stage04SkillProfiles PROFILES=Stage04SkillProfiles.loadCanonical(SKILLS);
    private ItemSkillGrants() { }

    public static Map<UUID,String> from(GearEffectSnapshot snapshot) {
        var grants=new LinkedHashMap<UUID,String>();
        for(var source:snapshot.sources(GearEffectSnapshot.Operator.ITEM_GRANT)) {
            if(!source.affixId().equals("WA-144"))continue;
            String selector=source.roll().selector();
            if(selector==null||SKILLS.skill(new SkillId(selector)).isEmpty()||!PROFILES.supports(selector))
                throw new IllegalStateException("INVALID_BORROWED_ART_SELECTOR:"+source.itemId());
            var item=snapshot.forItem(source.itemId()).items().getFirst();
            if(!GearDropGenerator.matchingSkillIds(GEAR.base(item.baseId())).contains(selector))
                throw new IllegalStateException("BORROWED_ART_SELECTOR_NOT_ALLOWED_ON_ITEM:"+source.itemId());
            grants.put(source.itemId(),selector);
        }
        return Map.copyOf(grants);
    }
}
