{ stdenv, lib, jre, clojure, callPackage, fetchgit, fetchMavenArtifact, ... }:

let
  base-name = "fudo-clojure";
  project = "org.fudo";
  version = "0.1";
  full-name = "${base-name}-${version}";

  clj-deps =
    callPackage ./deps.nix { inherit fetchgit fetchMavenArtifact lib; };
  classpath = clj-deps.makeClasspaths { };

  pthru = o: builtins.trace o o;

in stdenv.mkDerivation {
  name = "${full-name}.jar";
  src = ./.;
  buildInputs = [ jre clojure ] ++ (map (x: x.paths) clj-deps.packages);
  buildPhase = ''
    HOME=./home
    mkdir -p $HOME

    clojure -Scp .:./src:${classpath} -M:build
    cat /tmp/*.edn
  '';
  installPhase = ''
    cp ./target/${base-name}-${version}-standalone.jar $out
  '';
}
