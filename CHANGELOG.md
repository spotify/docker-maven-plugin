# Changelog

## 1.2.0 (released 2018-10-09)

* Support Java 9, 10, and 11.
* Fix an ExceptionInInitializerError when plugin is used on Java 11 ([401][])

  [401]: https://github.com/spotify/docker-maven-plugin/issues/401

## 1.0.0

### Revamped authentication support

Integrates [revamped support for authentication from
docker-client][RegistryAuthSupplier] into the plugin, making it possible for
the plugin to be more flexible in regards to authentication credentials used
when pushing/pulling/building images.

Previous versions of the plugin had the limitation of using the same
RegistryAuth header for all images, regardless of what registries they came
from.

In this version, the docker-maven-plugin will automatically use any
authentication present in the docker-cli configuration file at `~/.dockercfg`
or `~/.docker/config.json`.

Additionally the plugin will enable support for Google Container Registry if it
is able to successfully load [Google's "Application Default Credentials"][ADC].
The plugin will also load Google credentials from the file pointed to by the
environment variable `DOCKER_GOOGLE_CREDENTIALS` if it is defined. Since GCR
authentication requires retrieving short-lived access codes for the given
credentials, support for this registry is baked into the underlying
docker-client rather than having to first populate the docker config file
before running the plugin.

Lastly, authentication credentials can be explicitly configured in your pom.xml
and in your Maven installation's `settings.xml` file as part of the
`<servers></servers>` block.

[339](https://github.com/spotify/docker-maven-plugin/pull/339)

[RegistryAuthSupplier]: https://github.com/spotify/docker-client/blob/dba55b17d09d4a15aa9d26884b22b230d49fce64/docs/user_manual.md#authentication-to-private-registries
[ADC]: https://developers.google.com/identity/protocols/application-default-credentials
