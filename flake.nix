{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-21.05";
    utils.url = "github:numtide/flake-utils";
    clj2nix.url = "github:hlolli/clj2nix";
    gitignore = {
      url = "github:hercules-ci/gitignore.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, clj2nix, gitignore, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages."${system}";
        fudo-clojure = pkgs.callPackage ./fudo-clojure.nix {
          inherit (gitignore.lib) gitignoreSource;
        };
      in {
        packages = utils.lib.flattenTree { inherit fudo-clojure; };
        defaultPackage = self.packages."${system}".fudo-clojure;
        overlay = final: prev: { inherit fudo-clojure; };
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            bash
            clojure
            jre

            clj2nix.packages."${system}".clj2nix
          ];
        };
      });
}
