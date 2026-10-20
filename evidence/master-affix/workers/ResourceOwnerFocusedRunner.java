import java.lang.reflect.InvocationTargetException;

public final class ResourceOwnerFocusedRunner {
    public static void main(String[] args) throws Exception {
        for (int i=0;i<args.length;i+=2) {
            var type=Class.forName(args[i]);
            var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
            var method=type.getDeclaredMethod(args[i+1]);method.setAccessible(true);
            try {
                method.invoke(constructor.newInstance());
                System.out.println("PASS "+args[i]+"#"+args[i+1]);
            } catch (InvocationTargetException failure) {
                failure.getCause().printStackTrace();
                System.exit(1);
            }
        }
    }
}
