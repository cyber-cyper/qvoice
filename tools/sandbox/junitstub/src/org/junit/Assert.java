package org.junit;
import java.util.Arrays;
import java.util.Objects;
/** Sandbox-only subset of JUnit 4's Assert with identical signatures. */
public class Assert {
    protected Assert() {}
    public static void fail() { throw new AssertionError(); }
    public static void fail(String m) { throw new AssertionError(m); }
    public static void assertTrue(boolean c) { if (!c) fail("expected true"); }
    public static void assertTrue(String m, boolean c) { if (!c) fail(m); }
    public static void assertFalse(boolean c) { if (c) fail("expected false"); }
    public static void assertFalse(String m, boolean c) { if (c) fail(m); }
    public static void assertNull(Object o) { if (o != null) fail("expected null but was <" + o + ">"); }
    public static void assertNull(String m, Object o) { if (o != null) fail(m + " expected null but was <" + o + ">"); }
    public static void assertNotNull(Object o) { if (o == null) fail("expected not null"); }
    public static void assertNotNull(String m, Object o) { if (o == null) fail(m); }
    public static void assertEquals(Object e, Object a) { assertEquals(null, e, a); }
    public static void assertEquals(String m, Object e, Object a) {
        if (!Objects.equals(e, a)) fail((m == null ? "" : m + " ") + "expected:<" + e + "> but was:<" + a + ">");
    }
    public static void assertEquals(long e, long a) { assertEquals(null, e, a); }
    public static void assertEquals(String m, long e, long a) { if (e != a) fail((m == null ? "" : m + " ") + "expected:<" + e + "> but was:<" + a + ">"); }
    public static void assertEquals(double e, double a, double d) { assertEquals(null, e, a, d); }
    public static void assertEquals(String m, double e, double a, double d) { if (Math.abs(e - a) > d) fail((m == null ? "" : m + " ") + "expected:<" + e + "> but was:<" + a + ">"); }
    public static void assertEquals(float e, float a, float d) { assertEquals(null, e, a, d); }
    public static void assertEquals(String m, float e, float a, float d) { if (Math.abs(e - a) > d) fail((m == null ? "" : m + " ") + "expected:<" + e + "> but was:<" + a + ">"); }
    public static void assertNotEquals(Object u, Object a) { if (Objects.equals(u, a)) fail("values should differ: <" + a + ">"); }
    public static void assertNotEquals(String m, Object u, Object a) { if (Objects.equals(u, a)) fail(m); }
    public static void assertArrayEquals(byte[] e, byte[] a) { if (!Arrays.equals(e, a)) fail("arrays differ"); }
    public static void assertArrayEquals(Object[] e, Object[] a) { if (!Arrays.equals(e, a)) fail("arrays differ: " + Arrays.toString(e) + " vs " + Arrays.toString(a)); }
    public static void assertSame(Object e, Object a) { if (e != a) fail("expected same"); }
}
