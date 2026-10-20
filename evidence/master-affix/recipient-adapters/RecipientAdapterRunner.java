import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class RecipientAdapterRunner {
  public static void main(String[] args) {
    var request=LauncherDiscoveryRequestBuilder.request().selectors(
      selectClass("com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixInheritanceTest"),
      selectClass("com.inigmasgames.hytalerpg.execution.hytale.SentinelSourceConsumerTest"),
      selectClass("com.inigmasgames.hytalerpg.execution.hytale.GearStatusBindingsTest"),
      selectClass("com.inigmasgames.hytalerpg.execution.hytale.SentinelDefenseInheritanceTest")
    ).build();
    var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);launcher.execute(request);
    var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));
    summary.printFailuresTo(new PrintWriter(System.out));
    if(summary.getTestsFoundCount()==0||summary.getTotalFailureCount()>0)System.exit(1);
  }
}
