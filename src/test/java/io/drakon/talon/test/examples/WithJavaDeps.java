package io.drakon.talon.test.examples;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class WithJavaDeps {

    public static Set<String> test() {
        return Arrays.stream(new String[]{"1", "2"}).collect(Collectors.toSet());
    }

}
