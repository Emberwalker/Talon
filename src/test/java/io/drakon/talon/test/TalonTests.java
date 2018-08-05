package io.drakon.talon.test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.drakon.talon.Talon;
import io.drakon.talon.test.transformers.HasSeenAnyTransformer;
import io.drakon.talon.test.transformers.StringReplacingTransformer;
import io.drakon.talon.transformers.DebugTransformer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class TalonTests {

    private static final String WHITELIST_DIR = "io.drakon.talon.test.examples";

    @Test
    void testSmoke() {
        assertThatCode(Talon::new).doesNotThrowAnyException();
    }

    @Test
    void testOnlyAllowsSingleStart() throws Exception {
        Talon talon = new Talon();
        talon.start();
        assertThatCode(talon::start).isInstanceOf(Talon.AlreadyStartedException.class);
    }

    @Test
    void testConstructWithHasSeenTransformer() {
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", new String[]{});
        talon.addWhitelistedPackage(WHITELIST_DIR);
        HasSeenAnyTransformer transformer = new HasSeenAnyTransformer();
        talon.addTransformer(transformer);
        assertThatCode(talon::start).doesNotThrowAnyException();
        assertThat(transformer.isHasSeenAny()).isTrue();
    }

    @Test
    void testConstructWithDebugTransformer() {
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", new String[]{});
        talon.addWhitelistedPackage(WHITELIST_DIR);
        talon.addTransformer(new DebugTransformer());
        assertThatCode(talon::start).doesNotThrowAnyException();
    }

    @Test
    void testInvokeStaticNoArgs() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", "testStaticNoArgs", true);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        assertThat(talon.start()).isEqualTo("hello");
    }

    @Test
    void testInvokeStaticArg() throws Exception {
        Object obj = new Object();
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", "testStaticArgs", true, obj);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        assertThat(talon.start()).isEqualTo(obj);
    }

    @Test
    void testInvokeNonstaticNoArgs() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", "testNoArgs", false);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        assertThat(talon.start()).isEqualTo("hello");
    }

    @Test
    void testInvokeNonstaticArg() throws Exception {
        Object obj = new Object();
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", "testArgs", false, obj);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        assertThat(talon.start()).isEqualTo(obj);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWithJavaDeps() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.WithJavaDeps", "test", true);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        HasSeenAnyTransformer transformer = new HasSeenAnyTransformer();
        talon.addTransformer(transformer);
        assertThat((Set<String>)talon.start()).containsExactlyInAnyOrder("1", "2");
        assertThat(transformer.getSeenCount()).isEqualTo(1);
    }

    @Test
    void testWithBlacklistedDeps() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.WithBlacklistedDeps", "test", true);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        HasSeenAnyTransformer transformer = new HasSeenAnyTransformer();
        talon.addTransformer(transformer);
        assertThat(talon.start()).isNotNull();
        assertThat(transformer.getSeenCount()).isEqualTo(1);
    }

    @Test
    void testInvokeNonstaticWithTransform() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.Main", "testNoArgs", false);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        talon.addTransformer(new StringReplacingTransformer("hello", "pass"));
        assertThat(talon.start()).isEqualTo("pass");
    }

    @Test
    void testInvokeWithWhitelistedDepsTransform() throws Exception {
        Talon talon = new Talon("io.drakon.talon.test.examples.WithWhitelistedDeps", "test", true);
        talon.addWhitelistedPackage(WHITELIST_DIR);
        talon.addTransformer(new StringReplacingTransformer("hello", "pass"));
        assertThat(talon.start()).isEqualTo("pass");
    }

}
