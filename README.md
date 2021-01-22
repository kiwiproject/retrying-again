<!---
  Copyright 2012-2015 Ray Holder
  Modifications copyright 2017-2018 Robert Huffman
  Modifications copyright 2020 Kiwi Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  
     http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

[![Build](https://github.com/kiwiproject/retrying-again/workflows/build/badge.svg)](https://github.com/kiwiproject/retrying-again/actions?query=workflow%3Abuild)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_retrying-again&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_retrying-again)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_retrying-again&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_retrying-again)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/retrying-again/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/retrying-again)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/retrying-again)](https://search.maven.org/search?q=g:org.kiwiproject%20a:retrying-again)

## What is this?
**2020-12-23**: This library is a fork of [re-retrying](https://github.com/rhuffman/re-retrying), which is a fork of  [guava-retrying](https://github.com/rholder/guava-retrying). We forked it because our [kiwi](https://github.com/kiwiproject/kiwi) library has used it for years (circa 2015) and for now we intend to at least keep dependency versions up to date, and perhaps make some minimal changes and/or improvements as necessary. We make no guarantees whatsoever about anything. 

Another thing to note is that we forked re-retrying as re-retrying-fork and then _imported_ that as retrying-again, so that it is a disconnected fork. We did not want the reference to the original repository since it is no longer maintained, and therefore our fork will _never_ be pushed back to the original repository. Thus, while we maintain the history that this is a fork (of a fork), it is completely disconnected and is now a standalone (normal) repository.

---

The re-retrying module provides a general purpose method for retrying arbitrary Java code with specific stop, retry, and exception handling capabilities that are enhanced by Guava's predicate matching.

This is a fork of the [guava-retrying](https://github.com/rholder/guava-retrying) library by Ryan Holder (rholder), which is itself a fork of the [RetryerBuilder](http://code.google.com/p/guava-libraries/issues/detail?id=490) by Jean-Baptiste Nizet (JB). The guava-retrying project added a Gradle build for pushing it up to Maven Central, and exponential and Fibonacci backoff [WaitStrategies](http://rholder.github.io/guava-retrying/javadoc/2.0.0/com/github/rholder/retry/WaitStrategies.html) that might be useful for situations where more well-behaved service polling is preferred.

Why was this fork necessary? The primary reason was to make it compatible with projects using later versions of Guava. See [this project's Wiki](https://github.com/rhuffman/re-retrying/wiki#why-fork) for more details.

## Maven
```xml
    <dependency>
      <groupId>tech.huffman.re-retrying</groupId>
      <artifactId>re-retrying</artifactId>
      <version>3.0.0</version>
    </dependency>
```

## Gradle
```groovy
    compile "tech.huffman.re-retrying:re-retrying:3.0.0"
```

## Quickstart

Given a function that reads an integer:
```java

public int readAnInteger() throws IOException {
   ...
}
```

The following will retry if the result of the method is zero, if an `IOException` is thrown, or if any other `RuntimeException` is thrown from the `call()` method. It will stop after attempting to retry 3 times and throw a `RetryException` that contains information about the last failed attempt. If any other `Exception` pops out of the `call()` method it's wrapped and rethrown in an `ExecutionException`.

```java
    Retryer retryer = RetryerBuilder.newBuilder()
        .retryIfResult(Predicates.equalTo(0))
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withStopStrategy(StopStrategies.stopAfterAttempt(3))
        .build();
    try {
      retryer.call(this::readAnInteger);
    } catch (RetryException | ExecutionException e) {
      e.printStackTrace();
    }
```

## Exponential Backoff

Create a `Retryer` that retries forever, waiting after every failed retry in increasing exponential backoff intervals until at most 5 minutes. After 5 minutes, retry from then on in 5 minute intervals.

```java
Retryer retryer = RetryerBuilder.newBuilder()
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.exponentialWait(100, 5, TimeUnit.MINUTES))
        .withStopStrategy(StopStrategies.neverStop())
        .build();
```
You can read more about [exponential backoff](http://en.wikipedia.org/wiki/Exponential_backoff) and the historic role it played in the development of TCP/IP in [Congestion Avoidance and Control](http://ee.lbl.gov/papers/congavoid.pdf).

## Fibonacci Backoff

Create a `Retryer` that retries forever, waiting after every failed retry in increasing Fibonacci backoff intervals until at most 2 minutes. After 2 minutes, retry from then on in 2 minute intervals.

```java
Retryer retryer = RetryerBuilder.newBuilder()
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.fibonacciWait(100, 2, TimeUnit.MINUTES))
        .withStopStrategy(StopStrategies.neverStop())
        .build();
```

Similar to the `ExponentialWaitStrategy`, the `FibonacciWaitStrategy` follows a pattern of waiting an increasing amount of time after each failed attempt.

Instead of an exponential function it's (obviously) using a [Fibonacci sequence](https://en.wikipedia.org/wiki/Fibonacci_numbers) to calculate the wait time.

Depending on the problem at hand, the `FibonacciWaitStrategy` might perform better and lead to better throughput than the `ExponentialWaitStrategy` - at least according to [A Performance Comparison of Different Backoff Algorithms under Different Rebroadcast Probabilities for MANETs](http://www.comp.leeds.ac.uk/ukpew09/papers/12.pdf).

The implementation of `FibonacciWaitStrategy` is using an iterative version of the Fibonacci because a (naive) recursive version will lead to a [StackOverflowError](http://docs.oracle.com/javase/7/docs/api/java/lang/StackOverflowError.html) at a certain point (although very unlikely with useful parameters for retrying).

Inspiration for this implementation came from [Efficient retry/backoff mechanisms](https://paperairoplane.net/?p=640).

## Documentation
Javadoc can be found [here](http://rholder.github.io/guava-retrying/javadoc/2.0.0).

## Building from source
The re-retrying module uses a [Gradle](http://gradle.org)-based build system. In the instructions below, [`./gradlew`](http://vimeo.com/34436402) is invoked from the root of the source tree and serves as a cross-platform, self-contained bootstrap mechanism for the build. The only prerequisites are [Git](https://help.github.com/articles/set-up-git) and JDK 1.8+.

### check out sources
`git clone git://github.com/rhuffman/re-retrying.git`

### compile and test, build all jars
`./gradlew build`

### install all jars into your local Maven cache
`./gradlew install`

## License
The re-retrying module is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).

## Contributors
* Jean-Baptiste Nizet (JB)
* Jason Dunkelberger (dirkraft)
* Diwaker Gupta (diwakergupta)
* Jochen Schalanda (joschi)
* Shajahan Palayil (shasts)
* Olivier Grégoire (fror)
* Andrei Savu (andreisavu)
* (tchdp)
* (squalloser)
* Yaroslav Matveychuk (yaroslavm)
* Stephan Schroevers (Stephan202)
* Chad (voiceinsideyou)
* Kevin Conaway (kevinconaway)
* Alberto Scotto (alb-i986)
* Ryan Holder(rholder)
* Robert Huffman (rhuffman)

