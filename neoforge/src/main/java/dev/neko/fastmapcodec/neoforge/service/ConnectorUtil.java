package dev.neko.fastmapcodec.neoforge.service;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

/**
 * Portions adapted from Sinytra/Connector (MIT License):
 * https://github.com/Sinytra/Connector/blob/dev/1.21.x/src/main/java/org/sinytra/connector/util/ConnectorUtil.java
 */
public final class ConnectorUtil {
    public static final Unsafe UNSAFE = uncheck(() -> {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    });

    public static final MethodHandles.Lookup TRUSTED_LOOKUP = uncheck(() -> {
        Field hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        return (MethodHandles.Lookup) ConnectorUtil.UNSAFE.getObject(
            ConnectorUtil.UNSAFE.staticFieldBase(hackfield),
            ConnectorUtil.UNSAFE.staticFieldOffset(hackfield)
        );
    });

    private ConnectorUtil() {}
}
