package io.drakon.talon.test.examples;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("unused")
public class Main {

    public static void main(String[] args) {
        // Pass.
    }

    public Main() {
        log.info("Main constructed");
    }

    public static String testStaticNoArgs() {
        return "hello";
    }

    public static Object testStaticArgs(Object arg) {
        return arg;
    }

    public String testNoArgs() {
        return "hello";
    }

    public Object testArgs(Object arg) {
        return arg;
    }

}
