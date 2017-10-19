# OSS Review Toolkit

The OSS Review Toolkit (ORT for short) is a suite of tools to assist with reviewing Open Source Software dependencies in
your software deliverables. The different tools in the suite are designed as libraries (for programmatic use) with
minimal command line interface (for scripted use,
[doing one thing and doing it well](https://en.wikipedia.org/wiki/Unix_philosophy#Do_One_Thing_and_Do_It_Well)).

## Usage

### [analyzer](./analyzer/src/main/kotlin)

The Analyzer determines the dependencies of software projects inside the specified input directory (`-i`). It does so by
querying whatever [supported package manager](./analyzer/src/main/kotlin/managers) is found. No modifications to your
existing project source code, or especially to the build system, are necessary for that to work. The tree of transitive
dependencies per project is written out as [ABCD](https://github.com/nexB/aboutcode/tree/master/aboutcode-data)-style
YAML (or JSON, see `-f`) files to the specified output directory (`-d`) whose inner structure mirrors the one from the
input directory. The output files exactly document the status quo of all package-related meta-data. They can and
probably need to be further processed or manually edited before passing them to one of the other tools.

The `analyzer` command line tool takes the following arguments:

```
Usage: analyzer [options]
  Options:
    --ignore-versions
      Ignore versions of required tools. NOTE: This may lead to erroneous
      results.
      Default: false
    --debug
      Enable debug logging and keep any temporary files.
      Default: false
    --stacktrace
      Print out the stacktrace for all exceptions.
      Default: false
  * --input-dir, -i
      The project directory to scan.
    --info
      Enable info logging.
      Default: false
  * --output-dir, -o
      The directory to write dependency information to.
    --allow-dynamic-versions
      Allow dynamic versions of dependencies. This can result in unstable
      results when dependencies use version ranges. This option only affects
      package managers that support lock files, like NPM.
      Default: false
    --package-managers, -m
      A list of package managers to activate.
      Default: [Gradle, NPM, PIP]
    --output-format, -f
      The data format used for dependency information.
      Default: YAML
      Possible Values: [JSON, YAML]
    --help, -h
      Display the command line help.
```

## License

Copyright (c) 2017 HERE Europe B.V.

See the [LICENSE](./LICENSE) file in the root of this project for license details.