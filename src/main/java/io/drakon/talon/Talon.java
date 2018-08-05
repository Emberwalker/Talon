package io.drakon.talon;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.drakon.talon.internal.InstrumentationTransformer;
import io.drakon.talon.internal.TalonClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.apiguardian.api.API;

/**
 * Talon is a bytecode-transforming classloading utility intended for performing runtime bytecode modification or
 * analysis on an application while will be started in the same JVM.
 * <p>
 * It achieves this by constructing a classloader which inverts the normal delegation between classloaders in order to
 * run transformers over bytecode as it is loaded. Anything instantiated via this classloader will inherit it,
 * enabling the modification of an entire application and it's dependencies.
 */
@Slf4j
@API(status = API.Status.EXPERIMENTAL)
public class Talon {

    private final String targetClass;
    private final boolean isStatic;
    private final String targetMethod;
    private final Object[] args;
    private boolean started = false;
    private ClassLoader classLoader = null;

    private Set<String> packageWhitelist = Collections.synchronizedSet(new HashSet<>());
    private List<Transformer> transformers = new LinkedList<>();
    private List<Function<ClassLoader, Transformer>> pendingTransformers = new LinkedList<>();

    /**
     * Whether or not this Talon manager has been started. If this is {@literal true} then no more changes can be made
     * to the Talon configuration and the classloader is available.
     *
     * @return {@literal true} if Talon has started.
     */
    public boolean hasStarted() {
        return started;
    }

    /**
     * The Talon class loader. Use this if you need to manually load a class in the new class loader. Will be null
     * before {@link #start()} is called.
     *
     * @return The Talon classloader if available, {@literal null} if Talon has not started yet.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Construct a new Talon manager, which uses a <i>main(String[])</i> method as a target.
     *
     * @param targetClass The name of the target class with a main method.
     * @param args        The arguments to pass to that main method.
     */
    public Talon(String targetClass, String[] args) {
        this.targetClass = targetClass;
        this.targetMethod = "main";
        this.isStatic = true;
        this.args = new Object[]{args};
    }

