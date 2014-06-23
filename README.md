docker-maven-plugin
===

A Maven plugin for building and pushing Docker images.

Why?
---
You can use this plugin to create a Docker image with artifacts built from your Maven project. For
example, the build process for a Java service can output a Docker image that runs the service.

Prerequisites
---
* A Docker host, which does the actual build. Note that the docker API is not always backwards
  compatible, so each version of the plugin must be used with specific versions of our docker fork.

  Plugin Version | Docker Version
  -------------- | --------------
  0.0.3-0.0.13   | 0.8.0
  0.0.2          | 0.7.6
  0.0.1          | 0.6.7

Setup
---

For most services you can specify all the parameters for building your image directly in the pom,
without needing a separate `Dockerfile`. The pom file lets you declare the base image, entry point,
cmd, and maintainer. If you need other commands such as `RUN` or `VOLUME`, then you will need to
create a `Dockerfile`.

The snippets below assume the presence of a `start` script.  You can get such a start script from
[here](https://ghe.spotify.net/helios/docker-directory-template).  If you decide you need to go the
`Dockerfile` route, you can also get a starter one there too.

### Specify build info in the POM

This configuration should work for most projects. Note that you need to replace `SERVICE_NAME` with
the actual value. The resources element lets you specify files which will be added to the image.
The `directory` element is the source directory, and `targetPath`is where in the image the file should
be added.

**NOTE:** Put the `docker-maven-plugin` last in the `<build><plugins>` node in your pom.xml or the jar
files that will be copied into your images will not be the shaded jars (assuming that is the kind of
jar you intend to build) which is probably not what you want.

    <properties>
      ...
      <imageName>registry:80/spotify/SERVICE_NAME</imageName>
      ....
    </properties>

    <build>
      <plugins>
        ...
        <plugin>
          <groupId>com.spotify</groupId>
          <artifactId>docker-maven-plugin</artifactId>
          <version>0.0.11</version>
          <configuration>
            <imageName>${imageName}</imageName>
            <useGitCommitId>true</useGitCommitId>
            <baseImage>registry:80/spotify/squeeze-base-java:04cd0f6</baseImage>
            <!-- You probably want to keep the /root/startup bits as it takes care of
                 starting supervision (for auxiliary things only, not the main service)
                 and syslog.  We recommend using a start script as mentioned earlier in
                 the README, but you may decide not to. -->
            <entryPoint>["/root/startup", "/usr/bin/python", "-u", "/start"]</entryPoint>
            <resources>
              <resource>
                <!-- make sure this path matches the path in your service start script -->
                <targetPath>/usr/share/java/spotify-SERVICE_NAME/</targetPath>
                <directory>${project.build.directory}</directory>
                <include>${project.artifactId}*.jar</include>
              </resource>
              <resource>
                <targetPath>/usr/bin/</targetPath>
                <directory>${basedir}/bin</directory>
              </resource>
              <resource>
                <targetPath>/templates/</targetPath>
                <directory>${basedir}</directory>
                <!-- make sure this name matches the actual name of your conf file -->
                <include>${project.artifactId}.conf</include>
              </resource>
              <resource> <!-- if you're not using a start script, delete this element -->
                <directory>docker</directory>
                <include>start</include>
              </resource>
            </resources>
          </configuration>
          <executions>
            <execution>
              <id>build</id>
              <goals>
                <goal>build</goal>
              </goals>
              <phase>package</phase>
            </execution>
          </executions>
        </plugin>
        ...
      </plugins>
    </build>

### Use a Dockerfile

To use a `Dockerfile`, you must specify the `dockerDirectory` element. If
specified, the `baseImage`, `maintainer`, `cmd` and `entryPoint` elements will
be ignored.  Anything in the specified `dockerDirectory` will be copied
into the `target` subtree.

    <properties>
      ...
      <imageName>registry:80/spotify/SERVICE_NAME</imageName>
      ....
    </properties>

    <build>
      <plugins>
        ...
        <plugin>
          <groupId>com.spotify</groupId>
          <artifactId>docker-maven-plugin</artifactId>
          <version>0.0.11</version>
          <configuration>
            <imageName>${imageName}</imageName>
            <useGitCommitId>true</useGitCommitId>
            <resources>
              <!-- don't need to specify things that get put into target/ automatically -->
              <resource>
                <targetPath>tmp/bin</targetPath>
                <directory>${basedir}/bin</directory>
              </resource>
              <resource>
                <targetPath>tmp/conf</targetPath>
                <directory>${basedir}</directory>
                <!-- make sure this name matches the actual name of your conf file -->
                <include>${project.artifactId}.conf</include>
              </resource>
            </resources>
            <!-- put Dockerfile and anything else you need to add in docker/ directory -->
            <dockerDirectory>docker</dockerDirectory>
          </configuration>
          <executions>
            <execution>
              <id>build</id>
              <goals>
                <goal>build</goal>
              </goals>
              <phase>package</phase>
            </execution>
          </executions>
        </plugin>
        ...
      </plugins>
    </build>

Note: Since the contents of the `dockerDirectory` are copied to `target`,
your paths in `ADD` commands in the `Dockerfile` can be relative to things
in `target/`.  One upshot is that you don't have to specify resources
for things that are already in `target`, like generated jar files and
the like.


Usage
---

The above configuration will run the following goals.

* build

  Build the image, and tag it as 'latest'. Resources will be copied either directly into the image
  or into the dockerDirectory if using one.

* tag

 Generate a new tag based on the most recent git commit ID, and apply it to the image. The
 resulting image name will be something like `registry:80/spotify/serviceName:df8e8e6`.

Both goals are bound to the package phase, so they will be executed during a normal run of maven.

    mvn package

To push the image and both tags to the registry specify the `pushImage` flag.

    mvn package -DpushImage

You can also run the goals directly.

    mvn docker:build

By default the plugin will try to connect to docker on the localhost, but it is possible to use an
instance of docker running on another host.

    mvn package -DdockerHost=<host>:4160

The standard DOCKER_HOST environment variable is also supported.

    DOCKER_HOST=<host>:4160 mvn package

For a complete list of configuration options, see the generated documentation by checking out the code and running:

    mvn site

The documentation will then be in the `target/site` directory.

