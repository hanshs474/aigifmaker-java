#!/bin/bash
# build.sh: compile, test, and lay out the Central bundle (jar, sources, javadoc, pom, checksums) in build/bundle. No Maven needed.
# Signing is not done here: before upload, sign every jar/pom in build/bundle with GPG (.asc) and zip build/bundle.
set -e
cd "$(dirname "$0")"
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/sdk/jdk-17.0.20.1+1/Contents/Home" ]; then export JAVA_HOME=$HOME/sdk/jdk-17.0.20.1+1/Contents/Home; fi
[ -n "$JAVA_HOME" ] && export PATH=$JAVA_HOME/bin:$PATH
V=0.1.1; A=aigifmaker; G=io/github/hanshs474
rm -rf build; mkdir -p build/classes build/test build/doc
javac --release 11 -d build/classes $(find src/main -name '*.java')
javac --release 11 -cp build/classes -d build/test src/test/java/io/github/hanshs474/aigifmaker/AigifmakerTest.java
java -cp build/classes:build/test io.github.hanshs474.aigifmaker.AigifmakerTest
(cd build/classes && jar cf ../$A-$V.jar .)
(cd src/main/java && jar cf ../../../build/$A-$V-sources.jar .)
javadoc -quiet --release 11 -d build/doc -sourcepath src/main/java io.github.hanshs474.aigifmaker
(cd build/doc && jar cf ../$A-$V-javadoc.jar .)
cp pom.xml build/$A-$V.pom
D=build/bundle/$G/$A/$V; mkdir -p $D
cp build/$A-$V.jar build/$A-$V-sources.jar build/$A-$V-javadoc.jar build/$A-$V.pom $D/
for f in $D/*.jar $D/*.pom; do
  printf '%s' "$(openssl dgst -md5 -r $f | cut -d' ' -f1)" > $f.md5
  printf '%s' "$(openssl dgst -sha1 -r $f | cut -d' ' -f1)" > $f.sha1
done
ls $D
