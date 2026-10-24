// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "AssetDropKit",
    // macOS only so `swift test` runs on the host; the library ships for iOS.
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "AssetDropKit", targets: ["AssetDropKit"]),
    ],
    targets: [
        .target(name: "AssetDropKit", path: "Sources/AssetDropKit"),
        .testTarget(name: "AssetDropKitTests", dependencies: ["AssetDropKit"], path: "Tests/AssetDropKitTests"),
    ]
)
