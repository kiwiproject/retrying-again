[![Build](https://github.com/kiwiproject/retrying-again/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kiwiproject/retrying-again/actions/workflows/build.yml?query=branch%3Amain)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_retrying-again&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_retrying-again)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_retrying-again&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_retrying-again)
[![CodeQL](https://github.com/kiwiproject/retrying-again/actions/workflows/codeql.yml/badge.svg)](https://github.com/kiwiproject/retrying-again/actions/workflows/codeql.yml)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/retrying-again/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/retrying-again)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/retrying-again)](https://central.sonatype.com/artifact/org.kiwiproject/retrying-again/)

## Introduction
             
This small library provides a general purpose method for retrying arbitrary Java code with the ability to specify
various retry policies. For example, you can employ stop, wait, and exception handling strategies such as stopping
after a number of failed attempts, fixed wait or exponential backoff, and retry on specific results and/or exceptions.

## Background

This library is a fork of [re-retrying](https://github.com/rhuffman/re-retrying), which itself is a fork of
[guava-retrying](https://github.com/rholder/guava-retrying). We forked it because our [kiwi](https://github.com/kiwiproject/kiwi) 
library used the original guava-retrying library for years (circa 2015) and neither the original nor the re-retrying
fork are maintained.

For now, we intend to at least keep dependency versions up to date, and perhaps make some minimal changes and/or
improvements as necessary. We make no guarantees whatsoever about anything, however.

All other [kiwiproject](https://github.com/kiwiproject) projects are MIT-licensed. However, because the original
guava-retrying uses the Apache 2.0 license, and so does the re-retrying fork, this also uses the Apache 2.0 license.

Another thing to note is that we forked re-retrying as re-retrying-fork and then _imported_ that as retrying-again, so
that it is a disconnected fork. We did not want the reference to the original repository since it is no longer 
maintained, and therefore our fork will _never_ be pushed back to the original repository. Thus, while we maintain the
history that this is a fork (of a fork), it is completely disconnected and is now a standalone (normal) repository.


## Maven
```xml
<dependency>
    <groupId>org.kiwiproject</groupId>
    <artifactId>retrying-again</artifactId>
    <version>[current-version]</version>
</dependency>
```

## Gradle
```groovy
compile group: 'org.kiwiproject', name: 'retrying-again', version: '[current-version]'
```

## Quickstart

Given a function that reads an integer:
```java

public int readAnInteger() throws IOException {
   ...
}
```

The following creates a `Retryer` that will retry if the result of the method is zero, if an `IOException` is 
thrown, or if any other `RuntimeException` is thrown from the `call()` method. It will stop after three unsuccessful 
attempts and throw a `RetryException` that contains information about the last failed attempt. If an `Exception`
is thrown by the `call()` method, it can be retrieved from the `RetryException`.

```java
var retryer = RetryerBuilder.newBuilder()
        .retryIfResult(integer -> Objects.equals(integer, 0))
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withStopStrategy(StopStrategies.stopAfterAttempt(3))
        .build();

try {
    var result = retryer.call(this::readAnInteger);
    
    // do something with result...

} catch (RetryException retryException) {
    // handle the RetryException...
        
} catch (InterruptedException interruptedException) {
    Thread.currentThread().interrupt();

    // handle the InterruptedException...
}
```

If a retryer completes exceptionally with a `RetryException`, you can get the number of failed attempts
as well as the last failed attempt:

```java
var numFailedAttempts = retryException.getNumberOfFailedAttempts();

Attempt<?> lastFailedAttempt = retryException.getLastFailedAttempt();
```

The `Attempt` class provides information about an attempt: the attempt number, whether it has a result or an exception,
the result or exception, and the time since the first attempt was made by a `Retryer`.

## Exponential Backoff

Create a `Retryer` that retries forever, waiting after every failed retry in increasing exponential backoff 
intervals until at most 5 minutes. After 5 minutes, retry from then on in 5-minute intervals.

```java
var retryer = RetryerBuilder.newBuilder()
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.exponentialWait(100, 5, TimeUnit.MINUTES))
        .withStopStrategy(StopStrategies.neverStop())
        .build();
```

You can read more about [exponential backoff](http://en.wikipedia.org/wiki/Exponential_backoff) and the historic
role it played in the development of TCP/IP in [Congestion Avoidance and Control](http://ee.lbl.gov/papers/congavoid.pdf).

## Fibonacci Backoff

Create a `Retryer` that retries forever, waiting after every failed retry in increasing Fibonacci backoff
intervals until at most 2 minutes. After 2 minutes, retry from then on in 2-minute intervals.

```java
var retryer = RetryerBuilder.newBuilder()
        .retryIfExceptionOfType(IOException.class)
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.fibonacciWait(100, 2, TimeUnit.MINUTES))
        .withStopStrategy(StopStrategies.neverStop())
        .build();
```

Similar to the `ExponentialWaitStrategy`, the `FibonacciWaitStrategy` follows a pattern of waiting an increasing
amount of time after each failed attempt.

Instead of an exponential function, it's (obviously) using a [Fibonacci sequence](https://en.wikipedia.org/wiki/Fibonacci_numbers)
to calculate the wait time.

Depending on the problem at hand, the `FibonacciWaitStrategy` might perform better and lead to better throughput
than the `ExponentialWaitStrategy` - at least, according to
[A Performance Comparison of Different Backoff Algorithms under Different Rebroadcast Probabilities for MANETs](https://www.researchgate.net/publication/255672213_A_Performance_Comparison_of_Different_Backoff_Algorithms_under_Different_Rebroadcast_Probabilities_for_MANET%27s).

The implementation of `FibonacciWaitStrategy` is using an iterative version of the Fibonacci because a (naive) recursive
version will lead to a `StackOverflowError` at a certain point (although very unlikely with useful parameters for retrying).

Inspiration for this implementation came from [Efficient retry/backoff mechanisms](https://dzone.com/articles/efficient-retrybackoff).

## License
The retrying-again library is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).

