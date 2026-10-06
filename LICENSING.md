# Licensing

Copyright (C) 2026 Pier Dolique (Perdolique).

Unless a file has its own license notice, repository-owned files outside `worker/` are
licensed under the GNU General Public License, version 3 only (`GPL-3.0-only`). The
full license is in [LICENSE](LICENSE).

Repository-owned files under `worker/` are licensed under the GNU Affero General
Public License, version 3 only (`AGPL-3.0-only`). The full license is in
[worker/LICENSE](worker/LICENSE). This includes the Worker source, tests, configuration,
and documentation.

Both licenses allow commercial use and paid distribution. When distributing a
covered application, you must provide its Corresponding Source under the applicable
license, including your changes. AGPL also requires a modified network service to
offer its Corresponding Source to users who interact with it over a network.

## Mobile app permissions

The mobile app has narrow additional permissions for its Google SDK dependencies
and distribution through Apple's App Store services. Read
[Pole parkla additional permissions](LICENSES/Pole-parkla-exceptions.md).
These permissions do not allow a closed-source fork of the application.

## Third-party material

Third-party code and assets keep their own licenses. The project licenses and
additional permissions do not replace those terms.

| Material | License notice |
| --- | --- |
| Bundled YOLOv9-T and CCT-S ONNX models | [MIT notice](app/src/main/assets/licenses/ankandrew-models-MIT.txt) |
| Inter font files | [SIL Open Font License 1.1](LICENSES/Inter-OFL.txt) |
| Gradle wrapper and generated Cloudflare runtime declarations | Embedded Apache notices and [Apache License 2.0](LICENSES/Apache-2.0.txt) |

Installed dependencies are covered by their own package licenses and notices.
Preserve the notices required by those licenses when distributing a build.

## Test photograph

Pier Dolique (Perdolique) took the photograph used for
`app/src/androidTest/assets/street_plate_test_image.png`. An AI edit changed the main
vehicle plate. The edited fixture is licensed under `GPL-3.0-only`. See its
[provenance](app/src/androidTest/assets/README.md).

## Distributing builds

Provide the license text, applicable additional permissions, copyright notices,
and access to the complete Corresponding Source for the exact version you distribute.
Keep this information available to recipients of store builds as well.
For a modified Worker service, provide the source offer required by AGPL section 13.
