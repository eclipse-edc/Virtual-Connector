# Virtual-Connector
EDC core components and services for a virtualized control plane

## Quick start

To get the example up-and-running, there are two possibilities:

- using Docker:
  ```shell
  ./gradlew dockerize # on x86/64 platforms
  ./gradlew dockerize -Dplatform="linux/amd64" # on arm platforms, e.g. Apple M1
  ```
  Then there should be two new images in your image cache, `controlplane-memory:latest` and
  `controlplane-postgres:latest`

- using a native Java process
  ```shell
  ./gradlew build
  java -jar runtimes/[controlplane-memory|controlplane-postgres]/build/libs/[controlplane-memory|controlplane-postgres].jar
  ```

In both cases configuration must be supplied, either using Docker environment variables, or using Java
application properties.

## Directory structure

- `config`: contains the configuration file for the Checkstyle plugin
- `extensions`: this is where your extension modules should be implemented
- `gradle`: contains the Gradle Wrapper and the Version Catalog
- `runtimes`: contains executable modules for the controlplane and data plane