    /**
     * Construct a new Talon manager which calls a given method as a target. This method can be static, or Talon
     * can construct an instance using a no-args constructor and invoke the method on that instance.
     *
     * @param targetClass  The name of the target class containing the target method.
     * @param targetMethod The name of the target method.
     * @param isStatic     True if the method should be called statically, else false to construct an instance.
     * @param args         Any arguments to pass to the method. It is the callers responsibility to ensure these are the
     *                     correct types.
     */
    public Talon(String targetClass, String targetMethod, boolean isStatic, Object... args) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.isStatic = isStatic;
        this.args = args;
    }

    /**
     * Constructs a new Talon manager which does not have any target specified. This will skip invoking a class and
     * method in the new loader, allowing direct use of the classloader.
     */
    public Talon() {
        this.targetClass = null;
        this.targetMethod = null;
        this.isStatic = false;
        this.args = new Object[]{}; // This keeps the compiler warnings happy in start()
    }

    /**
     * Starts this Talon manager.
     * <p>
     * If a target was specified during construction, this will call the target method in the target class. If no target
     * was specified (such as with {@link #Talon()}) then nothing is invoked, but the classloader is set up.
     * <p>
     * This attempts to narrow down the possible set of methods on a target to a single method, but this process can
     * fail if there is either multiple possible invocations (such as with an overloaded method) or no matches at all
     * (the target method doesn't exist with the correct parameters).
     *
     * @return Any object returned from the invoked method, or null if no target is specified.
     * @throws ClassNotFoundException    if no matching class exists
     * @throws InvocationTargetException if the method could not be invoked
     * @throws IllegalAccessException    if the method was inaccessible to Talon
     * @throws InstantiationException    if the method is not static but an instance couldn't be constructed
     * @throws MethodCandidateException  if no candidates or multiple candidates were found for the method
     * @throws AlreadyStartedException   if Talon has already been started once.
     */
    public Object start() throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, InstantiationException, MethodCandidateException, AlreadyStartedException {
        if (started) {
            throw new AlreadyStartedException();
        }
        started = true;
        log.debug("Starting Talon.");
        classLoader = new TalonClassLoader(packageWhitelist.isEmpty() ? null : packageWhitelist, transformers, pendingTransformers);

        log.info("Talon started. Using {} whitelist, {} transformers registered.",
                packageWhitelist.isEmpty() ? "empty" : "size " + packageWhitelist.size(),
                transformers.size());
        if (!transformers.isEmpty()) {
            log.debug("Transformers in use: {}", transformers.stream().map(Transformer::toString).collect(Collectors.joining(", ")));
        }

        if (targetClass == null) {
            log.debug("No target specified. Nothing more to do.");
            return null; // No-args constructor used.
        }

        log.info("Starting target: {}#{} (static: {})", targetClass, targetMethod, isStatic);
        Class<?> clazz = classLoader.loadClass(targetClass);
        if (clazz == null) {
            throw new ClassNotFoundException(targetClass);
        }

        List<Method> methodCandidates = Arrays.stream(clazz.getMethods())
                .filter(it -> it.getName().equals(targetMethod))
                .filter(it -> Modifier.isStatic(it.getModifiers()) == isStatic)
                .filter(it -> it.getParameterCount() == args.length || it.isVarArgs())
                .filter(it -> {
                    if (it.isVarArgs()) {
                        return true;
                    }
                    Class<?>[] classes = it.getParameterTypes();
                    for (int i = 0; i < classes.length; i++) {
                        if (!classes[i].isAssignableFrom(args[i].getClass())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (methodCandidates.isEmpty()) {
            log.error("No candidates for {}#{} with {} args or varargs", targetClass, targetMethod, args.length);
            throw new MethodCandidateException("No viable candidate found");
        }
        if (methodCandidates.size() > 1) {
            log.error("Multiple candidates for {}#{} with {} args or varargs", targetClass, targetMethod, args.length);
            throw new MethodCandidateException("Multiple candidates found");
        }

        Method method = methodCandidates.get(0);
        Object target = null;
        if (!isStatic) {
            target = clazz.newInstance();
        }
        return method.invoke(target, args);
    }

    /**
     * Registers a transformer with this Talon manager.
     * <p>
     * All transformers registered in this way are guaranteed to always be executed in the same order they are
     * registered. Mixing this with {@link #addTransformer(ClassFileTransformer)} is not recommended and changes how
     * Talon orders transformers. For more, see the documentation for {@link #addTransformer(ClassFileTransformer)}.
     *
     * @param transformer The transformer to add.
     * @throws AlreadyStartedException if Talon has already been started once.
     */
    public void addTransformer(Transformer transformer) {
        if (started) {
            throw new AlreadyStartedException();
        }
        transformers.add(transformer);
    }

    /**
     * Registers a Java Instrumentation {@link ClassFileTransformer} with this Talon manager.
     * <p>
     * This is a compatibility shim for code outside your control. Prefer updating your transformers to the Talon-native
     * {@link Transformer} interface. All exceptions thrown by the Transformer will be caught, logged at DEBUG and then
     * ignored and safely converted to a no-op. The {@link java.security.ProtectionDomain} will always be null, as will
     * the {@link Class} <code>classBeingRedefined</code> parameter.
     * <p>
     * The ordering guarantees normally provided by Talon are different for {@link ClassFileTransformer} instances.
     * Transformers added in this way will only be executed in order relative to other {@link ClassFileTransformer}
     * instances, and strictly always after all normal {@link Transformer} instances have executed.
     * <p>
     * This is a one-shot operation - removing instances registered this way is not possible.
     * <p>
     * <b>DO NOT USE THIS IF AT ALL POSSIBLE!</b>
     *
     * @param transformer The {@link ClassFileTransformer} to be wrapped in a {@link Transformer}
     * @throws AlreadyStartedException if Talon has already been started once.
     */
    public void addTransformer(ClassFileTransformer transformer) {
        if (started) {
            throw new AlreadyStartedException();
        }
        pendingTransformers.add(loader -> new InstrumentationTransformer(transformer, loader));
    }

    /**
     * Unregisters a transformer with this Talon manager. This cannot remove {@link ClassFileTransformer} instances!
     *
     * @param transformer The transformer to remove.
     * @return True if a transformer was removed, false if no such transformer was found and removed.
     * @throws AlreadyStartedException if Talon has already been started once.
     */
    public boolean removeTransformer(Transformer transformer) {
        if (started) {
            throw new AlreadyStartedException();
        }
        return transformers.remove(transformer);
    }

    /**
     * Adds a package to the classloader whitelist for classes considered for transformation. The package should be
     * specified in the standard Java notation (e.g. <code>io.drakon.talon</code>) with an optional trailing '.'
     * <p>
     * If no packages are whitelisted, Talon will apply the transformers to all classes not on its default blacklist.
     * For information on the contents of the default blacklist, consult the readme. Whitelisting anything that is
     * loaded by the bootstrap classloader will have no effect - classes from the bootstrap loader will always be used
     * verbatim.
     *
     * @param pkg The package to add to the classloader transform whitelist.
     * @throws AlreadyStartedException thrown if the Talon instance has already been started.
     */
    public void addWhitelistedPackage(String pkg) throws AlreadyStartedException {
        if (started) {
            throw new AlreadyStartedException();
        }
        if (!pkg.endsWith(".")) {
            pkg += '.';
        }
        packageWhitelist.add(pkg);
    }

    /**
     * Removes a package from the classloader whitelist for classes considered for transformation. The package should be
     * specified in the standard Java notation (e.g. <code>io.drakon.talon</code>) with an optional trailing '.'
     *
     * @param pkg The package to remove from the classloader transform whitelist.
     * @throws AlreadyStartedException thrown if the Talon instance has already been started.
     */
    public void removeWhitelistedPackage(String pkg) throws AlreadyStartedException {
        if (started) {
            throw new AlreadyStartedException();
        }
        if (!pkg.endsWith(".")) {
            pkg += '.';
        }
        packageWhitelist.remove(pkg);
    }

    /**
     * Exception thrown when attempting to mutate manager state after Talon has been started.
     */
    public static class AlreadyStartedException extends RuntimeException {
        private AlreadyStartedException() {
            super("Talon instance already started; no modifications possible.");
        }
    }

    /**
     * Exception thrown when method candidate searching returns no candidates or multiple possible candidates.
     */
    public static class MethodCandidateException extends Exception {
        private MethodCandidateException(String reason) {
            super(reason + "; see log for details.");
        }
    }

}
