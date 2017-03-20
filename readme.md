Java watch build Java watcher [![Maven Central](https://img.shields.io/maven-central/v/hr.hrg/java-watch-build.svg)](https://mvnrepository.com/artifact/hr.hrg/java-watch-build)

#Introduction
Command line utility for building projects

Latest release is [0.2.0](../../releases/tag/v0.2.0) but you can build the current SNAPSHOT with maven and use that.

# Command line Usage

[Main.java](src/main/java/hr/hrg/watch/build/Main.java) is for direct command line usage.
To use it, just download latest ```java-watch-build-0.2.0-shaded.jar``` from [maven central](http://repo1.maven.org/maven2/hr/hrg/java-watch-build/) 
or [sonatype oss](https://oss.sonatype.org/content/repositories/releases/hr/hrg/java-watch-build/) 
or build the project with maven and use the shaded jar from target folder.

```
> java -jar java-watch-build-0.2.0-shaded.jar build.yml 
```

# Use in java code

Add maven dependency or download from [maven central](http://repo1.maven.org/maven2/hr/hrg/java-watch-build/)
or download from [sonatype oss](https://oss.sonatype.org/content/repositories/releases/hr/hrg/java-watch-build/)

```
<dependency>
	<groupId>hr.hrg</groupId>
	<artifactId>java-watch-build</artifactId>
	<version>0.2.0</version>
</dependency>
```

## License

See the [LICENSE](LICENSE.md) file for license rights and limitations (MIT).
