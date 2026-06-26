# Sky Logistics

This branch keeps the supported Minecraft versions in one repository branch.

- `common` contains source and resource files shared by all supported versions.
- `versions/1.21.1` contains the NeoForge 1.21.1 project.
- `versions/1.20.1` contains the Forge 1.20.1 project.

Each version directory remains independently buildable with its own Gradle
wrapper and version-specific build files. Version projects add `common` as an
extra source/resource root, while API-specific files stay under `versions/*`.

## Build

Build both versions from the repository root:

```bash
./scripts/build_all_versions.sh
```

Build one version directly:

```bash
cd versions/1.21.1
./gradlew --no-daemon clean build

cd ../1.20.1
./gradlew --no-daemon clean build
```

For local Forge 1.20.1 builds on this machine, prefer the root script because
it supplies the cached Jade jar and offline Maven repository when available.
