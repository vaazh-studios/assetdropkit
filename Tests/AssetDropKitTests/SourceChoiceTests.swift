import XCTest
@testable import AssetDropKit

final class SourceChoiceTests: XCTestCase {

    func testIOS17And18UseOnDemandResources() {
        XCTAssertEqual(AppleAssetPacks.kind(osMajor: 17, onDemandResourcesOnly: false), .onDemandResources)
        XCTAssertEqual(AppleAssetPacks.kind(osMajor: 18, onDemandResourcesOnly: false), .onDemandResources)
    }

    func testIOS26AndLaterUseAppleHosting() {
        XCTAssertEqual(AppleAssetPacks.kind(osMajor: 26, onDemandResourcesOnly: false), .appleHosted)
        XCTAssertEqual(AppleAssetPacks.kind(osMajor: 27, onDemandResourcesOnly: false), .appleHosted)
    }

    func testXcodeRunsCanForceOnDemandResources() {
        XCTAssertEqual(AppleAssetPacks.kind(osMajor: 26, onDemandResourcesOnly: true), .onDemandResources)
    }
}
