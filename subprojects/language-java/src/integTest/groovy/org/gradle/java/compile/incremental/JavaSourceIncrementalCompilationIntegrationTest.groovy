/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import java.nio.file.Files
import java.nio.file.Paths

class JavaSourceIncrementalCompilationIntegrationTest extends BaseJavaSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when module info changes"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module foo {
                requires java.logging;
            }
        """

        succeeds language.compileTaskName

        when:
        moduleInfo.text = """
            module foo {
            }
        """

        then:
        fails language.compileTaskName
        result.assertHasErrorOutput("package java.util.logging is not visible")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when module info is added"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)

        succeeds language.compileTaskName

        when:
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module foo {
            }
        """

        then:
        fails language.compileTaskName
        result.assertHasErrorOutput("package java.util.logging is not visible")
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Issue("https://github.com/gradle/gradle/issues/9202")
    def "source mapping file works with symlinks"() {
        given:
        buildFile << """
            sourceSets {
                main {
                    ${languageName} {
                        srcDirs = ['src/main/${languageName}/build', 'src/main/${languageName}/linkparent']
                    }
                }
            }
        """
        file("other/foo/a/MyClass.${languageName}") << """package foo.a;
            public class MyClass {
                public void foo() { }
            }
        """
        file("src/main/${languageName}/build/foo/b/Other.${languageName}") << """package foo.b;
            import foo.a.MyClass;

            public class Other {
                public void hello(MyClass my) { my.foo(); }
            }
        """
        Files.createSymbolicLink(Paths.get(file("src/main/${languageName}/linkparent").toURI()), Paths.get(file("other").toURI()))
        outputs.snapshot { run language.compileTaskName }

        when:
        file("other/foo/a/MyClass.${languageName}").text = """package foo.a;
            public class MyClass {
                public void foo() { }
                public void bar() { }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyClass', 'Other')
    }
}
