# Getting Started

This tutorial offers a brief introduction to how ORT works, by guiding you through the main steps for running ORT on the
NPM package [mime-types](https://www.npmjs.com/package/mime-types).

In the broadest outline, the steps are:
* Install ORT.
* Analyze the dependencies of `mime-types` using the `analyzer`.
* Scan the source code of `mime-types` and its dependencies using the `scanner`.

## 1. Prerequisites

ORT is tested to run on Linux, macOS, and Windows. This tutorial assumes that you are running on Linux, but it should be
easy to adapt the commands to macOS or Windows.

To run ORT, please ensure that your system meets the prerequisites listed under
[ORT Prerequisites (README)](./README.html#ort-prerequisites)


For some of the supported package managers and SCMs additional tools need to be installed:

* CVS
* Mercurial
* NPM 5.5.1 and Node.js 8.x (required for this tutorial because we scan `mime-types` which is an NPM project)
* SBT
* Subversion
* Yarn 1.3.2

  Note that the OSS toolkit expects specific versions of both Yarn and NPM. Installing the latest may not guarantee and
  error-free run, so please use the command-line switches `--ignore-versions` (Yarn) and `--allow-dynamic-versions`
  (NPM) -- see also *Analyzer* help and *Scanner* help (after [installing ORT](#download-&-install-ort), change to its
  directory and run, for example, ./analyzer/build/install/analyzer/bin/analyzer --help). [curating](#curating-the-metadata)

## 2. Download & Install ORT

In the future, we will provide binaries of the ORT tools, but currently you have to build the tools on your own. First
download the source code from GitHub:

```bash
git clone https://github.com/heremaps/oss-review-toolkit.git
```

To build the tools run:

```bash
cd oss-review-toolkit
./gradlew installDist
```

This will create binaries of the tools in their builds folders, for example the *Analyzer* binary can be found in
`analyzer/build/install/analyzer/bin/analyzer`. To get the command line help for the tool, run it with the `--help` option:

```bash
analyzer/build/install/analyzer/bin/analyzer --help
```

## 3. Download the `mime-types` Source Code

Run a scan on a locally cloned copy of `mime-types`. For reliable results, we use version 2.1.18 (replace
`[mime-types-path]` with the path you want to clone `mime-types` to):

```bash
git clone https://github.com/jshttp/mime-types.git [mime-types-path]
cd [mime-types-path]
git checkout 2.1.18
```

## 4. Run the *Analyzer* on `mime-types`

The next step is to run the `analyzer`. It will create a JSON or YAML output file containing the full dependency tree of
`mime-types` including the meta-data of `mime-types` and its dependencies.

```bash
# The easiest way to run the *Analyzer*. Be aware that the [output-path] directory must not exist.
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path]

# The command above creates the default YAML output. If you prefer JSON run:
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path] -f JSON

# To get the maximum log output, run:
analyzer/build/install/analyzer/bin/analyzer -i [mime-types-path] -o [output-path] --debug --stacktrace
```

The *Analyzer* searches for build files for all supported package managers. In `mime-types`, it should find the
`package.json` file and write the results of the dependency analysis to `package-json-dependencies.yml`:
    
```bash
$ analyzer/build/install/analyzer/bin/analyzer -i ~/git/mime-types -o ~/analyzer-results/mime-types
The following package managers are activated:
        Gradle, Maven, SBT, NPM, PIP
Scanning project path:
        [mime-types-path]
NPM projects found in:
        package.json
Resolving NPM dependencies for '[mime-types-path]/package.json'...
Writing results for
        [mime-types-path]/package.json
to
        [output-path]/mime-types/package-json-dependencies.yml
done.
```

The result file contains information about the `mime-types` package itself, the dependency tree for each scope, and
information about each dependency. The scope names come from the package managers, for NPM packages, these are usually
`dependencies` and `devDependencies`, for Maven, it is `compile`, `runtime`, `test`, and so on.

The structure of the results file is:

```yaml
allowDynamicVersions: false
# Metadata about the mime-types package.
project:
  id:
    provider: "NPM"
    namespace: ""
    name: "mime-types"
    version: "2.1.18"
  declared_licenses:
  - "MIT"
  aliases: []
  vcs: # Raw VCS metadata as provided by the package.
    type: ""
    url: "https://github.com/jshttp/mime-types.git"
    revision: ""
    path: ""
  vcs_processed: # Normalized metadata created by ORT.
    type: "Git"
    url: "https://github.com/jshttp/mime-types.git"
    revision: "076f7902e3a730970ea96cd0b9c09bb6110f1127"
    path: ""
  homepage_url: ""
  # The dependency trees by scope.
  scopes:
  - name: "dependencies"
    delivered: true
    dependencies:
    - namespace: ""
      name: "mime-db"
      version: "1.33.0"
      dependencies: []
      errors: [] # If an error occurred during the dependency analysis of this package it would be in this list.
  - name: "devDependencies"
    delivered: false
    dependencies:
    - namespace: ""
      name: "eslint-config-standard"
      version: "10.2.1"
      dependencies: []
      errors: []
    - namespace: ""
      name: "eslint-plugin-import"
      version: "2.8.0"
      dependencies:
      - namespace: ""
        name: "builtin-modules"
        version: "1.1.1"
        dependencies: []
        errors: []
    # ...
# Detailed metadata about each package from the dependency trees.
packages:
- id:
    provider: "NPM"
    namespace: ""
    name: "abbrev"
    version: "1.0.9"
  declared_licenses:
  - "ISC"
  description: "Like ruby's abbrev module, but in js"
  homepage_url: "https://github.com/isaacs/abbrev-js#readme"
  binary_artifact:
    url: "https://registry.npmjs.org/abbrev/-/abbrev-1.0.9.tgz"
    hash: "91b4792588a7738c25f35dd6f63752a2f8776135"
    hash_algorithm: "SHA-1"
  source_artifact:
    url: ""
    hash: ""
    hash_algorithm: ""
  vcs:
    type: "git"
    url: "git+ssh://git@github.com/isaacs/abbrev-js.git"
    revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
    path: ""
  vcs_processed:
    type: "Git"
    url: "ssh://git@github.com/isaacs/abbrev-js.git"
    revision: "c386cd9dbb1d8d7581718c54d4ba944cc9298d6f"
    path: ""
# ...
#  Finally a list of errors that happened during dependency analysis. Fortunately empty in this case.
errors: []
```

If you try the commands above with a different NPM package that does not have a
[package-lock.json](https://docs.npmjs.com/files/package-locks) (or `npm-shrinkwrap.json` or `yarn.lock`), the analyzer
will terminate with an error message like this:

```
ERROR - Analysis for these projects did not complete successfully:
[npm-project-path]/package.json
```

This means that there have been issues with the dependency resolution of these packages. The reasons for these errors
can be found in the log output of the *Analyzer* or in the results file:

```
Resolving NPM dependencies for '[npm-project-path]/package.json'...
17:11:16.683 ERROR - Resolving dependencies for 'package.json' failed with: No lockfile found in [npm-project-path], dependency versions are unstable.
```

This happens because without a [lockfile](https://docs.npmjs.com/files/package-locks) the versions of transitive
dependencies can change at any time. Therefore, ORT checks for the presence of a lockfile to generate reliable results.
This check can be disabled with the command-line option `--allow-dynamic-versions`.

## 5. Run the *Scanner*

To scan the source code of `mime-types` and its dependencies, the source code of `mime-types` and all its dependencies
needs to be downloaded. This is handled by the *Downloader*, which is integrated in the *Scanner*,
so the scanner automatically downloads the source code if the required VCS metadata can be obtained.

ORT is designed to integrate lots of different scanners and is not limited to license scanners -- technically any tool
that explores the source code of a software package can be integrated. The actual scanner does not have to run on the
same machine, for example, we will soon integrate the [ClearlyDefined](https://clearlydefined.io/) scanner backend 
to perform the actual scanning remotely.

For this tutorial, we will `ScanCode`. You do not have to install it manually, it is automatically be
bootstrapped by the `scanner`.

As with the *Analyzer*, you can get the command line options for the *Scanner* using the `--help` option:

```bash
scanner/build/install/scanner/bin/scanner --help
```

The `mime-types` package has only one dependency in the `dependencies` scope, but a many in the
`devDependencies` scope. Scanning all of the `devDependencies` would take a lot of time, so we will only run the
scanner on the `dependencies` scope in this tutorial. If you also want to scan the `devDependencies`, we strongly
recommend that you configure a cache for the scan results as documented under [scanner](./README.html/#scanner) in the
README file.

```bash
$ scanner/build/install/scanner/bin/scanner -d [analyzer-output-path]/package-json-dependencies.yml -o [scanner-output-path] --scopes dependencies
Using scanner 'ScanCode'.
Limiting scan to scopes [dependencies]
Using processed VcsInfo(type=Git, url=https://github.com/jshttp/mime-types.git, revision=076f7902e3a730970ea96cd0b9c09bb6110f1127, path=).
Original was VcsInfo(type=, url=https://github.com/jshttp/mime-types.git, revision=, path=).
Running ScanCode version 2.2.1.post277.4d68f9377 on directory '[scanner-output-path]/downloads/mime-types/2.1.18'.
Stored ScanCode results in '[scanner-output-path]/scanResults/mime-types-2.1.18_scancode.json'.
Using processed VcsInfo(type=Git, url=https://github.com/jshttp/mime-db.git, revision=e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb, path=).
Original was VcsInfo(type=git, url=git+https://github.com/jshttp/mime-db.git, revision=e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb, path=).
Running ScanCode version 2.2.1.post277.4d68f9377 on directory '[scanner-output-path]/downloads/mime-db/1.33.0'.
Stored ScanCode results in '[scanner-output-path]/scanResults/mime-db-1.33.0_scancode.json'.
Declared licenses for 'NPM::mime-types:2.1.18': MIT
Detected licenses for 'NPM::mime-types:2.1.18': MIT
Declared licenses for 'NPM::mime-db:1.33.0': MIT
Detected licenses for 'NPM::mime-db:1.33.0': MIT
Writing scan summary to [scanner-output-path]/scan-summary.yml.
```

As you can see from the output, the licenses detected by `ScanCode` match the licenses declared by the packages. This is
because we scanned a small and well-maintained package in this example, but if you run the scan on a bigger project you
will see that `ScanCode` often finds more licenses than are declared by the packages.

The `scanner` writes the raw scanner output for each scanned package to a file in the
`[scanner-output-path]/scanResults` directory. Additionally it creates a `[scanner-output-path]/scan-summary.yml` file
which contains a summary of all licenses for all packages and some more details like cache statistics and information
about the scanned scopes.

## 6. Curating the Metadata

In the example above, everything went well because the VCS information provided by the packages was correct, but this is
not always the case. Often the metadata of packages has no VCS information, points to outdated repositories, or the
repositories are not correctly tagged. Because it is not always possible to correct this information in remote packages, ORT provides
a mechanism to curate package metadata.

These curations can be configured in a YAML file that has to be passed to the *Analyzer*. The data from the curations
file will overwrite the metadata provided by the packages themselves. This way, it is possible to fix broken VCS URLs or
provide the location of source artifacts. The structure of the curations file is:

```yaml
# Example for a complete curation object:
#- id: "Maven:org.hamcrest:hamcrest-core:1.3"
#  curations:
#    declared_licenses:
#    - "license a"
#    - "license b"
#    description: "curated description"
#    homepage_url: "http://example.com"
#    binary_artifact:
#      url: "http://example.com/binary.zip"
#      hash: "ddce269a1e3d054cae349621c198dd52"
#      hash_algorithm: "MD5"
#    source_artifact:
#      url: "http://example.com/sources.zip"
#      hash: "ddce269a1e3d054cae349621c198dd52"
#      hash_algorithm: "MD5"
#    vcs:
#      type: "git"
#      url: "http://example.com/repo.git"
#      revision: "1234abc"
#      path: "subdirectory"

# A few examples:

# Repository moved to https://gitlab.ow2.org.
- id: "Maven:asm:asm" # No version means the curation will be applied to all versions of the package.
  curations:
    vcs:
      type: "git"
      url: "https://gitlab.ow2.org/asm/asm.git"

# Revisions found by comparing NPM packages with the sources from https://github.com/olov/ast-traverse.
- id: "NPM::ast-traverse:0.1.0"
  curations:
    vcs:
      revision: "f864d24ba07cde4b79f16999b1c99bfb240a441e"
- id: "NPM::ast-traverse:0.1.1"
  curations:
    vcs:
      revision: "73f2b3c319af82fd8e490d40dd89a15951069b0d"
```

To use the curations file pass it to the `--package-curations-file` option of the *Analyzer*:

```
analyzer/build/install/analyzer/bin/analyzer -i [input-path] -o [output-path] --package-curations-file [curations-file-path]
```

In future, we will integrate [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. Until then,
and also for curations for internal packages that cannot be published, the curations file can be used.
