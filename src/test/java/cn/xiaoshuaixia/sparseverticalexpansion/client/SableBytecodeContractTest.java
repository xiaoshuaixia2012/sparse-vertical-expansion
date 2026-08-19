package cn.xiaoshuaixia.sparseverticalexpansion.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bytecode-level contract check that the Sable compat layer's reflection targets and mixin injection
 * points exist in the exact Sable JAR. It verifies the {@code LevelAccelerator} block-sampling
 * chokepoint, the physics-pipeline registration API ({@code SubLevelContainer} -> {@code SubLevelPhysicsSystem}
 * -> {@code PhysicsPipeline}), and the lazy-registration mixin targets ({@code PhysicsChunkTicketManager#addTicket}
 * and the two {@code getSectionIndexFromSectionY} call sites in {@code update()}) without loading Sable.
 */
public final class SableBytecodeContractTest {
    private static final String ACCELERATOR = "dev/ryanhcode/sable/util/LevelAccelerator";
    private static final String CONTAINER = "dev/ryanhcode/sable/api/sublevel/SubLevelContainer";
    private static final String SERVER_CONTAINER = "dev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer";
    private static final String PHYSICS_SYSTEM = "dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem";
    private static final String PIPELINE = "dev/ryanhcode/sable/api/physics/PhysicsPipeline";
    private static final String TICKET_MANAGER = "dev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager";
    private static final String TICKET = "dev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicket";

    public static void main(String[] args) throws Exception {
        String jarPath = System.getProperty("sable.compile.jar");
        if (jarPath == null || jarPath.isBlank()) {
            throw new IllegalStateException("system property 'sable.compile.jar' must point at the Sable mod JAR");
        }
        Map<String, byte[]> classes = readClasses(jarPath);

        Sig accelerator = sig(classes, ACCELERATOR);
        accelerator.requireMethod("getBlockState",
                "(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/core/BlockPos;)"
                        + "Lnet/minecraft/world/level/block/state/BlockState;");

        Sig container = sig(classes, CONTAINER);
        container.requireMethod("getContainer",
                "(Lnet/minecraft/server/level/ServerLevel;)Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;");

        Sig serverContainer = sig(classes, SERVER_CONTAINER);
        serverContainer.requireMethod("physicsSystem", "()Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;");

        Sig physicsSystem = sig(classes, PHYSICS_SYSTEM);
        physicsSystem.requireMethod("getPipeline", "()Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;");
        physicsSystem.requireMethod("handleBlockChange",
                "(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;III"
                        + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V");

        Sig pipeline = sig(classes, PIPELINE);
        pipeline.requireMethod("handleChunkSectionAddition",
                "(Lnet/minecraft/world/level/chunk/LevelChunkSection;IIIZ)V");
        pipeline.requireMethod("handleChunkSectionRemoval", "(III)V");

        // Lazy-registration mixin targets.
        Sig ticketManager = sig(classes, TICKET_MANAGER);
        ticketManager.requireMethod("addTicket",
                "(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"
                        + "Lnet/minecraft/core/SectionPos;IIIIJ)"
                        + "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicket;");

        Sig ticket = sig(classes, TICKET);
        ticket.requireMethod("<init>", "(Lnet/minecraft/core/SectionPos;JLjava/util/Collection;)V");
        ticket.requireMethod("setLastInhabitedTick", "(J)V");

        // Lock the @Redirect ordinal assumption: update() must call getSectionIndexFromSectionY exactly twice.
        int sectionIndexCalls = countInvocations(
                classes,
                TICKET_MANAGER,
                "update",
                "(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;"
                        + "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V",
                "net/minecraft/server/level/ServerLevel",
                "getSectionIndexFromSectionY",
                "(I)I");
        if (sectionIndexCalls != 2) {
            throw new AssertionError("Expected exactly 2 getSectionIndexFromSectionY calls in update(), found " + sectionIndexCalls);
        }

        System.out.println("SableBytecodeContractTest OK");
    }

    private static int countInvocations(
            Map<String, byte[]> classes,
            String ownerInternalName,
            String methodName,
            String methodDescriptor,
            String targetOwner,
            String targetName,
            String targetDescriptor) {
        byte[] bytes = classes.get(ownerInternalName + ".class");
        if (bytes == null) {
            throw new AssertionError("Sable class not found in JAR: " + ownerInternalName);
        }
        final int[] count = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals(methodName) || !descriptor.equals(methodDescriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL
                                && owner.equals(targetOwner)
                                && name.equals(targetName)
                                && descriptor.equals(targetDescriptor)) {
                            count[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return count[0];
    }

    private static Map<String, byte[]> readClasses(String jarPath) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    classes.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return classes;
    }

    private static Sig sig(Map<String, byte[]> classes, String internalName) {
        byte[] bytes = classes.get(internalName + ".class");
        if (bytes == null) {
            throw new AssertionError("Sable class not found in JAR: " + internalName);
        }
        Sig sig = new Sig();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                sig.methods.add(name + descriptor);
                return null;
            }
        }, 0);
        return sig;
    }

    private static final class Sig {
        private final Set<String> methods = new HashSet<>();

        void requireMethod(String name, String descriptor) {
            if (!methods.contains(name + descriptor)) {
                throw new AssertionError("Sable method missing: " + name + descriptor);
            }
        }
    }
}
