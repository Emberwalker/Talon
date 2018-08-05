package io.drakon.talon.test.transformers;

import io.drakon.talon.Transformer;
import lombok.Getter;

public class HasSeenAnyTransformer implements Transformer {

    @Getter
    private boolean hasSeenAny = false;

    @Getter
    private int seenCount = 0;

    @Override
    public byte[] transform(String className, String pkgName, byte[] classBytes) {
        hasSeenAny = true;
        seenCount += 1;
        return null;
    }

}
