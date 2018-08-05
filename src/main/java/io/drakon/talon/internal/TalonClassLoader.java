package io.drakon.talon.internal;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.drakon.talon.Transformer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apiguardian.api.API;

/**
 * ClassLoader which performs the necessary transformations.
 * <p>
 * Based on the <a href="https://github.com/Mojang/LegacyLauncher/blob/master/src/main/java/net/minecraft/launchwrapper/LaunchClassLoader.java">
 * Mojang LaunchWrapper classloader</a>
 */
@Slf4j
@API(status = API.Status.INTERNAL, consumers = {"io.drakon.talon.Talon"})
public class TalonClassLoader extends ClassLoader {

    // Packages that would generally be bad to transform. Can be overridden by explicit whitelist entries.
    private static final String[] BLACKLISTED_PACKAGE_PREFIXES = new String[]{
            "java.",
            "javax.",
            "sun.",
            "com.sun.",
            "org.objectweb.asm.",
            "com.google.common.",
            "io.drakon.talon."
    };
    private static final String SAVE_CLASSES = System.getProperty("talon.saveClassesTo");

    private final Set<String> whitelist;
    private final List<Transformer> transformers;
    private Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final ClassLoader parent = ClassLoader.getSystemClassLoader();
    private final ClassLoader bootstrap = parent.getParent();

    /**
     * Constructs a new {@link TalonClassLoader}.
     *
     * @param whitelist    Whitelist of package prefixes to include in the transformation process. Can be null, in which
     *                     case all packages except those on the standard blacklist will be transformer candidates.
     * @param transformers The transformers to apply to acceptable classes.
     */
    public TalonClassLoader(Set<String> whitelist, List<Transformer> transformers, List<Function<ClassLoader, Transformer>> pendingTransformers) {
        super(ClassLoader.getSystemClassLoader());
        this.whitelist = whitelist;
        this.transformers = transformers;
        if (SAVE_CLASSES != null) {
            File saveClassDir = new File(SAVE_CLASSES);
            if (!saveClassDir.isDirectory() || !saveClassDir.canWrite()) {
                log.error("Value of talon.saveClassesTo is invalid: not a directory or not writable.");
                throw new RuntimeException("invalid talon.saveClassesTo directory");
            }
        }
        transformers.addAll(pendingTransformers.stream().map(it -> it.apply(this)).collect(Collectors.toList()));
    }

    // This is mostly a direct copy of the JDK implementation, switched around for our use. This version queries *this*
    // classloader before the parent, inverting the normal delegation pattern.
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = findClass(name);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (classCache.containsKey(name)) {
            return classCache.get(name);
        }

        // Always check the bootstrap classloader first. If it exists here, it's JDK-internal and we shouldn't fiddle
        // with it!
        try {
            Class<?> bootstrapResponse = bootstrap.loadClass(name);
            if (bootstrapResponse != null) {
                classCache.put(name, bootstrapResponse);
                return bootstrapResponse;
            }
        } catch (ClassNotFoundException ex) {
            // Pass
        }

        int lastDot = name.lastIndexOf('.');
        String pkgName = lastDot == -1 ? "" : name.substring(0, lastDot);
        String className = lastDot == -1 ? name : name.substring(lastDot + 1);
        String fileName = name.replace('.', '/').concat(".class");

        try {
            Package pkg = getPackage(pkgName);
            if (pkg == null) {
                // TODO(emberwalker): We could define this better, e.g. by using Jar metadata.
                pkg = definePackage(pkgName, null, null, null, null, null, null, null);
            }

            byte[] bytes = getBytes(fileName);
            if (bytes == null) {
                throw new ClassNotFoundException("unable to load class bytes: " + fileName);
            }

            if (shouldTransform(name)) {
                boolean hasTransformed = false;
                log.trace("Starting transforms of class: {}", name);
                for (Transformer transformer : transformers) {
                    log.trace("Running transformer {} on {}", transformer, name);
                    byte[] newBytes = transformer.transform(className, pkgName, bytes);
                    if (newBytes != null) {
                        bytes = newBytes;
                        hasTransformed = true;
                        log.trace("Transformer {} applied on {}", transformer, name);
                    } else {
                        log.trace("Transformer {} made no changes to {}", transformer, name);
                    }
                }
                if (!hasTransformed) {
                    log.trace("No transformations applied to {}", name);
                } else {
                    saveToDisk(bytes, fileName);
                }
            }

            Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
            classCache.put(name, clazz);
            return clazz;
        } catch (ClassNotFoundException ex) {
            throw new ClassNotFoundException(name, ex);
        } catch (Throwable t) {
            log.trace("Unable to load class {}", name, t);
            throw new ClassNotFoundException(name, t);
        }
    }

    private byte[] getBytes(String fileName) throws IOException {
        InputStream inputStream = null;
        try {
            URL resource = parent.getResource(fileName);
            if (resource == null) {
                return null;
            }
            inputStream = resource.openStream();
            return IOUtils.toByteArray(inputStream);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    // Skip!
                }
            }
        }
    }

    private void saveToDisk(byte[] bytes, String fileName) {
        if (SAVE_CLASSES == null) {
            return;
        }

        File out = new File(SAVE_CLASSES + "/" + fileName);
        if (!out.getParentFile().exists() && !out.getParentFile().mkdirs()) {
            log.error("Failed to create dirs for path: {}", out.getPath());
            return;
        }
        if (out.exists() && !out.delete()) {
            log.error("Failed to delete old file for path: {}", out.getPath());
            return;
        }

        try {
            log.debug("Saving transformed class: {}", fileName);
            OutputStream outStream = new FileOutputStream(out);
            outStream.write(bytes);
            outStream.close();
        } catch (IOException ex) {
            log.error("Unable to save class file '{}': {}", out.getPath(), ex);
        }
    }

    private boolean shouldTransform(String name) {
        if (whitelist == null) {
            return Arrays.stream(BLACKLISTED_PACKAGE_PREFIXES).noneMatch(name::startsWith);
        }
        return whitelist.stream().anyMatch(name::startsWith);
    }

}
