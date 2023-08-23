# Mastodon-sciview Bridge (take2)
This is a reincarnation of [an earlier project `mastodon-sciview`](https://github.com/mastodon-sc/mastodon-sciview/) by [`xulman`](https://github.com/xulman) and [`RuoshanLan`](https://github.com/ruoshanlan).
It aims to display data from [Mastodon](https://github.com/mastodon-sc) also in [sciview (and scenery)](https://github.com/scenerygraphics/sciview).

The repository was started during the [scenery and sciview hackathon](https://imagesc.zulipchat.com/#narrow/stream/391996-Zzz.3A-.5B2023-06.5D-scenery.2Bsciview-hackathon-dresden)
in Dresden (Germany) in June 2023, but most of the code was contributed by [`xulman`](https://github.com/xulman) shortly afterward.

[example image here]

# How to compile
This project is both a gradle build system project (when switched to the `master` branch)
as well as maven build system project (when switched to the `maven` branch).

It is a gradle project because scenery and sciview are gradle projects and thus it was the most natural choice when developing or contributing to this project.
However, for deployment, `xulman` found it easier to use maven and it's "collect all deps" functionality to assemble a minimal functional collection of jar files
to run this.

When switching between the branches, your IDE may get very confused. It is perhaps better to keep this repo cloned twice, switched to the different branches.

## Gradle, development, `master` branch
Since this regime is intended for development of this project and potentially of adding relevant functions in the sciview, which shall
be immediately accessible in this project, the gradle settings of this project is instructed to look for local sciview.
Therefore, the following layout is expected:

```shell
ulman@localhost ~/devel/sciview_hack2
$ tree -L 2
.
├── mastodon-sciview-take2
│   ├── build
│   ├── build.gradle.kts
│   ├── gradle
│   ├── gradlew
│   ├── gradlew.bat
│   ├── hs_err_pid9979.log
│   ├── settings.gradle.kts
│   └── src
└── sciview
    ├── ACKNOWLEDGEMENTS.md
    ├── build
    ├── build.gradle.kts
    ├── buildSrc
    ├── CITATION.cff
    ├── config
    ├── gradle
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── LICENSE.txt
    ├── populate_fiji.sh
    ├── README.md
    ├── sciview_deploy.sh
    ├── sciview_deploy_unstable.sh
    ├── scripts
    ├── settings.gradle.kts
    ├── src
    └── upload-site-simple.sh
```

(Put simply, both this and sciview repositories are next to each other.)

## Maven, deployment, `maven` branch

This regime is intended for deployment of this project. The deployment procedure is the following:

- To make sure the official scenery at v0.9.0 is used, remove local `.m2/repository/graphics/scenery/scenery` folder
- To make sure your current local sciview is used, remove local `.m2/repository/iview/sc/sciview/` folder, and then
- Publish your local sciview to local maven `.m2` folders as follows

```shell
ulman@localhost ~/devel/sciview_hack2
$ cd sciview

ulman@localhost ~/devel/sciview_hack2/sciview
$ ./gradlew clean jar publishToMavenLocal
```

- Build and assemble a complete runnable setup of this repository as follows

```shell
ulman@localhost ~/devel/sciview_hack2
$ cd mastodon-sciview-take2

ulman@localhost ~/devel/sciview_hack2/mastodon-sciview-take2
$ mvn clean package dependency:copy-dependencies
```

- Start the project

```shell
ulman@localhost ~/devel/sciview_hack2/mastodon-sciview-take2
$ cd target

ulman@localhost ~/devel/sciview_hack2/mastodon-sciview-take2/target
$ java -cp "mastodon-sciview-bridge-0.9.0-SNAPSHOT.jar:dependency/*" org.mastodon.mamut.util.StartFiji
```

### Notes:

Make sure Java 11 or newer is used.

The versions must match. This repository is wanting sciview of a particular version, see `pom.xml`, the `dependency` section.
Either use sciview of that version, or update the `version` tag in the `pom.xml` to that of currently used sciview.
The version of the currently used sciview can be found in the `sciview/settings.gradle.kts`, the entry `gradle.rootProject`.

Known functional versions are:
- sciview: 0.2.0-beta-9-SNAPSHOT
- scenery: 0.9.0
