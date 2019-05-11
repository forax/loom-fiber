# loom-fiber
Continuation & Fiber examples using the OpenJDK project Loom prototype

## How to build

First you need to get pro, the simplest solution is to use the pro_wrapper,
so grab a version of [jdk 11 or 12](http://jdk.java.net/) and type
```
  /path/to/jdk11/bin/java pro_wrapper.java
```

This should install a version of pro compatible with loom in a local directory named 'pro' and run the build

If you want to re-run the build, you can either re-run pro_wrapper or call directly pro like this
```
  ./pro/bin/pro
```

## What is a continuation and what is a fiber

A Continuation is a stack of function calls that can be stopped and store in the heap at some point (with yield) and restarted afterward (with run).
A continuation has no scheduler, so you have to write your own.

A Fiber is a continuation that interacts well with the Java API. Fibers are scheduled on a thread pool (java.util.concurrent.Executor)
so the execution of the code of a fiber can be carried by multiple worker threads (The go-routine of golang are fibers.). 
Blocking calls (IO, java.util.concurrent.lock, condition, sleep, etc) supports fibers so instead of blocking the fiber yield and
will be restarted when the data will be available.

## Examples using the project Loom

### Continuations

- Generators,
  an Iterator and a Stream using a continuation.
- EventContinuation,
  allows to send values back and forth between a code and a continuation.
- Server
  a small HTTP Server that create one continuation by request and uses a Selector to schedule the continuations. 

### Fibers

- Task,
  a simple async/await mechanism like in JavaScript, C# or Kotlin using a fiber. Note that unlike these languages, fibers are managed by the VM not by the compiler.

