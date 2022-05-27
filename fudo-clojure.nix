{ lib, stdenv, clojure, gitignoreSource, callPackage, writeText
, writeShellScript, ... }:

let
  base-name = "fudo-clojure";
  version = "0.1";
  full-name = "${base-name}-${version}";

  cljdeps = callPackage ./deps.nix { };

in stdenv.mkDerivation {
  name = full-name;
  src = ./.;
  buildInputs = [ clojure ];
  propagatedBuildInputs = map (d: d.paths) cljdeps.packages;
  buildPhase = ''
    HOME=$TEMP/home
    mkdir -p $HOME
    clojure -X:build jar :version ${version}
  '';
  installPhase = ''
    mkdir -p $lib/share
    mkdir -p $out/share
    cp ./target/${base-name}-${version}.jar $lib/share
    cp ./target/${base-name}-${version}.jar $out/share
  '';
}
