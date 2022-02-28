package com.android.singleclick;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

public class TimeClassVisitor extends ClassVisitor {

    private String mClassName;

    public TimeClassVisitor(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.mClassName = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if (((access & ACC_PUBLIC) != 0 && (access & ACC_STATIC) == 0) && //
                name.equals("onClick") && //
                desc.equals("(Landroid/view/View;)V") && mClassName.startsWith("com/wts/fqyf")) {
            return new TimeCheckVisitor(mv,1);
        }
        if (((access & ACC_PUBLIC) != 0 && (access & ACC_STATIC) == 0) && //
                name.equals("onClick") && //
                desc.equals(" com/wts/wtsbxw/ui/activities/DemoActivity.lambda$onCreate$0(Landroid/view/View;)") && mClassName.startsWith("com/wts/fqyf")) {
            return new TimeCheckVisitor(mv,1);
        }
        if (((access & ACC_PUBLIC) != 0 && (access & ACC_STATIC) == 0) && //
                name.equals("onItemClick") && //
                desc.equals("(Lcom/chad/library/adapter/base/BaseQuickAdapter;Landroid/view/View;I)V") && mClassName.startsWith("com/wts/fqyf")) {
            return new TimeCheckVisitor(mv,2);
        }
        return mv;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        return super.visitField(access, name, desc, signature, value);
    }
}
