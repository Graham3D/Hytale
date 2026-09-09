package com.inigmasgames.hytalerpg.execution;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, allowlisted diagnostics: never serialize arbitrary exception messages, paths or inventory data. */
public final class PreparationFailureDiagnostics {
    private PreparationFailureDiagnostics() { }

    public static Map<String, Object> describe(String stage, Stage04SkillProfile profile,
            SkillExecutionPort.Equipment equipment, RuntimeException failure) {
        String message = failure.getMessage();
        String code = "PREPARATION_EXCEPTION";
        String safeMessage = "Preparation failed at the recorded stage; no gameplay dispatch occurred.";
        if (failure instanceof IllegalArgumentException && message != null) {
            if (message.startsWith("Item has no authored MagicPower: ")) {
                code = "MISSING_AUTHORED_MAGIC_POWER";
                safeMessage = "Equipped item has no audited authored MagicPower; use an audited magic weapon.";
            } else if (message.startsWith("Item has no audited WeaponPower: ")) {
                code = "MISSING_AUDITED_WEAPON_POWER";
                safeMessage = "Equipped item has no audited WeaponPower.";
            } else if (message.equals("Item must have exactly one explicit RPG weapon classification")) {
                code = "INVALID_WEAPON_CLASSIFICATION";
                safeMessage = "Equipped power descriptor must have exactly one audited weapon classification.";
            }
        }
        var item = equipment == null ? null : profile.basePowerSource().equals("OFFHAND_WEAPON")
                ? equipment.offHand() : equipment.mainHand();
        var result = new LinkedHashMap<String, Object>();
        result.put("failureStage", safeToken(stage));
        result.put("failureCode", code);
        result.put("failureType", safeToken(failure.getClass().getSimpleName()));
        result.put("safeMessage", safeMessage);
        result.put("skillId", safeToken(profile.skillId()));
        result.put("powerSource", safeToken(profile.basePowerSource()));
        result.put("itemId", item == null ? "NONE" : safeToken(item.itemId()));
        result.put("weaponKind", item == null ? "NONE" : safeToken(item.weaponKind()));
        result.put("weaponPowerPresent", item != null && item.power() != null && item.power().weaponPower() != null);
        result.put("magicPowerPresent", item != null && item.power() != null && item.power().magicPower() != null);
        return Map.copyOf(result);
    }

    private static String safeToken(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,96}") ? value : "OMITTED";
    }
}
