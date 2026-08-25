# Third-party notices

EvoX Quick Fix is licensed under Apache-2.0. The following projects are dependencies, integration targets, or research references. Their licenses apply to their own works.

## Bundled/compiled dependencies

### libxposed API and service

- Projects: https://github.com/libxposed/api and https://github.com/libxposed/service
- License: Apache License 2.0
- Use: the APK compiles against the libxposed API and includes the service client needed to register the narrow Back Guard module.

## External software not bundled

### Vector

- Project: https://github.com/JingMatrix/Vector
- License: GNU General Public License v3.0
- Use: optional external module manager/runtime for Back Guard. Vector binaries and source are not bundled.

### KernelSU

- Project: https://github.com/tiann/KernelSU
- License: GNU General Public License v3.0
- Use: external root and module manager. KernelSU binaries and source are not bundled.

### Magic Mount-rs

- Project: https://github.com/KernelSU-Modules-Repo/magic_mount_rs
- License: Apache License 2.0, excluding the upstream `libs/` and `module/libs/`
  directories as stated by that project.
- Use: external KernelSU metamodule expected by the systemless fixes. No project asset is bundled.

## Data/research references

### Universal Android Debloater Next Generation

- Project: https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- License: GNU General Public License v3.0
- Use: safety/classification research reference. UAD-NG code, list files and descriptions are not bundled.
