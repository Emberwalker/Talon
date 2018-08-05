package io.drakon.talon.test.examples;

import org.objectweb.asm.util.ASMifier;

public class WithBlacklistedDeps {

    public static ASMifier test() {
        return new ASMifier();
    }

}
