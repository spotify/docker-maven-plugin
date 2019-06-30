# docker-maven-plugin
[![Build Status](https://travis-ci.com/spotify/docker-maven-plugin.svg?branch=master)](https://travis-ci.com/spotify/docker-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.spotify/docker-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/com.spotify/docker-maven-plugin/)
[![License](https://img.shields.io/github/license/spotify/dockerfile-maven.svg)](LICENSE)


A Maven plugin for building and pushing Docker images.

## Status: inactive

**We recommend you use [dockerfile-maven][] instead.**

### The future of docker-maven-plugin

This plugin was the initial Maven plugin used at Spotify for building Docker
images out of Java services. It was initially created in 2014 when we first
began experimenting with Docker. This plugin is capable of generating a
`Dockerfile` for you based on configuration in the pom.xml file for things like
the `FROM` image, resources to add with `ADD`/`COPY`, etc.

Over time at Spotify we have realized that the simplest way to build a Docker
image from a Java project is to have the developer write the `Dockerfile`.  The
behavior of this plugin around generating Dockerfiles, copying your project
directory to a "staging" directory to use as the Docker build context, etc.,
ultimately led to a lot of unnecessary confusion with our users that stemmed
from introducing extra abstractions and a need for configuration on top of what
Docker is providing.

This led to the creation of a second Maven plugin for building docker images,
[dockerfile-maven][], which we think offers a simpler mental model of working
with Docker from Maven, for all of the reasons outlined in dockerfile-maven's
README.


[dockerfile-maven]: https://github.com/spotify/dockerfile-maven
* [The future of docker-maven-plugin](#the-future-of-docker-maven-plugin)
* [Purpose](#purpose)
* [Setup](#setup)
  * [Specify build info in the POM](#specify-build-info-in-the-pom)
  * [Use a Dockerfile](#use-a-dockerfile)
* [Usage](#usage)
  * [Bind Docker commands to Maven phases](#bind-docker-commands-to-maven-phases)
  * [Using with Private Registries](#using-with-private-registries)
  * [Authentication](#authentication)
    * [Using encrypted passwords for authentication](#using-encrypted-passwords-for-authentication)
* [Testing](#testing)
* [Releasing](#releasing)
* [Known Issues](#known-issues)


## Purpose

You can use this plugin to create a Docker image with artifacts built from your Maven project. For
example, the build process for a Java service can output a Docker image that runs the service.

## Setup

You can specify the base image, entry point, cmd, maintainer and files you want to add to your
image directly in the pom, without needing a separate `Dockerfile`.
If you need `VOLUME` command(or any other not supported dockerfile command), then you will need
to create a `Dockerfile` and use the `dockerDirectory` element.

By default the plugin will try to connect to docker on localhost:2375. Set the DOCKER_HOST 
environment variable to connect elsewhere.

    DOCKER_HOST=tcp://<host>:2375

Other docker-standard environment variables are honored too such as TLS and certificates.

### Specify build info in the POM

This example creates a new image named `example`, copies the project's jar file into the image,
and sets an entrypoint which runs the jar. Change `VERSION GOES HERE` to the latest tagged version.

    <build>
      <plugins>
        ...
        <plugin>
          <groupId>com.spotify</groupId>
          <artifactId>docker-maven-plugin</artifactId>
          <version>VERSION GOES HERE</version>
          <configuration>
            <imageName>example</imageName>
            <baseImage>java</baseImage>
            <entryPoint>["java", "-jar", "/${project.build.finalName}.jar"]</entryPoint>
            <!-- copy the service's jar file from target into the root directory of the image --> 
            <resources>
               <resource>
                 <targetPath>/</targetPath>
                 <directory>${project.build.directory}</directory>
                 <include>${project.build.finalName}.jar</include>
               </resource>
            </resources>
          </configuration>
        </plugin>
        ...
      </plugins>
    </build>

### Use a Dockerfile

To use a `Dockerfile`, you must specify the `dockerDirectory` element. If specified, the 
`baseImage`, `maintainer`, `cmd` and `entryPoint` elements will be ignored. The contents of the
`dockerDirectory` will be copied into `${project.build.directory}/docker`. Use the `resources`
element to copy additional files, such as the service's jar file.

    <build>
      <plugins>
        ...
        <plugin>
          <groupId>com.spotify</groupId>
          <artifactId>docker-maven-plugin</artifactId>
          <version>VERSION GOES HERE</version>
          <configuration>
            <imageName>example</imageName>
            <dockerDirectory>docker</dockerDirectory>
            <resources>
               <resource>
                 <targetPath>/</targetPath>
                 <directory>${project.build.directory}</directory>
                 <include>${project.build.finalName}.jar</include>
               </resource>
            </resources>
          </configuration>
        </plugin>
        ...
      </plugins>
    </build>

## Usage

You can build an image with the above configurations by running this command.

    mvn clean package docker:build

To push the image you just built to the registry, specify the `pushImage` flag.

    mvn clean package docker:build -DpushImage

To push only specific tags of the image to the registry, specify the `pushImageTag` flag.

    mvn clean package docker:build -DpushImageTag

In order for this to succeed, at least one imageTag must be present in the config, multiple tags can be used.

    <build>
      <plugins>
        ...
        <plugin>
          <configuration>
            ...
            <imageTags>
               <imageTag>${project.version}</imageTag>
               <imageTag>latest</imageTag>
            </imageTags>
          </configuration>
        </plugin>
        ...
      </plugins>
    </build>

Optionally, you can force docker to overwrite your image tags on each new build:

    <build>
      <plugins>
        ...
        <plugin>
          <configuration>
            ...
            <!-- optionally overwrite tags every time image is built with docker:build -->
            <forceTags>true</forceTags>
            <imageTags>
               ...
            </imageTags>
          </configuration>
        </plugin>
        ...
      </plugins>
    </build>

Tags-to-be-pushed can also be specified directly on the command line with

    mvn ... docker:build -DpushImageTag -DdockerImageTags=latest,another-tag

### Bind Docker commands to Maven phases

You can also bind the build, tag & push goals to the Maven phases, so the container will be built, tagged and pushed 
when you run just `mvn deploy`. If you have a multi-module project where a sub-module builds an image, you
will need to do this binding so the image gets built when maven is run from the parent project. 

    <plugin>
      <groupId>com.spotify</groupId>
      <artifactId>docker-maven-plugin</artifactId>
      <version>VERSION GOES HERE</version>
      <executions>
        <execution>
          <id>build-image</id>
          <phase>package</phase>
          <goals>
            <goal>build</goal>
          </goals>
        </execution>
        <execution>
          <id>tag-image</id>
          <phase>package</phase>
          <goals>
            <goal>tag</goal>
          </goals>
          <configuration>
            <image>my-image:${project.version}</image>
            <newName>registry.example.com/my-image:${project.version}</newName>
          </configuration>
        </execution>
        <execution>
          <id>push-image</id>
          <phase>deploy</phase>
          <goals>
            <goal>push</goal>
          </goals>
          <configuration>
            <imageName>registry.example.com/my-image:${project.version}</imageName>
          </configuration>
        </execution>        
      </executions>
    </plugin>

You can skip Docker goals bound to Maven phases with:

* `-DskipDockerBuild` to skip image build
* `-DskipDockerTag` to skip image tag
* `-DskipDockerPush` to skip image push
* `-DskipDocker` to skip any Docker goals

To remove the image named `foobar` run the following command:

    mvn docker:removeImage -DimageName=foobar

For a complete list of configuration options run:
`mvn com.spotify:docker-maven-plugin:<version>:help -Ddetail=true`

### Using with Private Registries

To push an image to a private registry, Docker requires that the image tag
being pushed is prefixed with the hostname and port of the registry. For
example to push `my-image` to `registry.example.com`, the image needs to be
tagged as `registry.example.com/my-image`.

The simplest way to do this with docker-maven-plugin is to put the registry
name in the `<imageName>` field, for example

```xml
<plugin>
  <groupId>com.spotify</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <configuration>
    <imageName>registry.example.com/my-image</imageName>
    ...
```

Then when pushing the image with either `docker:build -DpushImage` or
`docker:push`, the docker daemon will push to `registry.example.com`.

Alternatively, if you wish to use a short name in `docker:build` you can use
`docker:tag -DpushImage` to tag the just-built image with the full registry
hostname and push it. It's important to use the `pushImage` flag as using
`docker:push` independently will attempt to push the original image.

For example:

```xml
<plugin>
  <groupId>com.spotify</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <configuration>
    <imageName>my-image</imageName>
    ...
  </configuration>
  <executions>
    <execution>
      <id>build-image</id>
      <phase>package</phase>
      <goals>
        <goal>build</goal>
      </goals>
    </execution>
    <execution>
      <id>tag-image</id>
      <phase>package</phase>
      <goals>
        <goal>tag</goal>
      </goals>
      <configuration>
        <image>my-image</image>
        <newName>registry.example.com/my-image</newName>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Authentication

Since version 1.0.0, the docker-maven-plugin will automatically use any
authentication present in the docker-cli configuration file at `~/.dockercfg`
or `~/.docker/config.json`, without the need to configure anything (in earlier
versions of the plugin this behavior had to be enabled with
`<useConfigFile>true</useConfigFile>`, but now it is always active).

Additionally the plugin will enable support for Google Container Registry if it
is able to successfully load [Google's "Application Default Credentials"][ADC].
The plugin will also load Google credentials from the file pointed to by the
environment variable `DOCKER_GOOGLE_CREDENTIALS` if it is defined. Since GCR
authentication requires retrieving short-lived access codes for the given
credentials, support for this registry is baked into the underlying
docker-client rather than having to first populate the docker config file
before running the plugin.

[ADC]: https://developers.google.com/identity/protocols/application-default-credentials

Lastly, authentication credentials can be explicitly configured in your pom.xml
and in your Maven installation's `settings.xml` file as part of the
`<servers></servers>` block.

    <servers>
      <server>
        <id>docker-hub</id>
        <username>foo</username>
        <password>secret-password</password>
        <configuration>
          <email>foo@foo.bar</email>
        </configuration>
      </server>
    </servers>

Now use the server id in your project `pom.xml`.

    <plugin>
      <plugin>
        <groupId>com.spotify</groupId>
        <artifactId>docker-maven-plugin</artifactId>
        <version>VERSION GOES HERE</version>
        <configuration>
          [...]
          <serverId>docker-hub</serverId>
          <registryUrl>https://index.docker.io/v1/</registryUrl>
        </configuration>
      </plugin>
    </plugins>

The plugin gives priority to any credentials in the docker-cli config file
before explicitly configured credentials.

#### Using encrypted passwords for authentication

Credentials can be encrypted using [Maven's built in encryption function.](https://maven.apache.org/guides/mini/guide-encryption.html)
Only passwords enclosed in curly braces will be considered as encrypted.

    <servers>
      <server>
        <id>docker-hub</id>
        <username>foo</username>
        <password>{gc4QPLrlgPwHZjAhPw8JPuGzaPitzuyjeBojwCz88j4=}</password>
      </server>
    </servers>

## Testing

Make sure Docker daemon is running and that you can do `docker ps`. Then run `mvn clean test`.

## Releasing

Commits to the master branch will trigger our continuous integration agent to build the jar and
release by uploading to Sonatype. If you are a project maintainer with the necessary credentials,
you can also build and release locally by running the below.

```sh
mvn release:clean
mvn release:prepare
mvn release:perform
```

## Known Issues

### Exception caught: system properties: docker has type STRING rather than OBJECT

Because the plugin uses Maven properties named like
`docker.build.defaultProfile`, if you declare any other Maven property with the
name `docker` you will get a rather strange-looking error from Maven:

```
[ERROR] Failed to execute goal com.spotify:docker-maven-plugin:0.0.21:build (default) on project <....>: 
Exception caught: system properties: docker has type STRING rather than OBJECT
```

To fix this, rename the `docker` property in your pom.xml.

### InternalServerErrorException: HTTP 500 Internal Server Error

Problem: when building the Docker image, Maven outputs an exception with a
stacktrace like:

> Caused by: com.spotify.docker.client.shaded.javax.ws.rs.InternalServerErrorException: HTTP 500 Internal Server Error

docker-maven-plugin communicates with your local Docker daemon using the HTTP
Remote API and any unexpected errors that the daemon encounters will be
reported as `500 Internal Server Error`.

Check the Docker daemon log (typically at `/var/log/docker.log` or
`/var/log/upstart/docker.log`) for more details.

#### Invalid repository name ... only [a-z0-9-\_.] are allowed

One common cause of `500 Internal Server Error` is attempting to build an image
with a repository name containing uppercase characters, such as if the
`<imageName>` in the plugin's configuration refers to `${project.version}` when
the Maven project version is ending in `SNAPSHOT`.

Consider putting the project version in an image tag (instead of repository
name) with the `<dockerImageTags>` configuration option instead.
