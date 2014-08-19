docker-maven-plugin
===

A Maven plugin for building and pushing Docker images.

Why?
---
You can use this plugin to create a Docker image with artifacts built from your Maven project. For
example, the build process for a Java service can output a Docker image that runs the service.

Setup
---
You can specify the base image, entry point, cmd, maintainer and files you want to add to your 
image directly in the pom, without needing a separate `Dockerfile`. If you need other commands such 
as `RUN` or `VOLUME`, then you will need to create a `Dockerfile` and use the `dockerDirectory`
element.

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


Usage
---

You can build an image with the above configurations by running this command.

    mvn clean package docker:build

To push the image you just built to the registry, specify the `pushImage` flag.

    mvn clean package docker:build -DpushImage

By default the plugin will try to connect to docker on localhost:2375. Set the DOCKER_HOST 
environment variable to connect elsewhere. 

    DOCKER_HOST=tcp://<host>:2375

You can also bind the build goal to the package phase, so the container will be built when you run
just `mvn package`. 

    <plugin>
      <groupId>com.spotify</groupId>
      <artifactId>docker-maven-plugin</artifactId>
      <version>VERSION GOES HERE</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals>
            <goal>build</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

For a complete list of configuration options run `mvn spotify-docker:help -Ddetail=true`
