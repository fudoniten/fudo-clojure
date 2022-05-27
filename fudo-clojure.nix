{ lib, stdenv, clojure, gitignoreSource, callPackage, writeText
, writeShellScript, ... }:

let
  base-name = "fudo-clojure";

  cljdeps = callPackage ./deps.nix { };

in stdenv.mkDerivation {
  name = base-name;
  src = gitignoreSource ./.;
  outputs = [ "lib" ];
  buildInputs = [ clojure ] ++ map (d: d.paths) cljdeps.packages;
  buildPhase = ''
    clojure -T:build jar
  '';
  installPhase = ''
    mkdir $lib
    cp ./target/${base-name}*.jar $lib
  '';
}
