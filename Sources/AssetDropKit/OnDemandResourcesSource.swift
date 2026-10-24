import Foundation

/// One On-Demand Resources request for one tag; the real one wraps `NSBundleResourceRequest`.
protocol ResourceRequest: AnyObject, Sendable {
    var progress: Progress { get }
    /// True when the tag is already on the device; access then stays open until `end()`.
    func conditionallyBegin() async -> Bool
    func begin() async throws
    func end()
}

/// Makes requests and reaches the bundle; tests replace it.
protocol ResourceRequestFactory: Sendable {
    func request(tag: String) -> ResourceRequest
    func setPreservationPriority(_ priority: Double, tag: String)
    func contents(tag: String, path: String) throws -> Data
}

/// On-Demand Resources (iOS 17 and 18): each pack is a folder reference tagged with its pack ID.
/// One request per fetched pack is held for the process's life with preservation raised, so the
/// system keeps the pack; `remove` ends access and lowers it, and the system frees the space later.
actor OnDemandResourcesSource: ApplePackSource {

    private let requests: ResourceRequestFactory
    private var held: [String: ResourceRequest] = [:]
    private var inFlight: [String: Task<Void, Error>] = [:]
    private var listeners: [String: [UUID: AsyncStream<PackStatus>.Continuation]] = [:]

    init(requests: ResourceRequestFactory) {
        self.requests = requests
    }

    func status(_ id: String) async -> PackStatus {
        if held[id] != nil { return .ready }
        if inFlight[id] != nil { return .pending }
        let request = requests.request(tag: id)
        guard await request.conditionallyBegin() else { return .notDownloaded }
        if held[id] == nil { held[id] = request } else { request.end() }
        return .ready
    }

    nonisolated func statusUpdates(_ id: String) -> AsyncStream<PackStatus> {
        let key = UUID()
        let (stream, continuation) = AsyncStream<PackStatus>.makeStream()
        continuation.onTermination = { [weak self] _ in
            Task { await self?.stopListening(id, key) }
        }
        Task { await self.listen(id, key, continuation) }
        return stream
    }

    func fetch(_ id: String) async throws {
        if held[id] != nil { return }
        if let running = inFlight[id] { return try await running.value }
        let task = Task { try await self.download(id) }
        inFlight[id] = task
        defer { inFlight[id] = nil }
        try await withTaskCancellationHandler {
            try await task.value
        } onCancel: {
            task.cancel()
        }
    }

    nonisolated func contents(_ id: String, _ path: String) throws -> Data {
        try requests.contents(tag: id, path: path)
    }

    func remove(_ id: String) async {
        held.removeValue(forKey: id)?.end()
        requests.setPreservationPriority(0, tag: id)
        publish(id, .notDownloaded)
    }

    private func download(_ id: String) async throws {
        let request = requests.request(tag: id)
        publish(id, .pending)
        let watcher = ProgressWatcher(request.progress) { [weak self] status in
            Task { await self?.publishProgress(id, status) }
        }
        defer { watcher.stop() }
        do {
            try await withTaskCancellationHandler {
                try await request.begin()
            } onCancel: {
                request.progress.cancel()
            }
            try Task.checkCancellation()
        } catch {
            let mapped = Task.isCancelled ? PackError.cancelled : PackStatusMapping.error(error)
            publish(id, .failed(mapped))
            throw mapped
        }
        held[id] = request
        requests.setPreservationPriority(1.0, tag: id)
        publish(id, .ready)
    }

    private func publishProgress(_ id: String, _ status: PackStatus) {
        guard held[id] == nil, inFlight[id] != nil else { return }
        publish(id, status)
    }

    private func publish(_ id: String, _ status: PackStatus) {
        listeners[id]?.values.forEach { $0.yield(status) }
    }

    private func listen(_ id: String, _ key: UUID, _ continuation: AsyncStream<PackStatus>.Continuation) {
        listeners[id, default: [:]][key] = continuation
    }

    private func stopListening(_ id: String, _ key: UUID) {
        listeners[id]?[key] = nil
    }
}

/// Reports a `Progress` as `.downloading` on every change, without holding the progress across actors.
final class ProgressWatcher: @unchecked Sendable {
    private var observation: NSKeyValueObservation?

    init(_ progress: Progress, onChange: @escaping @Sendable (PackStatus) -> Void) {
        observation = progress.observe(\.completedUnitCount, options: [.new]) { progress, _ in
            onChange(PackStatusMapping.status(of: progress))
        }
    }

    func stop() {
        observation?.invalidate()
        observation = nil
    }
}

#if os(iOS) || os(tvOS) || os(visionOS)
/// The real request: `NSBundleResourceRequest` at urgent priority.
final class BundleResourceRequest: ResourceRequest, @unchecked Sendable {
    private let request: NSBundleResourceRequest

    init(tag: String) {
        request = NSBundleResourceRequest(tags: [tag])
        request.loadingPriority = NSBundleResourceRequestLoadingPriorityUrgent
    }

    var progress: Progress { request.progress }

    func conditionallyBegin() async -> Bool {
        await request.conditionallyBeginAccessingResources()
    }

    func begin() async throws {
        try await request.beginAccessingResources()
    }

    func end() {
        request.endAccessingResources()
    }
}

/// Requests against the main bundle; a pack's files are found as `<tag>/<path>`.
struct MainBundleRequests: ResourceRequestFactory {
    func request(tag: String) -> ResourceRequest { BundleResourceRequest(tag: tag) }

    func setPreservationPriority(_ priority: Double, tag: String) {
        Bundle.main.setPreservationPriority(priority, forTags: [tag])
    }

    func contents(tag: String, path: String) throws -> Data {
        let full = "\(tag)/\(path)" as NSString
        guard let url = Bundle.main.url(
            forResource: full.lastPathComponent,
            withExtension: nil,
            subdirectory: full.deletingLastPathComponent
        ) else { throw PackError.notFound }
        return try Data(contentsOf: url, options: .mappedIfSafe)
    }
}
#endif
