/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.scala3;

import com.google.common.collect.Iterables;
//import dotty.tools.dotc.core.Contexts;
import dotty.tools.dotc.interfaces.Diagnostic;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;

class GradleDottyReporter implements dotty.tools.dotc.interfaces.SimpleReporter {
    protected static final Logger LOGGER = Logging.getLogger(GradleDottyReporter.class);

    @Override
    public void report(Diagnostic diag) {
        LOGGER.info(diag.message());
    }
}

public class Scala3Compiler implements Compiler<ScalaJavaJointCompileSpec> {
    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
//        dotty.tools.dotc.Compiler compiler =  new dotty.tools.dotc.Compiler();
//        Contexts.Context context = new Contexts.FreshContext(new Contexts.ContextBase());
//        compiler.newRun(context);


        dotty.tools.dotc.Driver driver = new dotty.tools.dotc.Driver();
        dotty.tools.dotc.reporting.Reporter reporter = dotty.tools.dotc.reporting.Reporter.fromSimpleReporter(new GradleDottyReporter());
        Iterable<String> sourceIterator = Iterables.transform(spec.getSourceFiles(), File::getAbsolutePath);
        String[] toCompile = Iterables.toArray(sourceIterator, String.class);
        driver.process(toCompile, reporter, null);

        return WorkResults.didWork(true);
    }
}
