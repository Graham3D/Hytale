package com.inigmasgames.hytalerpg.commands;

import java.util.ArrayList;
import java.util.List;

/** Positional suffix parser for the exact owner QA spawn syntax. */
final class QaSpawnAliasSyntax {
    private QaSpawnAliasSyntax() { }

    static List<String> positionalAliases(String input, String role, String family, String era) {
        var tokens=input==null?new String[0]:input.strip().split("\\s+");
        for(int i=0;i+1<tokens.length;i++)if(tokens[i].equalsIgnoreCase(role)
                &&tokens[i+1].equalsIgnoreCase(family)&&i+2<tokens.length
                &&tokens[i+2].equalsIgnoreCase(era)){
            var result=new ArrayList<String>();
            for(int j=i+3;j<tokens.length;j++){
                if(tokens[j].startsWith("--"))throw new IllegalArgumentException(
                        "Use positional affix aliases: /rpg spawn <monster> <family> <era> [affixAlias...]");
                result.add(tokens[j]);
            }
            return List.copyOf(result);
        }
        throw new IllegalArgumentException("Could not parse QA spawn affixes; use /rpg spawn <monster> <family> <era> [affixAlias...]");
    }
}
