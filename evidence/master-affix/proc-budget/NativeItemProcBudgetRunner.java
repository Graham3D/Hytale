package com.inigmasgames.hytalerpg.gear;

public final class NativeItemProcBudgetRunner {
    public static void main(String[] args){
        new NativeItemProcBudgetTest().controlDuplicateChannelsAndCancelledFirstComponent();
        new NativeItemProcBudgetTest().crowdsAndPelletsShareOneRootAllowance();
        new NativeItemProcBudgetTest().wrongWorldSourceChangeAndCleanup();
        new NativeItemProcBudgetTest().paidWindowsSharePaidDeltaAndUnpaidTicksHaveNoCredit();
        System.out.println("NativeItemProcBudgetTest: 4 passed");
    }
}
