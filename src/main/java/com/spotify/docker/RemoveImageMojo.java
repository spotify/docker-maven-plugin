/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.docker;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.DockerRequestException;
import com.spotify.docker.client.shaded.javax.ws.rs.NotFoundException;
import com.spotify.docker.client.ImageNotFoundException;

/**
 * Removes a docker image.
 */
@Mojo(name = "removeImage")
public class RemoveImageMojo extends AbstractDockerMojo {

    /** Name of image to remove. */
    @Parameter(property = "imageName", required = true)
    private String imageName;

    protected void execute(DockerClient docker)
            throws MojoExecutionException, DockerException, IOException, InterruptedException {

        getLog().info("Removing -f " + imageName);

        try {
            // force the image to be removed but don't remove untagged parents 
            docker.removeImage(imageName, true, false);
        } catch (Exception e) {
            // ignoring 404 errors only
            for (Throwable rootCause = e;
                           rootCause != null && rootCause.getCause() != rootCause;
                           rootCause = rootCause.getCause()) {
               if (rootCause instanceof ImageNotFoundException || 
                   rootCause instanceof NotFoundException) {
                       getLog().warn("Image " + imageName + 
                                     " does not exist and cannot be deleted - ignoring");
                       return;
                 }
            }
            throw e;
        }
    }
}
