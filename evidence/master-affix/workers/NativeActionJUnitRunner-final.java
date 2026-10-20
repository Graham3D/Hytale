package com.inigmasgames.hytalerpg.gear;

import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public final class NativeActionJUnitRunner {
    public static void main(String[] args) {
        var selectors = args.length == 0
                ? new org.junit.platform.engine.DiscoverySelector[]{selectClass(NativeAffixActionAssetQualificationTest.class)}
                : java.util.Arrays.stream(args).map(org.junit.platform.engine.discovery.DiscoverySelectors::selectClass)
                        .toArray(org.junit.platform.engine.DiscoverySelector[]::new);
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));
        summary.getFailures().forEach(failure -> failure.getException().printStackTrace(System.out));
        if (summary.getTotalFailureCount() > 0 || summary.getTestsFoundCount() == 0) System.exit(1);
    }
}
