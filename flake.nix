{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-21.05";
    utils.url = "github:numtide/flake-utils";
    clj2nix.url = "github:hlolli/clj2nix";
    # build-tools.url = "git+https://git.fudo.org/fudo-public/build.tools.nix";
    build-tools.url = "path:/net/projects/niten/clojure-build-tools";
  };

  outputs = { self, nixpkgs, utils, clj2nix, build-tools, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        clj2nix-pkg = clj2nix.packages.${system}.clj2nix;
        update-deps = pkgs.writeShellScriptBin "update-deps.sh" ''
          ${clj2nix-pkg}/bin/clj2nix ./deps.edn ./deps.nix
        '';
      in {
        packages = utils.lib.flattenTree {
          fudo-clojure = pkgs.callPackage ./fudo-clojure.nix {
            inherit (build-tools.lib) mkClojureLib;
            inherit (build-tools.packages."${system}") build-tools-jar;
          };
        };
        defaultPackage = self.packages."${system}".fudo-clojure;
        overlay = final: prev: {
          inherit (self.packages.${system}) fudo-clojure;
        };
        devShell."${system}" = pkgs.mkShell {
          buildInputs = with pkgs; [ clojure clj2nix-pkg update-deps ];
        };
      });
}
