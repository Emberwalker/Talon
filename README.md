# Talon

_Classloading with bytecode transformations without Java Agents_

## Motivation

Some tasks are tedious to do over and over again in application code. The motivating case that lead to this project was
propagating thread state across async calls. All async jobs started have to be wrapped in a library method, which can
occasionally be forgotten by tired developers. Talon provides a (potential) solution - use bytecode manipulation to
insert the calls automatically.

Other uses might be installing instrumentation code without the use of a Java agent, automatic addition of tracing logic
or anything else that can be achieved with bytecode inspection and modification at runtime.

## Usage

1. Create a new `main(String[])` entry class.
2. Create a `Talon` instance within the new `main` method, using the `Talon(String, String[])` constructor.
    - Pass the original `main` class full name as the first parameter (e.g. `io.drakon.example.Main`)
    - Pass your `String[]` argument as the second parameter
3. Add any `Transformer` instances as desired with `Talon#addTransformer(Transformer)`
4. Optional: Add whitelisted packages with `Talon#addWhitelistedPackage(String)`
5. Call `Talon#start()`
6. ???
7. Profit.

More advanced usage is possible (such as specifying the method to be called, and non-static invocations), consult the
Javadocs for more details. Talon cannot redefine classes which have already been loaded, which is why your application
entrypoint should be started directly by Talon, which ensures transformed classes are always used.

## Default Behaviour

By default, Talon will explicitly _not_ run transformations of the following packages:

- `java.*`
- `javax.*`
- `sun.*`
- `com.sun.*`
- `org.objectweb.asm.*`
- `com.google.common.*`
- `io.drakon.talon.*`

In addition, Talon checks to see if a class is part of the bootstrap classloader. If the bootstrap classloader returns a
class, Talon will always use it. This prevents breaking internal JDK components, such as SAX.

All of these packages, save for the bootstrap classloader exception, will still be loaded by Talon's classloader but
without transformers applied. This is intended as a sane default set of rules for applications that are not using
whitelists. This list can be overruled with explicit whitelist entries, but be **very** sure you know what you're doing
first.

Talon forces `java.*` to be passed to the system classloader for safety reasons. There are reports that some `java.*`
classes have undesirable behaviour when instantiated from another classloader. This decision may be revisited in future,
if more detailed tests can reveal anything more on the matter.

## Utilities

### Save Transformed Classes

Talon's classloader can optionally dump any classes it transforms to the filesystem for inspection. To enable this, add
`-Dtalon.saveClassesTo=/your/path/here` to your JVM flags, where the path can be either relative or absolute.

### Developing: ASMifier

The Gradle file for Talon includes `asm-util` in it's `testRuntime` scope. This allows easily adding ASMifier/Textifier
run tasks to your IDE for testing. For IDEA:
 
1. Add a new Application configuration
2. Set the module to the test source set
3. Set Main class to `org.objectweb.asm.util.ASMifier` or `org.objectweb.asm.util.Textifier` as desired
4. Set the path to your class file in program arguments
    - If you want to run it against a class in the `main` source set, use the class file path in `out/production`
    - ...and for `test` source set, use the class file path in `out/test`

## Building

This project uses Gradle for developing.

To build, run `./gradlew build` (macOS/Linux) or `.\gradlew.bat build` (Windows)
