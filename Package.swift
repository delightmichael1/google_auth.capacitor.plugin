// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Googleauth",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "Googleauth",
            targets: ["GoogleAuthPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "GoogleAuthPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/GoogleAuthPlugin"),
        .testTarget(
            name: "GoogleAuthPluginTests",
            dependencies: ["GoogleAuthPlugin"],
            path: "ios/Tests/GoogleAuthPluginTests")
    ]
)