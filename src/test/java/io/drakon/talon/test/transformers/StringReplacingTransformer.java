package io.drakon.talon.test.transformers;

import static org.objectweb.asm.Opcodes.ASM6;

import io.drakon.talon.Transformer;
import lombok.AllArgsConstructor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

@AllArgsConstructor
public class StringReplacingTransformer implements Transformer {

    private final String target;
    private final String replacement;

    @Override
    public byte[] transform(String className, String pkgName, byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ReplacingClassVisitor(target, replacement, writer), ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

    private static class ReplacingClassVisitor extends ClassVisitor {

        private final String target;
        private final String replacement;

        private ReplacingClassVisitor(String target, String replacement, ClassVisitor cv) {
            super(ASM6, cv);
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new ReplacingMethodVisitor(target, replacement, super.visitMethod(access, name, descriptor, signature, exceptions));
        }

    }

    private static class ReplacingMethodVisitor extends MethodVisitor {

        private final String target;
        private final String replacement;

        private ReplacingMethodVisitor(String target, String replacement, MethodVisitor mv) {
            super(ASM6, mv);
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value.equals(target)) {
                super.visitLdcInsn(replacement);
            } else {
                super.visitLdcInsn(value);
            }
        }
    }

}
