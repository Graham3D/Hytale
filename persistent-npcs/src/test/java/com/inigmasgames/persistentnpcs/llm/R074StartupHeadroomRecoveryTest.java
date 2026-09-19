package com.inigmasgames.persistentnpcs.llm;

/** Permanent regression for the connected R073 two-layer headroom shortfall. */
public final class R074StartupHeadroomRecoveryTest {
    private R074StartupHeadroomRecoveryTest() { }

    public static void main(String[] args) {
        assert OpenAiCompatibleProvider.nextLowerMemoryGpuLayers(4) == 2;
        assert OpenAiCompatibleProvider.nextLowerMemoryGpuLayers(2) == 0;
        assert OpenAiCompatibleProvider.nextLowerMemoryGpuLayers(1) == 0;
        assert OpenAiCompatibleProvider.nextLowerMemoryGpuLayers(0) == 0;
        System.out.println("R074 startup headroom recovery tests passed.");
    }
}
