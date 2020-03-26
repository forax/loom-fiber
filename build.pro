import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

pro.loglevel("verbose")

resolver.
  checkForUpdate(true).
  dependencies(
    // JUnit 5
    "org.junit.jupiter.api:5.6.1",
    "org.junit.jupiter.params:5.6.1",
    "org.junit.platform.commons:1.6.1",
    "org.apiguardian.api:1.1.0",
    "org.opentest4j:1.2.0",

    // JMH
    "org.openjdk.jmh=org.openjdk.jmh:jmh-core:1.23",
    "org.apache.commons.math3=org.apache.commons:commons-math3:3.3.2",
    "net.sf.jopt-simple=net.sf.jopt-simple:jopt-simple:4.6",
    "org.openjdk.jmh.generator=org.openjdk.jmh:jmh-generator-annprocess:1.23"
    );

compiler.
  sourceRelease(15).
  enablePreview(true).
  rawArguments(
    "--processor-module-path", "deps"   // enable JMH annotation processor
    )

packager.
  modules("fr.umlv.loom@1.0/fr.umlv.loom.Main")   

run(resolver, modulefixer, compiler, tester, packager /*, perfer */)

pro.arguments().forEach(plugin -> run(plugin))   // run command line defined plugins

/exit errorCode()
