#!/bin/bash
# This script runs (or creates) a self-installing bundle.
#
# A self-installing bundle is really just a bash script
# and a tar.gz file concatenated into a single file.
# The two parts of the file are separated by a marker
# string (__MARKER__ in this script). 
#
# When the bundle is run, the bash script extracts the
# tar.gz file (everything after __MARKER__) to a temp.
# directory. If the extracted files include an executable
# file named "install", that file is executed, which
# allows for custom installation scripts to run.
#
# This makes it easier to release a single bundle of files
# which can be installed.
#
# To extract a bundle without running "install",
# use the -x flag.
#
# This script can also create a self-installing bundle
# from a directory. Run:
#   ./bundler -c ./your-bundle-dir

THIS=$0

usage() {
cat >&2 <<USAGE
Usage:
./installer [-x] [-h] [-r] [-c bundle-dir/]

  -c    Create an installer from the given bundle directory.
  -x    Extract without running installer.
  -r    Remove installer header. Result is a .tar.gz file.
  -h    Print help.
USAGE
}

# TODO extract should take argument to extract to

# Create a bundle from a directory. Writes to ./bundle.run
create() {
  THISDIR=`pwd`
  echo "Creating ${THISDIR}/bundle.run" >&2
  TARDIR=`mktemp -d /tmp/bundler.XXXXXX`
  cd $1 && tar -czf $TARDIR/bundle.tar.gz ./*
  cd $THISDIR
  # Prepend this script
  cat $THIS > bundle.run
  # Append archive marker
  printf '\n__MARKER__\n' >> bundle.run
  # Append archive
  cat $TARDIR/bundle.tar.gz >> bundle.run
  chmod +x bundle.run
}

# Install the bundle.
install() {
  if [ -z $TARBALL ]; then
    usage;
    exit 1
  fi

  echo "Installing" >&2
  # Create temp. dir to extract into
  EXDIR=`mktemp -d /tmp/bundler.XXXXXX`
  # Get the archive.
  tail -n+$TARBALL $THIS | tar xzv -C $EXDIR
  # Switch to extracted dir and run install script.
  SAVEDIR=`pwd`
  cd $EXDIR
  ./install
  # Clean up.
  cd $SAVEDIR
  rm -rf $EXDIR
}

# Extract the bundle without installing.
extract() {
  if [ -z $TARBALL ]; then
    echo "Error: can't extract. This is not a bundle." >&2
    exit 1
  fi

  echo "Extracting" >&2
  # Extract the archive without installing.
  tail -n+$TARBALL $THIS | tar xzv
}

# Remove the bundler/installer script from the tarball.
remove() {
  if [ -z $TARBALL ]; then
    echo "Error: can't remove header. This is not a bundle." >&2
    exit 1
  fi

  echo "Removing header" >&2
  tail -n+$TARBALL $THIS > bundle.tar.gz
}

# Find the archive marker
TARBALL=`awk '/^__MARKER__/ {print NR + 1; exit 0; }' $0`

while getopts ":c:xrh" opt; do
case $opt in
  c) create $OPTARG; exit 0;;
  r) remove; exit 0;;
  x) extract; exit 0;;
  h) usage; exit 1;;
  : | \?) printf "Error: unknown arguments\n\n" >&2; usage; exit 1;;
esac
done

install
exit 0
