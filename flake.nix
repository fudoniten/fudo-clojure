{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-21.11";
    utils.url = "github:numtide/flake-utils";
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, clj-nix, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages."${system}";
        cljpkgs = clj-nix.packages."${system}";
        update-deps = pkgs.writeShellScriptBin "update-deps.sh" ''
          ${clj-nix.packages."${system}".deps-lock}/bin/deps-lock
        '';
      in {
        packages = {
          fudo-clojure = cljpkgs.mkCljBin {
            projectSrc = ./.;
            name = "org.fudo/fudo-clojure";
            main-ns = "fudo-clojure.core";
            jdkRunner = pkgs.jdk17_headless;

            doCheck = true;
            checkPhase = "clj -X:test";
          };
        };

        packages.default = self.packages."${system}".fudo-clojure;

        devShells.default =
          pkgs.mkShell { buildInputs = with pkgs; [ clojure update-deps ]; };
      });
}
