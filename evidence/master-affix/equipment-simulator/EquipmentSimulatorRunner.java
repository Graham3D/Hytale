import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.engine.TestExecutionResult;

public final class EquipmentSimulatorRunner {
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass("com.inigmasgames.hytalerpg.gear.MasterAffixEquipmentSimulatorTest"),
                        selectClass("com.inigmasgames.hytalerpg.gear.GearEquipmentLifecycleTest")).build();
        var listener=new SummaryGeneratingListener();
        var invocations=new StringBuilder("uniqueId\tdisplayName\tstatus\n");
        var launcher=LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.registerTestExecutionListeners(new TestExecutionListener() {
            @Override public void executionFinished(TestIdentifier test,TestExecutionResult result) {
                if(test.isTest()) invocations.append(test.getUniqueId()).append('\t')
                        .append(test.getDisplayName()).append('\t').append(result.getStatus()).append('\n');
            }
        });
        launcher.execute(request);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(
                    "evidence/master-affix/equipment-simulator/invocations.tsv"),invocations);
        } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        var summary=listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out,true));
        summary.getFailures().forEach(f->System.out.println("FAIL "+f.getTestIdentifier().getDisplayName()
                +": "+f.getException().getMessage()));
        if(summary.getTestsFailedCount()!=0 || summary.getTestsFoundCount()<330) System.exit(1);
    }
}
