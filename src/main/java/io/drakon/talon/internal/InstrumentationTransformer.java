package io.drakon.talon.internal;

import java.lang.instrument.ClassFileTransformer;

import io.drakon.talon.Transformer;
import lombok.extern.slf4j.Slf4j;
import org.apiguardian.api.API;

/**
 * Wrapper for existing {@link ClassFileTransformer} implementations, to make them run on Talon.
 */
@Slf4j
@API(status = API.Status.INTERNAL, consumers = {"io.drakon.talon.Talon"})
public class InstrumentationTransformer implements Transformer {

    private final ClassFileTransformer wrapped;
    private final ClassLoader classLoader;

    public InstrumentationTransformer(ClassFileTransformer wrapped, ClassLoader classLoader) {
        this.wrapped = wrapped;
        this.classLoader = classLoader;
    }

    @Override
    public byte[] transform(String className, String pkgName, byte[] classBytes) {
        try {
            return wrapped.transform(classLoader, pkgName + "." + className, null, null, classBytes);
        } catch (Throwable ex) {
            log.debug("Exception caught from ClassFileTransformer; skipping transformer.", ex);
            return null;
        }
    }

}
