package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Generated developer view over the existing role binding and affix planner; never a gameplay registry. */
public final class EnemyEliteRoleMatrix {
    public record Row(String roleId,String canonicalRoleId,String rejection,boolean productionEnabled,
                      Map<DifficultyId,Boolean> champion,Map<DifficultyId,Boolean> unique,
                      List<String> authoredSuperUniqueTemplates){
        public Row {
            champion=Map.copyOf(champion);unique=Map.copyOf(unique);
            authoredSuperUniqueTemplates=List.copyOf(authoredSuperUniqueTemplates);
        }
        public boolean certified(){return rejection==null;}
    }
    private final EnemyRewardRegistry catalog;
    private final EnemyNativeBindings bindings;
    private final EnemyAffixRegistry affixes;
    private final EnemyBalance balance;
    private final EnemyAffixSelection selection;
    private final SuperUniqueTemplates templates;

    public EnemyEliteRoleMatrix(){
        catalog=EnemyRewardRegistry.load();bindings=EnemyNativeBindings.load();
        affixes=EnemyAffixRegistry.canonical();balance=EnemyBalance.canonical();
        selection=new EnemyAffixSelection(affixes);templates=SuperUniqueTemplates.canonical();
    }
    public List<Row> rows(){
        var ids=new TreeSet<String>();
        catalog.roles().forEach(role->ids.add(role.roleId()));
        catalog.aliases().forEach(alias->ids.add(alias.roleId()));
        var rows=new ArrayList<Row>();for(var id:ids)rows.add(evaluate(id));
        return List.copyOf(rows);
    }
    private Row evaluate(String id){
        var resolved=catalog.resolveRole(id).orElseThrow();
        var rejection=bindings.productionEligibilityRejection(id).orElse(null);
        var role=bindings.role(id).orElse(null);
        var champion=new EnumMap<DifficultyId,Boolean>(DifficultyId.class);
        var unique=new EnumMap<DifficultyId,Boolean>(DifficultyId.class);
        if(rejection==null){
            for(var era:DifficultyId.values()){
                champion.put(era,hasSet(role,era,EnemyRarity.CHAMPION));
                unique.put(era,hasSet(role,era,EnemyRarity.UNIQUE));
            }
            if(champion.values().stream().noneMatch(Boolean::booleanValue)
                    &&unique.values().stream().noneMatch(Boolean::booleanValue))
                rejection="NO_COMPLETE_LEGAL_AFFIX_SET";
        }
        var authored=new ArrayList<String>();
        if(rejection==null)for(var template:templates.all())
            if(template.canonicalRoleId().equals(resolved.canonical().roleId())&&templateCompatible(role,template))
                authored.add(template.id());
        authored.sort(String::compareTo);
        return new Row(id,resolved.canonical().roleId(),rejection,role!=null&&role.productionPromotionEnabled(),
                champion,unique,authored);
    }
    private boolean hasSet(EnemyNativeBindings.Role role,DifficultyId era,EnemyRarity rarity){
        int minions=rarity==EnemyRarity.CHAMPION?0:balance.promotion().minionMinimum();
        var binding=role.affixBinding(bindings.revision(),meaningfulResistance(role),minions);
        int count=balance.rarity(rarity,false).counts().get(era.ordinal());
        return selection.select(new EnemyAffixSelection.Request(binding,era,rarity,count,List.of(),true),
                "elite-matrix/"+role.canonicalRoleId()+"/"+rarity+"/"+era).isPresent();
    }
    private boolean templateCompatible(EnemyNativeBindings.Role role,SuperUniqueTemplates.Template template){
        var binding=role.affixBinding(bindings.revision(),meaningfulResistance(role),template.minionCount());
        for(var era:template.difficultyModes()){
            for(var fixed:template.fixedAffixes())if(selection.rejectionReason(binding,EnemyRarity.SUPER_UNIQUE,fixed).isPresent())
                return false;
            var request=new EnemyAffixSelection.Request(binding,era,EnemyRarity.SUPER_UNIQUE,
                    template.affixCount(era),template.fixedAffixes(),false);
            if(selection.select(request,"elite-template/"+template.id()+"/"+era).isEmpty())return false;
        }
        return true;
    }
    private static boolean meaningfulResistance(EnemyNativeBindings.Role role){
        return role.nativeImmunityChannels().stream().filter(channel->!channel.equals("PHYSICAL")).count()<6;
    }
    private String affixDecision(EnemyNativeBindings.Role role,EnemyAffixRegistry.Operator operator){
        var binding=role.affixBinding(bindings.revision(),meaningfulResistance(role),balance.promotion().minionMinimum());
        var choices=new ArrayList<EnemyAffixSelection.Choice>();
        if(operator==EnemyAffixRegistry.Operator.AURA_ENCHANTED)
            for(var selector:EnemyAffixRegistry.Selector.values())choices.add(new EnemyAffixSelection.Choice(operator.id(),selector));
        else choices.add(EnemyAffixSelection.Choice.of(operator));
        if(affixes.require(operator).weight(DifficultyId.HELL)==0)return "NO_RANDOM_WEIGHT_HELL";
        for(var choice:choices)if(selection.rejectionReason(binding,EnemyRarity.UNIQUE,choice).isEmpty())return "LEGAL";
        return selection.rejectionReason(binding,EnemyRarity.UNIQUE,choices.getFirst()).orElseThrow();
    }
    public String render(){
        var rows=rows();var output=new StringBuilder();
        output.append("# Production Elite role matrix\n\n")
                .append("Generated from `EnemyRewardRegistry`, `EnemyNativeBindings`, `EnemyAffixSelection`, ")
                .append("`EnemyBalance`, and authored Super Unique templates. Regenerate with ")
                .append("`./gradlew generateEliteRoleMatrix`. Do not edit this file by hand.\n\n")
                .append("Certification describes a role's native binding. At each natural birth, the existing ")
                .append("world-spawn classifier must still prove a hostile actor, authored combat level, ")
                .append("ordinary defeat lifecycle, and no protected/QA/campaign ownership. ")
                .append("`Enabled` is the independent rollout gate; a role without a native world-spawn entry has no natural birth to promote. ")
                .append("Super Uniques require an authored template; they are never random. ")
                .append("Elemental resistance assumes at least one nonimmune channel and is rechecked against the frozen combat profile at birth.\n\n")
                .append("| Concrete role | Canonical role | Certified | Enabled | Champion N/Nm/H | Unique N/Nm/H | Authored SU template | Denial |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for(var row:rows)output.append("| ").append(row.roleId()).append(" | ").append(row.canonicalRoleId())
                .append(" | ").append(row.certified()?"yes":"no")
                .append(" | ").append(row.productionEnabled()?"yes":"no")
                .append(" | ").append(eraFlags(row.champion()))
                .append(" | ").append(eraFlags(row.unique()))
                .append(" | ").append(row.authoredSuperUniqueTemplates().isEmpty()?"—":String.join(", ",row.authoredSuperUniqueTemplates()))
                .append(" | ").append(row.rejection()==null?"—":row.rejection()).append(" |\n");
        output.append("\n## Affix decisions for certified roles\n\n")
                .append("The entries below use the real Unique/Hell selector with the authored minimum minion roster. ")
                .append("`LEGAL` means that individual card passes admission, not that every combination is legal; ")
                .append("the birth planner still enforces group and count constraints. ")
                .append("For denied roles, every ME-001..ME-027 card is blocked by the role denial above.\n\n");
        for(var row:rows)if(row.certified()){
            var role=bindings.role(row.roleId()).orElseThrow();
            output.append("### ").append(row.roleId()).append("\n\n| Affix | Decision |\n| --- | --- |\n");
            for(var operator:EnemyAffixRegistry.Operator.values())
                output.append("| ").append(operator.id()).append(' ').append(affixes.require(operator).displayName())
                        .append(" | ").append(affixDecision(role,operator)).append(" |\n");
            output.append('\n');
        }
        return output.toString().stripTrailing()+"\n";
    }
    private static String eraFlags(Map<DifficultyId,Boolean> values){
        if(values.isEmpty())return "—";
        var flags=new ArrayList<String>();for(var era:DifficultyId.values())flags.add(values.getOrDefault(era,false)?"Y":"N");
        return String.join("/",flags);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("EXPECTED_MATRIX_OUTPUT_PATH");
        var path=Path.of(args[0]);Files.createDirectories(path.getParent());
        Files.writeString(path,new EnemyEliteRoleMatrix().render(),StandardCharsets.UTF_8);
    }
}
