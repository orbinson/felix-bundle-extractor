# Felix Bundle Extractor

Sorts and decompiles your Felix / OSGi bundles in to a searchable directory

## Usage

* Find your felix folder that contains all the OSGi bundles (Apache Sling: sling/launchpad/felix, AEM: crx-quickstart/launchpad/felix)
* Execute the java program with the following command line arguments:
    * bundles.input.dir: Location of the input bundles or a copy of the directory.
    * bundles.output.dir: Location where you want your decompiled / sorted jars to be placed

## Included grep-in bash script

A bash script to allow quick searching in the sources folder. Usage: "bash grep-in-jars.sh ClassName"