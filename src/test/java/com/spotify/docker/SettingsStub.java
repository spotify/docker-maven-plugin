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

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class SettingsStub extends Settings {

    public SettingsStub() {
        super();
        final Server server = new Server();
        server.setId("docker-hub");
        server.setUsername("dxia3");
        // plaintext value is: SxpxdUQA2mvX7oj
        server.setPassword("{gc4QPLrlgPwHZjAhPw8JPuGzaPitzuyjeBojwCz88j4=}");
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom email = new Xpp3Dom("email");
        email.setValue("dxia+3@spotify.com");
        configuration.addChild(email);
        server.setConfiguration(configuration);
        addServer(server);
    }
}
