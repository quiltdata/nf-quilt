package nextflow.quilt.jep;

public class Quilt {
    public static native String commit(String domain, String namespace, String message);

    public static native String install(String domain, String uri);

    public static native String push(String domain, String namespace);

    static {
        System.load("/home/fiskus/reps/quilt-rs/target/debug/libquilt_rs.so");
    }
}
