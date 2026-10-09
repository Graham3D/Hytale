package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GearTooltipPresentationTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);
    private static final UUID OWNER=UUID.fromString("3b574c31-6d72-4e92-9c22-cade00000176");
    private GearInstance item(String band){return QA.create("tooltip-"+band+"-battleaxe",OWNER);}
    private List<GearTooltip.Line> lines(String band){return GearTooltip.describe(item(band),99,Map.of(RpgAttribute.STR,999));}
    private String text(List<GearTooltip.Line> lines){return String.join("\n",lines.stream().map(GearTooltip.Line::text).toList());}
    private void identity(String band,String color,String label) throws Exception {
        var item=item(band);var title=lines(band).getFirst();
        assertEquals(item.displayName(),title.text());assertEquals(color,title.color());
        var path=Path.of("src/main/resources/Server/Item/Qualities/"+item.rarity().qualityAsset()+".json");
        var quality=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(color.toLowerCase(Locale.ROOT),quality.get("TextColor").getAsString().toLowerCase(Locale.ROOT));
        assertEquals("server.gear.quality."+label,quality.get("LocalizationKey").getAsString());
        assertTrue(quality.get("VisibleQualityLabel").getAsBoolean());
        String textureBand = label.equals("Rare") ? "Magic" : label;
        assertTrue(quality.get("ItemTooltipTexture").getAsString().endsWith("/Hywind/ItemTooltip"+textureBand+".png"));
    }
    @Test void commonTitleColorAndLabel() throws Exception {identity("common","#ffffff","Common");}
    @Test void magicTitleColorAndLabel() throws Exception {identity("magic","#1d4dff","Rare");}
    @Test void rareTitleColorAndLabel() throws Exception {identity("rare","#6b00ff","Epic");}
    @Test void classificationUsesCatalogFamilyVocabulary(){assertEquals("Two-Handed Axe",lines("common").get(1).text());
        for(var base:CATALOG.bases())assertNotNull(GearBasePresentation.classification(base.id()),base.family());}
    @Test void resolvedLocalDamageUsesTheCombatOwnerExactlyOnce(){
        for(String band:List.of("common","magic","rare")){
            var item=item(band);var resolved=GearCombatEffects.physical(item);
            var range=lines(band).stream().filter(l->l.style()==GearTooltip.Style.DAMAGE).findFirst().orElseThrow();
            assertEquals(String.format(Locale.ROOT,"%.1f - %.1f",resolved.minimum(),resolved.maximum()),range.text());
            assertEquals(GearTooltip.DAMAGE_COLOR,range.color());
        }
        assertNotEquals(lines("common").stream().filter(l->l.style()==GearTooltip.Style.DAMAGE).findFirst(),
                lines("magic").stream().filter(l->l.style()==GearTooltip.Style.DAMAGE).findFirst());
    }
    @Test void allFourLocalPhysicalOperatorsUseCanonicalResolution(){
        var base=CATALOG.base("gm.battleaxe_adamantite.h");
        var rolls=new ArrayList<GearInstance.AffixRoll>();
        for(String id:List.of("WA-001","WA-002","WA-157","WA-158")){
            var a=CATALOG.affix(id);rolls.add(new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),1,
                    id.equals("WA-002")?160:30,new GearRequirements.Gate(1,Map.of()),a.effectContract(),a.name()));
        }
        var gear=GearInstance.authoredQa(base,OWNER,99,950,GearRarity.VERY_RARE,rolls,java.math.BigDecimal.ZERO);
        var resolved=GearCombatEffects.physical(gear);
        assertTrue(text(GearTooltip.describe(gear,99,Map.of())).contains(String.format(Locale.ROOT,"%.1f - %.1f",resolved.minimum(),resolved.maximum())));
    }
    @Test void unverifiedAttacksPerSecondIsOmittedByUserDecision(){
        assertFalse(text(lines("rare")).contains("Attacks per Second"));
        assertFalse(text(lines("rare")).contains("Maximum input rate"));
    }
    @Test void oneAffixHasExactlyOneCanonicalBlueLine(){
        var item=item("magic");var affixes=lines("magic").stream().filter(l->l.style()==GearTooltip.Style.AFFIX).toList();
        assertEquals(1,affixes.size());assertEquals(GearAffixDisplay.format(item.affixes().getFirst()),affixes.getFirst().text());
        assertEquals(GearRarity.AFFIX_COLOR,affixes.getFirst().color());
    }
    @Test void multiAffixBlockHasNoBulletsOrInterleavedDividers(){
        var item=item("rare");var affixes=lines("rare").stream().filter(l->l.style()==GearTooltip.Style.AFFIX).toList();
        assertEquals(3,affixes.size());
        for(int i=0;i<affixes.size();i++){
            assertEquals(GearAffixDisplay.format(item.affixes().get(i)),affixes.get(i).text());
            assertEquals(GearRarity.AFFIX_COLOR,affixes.get(i).color());assertFalse(affixes.get(i).breakBefore());
            assertFalse(affixes.get(i).text().startsWith("•"));
        }
        var all=lines("rare");int first=all.indexOf(affixes.getFirst()),last=all.indexOf(affixes.getLast());
        assertEquals(first+affixes.size()-1,last);
        assertEquals(GearTooltip.Style.DIVIDER,all.get(first-1).style());
        assertEquals(GearTooltip.Style.DIVIDER,all.get(last+1).style());
    }
    @Test void skillRankLineUsesTheCanonicalSkillName(){
        var gear=QA.create("ab-wa-122-affixed",OWNER);
        var expected=GearAffixDisplay.format(gear.affixes().getFirst());
        assertTrue(text(GearTooltip.describe(gear,99,Map.of())).contains(expected));
        assertTrue(expected.startsWith("+"));assertFalse(expected.contains("[Skill]"));
    }
    @Test void elementalLineUsesCanonicalWording(){
        var gear=item("rare");var water=gear.affixes().stream().filter(a->a.familyId().equals("WA-018")).findFirst().orElseThrow();
        assertTrue(text(lines("rare")).contains(GearAffixDisplay.format(water)));
    }
    @Test void metRequirementsAreMuted(){
        var requirements=lines("common").stream().filter(l->l.text().startsWith("Requires ")).toList();
        assertEquals(2,requirements.size());assertTrue(requirements.stream().allMatch(l->l.style()==GearTooltip.Style.NORMAL&&l.color().equals(GearTooltip.MUTED_COLOR)));
    }
    @Test void unmetLevelIsRedWhileMetStrengthIsNeutral(){
        var result=GearTooltip.describe(item("common"),1,Map.of(RpgAttribute.STR,999));
        assertTrue(result.stream().anyMatch(l->l.text().equals("Requires Level 25")&&l.style()==GearTooltip.Style.ERROR&&l.color().equals(GearTooltip.ERROR_COLOR)));
        assertTrue(result.stream().anyMatch(l->l.text().equals("Requires 70 Strength")&&l.style()==GearTooltip.Style.NORMAL));
    }
    @Test void unmetAttributeIsRedWhileMetLevelIsNeutral(){
        var result=GearTooltip.describe(item("common"),99,Map.of());
        assertTrue(result.stream().anyMatch(l->l.text().equals("Requires 70 Strength")&&l.style()==GearTooltip.Style.ERROR));
        assertTrue(result.stream().anyMatch(l->l.text().equals("Requires Level 25")&&l.style()==GearTooltip.Style.NORMAL));
    }
    @Test void twoUnmetRequirementsMatchTheCanonicalGate(){
        var item=item("common");var attrs=Map.<RpgAttribute,Integer>of();
        assertEquals(2,item.requirements().failures(1,attrs,false).size());
        assertEquals(2,GearTooltip.describe(item,1,attrs).stream().filter(l->l.style()==GearTooltip.Style.ERROR).count());
    }
    @Test void flavorIsOptionalMutedAndSeparated(){
        var flavor="Forged in the deep places, where stone remembers.";
        var full=GearTooltip.describe(item("common"),99,Map.of(),flavor);
        assertEquals(flavor,full.getLast().text());assertEquals(GearTooltip.Style.FLAVOR,full.getLast().style());
        assertEquals(GearTooltip.MUTED_COLOR,full.getLast().color());
        assertEquals(GearTooltip.Style.DIVIDER,full.get(full.size()-2).style());
        assertTrue(lines("common").stream().noneMatch(l->l.style()==GearTooltip.Style.FLAVOR));
        assertEquals(lines("common").size(),GearTooltip.describe(item("common"),99,Map.of()," ").size());
    }
    @Test void noInternalIdsOrGenerationDebugData(){
        var text=text(lines("rare"));
        for(String forbidden:List.of("ID:","Weapon_Battleaxe_Adamantite","gm.battleaxe",OWNER.toString(),"Source era","Intrinsic base roll","WA-"))assertFalse(text.contains(forbidden),forbidden);
    }
    @Test void dynamicContentOrdersDamageAffixesThenRequirements(){
        var common=lines("common");var rare=lines("rare");assertTrue(rare.size()>common.size());
        var text=text(rare);assertTrue(text.indexOf("Damage")<text.indexOf(GearAffixDisplay.format(item("rare").affixes().getFirst())));
        assertTrue(text.indexOf(GearAffixDisplay.format(item("rare").affixes().getLast()))<text.indexOf("Requires Level"));
    }
    @Test void dividerAndTypographyTokensFollowPresentSections(){
        var common=lines("common");var rare=lines("rare");
        assertEquals(GearTooltip.Style.NAME,common.getFirst().style());
        assertEquals(GearTooltip.Style.NORMAL,common.get(1).style());
        assertEquals(GearTooltip.Style.DIVIDER,common.get(2).style());
        assertEquals(GearTooltip.Style.HEADING,common.get(3).style());
        assertEquals("#bca57a",common.get(3).color());
        assertEquals(GearTooltip.Style.DAMAGE,common.get(4).style());
        assertEquals("#ffffff",common.get(4).color());
        assertEquals(GearTooltip.Style.DIVIDER,common.get(5).style());
        assertEquals(2,common.stream().filter(l->l.style()==GearTooltip.Style.DIVIDER).count());
        assertEquals(3,rare.stream().filter(l->l.style()==GearTooltip.Style.DIVIDER).count());
        for(var line:rare)if(line.style()==GearTooltip.Style.DIVIDER){
            assertEquals("",line.text(),"section boundaries are rendered by the custom UI, not glyphs");
            assertEquals(GearTooltip.DIVIDER_COLOR,line.color());
        }
    }
    @Test void utilityRestrictionIsNotNormalTooltipContent(){
        assertFalse(text(lines("rare")).contains("Cannot salvage"));
    }
    @Test void equipUnequipAndTooltipViewsNeverChangeFrozenRolls(){
        var item=item("rare");String before=item.toJson();
        var baseline=Map.of(RpgAttribute.STR,999);
        for(boolean equipped:new boolean[]{false,true,false}){
            var resolved=GearEquipmentResolution.resolve(99,baseline,List.of(new GearEquipmentResolution.Candidate(item,equipped,true,true)));
            GearTooltip.describe(item,99,resolved.validity().permanentAttributes());
            assertEquals(before,item.toJson());assertEquals(item.affixes(),GearInstance.fromJson(before).affixes());
        }
    }
    @Test void qaFixturesAreDeterministicAndHaveOrdinaryLegalRarityBudgets(){
        for(String band:List.of("common","magic","rare")){
            var gear=item(band);assertEquals(gear,item(band));assertEquals("gm.battleaxe_adamantite.n",gear.baseId());
            int prefixes=(int)gear.affixes().stream().filter(a->a.side()==GearCatalog.Side.PREFIX).count();
            assertTrue(gear.rarity().legalNewBudget(prefixes,gear.affixes().size()-prefixes));
            assertTrue(gear.qaOnly());assertEquals(item("common").intrinsicStats(),gear.intrinsicStats());
        }
    }
}
