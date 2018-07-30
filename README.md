# loom-fiber
fiber examples using the OpenJDK project Loom prototype

## How to build

First you need to get pro, the simplest solution is to use the pro_wrapper,
so grab a version of [http://jdk.java.net/](jdk 11 or 12) and type
```
  /path/to/jdk/bin/java pro_wrapper
```

It should install pro in the local directory pro and run the build

If you want to re-run the build, you can either re-run pro_wrapper or call directly pro like this
```
  ./pro/bin/pro
```

## Examples using the project Loom

### Continuations

- Generators
  Implementation of an Iterator and a Stream using a continuation
- EventContinuation
  allow to send values back and forth between a code and a continuation


### Fibers

- Task
  A simple async/await mechanism like in JavaScript, C# or Kotlin using a fiber
- YetAnotherExecutors
  Implements yet another executor using fibers
