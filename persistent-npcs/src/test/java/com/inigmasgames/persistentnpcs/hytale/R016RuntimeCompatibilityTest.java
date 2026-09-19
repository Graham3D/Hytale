package com.inigmasgames.persistentnpcs.hytale;

/** Verifies the installed runtime exposes the Update 6 NPC ABI. */
public final class R016RuntimeCompatibilityTest {
    private R016RuntimeCompatibilityTest() {
    }

    public static void main(String[] args) {
        boolean expected = args.length == 1 && "update6".equals(args[0]);
        RuntimeApiCompatibility compatibility = RuntimeApiCompatibility.detect();
        assert compatibility.update6NpcApi() == expected
                : "expected update6=" + expected + " but detected " + compatibility;
        if (!expected) {
            assert compatibility.blockerMessage().contains("Select Pre-release");
        }
        System.out.println("R016 runtime compatibility gate passed expectedUpdate6=" + expected);
    }
}
