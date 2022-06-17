# Felix Bundle Extractor

Sorts and decompiles your Felix / OSGi bundles in to a searchable directory

## Usage

* Find your felix folder that contains all the OSGi bundles (Apache Sling: sling/launchpad/felix, AEM: crx-quickstart/launchpad/felix)
* Execute the java program with the following command line arguments:

### Mandatory
* -i/--bundlesInputDir: Location of the input bundles or a copy of the directory.
* -o/--bundlesOutputDir: Location where you want your decompiled / sorted jars to be placed

### Optional

* -orfn/--outputResultFileName: Output result json file name (default result.json)
* -t/--threadCount: Thread count of the bundle extractor (default 8)
* -ig/--includedGroupIds: Regex pattern to include specific group ids (default empty)
* -eg/--excludedGroupIds: Regex pattern to exclude specific group ids (default empty)
* -ea/--excludedArtifactIds: Regex pattern to exclude specific artifact ids (default empty)
* -enma/--excludeNonMavenArtifacts: Add argument to disable extraction of non maven artifacts, ex. artifacts that only contain a manifast. (default false)

## Included grep-in bash script

A bash script to allow quick searching in the sources folder. Usage: "bash grep-in-jars.sh ClassName"
