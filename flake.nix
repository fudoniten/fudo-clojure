{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-21.05";
    utils.url = "github:numtide/flake-utils";
    clj2nix.url = "github:hlolli/clj2nix";
  };

  outputs = { self, nixpkgs, utils, clj2nix, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages."${system}";
        fudo-clojure = pkgs.callPackage ./fudo-clojure.nix { };
      in {
        packages = utils.lib.flattenTree { inherit fudo-clojure; };
        defaultPackage = self.packages."${system}".fudo-clojure;
        overlay = final: prev: { inherit fudo-clojure; };
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            clojure
            jre

            clj2nix.packages."${system}".clj2nix
          ];
        };
      });
}
