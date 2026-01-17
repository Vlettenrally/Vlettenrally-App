# Vlettenrally API

Quarkus-based REST API for the Vlettenrally application.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Mock Data (Development Mode)

In dev mode, the API automatically uses mock repositories with prefilled data. This allows frontend development without needing a database.

### Available Mock Data

| Entity | Count | Description |
|--------|-------|-------------|
| **Games** | 2 | 1 active rally, 1 planned summer edition |
| **Teams** | 4 | With rivalries, points, and participants |
| **Checkpoints** | 5 | Across Amsterdam with coordinates |
| **Achievements** | 10 | Checkpoint completions for teams |
| **Messages** | 9 | Various types (checkpoint, rival, achievement, rallydesk) |

### Key Mock IDs

Use these UUIDs for testing:

- **Main Game**: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
- **Team "De Watervlooien"**: `11111111-1111-1111-1111-111111111111` (main player team, 350 points)
- **Team "Windkracht Tien"**: `22222222-2222-2222-2222-222222222222` (rival team, 300 points)
- **Device ID**: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`

### Running with Mock Profile Explicitly

```shell script
./mvnw quarkus:dev -Dquarkus.profile=mock
```

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
