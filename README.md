protobuf-jackson
================

# Overview

This library provides support for JSON serialization for protobuf objects.

Unfortunately standard library provided by google requires both ``gson`` and ``guava`` libraries and this may be an
overkill for certain projects that already depend on jackson.

So, if your project already has a dependency on new jackson library and you need JSON support for protobuf,
this library is for you!

# How to use

Add to dependencies in your ``pom.xml``

```xml
<dependency>
  <groupId>com.truward.protobuf</groupId>
  <artifactId>protobuf-jackson</artifactId>
  <version>1.0.0</version>
</dependency>
```
