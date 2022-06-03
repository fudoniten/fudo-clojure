{ stdenv, lib, clojure, callPackage, fetchgit, fetchMavenArtifact, ... }:

let
  base-name = "fudo-clojure";
  project = "org.fudo";
  version = "0.1";
  full-name = "${base-name}-${version}";

  clj-deps =
    callPackage ./deps.nix { inherit fetchgit fetchMavenArtifact lib; };
  classpath = clj-deps.makeClasspaths { };

in stdenv.mkDerivation {
  name = "${full-name}.jar";
  src = ./.;
  buildInputs = [ clojure ] ++ (map (x: x.paths) clj-deps.packages);
  buildPhase = ''
    HOME=./home
    mkdir -p $HOME
    clojure -Scp ./src:${classpath} -X:build build/uberjar :project ${project}/${base-name} :version ${version}
  '';
  installPhase = ''
    cp ./target/${base-name}-${version}-standalone.jar $out
  '';
}
