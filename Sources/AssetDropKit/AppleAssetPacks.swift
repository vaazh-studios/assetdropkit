import Foundation

/// The Apple side of AssetDropKit: Apple-hosted Background Assets on iOS 26 and later,
/// On-Demand Resources on iOS 17 and 18. The app's adapter (templates/ios/AssetDropAdapter.swift)
/// connects it to Kotlin.
public final class AppleAssetPacks: Sendable {

    enum Kind: Equatable {
        case appleHosted
        case onDemandResources
    }

    static func kind(osMajor: Int, onDemandResourcesOnly: Bool) -> Kind {
        osMajor >= 26 && !onDemandResourcesOnly ? .appleHosted : .onDemandResources
    }

    let kind: Kind
    private let source: any ApplePackSource

    /// `onDemandResourcesOnly` keeps iOS 26 on On-Demand Resources, for Xcode runs that
    /// have no Apple-hosted pack to fetch (Xcode hosts the ODR tags itself).
    public convenience init(onDemandResourcesOnly: Bool = false) {
        if #available(iOS 26, macOS 26, tvOS 26, visionOS 26, *), !onDemandResourcesOnly {
            #if canImport(BackgroundAssets) && !os(watchOS)
            self.init(source: AppleHostedSource(), kind: .appleHosted)
            return
            #endif
        }
        self.init(source: OnDemandResourcesSource(requests: Self.bundleRequests()), kind: .onDemandResources)
    }

    init(source: any ApplePackSource, kind: Kind) {
        self.source = source
        self.kind = kind
    }

    /// The pack's state on this device, read from the system.
    public func status(_ packId: String) async -> PackStatus { await source.status(packId) }

    /// Progress and state changes while a fetch runs.
    public func statusUpdates(_ packId: String) -> AsyncStream<PackStatus> { source.statusUpdates(packId) }

    /// Downloads the pack if needed and keeps it on the device. Throws `PackError`; cancelling
    /// the task cancels the download.
    public func fetch(_ packId: String) async throws { try await source.fetch(packId) }

    /// A file of a ready pack; `path` is inside the pack, as `town/sedan.glb`.
    public func contents(_ packId: String, _ path: String) throws -> Data { try source.contents(packId, path) }

    /// Lets the system free the pack's space; the next fetch downloads it again.
    public func remove(_ packId: String) async { await source.remove(packId) }

    private static func bundleRequests() -> ResourceRequestFactory {
        #if os(iOS) || os(tvOS) || os(visionOS)
        MainBundleRequests()
        #else
        UnsupportedRequests()
        #endif
    }
}

#if !(os(iOS) || os(tvOS) || os(visionOS))
/// On-Demand Resources exist only on iOS, tvOS and visionOS.
struct UnsupportedRequests: ResourceRequestFactory {
    final class Request: ResourceRequest, @unchecked Sendable {
        let progress = Progress()
        func conditionallyBegin() async -> Bool { false }
        func begin() async throws { throw PackError.notFound }
        func end() {}
    }
    func request(tag: String) -> ResourceRequest { Request() }
    func setPreservationPriority(_ priority: Double, tag: String) {}
    func contents(tag: String, path: String) throws -> Data { throw PackError.notFound }
}
#endif
