{
  description = "Fudo Clojure utilities.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-22.05";
    clj2nix.url = "github:hlolli/clj2nix";
  };

  outputs = { self, nixpkgs, clj2nix, ... }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      clj2nix-pkg = clj2nix.packages.${system}.clj2nix;
      update-deps = pkgs.writeShellScriptBin "update-deps.sh" ''
        ${clj2nix-pkg}/bin/clj2nix ./deps.edn ./deps.nix
      '';
    in {
      packages."${system}".fudo-clojure =
        pkgs.callPackage ./fudo-clojure.nix { };
      defaultPackage."${system}" = self.packages."${system}".fudo-clojure;
      overlay = final: prev: {
        inherit (self.packages.${system}) fudo-clojure;
      };
      devShell."${system}" = pkgs.mkShell {
        buildInputs = with pkgs; [ clojure clj2nix-pkg update-deps ];
      };
    };
}
