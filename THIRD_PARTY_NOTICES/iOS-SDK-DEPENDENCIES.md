# CascadeEditor iOS SDK dependency notices

This inventory covers the dependencies linked into the distributed
`CascadeEditor.xcframework` for version 1.9.1. CascadeEditor itself is licensed
under the MIT License in the repository root.

## Apache License 2.0

The following linked component families are distributed under the Apache
License 2.0:

- Kotlin standard library 2.3.21
- Compose Multiplatform runtime, animation, foundation, UI, and resources 1.11.1
- Jetpack/AndroidX annotation, collection, lifecycle, navigation-event, and
  saved-state multiplatform artifacts resolved by Compose Multiplatform
- kotlinx.coroutines 1.9.0
- kotlinx.serialization 1.11.0
- kotlinx.atomicfu 0.28.0
- JetBrains Markdown 0.7.7
- Skiko 0.144.6

The full Apache License 2.0 text is included in
`JetBrains-Markdown-Apache-2.0.txt`.

## Skia and native dependencies

Skiko statically links Skia and native libraries used by its Apple rendering
backend. Skia is distributed under the BSD 3-Clause license; its license text is
included in `Skia-BSD-3-Clause.txt`.

The Skiko binary may include the following Skia third-party components:

- Adobe DNG SDK
- Expat
- HarfBuzz
- ICU
- libjpeg-turbo
- libpng
- libwebp
- Piex
- zlib
- Skia modules including skottie, skparagraph, skresources, sksg, skshaper,
  skunicode, and SVG

Their upstream notices and license files are maintained with the corresponding
Skia source revision. The authoritative bundled notice set is available in
Skia's `third_party` tree:

https://skia.googlesource.com/skia/+/main/third_party/

Skiko source and license:

https://github.com/JetBrains/skiko

Skia source and license:

https://skia.googlesource.com/skia/

## Not linked into the SDK

`Geist-OFL.txt` covers the Geist font used by repository samples and
documentation. The font is not linked into `CascadeEditor.xcframework`.
