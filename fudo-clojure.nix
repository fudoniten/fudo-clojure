{ mkClojureLib, build-tools-jar, callPackage, ... }:

let clj-deps = callPackage ./deps.nix { };
in mkClojureLib {
  inherit build-tools-jar clj-deps;
  name = "fudo-clojure";
  group = "org.fudo";
  version = "0.1";
  src = ./.;
  deps-edn = ./deps.edn;
  src-paths = [ "src" ];
}
