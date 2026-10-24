import XCTest
@testable import AssetDropKit

/** A request whose begin() reports progress then succeeds, or fails with `failure`. */
final class FakeRequest: ResourceRequest, @unchecked Sendable {
    let tag: String
    let progress = Progress(totalUnitCount: 10)
    private let lock = NSLock()
    private var _begins = 0
    private var _ends = 0
    var failure: Error?
    var available = false
    var hang = false

    init(tag: String) { self.tag = tag }

    var begins: Int { lock.withLock { _begins } }
    var ends: Int { lock.withLock { _ends } }

    func conditionallyBegin() async -> Bool { available }

    func begin() async throws {
        lock.withLock { _begins += 1 }
        progress.completedUnitCount = 5
        if hang {
            while !progress.isCancelled { try await Task.sleep(nanoseconds: 1_000_000) }
            throw CocoaError(.userCancelled)
        }
        if let failure { throw failure }
        progress.completedUnitCount = 10
    }

    func end() { lock.withLock { _ends += 1 } }
}

final class FakeRequests: ResourceRequestFactory, @unchecked Sendable {
    private let lock = NSLock()
    private var _made: [FakeRequest] = []
    private var _priorities: [String: Double] = [:]
    var next: (String) -> FakeRequest = { FakeRequest(tag: $0) }
    var files: [String: Data] = [:]

    var made: [FakeRequest] { lock.withLock { _made } }
    func priority(_ tag: String) -> Double? { lock.withLock { _priorities[tag] } }

    func request(tag: String) -> ResourceRequest {
        let request = next(tag)
        lock.withLock { _made.append(request) }
        return request
    }

    func setPreservationPriority(_ priority: Double, tag: String) {
        lock.withLock { _priorities[tag] = priority }
    }

    func contents(tag: String, path: String) throws -> Data {
        guard let data = files["\(tag)/\(path)"] else { throw CocoaError(.fileReadNoSuchFile) }
        return data
    }
}

final class OnDemandLifecycleTests: XCTestCase {

    func testFetchHoldsTheRequestAndRaisesPreservation() async throws {
        let requests = FakeRequests()
        let source = OnDemandResourcesSource(requests: requests)
        try await source.fetch("p")
        XCTAssertEqual(requests.made.count, 1)
        XCTAssertEqual(requests.made[0].begins, 1)
        XCTAssertEqual(requests.made[0].ends, 0, "access is held for the process's life")
        XCTAssertEqual(requests.priority("p"), 1.0)
        let status = await source.status("p")
        XCTAssertEqual(status, .ready)
    }

    func testASecondFetchReusesTheHeldRequest() async throws {
        let requests = FakeRequests()
        let source = OnDemandResourcesSource(requests: requests)
        try await source.fetch("p")
        try await source.fetch("p")
        XCTAssertEqual(requests.made.count, 1)
        XCTAssertEqual(requests.made[0].begins, 1)
    }

    func testConcurrentFetchesShareOneRequest() async throws {
        let requests = FakeRequests()
        let source = OnDemandResourcesSource(requests: requests)
        async let a: Void = source.fetch("p")
        async let b: Void = source.fetch("p")
        _ = try await (a, b)
        XCTAssertEqual(requests.made.filter { $0.begins > 0 }.count, 1)
    }

    func testRemoveEndsAccessAndLowersPreservation() async throws {
        let requests = FakeRequests()
        let source = OnDemandResourcesSource(requests: requests)
        try await source.fetch("p")
        await source.remove("p")
        XCTAssertEqual(requests.made[0].ends, 1)
        XCTAssertEqual(requests.priority("p"), 0.0)
        requests.next = { let r = FakeRequest(tag: $0); r.available = false; return r }
        let status = await source.status("p")
        XCTAssertEqual(status, .notDownloaded)
    }

    func testStatusAfterRelaunchAsksTheSystemAndKeepsAccess() async {
        let requests = FakeRequests()
        requests.next = { let r = FakeRequest(tag: $0); r.available = true; return r }
        let source = OnDemandResourcesSource(requests: requests)
        let status = await source.status("p")
        XCTAssertEqual(status, .ready)
        XCTAssertEqual(requests.made[0].ends, 0, "a pack found on the device stays accessible")
    }

    func testAFailedFetchHoldsNothingAndReportsTheMappedError() async {
        let requests = FakeRequests()
        requests.next = { let r = FakeRequest(tag: $0); r.failure = URLError(.notConnectedToInternet); return r }
        let source = OnDemandResourcesSource(requests: requests)
        do {
            try await source.fetch("p")
            XCTFail("fetch succeeded")
        } catch {
            XCTAssertEqual(error as? PackError, .noNetwork)
        }
        let status = await source.status("p")
        XCTAssertEqual(status, .notDownloaded)
    }

    func testCancellingTheTaskCancelsTheDownload() async {
        let requests = FakeRequests()
        requests.next = { let r = FakeRequest(tag: $0); r.hang = true; return r }
        let source = OnDemandResourcesSource(requests: requests)
        let task = Task { try await source.fetch("p") }
        while requests.made.first?.begins != 1 { await Task.yield() }
        task.cancel()
        do {
            try await task.value
            XCTFail("fetch succeeded")
        } catch {
            XCTAssertEqual(error as? PackError, .cancelled)
        }
        XCTAssertTrue(requests.made[0].progress.isCancelled)
    }

    func testStatusUpdatesReportProgressThenReady() async throws {
        let requests = FakeRequests()
        let source = OnDemandResourcesSource(requests: requests)
        let updates = source.statusUpdates("p")
        let collected = Task { () -> [PackStatus] in
            var seen: [PackStatus] = []
            for await status in updates {
                seen.append(status)
                if status == .ready { break }
            }
            return seen
        }
        try await Task.sleep(nanoseconds: 20_000_000)
        try await source.fetch("p")
        let seen = await collected.value
        XCTAssertEqual(seen.first, .pending)
        XCTAssertEqual(seen.last, .ready)
    }

    func testContentsReadsUnderThePackFolder() throws {
        let requests = FakeRequests()
        requests.files["p/town/a.txt"] = Data("hi".utf8)
        let source = OnDemandResourcesSource(requests: requests)
        XCTAssertEqual(try source.contents("p", "town/a.txt"), Data("hi".utf8))
        XCTAssertThrowsError(try source.contents("p", "missing.txt"))
    }
}
