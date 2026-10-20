import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class ChannelFinalOwnerRunner {
  public static void main(String[] args) throws Exception {
    var builder=LauncherDiscoveryRequestBuilder.request();
    for(String name:args)builder.selectors(selectClass(Class.forName(name)));
    var listener=new SummaryGeneratingListener();
    var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
    launcher.execute(builder.build());var summary=listener.getSummary();
    summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
    if(summary.getTotalFailureCount()>0||summary.getTestsFoundCount()==0)System.exit(1);
  }
}
