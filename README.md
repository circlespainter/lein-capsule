# *lein-capsule*<br/>A [Leiningen](https://github.com/technomancy/leiningen) plugin for [Parallel Universe](http://www.paralleluniverse.co)'s [Capsule](https://github.com/puniverse/capsule).
[![Build Status](http://img.shields.io/travis/circlespainter/lein-capsule.svg?style=flat)](https://travis-ci.org/circlespainter/lein-capsule) [![Dependency Status](https://www.versioneye.com/user/projects/54379f97b2a9c5aed60000d3/badge.svg?style=flat)](https://www.versioneye.com/user/projects/54379f97b2a9c5aed60000d3) [![Version](http://img.shields.io/badge/version-0.1.0--SNAPSHOT-red.svg?style=flat)](https://github.com/circlespainter/lein-capsule) [![License](http://img.shields.io/badge/license-EPL-blue.svg?style=flat)](https://www.eclipse.org/legal/epl-v10.html) [![License](http://img.shields.io/badge/license-LGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/lgpl.html)

A [Leiningen](https://github.com/technomancy/leiningen) plugin for
[Parallel Universe](http://www.paralleluniverse.co)'s
[Capsule](https://github.com/puniverse/capsule).

Please report any issues. Very first release, partially tested
(see [lein-capsule-test/project.clj](../master/lein-capsule-test/project.clj)),
so expect some bumpiness and spec format changes until major release 1.0.

## Getting started

Use this for user-level plugins:

Put `[lein-capsule "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-capsule 0.1.0-SNAPSHOT`.

Use this for project-level plugins:

Put `[lein-capsule "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your project.clj.

The plugin is configured through the `:capsule` project section and currently supports no
command line arguments. Minimal `project.clj` sample:

```clojure
(defproject lein-capsule-test "0.1.0-SNAPSHOT"
 :plugins [
   [lein-capsule "0.1.0-SNAPSHOT"] ]

 :dependencies [
   [org.clojure/clojure "1.6.0"] ]

 :jvm-opts ["-client"]

 ;;; Needed for executable jars as well as for capsules, if not present an artifact executable will be assumed
 :main lein-capsule-test.core
  
 ;;; Leinengen 3 will remove implicit AOT-compilation of :main
 :aot [lein-capsule-test.core]
  
 ;;; Capsule plugin configuration section, optional
 :capsule {
   :types {
     ;; Optional, can override anything, will trigger building a thin capsule
     :thin {} } } )
```

Then from project root:

    $ lein capsule

...Will build capsules in `target/capsules` (unless differently configured).

## Features

Full reference: [lein-capsule-test/project.clj](../master/lein-capsule-test/project.clj)

- Common `:capsule` section supporting (hopefully) every capsule manifest entry. Sensible defaults are based
on project-level configuration and Capsule defaults themselves but they can be adjusted and overridden.
- Main namespace, dependencies and repositories are inherited from project's configuration but can be adjusted
  for capsule builds
- Support for building many capsules of different `:types`, currently `:fat`, `:thin`, `:fat-except-clojure`,
`:thin-except-clojure`, `:mixed`; the latter lets you specify  base type (fat or thin) and exceptions
(i.e. dependencies to download or embed respectively)
- Specific capsule builds can override any settings in the toplevel `:capsule` section
- Support for [capsule modes](https://github.com/puniverse/capsule#capsule-configuration-and-modes)
- Specific capsule modes can override any settings in the toplevel `:capsule` section (except `:types` and
`:application`)
- It will automatically use minimal Capsule embedding (i.e. only the `Capsule` class) whenever possible
(i.e. no artifacts to get)

## (Some) TODOs

- More manual tests but most importantly a comprehensive (eventually) testsuite
- Possibly refactor configuration to make it smarter, shorter and more readable; especially remove repetitions
- Support ["Really Executable" Capsules](https://github.com/puniverse/capsule#really-executable-capsules)
- Reference docs, look into [Codox](https://github.com/weavejester/codox),
[Marginalia](https://github.com/gdeer81/marginalia) and [lein-html5-docs](https://github.com/tsdh/lein-html5-docs)
- Consider using [core.typed](https://github.com/clojure/core.typed)
- Consider integrating http://yajsw.sourceforge.net/ for services (or other convenient OS service wrapper[s])
- Spot, reshape and publish generally useful logic (right now everything except main plugin's function is either
private or marked as `:internal`) 
- More examples
- Various TODOs in code
- Uberjar-style dedicated profile?

## License

lein-capsule is free software published under the following license:

```
Copyright © 2014 Fabio Tudone

This program and the accompanying materials are dual-licensed under
either the terms of the Eclipse Public License v1.0 as published by
the Eclipse Foundation

  or (per the licensee's choosing)

under the terms of the GNU Lesser General Public License version 3.0
as published by the Free Software Foundation.
