Bayesian Network
===================

Authors:
* Daniel Beckwith ([dbeckwith](http://github.com/dbeckwith))
* Aditya Nivarthi ([SIZMW](http://github.com/sizmw))

## Purpose
The purpose of this project was to create a Bayesian network and test various sampling methods on it.

## Implementation
We implemented option B for this project, where the number of parents is not limited at two parents for a node.

## Building
Building this project requires [Maven 3](https://maven.apache.org/download.cgi) and [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). To build a standalone executable JAR file, run the following command from the project root directory:
```console
mvn package
```
This will generate a JAR file at `./target/resolution.jar`

## Execution
Running this program requires [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html). To run the JAR file, execute the following command:
```console
java -jar anivarthi-djbeckwith-bnet.jar
```
This will cause the program to show its usage and help message.
