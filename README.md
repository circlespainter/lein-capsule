# lein-capsule

A [Leiningen](https://github.com/technomancy/leiningen) plugin for
[Parallel Universe](http://www.paralleluniverse.co)'s
[Capsule](https://github.com/puniverse/capsule).

Please report any issues. Very first release, partially tested
(see [lein-capsule-test/project.clj](../master/lein-capsule-test/project.clj)),
so expect some bumpiness and spec format changes until major release 1.0.

## Features

Full reference: [lein-capsule-test/project.clj](../master/lein-capsule-test/project.clj)

- Common `:capsule` section supporting (hopefully) every capsule manifest entry
- Main namespace, dependencies and repositories are inherited from project's configuration but can be adjusted
  for capsule builds
- Support for building many capsules of different `:types`, currently `:fat`, `:thin`, `:fat-except-clojure`,
`:thin-except-clojure`, `:mixed`; the latter lets you specify  base type (fat or thin) and exceptions
(i.e. dependencies to download or embed respectively)
- Specific capsule builds can override any settings in the toplevel `:capsule` section
- Support for [capsule modes](https://github.com/puniverse/capsule#capsule-configuration-and-modes)
- Specific capsule modes can override any settings in the toplevel `:capsule` section (except `:types` and
`:application`)

## Usage

Use this for user-level plugins:

Put `[lein-capsule "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-capsule 0.1.0-SNAPSHOT`.

Use this for project-level plugins:

Put `[lein-capsule "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your project.clj.

The plugin is configured through the `:capsule` project section and currently supports no
command line arguments.

From project root:

    $ lein capsule

...Will build capsules in `target/capsules` (unless differently configured).

## (Some) TODOs

- More tests and a comprehensive testsuite
- Refactor configuration to make it smarter, shorter and more readable; especially remove repetitions
- Support ["Really Executable" Capsules](https://github.com/puniverse/capsule#really-executable-capsules)
- Reference docs, look into [Codox](https://github.com/weavejester/codox) and
[Marginalia](https://github.com/gdeer81/marginalia)
- More examples
- Various TODOs in code
- Uberjar-style dedicated profile?

## License

Copyright © 2014 Fabio Tudone

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.