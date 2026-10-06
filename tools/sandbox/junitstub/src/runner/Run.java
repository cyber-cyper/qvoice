package runner;
import java.io.File;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
/** Minimal reflective JUnit-4-style runner for the sandbox. Usage: Run <classesDir> [classpath...] */
public class Run {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        List<String> names = new ArrayList<>();
        collect(root, root, names);
        int pass = 0, fail = 0;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String n : names) {
            Class<?> c;
            try { c = Class.forName(n, false, cl); } catch (Throwable t) { continue; }
            List<Method> tests = new ArrayList<>(), befores = new ArrayList<>(), afters = new ArrayList<>();
            for (Method m : c.getMethods()) {
                if (m.isAnnotationPresent(org.junit.Test.class)) tests.add(m);
                if (m.isAnnotationPresent(org.junit.Before.class)) befores.add(m);
                if (m.isAnnotationPresent(org.junit.After.class)) afters.add(m);
            }
            tests.sort(Comparator.comparing(Method::getName));
            for (Method m : tests) {
                Class<? extends Throwable> expected = m.getAnnotation(org.junit.Test.class).expected();
                boolean expectsThrow = expected != org.junit.Test.None.class;
                try {
                    Object o = c.getDeclaredConstructor().newInstance();
                    for (Method b : befores) b.invoke(o);
                    try { m.invoke(o); } finally { for (Method a : afters) a.invoke(o); }
                    if (expectsThrow) { fail++; System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + ": expected " + expected.getSimpleName()); }
                    else pass++;
                } catch (InvocationTargetException e) {
                    if (expectsThrow && expected.isInstance(e.getCause())) { pass++; continue; }
                    fail++;
                    System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + ": " + e.getCause());
                    StackTraceElement[] st = e.getCause().getStackTrace();
                    for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("    at " + st[i]);
                }
            }
        }
        System.out.println("TESTS: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
    static void collect(File root, File f, List<String> out) {
        if (f.isDirectory()) { for (File k : Objects.requireNonNull(f.listFiles())) collect(root, k, out); return; }
        String p = root.toURI().relativize(f.toURI()).getPath();
        if (p.endsWith("Test.class") && !p.contains("$")) out.add(p.substring(0, p.length() - 6).replace('/', '.'));
    }
}
