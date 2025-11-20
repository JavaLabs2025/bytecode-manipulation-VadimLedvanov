package org.example;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.example.Abc.countAbc;

public class Example {

    public static void main(String[] args) throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();

        try (JarFile jf = new JarFile("src/main/resources/sample.jar")) {
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.getName().endsWith(".class")) {
                    try (InputStream in = jf.getInputStream(je)) {
                        ClassReader cr = new ClassReader(in);
                        ClassNode cn = new ClassNode();
                        cr.accept(cn, 0);
                        classes.put(cn.name, cn);
                    }
                }
            }
        }

        int classCount = 0;
        int maxDepth = 0;
        long sumDepth = 0;
        long sumFields = 0;
        long sumOverridden = 0;

        Abc totalAbc = new Abc(0,0,0);

        for (ClassNode cn : classes.values()) {
            boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;
            if (isInterface) continue;

            classCount++;

            int d = depthOf(cn.name, classes);
            maxDepth = Math.max(maxDepth, d);
            sumDepth += d;

            long fieldsHere = cn.fields.stream()
                    .filter(fn -> (fn.access & Opcodes.ACC_SYNTHETIC) == 0)
                    .count();
            sumFields += fieldsHere;

            long overriddenHere = cn.methods.stream()
                    .filter(m -> overridesSomewhere(cn, m, classes))
                    .count();
            sumOverridden += overriddenHere;

            for (MethodNode o : cn.methods) {
                totalAbc = totalAbc.add(countAbc(o));
            }
        }

        double avgDepth = classCount == 0 ? 0.0 : (double) sumDepth / classCount;
        double avgFields = classCount == 0 ? 0.0 : (double) sumFields / classCount;
        double avgOverrides = classCount == 0 ? 0.0 : (double) sumOverridden / classCount;

        System.out.printf("Classes: %d%n", classCount);
        System.out.printf("Inheritance: max=%d, avg=%.2f%n", maxDepth, avgDepth);
        System.out.printf("ABC: A=%d, B=%d, C=%d%n", totalAbc.a, totalAbc.b, totalAbc.c);
        System.out.printf("Avg overrides per class: %.2f%n", avgOverrides);
        System.out.printf("Avg fields per class: %.2f%n", avgFields);

        String json = String.format(
                java.util.Locale.US,
                """
                {
                  "classesAnalyzed": %d,
                  "inheritance": { "maxDepth": %d, "avgDepth": %.4f },
                  "abc": { "assignments": %d, "branches": %d, "conditions": %d },
                  "overrides": { "avgOverriddenMethodsPerClass": %.4f },
                  "fields": { "avgFieldsPerClass": %.4f }
                }
                """,
                classCount, maxDepth, avgDepth,
                totalAbc.a, totalAbc.b, totalAbc.c,
                avgOverrides, avgFields
        );

        java.nio.file.Files.writeString(
                java.nio.file.Path.of("src/main/resources/result.json"),
                json,
                java.nio.charset.StandardCharsets.UTF_8
        );

    }

    static int depthOf(String internalName, Map<String, ClassNode> classes) {
        int depth = 0;
        String cur = internalName;
        while (true) {
            ClassNode cn = classes.get(cur);
            String superName = (cn != null) ? cn.superName : "java/lang/Object";
            if (superName == null || "java/lang/Object".equals(superName)) {
                return depth;
            }
            depth++;

            ClassNode sup = classes.get(superName);
            if (sup == null) {
                return depth + 1;
            }
            cur = superName;
        }
    }

    static boolean overridesSomewhere(ClassNode cn, MethodNode m, Map<String, ClassNode> classes) {
        if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0) return false;
        if (m.name.equals("<init>") || m.name.equals("<clinit>")) return false;
        if ((m.access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) return false;

        if (declares(cn.superName, m.name, m.desc, classes)) return true;

        for (String itf : cn.interfaces) {
            if (declares(itf, m.name, m.desc, classes)) return true;
        }
        return false;
    }

    static boolean declares(String owner, String name, String desc, Map<String, ClassNode> classes) {
        if (owner == null) return false;
        if ("java/lang/Object".equals(owner)) {
            return declaresInObject(name, desc);
        }

        ClassNode n = classes.get(owner);
        if (n == null) {
            return false;
        }

        for (MethodNode o : n.methods) {
            MethodNode mm = o;
            if (mm.name.equals(name) && mm.desc.equals(desc)) return true;
        }

        if (declares(n.superName, name, desc, classes)) return true;
        for (String itf : n.interfaces) if (declares(itf, name, desc, classes)) return true;
        return false;
    }

    static boolean declaresInObject(String name, String desc) {
        return ("equals".equals(name) && "(Ljava/lang/Object;)Z".equals(desc))
                || ("hashCode".equals(name) && "()I".equals(desc))
                || ("toString".equals(name) && "()Ljava/lang/String;".equals(desc))
                || ("clone".equals(name) && "()Ljava/lang/Object;".equals(desc))
                || ("finalize".equals(name) && "()V".equals(desc));
    }

}
