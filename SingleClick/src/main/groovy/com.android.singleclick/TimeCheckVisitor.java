package com.android.singleclick;

import com.android.singleclick.utils.TimeCheck;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

public class TimeCheckVisitor extends MethodVisitor  {

    private int position;

    private boolean inject = true;

    public TimeCheckVisitor(MethodVisitor mv,int position) {
        super(Opcodes.ASM5, mv);
        this.position = position;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (Type.getDescriptor(TimeCheck.class).equals(desc)) {
            inject = false;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override public void visitCode() {
        super.visitCode();
        if (inject) {
            mv.visitVarInsn(Opcodes.ALOAD, position);
            mv.visitMethodInsn(INVOKESTATIC, "com/android/singleclick/TimeCheckUtils",
                    "checkTime", "(Landroid/view/View;)Z", false);
            Label label = new Label();
            mv.visitJumpInsn(IFNE, label);
            mv.visitInsn(RETURN);
            mv.visitLabel(label);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
    }
}
