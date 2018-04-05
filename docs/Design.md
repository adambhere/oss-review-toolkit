# Software Design Considerations

## Analyzer

There are two major design goals for the Analyzer:

1) It must not require any modifications to the project being analyzed. In particular, there must be no requirement for
   a particular build system plugin to be installed before the Analyzer can analyze a project. This is because even for
   source code under our control, we do not want to interfere with development processes determined by the product
   teams, and we do want to avoid vendor lock-in by introducing modifications to the build system that potentially are
   hard to change later on. For source code we do not control, such as that of Open Source projects, it would be practically
   impossible to convince the maintainers to apply any third-party plugins they do not require.

2) The reported meta-data must not be limited to the declared license, but in particular the canonical source code
   location of the specific version of a dependency must be reported. This helps us both to record provenance
   information and download and scan the actual source code for license/copyright information instead
   of relying on declared information.

We looked at similar tools before developing the Analyzer. Please see the table below for an
overview:

| Tool | Works without modifications to the project to analyze | Looks at package manager meta-data | Looks at source code of dependencies |
| ---- | ----------------------------------------------------- | ---------------------------------- | ------------------------------------ |
| https://github.com/pivotal/LicenseFinder | Yes | [Yes](https://github.com/pivotal/LicenseFinder/tree/5e876e6/lib/license_finder/package_managers) | [Limited](https://github.com/pivotal/LicenseFinder/blob/f509e33/lib/license_finder/packages/license_files.rb#L5), and only as a [fallback](https://github.com/pivotal/LicenseFinder/blob/7eedfd9/lib/license_finder/packages/licensing.rb#L6) |
| https://github.com/blackducksoftware/hub-detect | Yes | [Yes](https://github.com/blackducksoftware/hub-detect/tree/41bc385/src/main/groovy/com/blackducksoftware/integration/hub/detect/bomtool) | No (unless you have a commercial Blackduck Hub subscription) |
| https://github.com/librariesio/bibliothecary | Yes (only static parsing) | [Yes](https://github.com/librariesio/bibliothecary/tree/164b149/lib/bibliothecary/parsers) | No |
| https://github.com/snyk/snyk | Yes | [Yes](https://github.com/snyk/snyk/tree/e36ba8f/lib/plugins) | No |

#### Python

- https://github.com/kennethreitz/pipenv

  A new package manager designed around `Pipfile{.lock}` files. Claims to offer
  [import of requirements.txt files](http://docs.pipenv.org/en/latest/advanced.html#importing-from-requirements-txt) and
  [import of setup.py files](https://github.com/kennethreitz/pipenv/issues/592), but it does not actually work, or only
  in very simple cases.

- https://github.com/pivotal/LicenseFinder/blob/b784114/lib/license_finder/package_managers/pip.rb

  Uses a [helper script](https://github.com/pivotal/LicenseFinder/blob/3c073e7/bin/license_finder_pip.py) to parse only
  `requirements.txt` via the `pip.req.parse_requirements` API from Ruby code.

- https://github.com/librariesio/bibliothecary/blob/8704537/lib/bibliothecary/parsers/pypi.rb

  Manually parses `requirements.txt`, `setup.py` and `Pipfile{.lock}` files. Only supports basic syntax.

- https://github.com/librariesio/pydeps/

  Uses `pip download`, but only supports `requirements.txt` files.

- https://github.com/sourcegraph/pydep

  Uses `pip.req.parse_requirements` for `requirements.txt` files (see 
  [this article](http://jelly.codes/articles/python-pip-module/)) and a `distutils.core.setup` replacement to parse
  `setup.py` files (similar to [this approach](https://stackoverflow.com/a/27790447/1127485)). Has JSON output, but
  outputs dependencies in a flat list as opposed to a tree.

- https://github.com/fossas/srclib-pip/

  An add-on for [srclib](https://github.com/sourcegraph/srclib) that manually parses `requirements.txt` files only.

- https://github.com/blackducksoftware/hub-detect/blob/c5665d1/src/main/resources/pip-inspector.py

  Uses the `pip.req.parse_requirements` API. `setup.py` seems to be recognized but not yet handled.

##### Conclusions

While using `pip.req.parse_requirements` to parse `requirements.txt` files and using `distutils.core.setup` to parse
`setup.py` files basically is an elegant solution, that approach cannot handle, for example, custom/extra index URLs defined as
part of `requirements.txt` files such as:

    # common requirements
    -i http://foo.bar.com/ptpi/devpi/dev/+simple/

That is why calling `pip` and inspecting the output is probably the best way to determine the dependencies correctly,
even if that means downloading the dependencies already at a stage, where only need a dependency graph would suffice.
