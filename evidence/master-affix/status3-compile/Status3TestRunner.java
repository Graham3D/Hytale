import java.lang.reflect.*;
public class Status3TestRunner {
  public static void main(String[] args) throws Exception {
    int passed=0,failed=0;
    for(String name:args){
      Class<?> type=Class.forName(name);Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
      Object instance=ctor.newInstance();
      for(Method method:type.getDeclaredMethods())if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)){
        method.setAccessible(true);
        try{method.invoke(instance);System.out.println("PASS "+name+"#"+method.getName());passed++;}
        catch(InvocationTargetException error){System.out.println("FAIL "+name+"#"+method.getName());error.getCause().printStackTrace();failed++;}
      }
    }
    System.out.println("TOTAL passed="+passed+" failed="+failed);
    if(failed>0)System.exit(1);
  }
}
