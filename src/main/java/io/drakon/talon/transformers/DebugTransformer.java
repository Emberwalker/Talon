package io.drakon.talon.transformers;

import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.ASM6;

import io.drakon.talon.Transformer;
import lombok.extern.slf4j.Slf4j;
import org.apiguardian.api.API;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * Sample {@link Transformer} which simply logs every class it sees, along with all the methods in those classes.
 */
@Slf4j
@API(status = API.Status.EXPERIMENTAL)
public class DebugTransformer implements Transformer {

    @Override
    public byte[] transform(String className, String pkgName, byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new LoggingClassVisitor(ASM6), ClassReader.SKIP_FRAMES);
        return null;
    }

    private static class LoggingClassVisitor extends ClassVisitor {
        private LoggingClassVisitor(int api) {
            super(api);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            log.debug("Class: {} {}<{}> extends {} implements {}", Modifier.toString(access), name.replace('/', '.'),
                    signature == null ? "" : signature, superName, interfaces);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            log.debug("Method: {} <{}>{}{} throws {}", Modifier.toString(access), signature == null ? "" : signature, name, descriptor, exceptions);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

}
