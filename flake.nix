{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-23.11";
    utils.url = "github:numtide/flake-utils";
    helpers = {
      url = "git+https://fudo.dev/public/nix-helpers.git";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, helpers, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages."${system}";
        inherit (helpers.packages."${system}") mkClojureLib mkClojureBin;
      in {
        packages = rec {
          default = fudo-clojure;
          fudo-clojure-bin = mkClojureBin {
            name = "org.fudo/fudo-clojure-bin";
            primaryNamespace = "fudo-clojure.core";
            src = ./.;
            checkPhase = "clj -X:test";
          };
          fudo-clojure = mkClojureLib {
            name = "org.fudo/fudo-clojure";
            src = ./.;
          };
        };

        devShells = rec {
          default = updateDeps;
          updateDeps = pkgs.mkShell {
            buildInputs = with helpers.packages."${system}";
              [ (updateClojureDeps { }) ];
          };
        };
      });
}
