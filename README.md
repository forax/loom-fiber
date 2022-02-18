# loom-fiber
This repository contains both my experimentation of the OpenJDK project Loom prototype
and a presentation and examples showing how to use it.

There is a [presentation of the loom project](loom%20is%20looming.pdf)
wiht the [examples](src/main/java/fr/umlv/loom/example).

## How to build

Download the latest early access build of loom [http://jdk.java.net/loom](http://jdk.java.net/loom)
set the environment variable JAVA_HOME to point to that JDK and then use Maven.

```
  export JAVA_HOME=/path/to/jdk
  mvn package
```

## How to run the examples

On top of each example, there is the command line to run it.
- Loom is a preview version so `--enable-preview` is required,
- For `ScopeLocal` and `StructuredTaskScope`, these are not yet part of the official API
  and are declared in module/package `jdk.incubator.concurrent`so this module
  has to be added to the command line with `--add-modules jdk.incubator.concurrent`,
- If you want to play with the internals, the class `Continuation` is hidden thus
  `--add-exports java.base/jdk.internal.vm=ALL-UNNAMED` should be added to the command line.

## Loom actor framework

At some point of the history, this project was containing an actor system based on loom.
It has now its own repository [https://github.com/forax/loom-actor](https://github.com/forax/loom-actor).
