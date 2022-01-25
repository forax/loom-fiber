# loom-fiber
Virtual thread examples using the OpenJDK project Loom prototype

This repository contains my experimentation of the loom API.
If you are looking for some examples you can take a look to the folder [example](src/main/java/fr/umlv/loom/example).

## How to build

Download the latest early access build of loom [http://jdk.java.net/loom](http://jdk.java.net/loom)
set the environment variable JAVA_HOME to point to that JDK and then use Maven.

```
  export JAVA_HOME=/path/to/jdk
  mvn package
```

### Gitpod support

[![](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/forax/loom-fiber)

## Loom actor framework

At some point of the history, this project was containing an actor system based on loom.
It has now its own repository [https://github.com/forax/loom-actor](https://github.com/forax/loom-actor).
