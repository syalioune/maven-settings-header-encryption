Maven settings header encryption
---

[Apache maven](https://maven.apache.org/) allow [passwords encryption](https://maven.apache.org/guides/mini/guide-encryption.html) in its `settings.xml` file since version **2.1.0**.
This feature is however restricted to the following parts of the configuration file :

* `//settings/servers/server/password`
* `//settings/servers/server/passphrase`
* `//settings/proxies/proxy/password`

Some private maven registry rely on HTTP headers for authentication (e.g. [gitlab](https://docs.gitlab.com/ee/user/packages/maven_repository/#publish-to-the-gitlab-package-registry)) and thus cannot benefit from `settings.xml` encryption.

The purpose of this extension is to enhance maven default behavior by hooking into its setup phase to decrypt HTTP headers as well.

# Prerequisite

This maven extension requires maven **3.9.0+**.

# Extension behavior

The extension will decrypt any HTTP headers defined in `settings.xml` through either :

* Wagon standard general configuration

```xml
<settings>
    <servers>
        <server>
            <id>wagon</id>
            <configuration>
                <httpHeaders>
                    <property>
                        <name>my-header</name>
                        <value>{my-encrypted-value}</value>
                    </property>
                </httpHeaders>
            </configuration>
        </server>
    </servers>
</settings>
```

* Wagon `httpClient` _all_ configuration

```xml
<settings>
    <servers>
        <server>
            <id>httpClient</id>
            <configuration>
                <httpConfiguration>
                    <all>
                        <headers>
                            <property>
                                <name>all-header-name</name>
                                <value>{all-header-encrypted-value}</value>
                            </property>
                        </headers>
                    </all>
                </httpConfiguration>
            </configuration>
        </server>
    </servers>
</settings>
```

See [Advanced configuration to HTTP Wagon](https://maven.apache.org/guides/mini/guide-http-settings.html)

# Usage

1. Encrypt your HTTP headers values as described in [encryption guide](https://maven.apache.org/guides/mini/guide-encryption.html) and matching the description above
2. Define a `${projectRoot}/.mvn/extensions.xml` file containing

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
    <extension>
        <groupId>io.gitlab.syalioune</groupId>
        <artifactId>maven-settings-header-encryption</artifactId>
        <version>1.0.0</version>
    </extension>
</extensions>
```

3. You can observe log lines similar to the example below when enabling _debug_ logging with `mvn -X ...`

```
[DEBUG] Starting processing maven execution request
[DEBUG] Handling server with id <server_id>
[DEBUG] Trying to decrypt value for HTTP header <header_name>
[DEBUG] HTTP header <header_name> value decrypted successfully
[DEBUG] Finished processing maven execution request
```

ℹ️ You can refer to [Maven guide using extensions](https://maven.apache.org/guides/mini/guide-using-extensions.html) for alternative ways to load the extension. Please be advised that it needs to be loaded as a **Core extension**. ℹ️

# Build from source

```shell
mvn clean install
```

# Publish to gitlab maven registry

It requires a `${CI_JOB_TOKEN}` being generated during Gitlab CI job to work.

The maven profile `gitlab-ci` contains the required configuration for publication.

```shell
mvn clean deploy -P gitlab-ci
```

# Publish to maven central

It requires :

* A [GPG key](https://www.gnupg.org/) to work. See [Maven central requirements](https://central.sonatype.org/publish/publish-maven)
* The passphrase to the GPG key. See [settings.xml](./settings.xml) and `maven-central` server
* [Sonatype JIRA](https://issues.sonatype.org/) credentials. See [settings.xml](./settings.xml) and `ossrh` server
* Maven _master password_ for decrypting the project [settings.xml](./settings.xml)

```shell
mvn clean deploy -P maven-central
```


