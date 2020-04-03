# Felix Bundle Extractor

Sorts and decompiles your Felix / OSGi bundles in to a searchable directory

## Usage

- Find your felix folder that contains all the OSGi bundles (Apache Sling: sling/launchpad/felix, AEM: crx-quickstart/launchpad/felix)
- Set bundles.dir.input to this location or copy over so that the folder can be read
- Set a bundles.dir.output where you want your decompiled / sorted jars to be placed
- Start the spring application

### Optional settings

- separate.artifacts.and.sources: Will place the artifacts and sources in two separate folders
- add.grep.in.jars: Adds a bash script to allow quick searching in the sources folder. Usage: "bash grep-in-jars.sh ClassName"