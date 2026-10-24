import XCTest
@testable import AssetDropKit

final class StatusMappingTests: XCTestCase {

    func testProgressIsDownloading() {
        let progress = Progress(totalUnitCount: 100)
        progress.completedUnitCount = 40
        XCTAssertEqual(PackStatusMapping.status(of: progress), .downloading(bytes: 40, total: 100))
    }

    func testNoInternetIsNoNetwork() {
        XCTAssertEqual(PackStatusMapping.error(NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)), .noNetwork)
        XCTAssertEqual(PackStatusMapping.error(URLError(.networkConnectionLost)), .noNetwork)
        XCTAssertEqual(PackStatusMapping.error(URLError(.timedOut)), .noNetwork)
    }

    func testAFullDiskIsInsufficientStorage() {
        XCTAssertEqual(PackStatusMapping.error(CocoaError(.fileWriteOutOfSpace)), .insufficientStorage)
        XCTAssertEqual(PackStatusMapping.error(NSError(domain: NSCocoaErrorDomain, code: 4992)), .insufficientStorage, "NSBundleOnDemandResourceOutOfSpaceError")
    }

    func testAnUnknownTagIsNotFound() {
        XCTAssertEqual(PackStatusMapping.error(NSError(domain: NSCocoaErrorDomain, code: 4994)), .notFound, "NSBundleOnDemandResourceInvalidTagError")
    }

    func testCancellationIsCancelled() {
        XCTAssertEqual(PackStatusMapping.error(CancellationError()), .cancelled)
        XCTAssertEqual(PackStatusMapping.error(NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError)), .cancelled)
        XCTAssertEqual(PackStatusMapping.error(URLError(.cancelled)), .cancelled)
    }

    func testAnythingElseIsUnknownWithItsDescription() {
        guard case .unknown(let detail) = PackStatusMapping.error(NSError(domain: "x", code: 7)) else { return XCTFail("not unknown") }
        XCTAssertTrue(detail.contains("7"), detail)
    }
}
