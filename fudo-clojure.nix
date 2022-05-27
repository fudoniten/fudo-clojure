{ lib, stdenv, clojure, gitignoreSource, callPackage, writeText
, writeShellScript, ... }:

let
  base-name = "fudo-clojure";

  cljdeps = callPackage ./deps.nix { };

in stdenv.mkDerivation {
  name = base-name;
  src = gitignoreSource ./.;
  buildInputs = [ clojure ];
  propagatedBuildInputs = map (d: d.paths) cljdeps.packages;
  buildPhase = ''
    HOME=$TEMP/home
    mkdir -p $HOME
    clojure -X:build jar
  '';
  installPhase = ''
    mkdir -p $lib/share
    mkdir -p $out/share
    cp ./target/${base-name}*.jar $lib/share
    cp ./target/${base-name}*.jar $out/share
  '';
}
