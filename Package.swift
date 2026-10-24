// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "AssetDropKit",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "AssetDropKit", targets: ["AssetDropKit"]),
    ],
    targets: [
        .target(name: "AssetDropKit", path: "Sources/AssetDropKit"),
        .testTarget(name: "AssetDropKitTests", dependencies: ["AssetDropKit"], path: "Tests/AssetDropKitTests"),
    ]
)
