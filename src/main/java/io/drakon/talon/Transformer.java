package io.drakon.talon;

import org.apiguardian.api.API;

/**
 * Interface that defines a bytecode transformer which can be attached to a {@link Talon} instance via
 * {@link Talon#addTransformer(Transformer)}
 */
@API(status = API.Status.EXPERIMENTAL)
public interface Transformer {

    /**
     * Optionally transform the provided class bytes. If no transformation is required on the class, return null. The
     * byte array input must not be directly modified.
     *
     * Note that transformers are created in the time before a Talon classloader is available, so any classes accessed
     * or instantiated in this call will be from the system classloader. Passing data from a transformer to code within
     * the application is extremely risky, and best avoided, due to class identity crisis issues.
     *
     * @param className The class name to be transformed.
     * @param pkgName The package the class is within.
     * @param classBytes The class bytes.
     * @return New class bytes, or null if no transformation required.
     */
    byte[] transform(String className, String pkgName, byte[] classBytes);

}
