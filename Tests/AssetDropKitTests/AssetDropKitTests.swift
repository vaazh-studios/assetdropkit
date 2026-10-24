import XCTest
@testable import AssetDropKit

final class AssetDropKitTests: XCTestCase {
    func testVersionMatchesTheKotlinArtifact() {
        XCTAssertEqual(AssetDropKit.version, "0.1.0")
    }
}
