import java.lang.reflect.*;
public class CleanseTestRunner {
  public static void main(String[] args) throws Exception {
    var type=Class.forName("com.inigmasgames.hytalerpg.GearSupportConsumerTest");
    var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var instance=ctor.newInstance();
    var method=type.getDeclaredMethod("genericStatusOwnerCleanseEmitsOnlyAfterActualRemovalAndGrantsRespite");
    method.setAccessible(true);
    try {method.invoke(instance);System.out.println("PASS "+method.getName());}
    catch(InvocationTargetException e){e.getCause().printStackTrace();System.exit(1);}
  }
}
