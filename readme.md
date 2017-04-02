# Java watch build [![Maven Central](https://img.shields.io/maven-central/v/hr.hrg/java-watch-build.svg)](https://mvnrepository.com/artifact/hr.hrg/java-watch-build)

## Introduction
The main goal for this project is to create a utility for building projects 
that can also be left running to watch for changes and rebuild as soon as possible.
 
In the sea of options for building your projects, sometimes it is difficult to find one that suits you.
So, I wrote this specifically for myself. 

One of the first tools I wanted to have included was a SASS build/watch and after i found
[libsass-maven-plugin](https://github.com/warmuuh/libsass-maven-plugin) I started to create it.
The libsass-maven-plugin is an excellent choice if you need to compile SASS as part of your maven build, 
and since recently also has watch option.

If you use gradle then you most likely just need to run it in continuous mode,
check  thiis blog post [gradle continuous build](https://blog.gradle.org/introducing-continuous-build) for details.

Latest release is [0.2.0](../../releases/tag/v0.2.0) but you can build the current SNAPSHOT with maven and use that.

## Command line Usage

[Main.java](src/main/java/hr/hrg/watch/build/Main.java) is for direct command line usage.
To use it, just download latest ```java-watch-build-0.2.0-shaded.jar``` from [maven central](http://repo1.maven.org/maven2/hr/hrg/java-watch-build/) 
or [sonatype oss](https://oss.sonatype.org/content/repositories/releases/hr/hrg/java-watch-build/) 
or build the project with maven and use the shaded jar from target folder. (I tried gradle few times, and it just does not fit me)

```
> java -jar java-watch-build-0.2.0-shaded.jar build.yml 
```

## Shaded bundle (mini versus full)
Shaded jar includes all the dependencies to the jar is runnable standalone, this makes relatively large size jar, but is easy to use. Since version 0.3.0 The project builds 2 different shaded jars
 
  * full - (~16MB) all tasks
  * mini - (~5MB) excludes parts that are big but you might not need   
    * `@sass` completely 
    * `@jsbundles` works except JavaScript compiling  (set `outputJS: false`)

if you use mini version and define `@sass` task you will get the following error: 

`problem starting config object#0 Sass compiling task is not available due to missing dependency hr.hrg:java-watch-sass (download full shaded version to fix or remove the @sass task)`

also if you define `@jsbundles` task but do not change outputJS option to `false`

`problem starting config object#0 JsBundles compiling JavaScript is not available due to missing dependency com.google.javascript:closure-compiler (download full shaded version to fix or set outputJS: false in the configuration)`

you need to either remove that task from your build or download and use full version

## Use in java code

Add maven dependency or download from [maven central](http://repo1.maven.org/maven2/hr/hrg/java-watch-build/)
or download from [sonatype oss](https://oss.sonatype.org/content/repositories/releases/hr/hrg/java-watch-build/)

```
<dependency>
	<groupId>hr.hrg</groupId>
	<artifactId>java-watch-build</artifactId>
	<version>0.2.0</version>
</dependency>
```

## misc
If you are by any chance looking for a sass build/watch tool for maven check: .
I

## License

See the [LICENSE](LICENSE.md) file for license rights and limitations (MIT).
